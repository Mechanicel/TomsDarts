package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import com.mechanicel.tomsdarts.ui.input.DartModifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Edge-Case-, Fehlerpfad- und Regressionstests fuer [GameViewModel] als Ergaenzung
 * zu den Happy-Path-Basistests in [GameViewModelTest]. Haertet die Kopplung von
 * Eingabe an die Spiel-Logik und die throw-level-Persistenz ueber In-Memory-Room
 * (echte Repositories) ab:
 *
 * - mehrere vollstaendige Aufnahmen (turnIndex, Turn-/Throw-Persistenz, remaining),
 * - Bust mitten im Leg (Event, Revert auf Aufnahme-Start, persistierter Bust-Turn),
 * - Checkout/Won inkl. dartsUsed-IST-Verhalten und Leg/Match-Abschluss,
 * - onUndo (mehrfach, leere Aufnahme, nach Aufnahme-Ende),
 * - onNewLeg aus dem Won-Zustand,
 * - ungueltige Spieler-ID -> NoPlayer,
 * - Toggle-Modifier (Double-Wertung + Auto-Reset).
 *
 * Setup identisch zu [GameViewModelTest]: synchroner (direkter) Room-Executor,
 * damit die im [GameViewModel] fire-and-forget feuernde Persistenz unter
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] deterministisch
 * abgeschlossen ist, bevor die Assertions lesen. Laeuft host-seitig unter
 * Robolectric (SDK 34 gepinnt). Bleibt rein lokal (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelEdgeCasesTest {

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

    private suspend fun GameViewModel.awaitWon(): GameUiState.Won =
        uiState.first { it is GameUiState.Won } as GameUiState.Won

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    // --- Mehrere Aufnahmen ----------------------------------------------------

    @Test
    fun mehrereAufnahmen_erhoehenTurnIndexUndPersistierenProAufnahmeGenauEinenTurn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Aufnahme 1: 3x Single 20 -> 60 -> Rest 441.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            // Aufnahme 2: 3x Single 20 -> 60 -> Rest 381.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            // Aufnahme 3: 3x Single 19 -> 57 -> Rest 324.
            vm.onNumber(19); vm.onNumber(19); vm.onNumber(19)

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals(324, playing.remaining)
            assertTrue("neue Aufnahme ist leer", playing.input.darts.isEmpty())

            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals("genau ein Turn pro abgeschlossener Aufnahme", 3, turns.size)
            assertEquals(listOf(0, 1, 2), turns.map { it.turnIndex })
            assertEquals(listOf(60, 60, 57), turns.map { it.totalScored })
            assertTrue("keine Aufnahme war Bust", turns.none { it.bust })

            // Pro Aufnahme genau 3 Throws mit korrekten Indizes.
            turns.forEach { turn ->
                val throws = matchRepository.getThrows(turn.id)
                assertEquals(3, throws.size)
                assertEquals(listOf(1, 2, 3), throws.map { it.dartIndex })
            }
        }

    // --- Bust mitten im Leg ---------------------------------------------------

    @Test
    fun bustMitteImLeg_feuertEreignisRevertiertAufAufnahmeStartUndPersistiertBustDarts() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(
                playerId,
                GameConfig(startScore = 100, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Aufnahme 1: 3x Single 20 = 60 -> Rest 40.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            assertEquals(40, (vm.uiState.value as GameUiState.Playing).remaining)

            val bustBefore = vm.bustEvents.value

            // Aufnahme 2 (Start-Rest 40): Single 5 -> 35, dann Triple 20 = 60 -> Bust.
            vm.onNumber(5)
            vm.onToggleTriple()
            vm.onNumber(20)

            // Bust-Ereignis genau einmal hochgezaehlt.
            assertEquals(bustBefore + 1, vm.bustEvents.value)

            val playing = vm.uiState.value as GameUiState.Playing
            // Revert auf Aufnahme-Start (40), NICHT auf den Leg-Start (100).
            assertEquals(40, playing.remaining)
            assertTrue(playing.input.darts.isEmpty())

            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(2, turns.size)

            val bustTurn = turns.single { it.turnIndex == 1 }
            assertTrue("zweite Aufnahme ist Bust", bustTurn.bust)
            assertEquals("Bust wertet 0", 0, bustTurn.totalScored)

            // Geworfene Darts inkl. ueberwerfendem Dart sind persistiert.
            val throws = matchRepository.getThrows(bustTurn.id)
            assertEquals(2, throws.size)
            assertEquals(listOf(1, 2), throws.map { it.dartIndex })
            assertEquals(listOf(5, 60), throws.map { it.value })
        }

    // --- Checkout / Won -------------------------------------------------------

    @Test
    fun checkoutNachBust_setztWonUndSchliesstLegMatchAbUndDartsUsedZaehltBustDartsMit() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer("Win")
            val vm = viewModel(
                playerId,
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Aufnahme 1 (Rest 40): Single 20 -> 20, dann Triple 20 = 60 -> Bust (2 Darts).
            vm.onNumber(20)
            vm.onToggleTriple()
            vm.onNumber(20)
            assertEquals(40, (vm.uiState.value as GameUiState.Playing).remaining)

            // Aufnahme 2 (Rest 40): Double 20 = 40 -> Checkout (1 Dart).
            vm.onToggleDouble()
            vm.onNumber(20)

            val won = vm.awaitWon()
            assertEquals("Win", won.playerName)
            // IST-Verhalten: dartsUsed zaehlt JEDEN akzeptierten Dart im Leg,
            // inkl. der beiden Bust-Darts -> 2 (Bust) + 1 (Checkout) = 3.
            assertEquals(3, won.dartsUsed)

            val match = matchRepository.getMatches().first()
            assertNotNull("Match endedAt gesetzt", match.endedAt)
            assertEquals(playerId, match.winnerId)

            val leg = matchRepository.getLegs(match.id).first()
            assertNotNull("Leg endedAt gesetzt", leg.endedAt)
            assertEquals(playerId, leg.winnerId)

            val turns = matchRepository.getTurns(leg.id)
            assertEquals(2, turns.size)
            val winTurn = turns.single { it.turnIndex == 1 }
            assertFalse(winTurn.bust)
            assertEquals(40, winTurn.totalScored)
            assertEquals(1, matchRepository.getThrows(winTurn.id).size)
        }

    // --- onUndo ---------------------------------------------------------------

    @Test
    fun onUndo_zweimal_stelltBeideDartsKorrektZurueck() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20) // -> 481
            vm.onToggleTriple()
            vm.onNumber(20) // Triple 20 = 60 -> 421
            val before = vm.uiState.value as GameUiState.Playing
            assertEquals(421, before.remaining)
            assertEquals(2, before.input.darts.size)

            vm.onUndo() // letzten (Triple-)Dart zurueck -> 481, 1 Dart
            val afterFirst = vm.uiState.value as GameUiState.Playing
            assertEquals(481, afterFirst.remaining)
            assertEquals(1, afterFirst.input.darts.size)

            vm.onUndo() // ersten Dart zurueck -> 501, 0 Darts
            val afterSecond = vm.uiState.value as GameUiState.Playing
            assertEquals(501, afterSecond.remaining)
            assertTrue(afterSecond.input.darts.isEmpty())
        }

    @Test
    fun onUndo_aufLeererAufnahme_istKeinEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            backgroundScope.launch { vm.uiState.collect {} }
            val playing = vm.awaitPlaying()
            assertTrue(playing.input.darts.isEmpty())

            vm.onUndo()

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(501, after.remaining)
            assertTrue(after.input.darts.isEmpty())
        }

    @Test
    fun onUndo_nachAufnahmeEnde_istKeinEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Aufnahme voll spielen (3 Darts) -> neue, leere Aufnahme, Rest 441.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            val ended = vm.uiState.value as GameUiState.Playing
            assertEquals(441, ended.remaining)
            assertTrue(ended.input.darts.isEmpty())

            vm.onUndo() // darf abgeschlossene Aufnahme nicht beeinflussen

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(441, after.remaining)
            assertTrue(after.input.darts.isEmpty())

            // Persistierte Aufnahme bleibt unveraendert (1 Turn, 3 Throws).
            val turns = matchRepository.getTurns(singleLegId())
            assertEquals(1, turns.size)
            assertEquals(3, matchRepository.getThrows(turns.first().id).size)
        }

    // --- onNewLeg -------------------------------------------------------------

    @Test
    fun onNewLeg_ausWon_legtZweitesLegImSelbenMatchAnUndStartetPlaying() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(
                playerId,
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Leg 1 gewinnen: Double 20 = 40 -> Checkout.
            vm.onToggleDouble()
            vm.onNumber(20)
            vm.awaitWon()

            val matchId = matchRepository.getMatches().first().id

            vm.onNewLeg()

            val playing = vm.awaitPlaying()
            assertEquals(40, playing.remaining)
            assertTrue(playing.input.darts.isEmpty())

            val legs = matchRepository.getLegs(matchId)
            assertEquals("neues Leg im selben Match angelegt", 2, legs.size)
            assertEquals(listOf(1, 2), legs.map { it.legNumber }.sorted())
            assertTrue("alle Legs gehoeren zum selben Match", legs.all { it.matchId == matchId })
        }

    // --- NoPlayer -------------------------------------------------------------

    @Test
    fun negativeSpielerId_fuehrtZuNoPlayerOhneMatch() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel(playerId = -1L)

            val state = vm.uiState.first { it !is GameUiState.Loading }
            assertTrue(state is GameUiState.NoPlayer)
            assertTrue("kein Match bei fehlendem Spieler", matchRepository.getMatches().isEmpty())
        }

    // --- Toggle-Modifier ------------------------------------------------------

    @Test
    fun onToggleDouble_wertetNaechstenDartDoppeltUndResettetDanachAufSingle() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val playerId = newPlayer()
            val vm = viewModel(playerId)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onToggleDouble()
            vm.onNumber(20) // Double 20 = 40 -> Rest 461

            val afterDouble = vm.uiState.value as GameUiState.Playing
            assertEquals(461, afterDouble.remaining)
            assertEquals(Dart(20, 2), afterDouble.input.darts.last())
            assertEquals("Modifier nach Wurf auf SINGLE zurueck", DartModifier.SINGLE, afterDouble.input.modifier)

            vm.onNumber(20) // jetzt wieder Single 20 -> Rest 441

            val afterSingle = vm.uiState.value as GameUiState.Playing
            assertEquals(441, afterSingle.remaining)
            assertEquals(Dart(20, 1), afterSingle.input.darts.last())
        }
}
