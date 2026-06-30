package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Happy-Path-Basistests fuer [GameViewModel]: In-Memory-Room-DB ueber die echten
 * Repositories. Prueft die Kopplung von Eingabe an die Spiel-Logik, die
 * throw-level-Persistenz, Bust-/Checkout-Verhalten und In-Turn-Undo.
 *
 * Die DB nutzt einen synchronen (direkten) Executor, damit die im
 * [GameViewModel] feuernde Persistenz unter [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * deterministisch abgeschlossen ist, bevor die Assertions lesen. Edge-Cases
 * deckt das Test-Gate ab. Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private lateinit var matchRepository: MatchRepository
    private lateinit var playerRepository: PlayerRepository

    @Before
    fun setUp() {
        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        )
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        matchRepository = MatchRepository(
            matchDao = db.matchDao(),
            legDao = db.legDao(),
            turnDao = db.turnDao(),
            throwDao = db.throwDao(),
            matchPlayerDao = db.matchPlayerDao(),
        )
        playerRepository = PlayerRepository(db.playerDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- Helfer ---------------------------------------------------------------

    private suspend fun newPlayer(name: String = "Tom"): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    private fun viewModel(
        playerId: Long,
        config: GameConfig = GameConfig(startScore = 501, doubleOut = true, legsToWin = 1, setsToWin = 1),
    ) = GameViewModel(matchRepository, playerRepository, playerId, config)

    private suspend fun GameViewModel.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    // --- Tests ----------------------------------------------------------------

    @Test
    fun init_legtMatchSpielerUndLegAnUndIstPlaying() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val playerId = newPlayer("Tom")
        val vm = viewModel(playerId)

        val playing = vm.awaitPlaying()
        assertEquals("Tom", playing.playerName)
        assertEquals(501, playing.startScore)
        assertEquals(501, playing.remaining)
        assertTrue(playing.input.darts.isEmpty())

        val matches = matchRepository.getMatches()
        assertEquals(1, matches.size)
        assertEquals("X01", matches.first().modeType)
        assertEquals(501, matches.first().startScore)

        val matchPlayers = matchRepository.getMatchPlayers(matches.first().id)
        assertEquals(1, matchPlayers.size)
        assertEquals(playerId, matchPlayers.first().playerId)

        val legs = matchRepository.getLegs(matches.first().id)
        assertEquals(1, legs.size)
        assertEquals(1, legs.first().legNumber)
    }

    @Test
    fun regulaereAufnahme_reduziertRestUndPersistiertTurnUndThrows() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            vm.awaitPlaying()

            vm.onNumber(20) // Single 20 -> 481
            vm.onNumber(20) // -> 461
            vm.onNumber(20) // -> 441, Aufnahme endet, neue Aufnahme beginnt

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals(441, playing.remaining)
            assertTrue("neue Aufnahme leer", playing.input.darts.isEmpty())

            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(1, turns.size)
            assertEquals(60, turns.first().totalScored)
            assertFalse(turns.first().bust)
            assertEquals(0, turns.first().turnIndex)

            val throws = matchRepository.getThrows(turns.first().id)
            assertEquals(3, throws.size)
            assertEquals(listOf(1, 2, 3), throws.map { it.dartIndex })
            assertEquals(listOf(20, 20, 20), throws.map { it.value })
        }

    @Test
    fun bust_feuertEreignisLaesstRestUnveraendertUndPersistiertBustTurn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId, GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1))
            vm.awaitPlaying()

            val bustBefore = vm.bustEvents.value

            vm.onNumber(20) // Single 20 -> Rest 20
            vm.onToggleTriple()
            vm.onNumber(20) // Triple 20 = 60 > 20 -> Bust

            // Bust-Ereignis hochgezaehlt.
            assertEquals(bustBefore + 1, vm.bustEvents.value)

            val playing = vm.uiState.value as GameUiState.Playing
            // Rest zurueck auf Aufnahme-Start (40), neue Aufnahme.
            assertEquals(40, playing.remaining)
            assertTrue(playing.input.darts.isEmpty())

            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(1, turns.size)
            assertTrue(turns.first().bust)
            assertEquals(0, turns.first().totalScored)

            val throws = matchRepository.getThrows(turns.first().id)
            assertEquals(2, throws.size)
            assertEquals(listOf(20, 60), throws.map { it.value })
        }

    @Test
    fun checkout_fuehrtZuWonUndSchliesstLegUndMatchAb() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer("Win")
            val vm = viewModel(playerId, GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1))
            vm.awaitPlaying()

            vm.onToggleDouble()
            vm.onNumber(20) // Double 20 = 40 -> Checkout

            val won = vm.uiState.value as GameUiState.Won
            assertEquals("Win", won.playerName)
            assertEquals(1, won.dartsUsed)

            val match = matchRepository.getMatches().first()
            assertTrue("Match endedAt gesetzt", match.endedAt != null)
            assertEquals(playerId, match.winnerId)

            val leg = matchRepository.getLegs(match.id).first()
            assertTrue("Leg endedAt gesetzt", leg.endedAt != null)
            assertEquals(playerId, leg.winnerId)

            val turns = matchRepository.getTurns(leg.id)
            assertEquals(1, turns.size)
            assertEquals(40, turns.first().totalScored)
            assertEquals(1, matchRepository.getThrows(turns.first().id).size)
        }

    @Test
    fun onUndo_stelltRestUndEingabeVorAufnahmeEndeZurueck() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            vm.awaitPlaying()

            vm.onNumber(20) // -> 481
            vm.onNumber(5) // -> 476
            val before = vm.uiState.value as GameUiState.Playing
            assertEquals(476, before.remaining)
            assertEquals(2, before.input.darts.size)

            vm.onUndo()

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(481, after.remaining)
            assertEquals(1, after.input.darts.size)
        }

    @Test
    fun ungueltigeSpielerId_fuehrtZuNoPlayer() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel(playerId = 9999L)

        val state = vm.uiState.first { it !is GameUiState.Loading }
        assertTrue(state is GameUiState.NoPlayer)
        assertTrue(matchRepository.getMatches().isEmpty())
    }
}
