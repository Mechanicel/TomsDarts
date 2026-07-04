package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import com.mechanicel.tomsdarts.ui.setup.START_SCORES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Haertet die Verdrahtung des im Setup-Bildschirm gewaehlten Startpunkts
 * (siehe [com.mechanicel.tomsdarts.ui.setup.SetupScreen]) durch das
 * [GameViewModel] bis in den persistierten [com.mechanicel.tomsdarts.data.entity.Match]
 * ab: ausschliesslich [GameConfig.startScore] darf variieren, die uebrige
 * Regel-Konfiguration ([GameConfig.doubleOut]/[GameConfig.legsToWin]/
 * [GameConfig.setsToWin]) bleibt unveraendert; die Rest-Berechnung skaliert
 * korrekt mit der Groessenordnung; und ein domainseitig nicht ueber
 * [com.mechanicel.tomsdarts.ui.game.GameViewModel.provideFactory] restringierter
 * Startpunkt (ausserhalb der drei angebotenen Karten) laeuft die Kette
 * ebenfalls unveraendert durch (IST-Verhalten: die Einschraenkung auf
 * 301/501/701 lebt ausschliesslich in der Setup-UI, nicht in [GameConfig]/
 * [GameViewModel]).
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
class GameViewModelStartScoreWiringTest {

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

    private suspend fun newPlayer(name: String): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    private fun viewModel(playerIds: List<Long>, config: GameConfig) =
        GameViewModel(matchRepository, playerRepository, playerIds, config)

    private suspend fun GameViewModel.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    /** Konfiguration, wie sie [GameViewModel.provideFactory] fest verdrahtet - bis auf startScore. */
    private fun configFor(startScore: Int) =
        GameConfig(startScore = startScore, doubleOut = true, legsToWin = 2, setsToWin = 1)

    // --- Ausschliesslich startScore variiert, Rest-Konfiguration bleibt fix --

    @Test
    fun startScore_variiertGameConfigNurUmDenWert_restKonfigurationBleibtUnveraendert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            START_SCORES.forEach { startScore ->
                val vm = viewModel(listOf(tom, anna), configFor(startScore))
                backgroundScope.launch { vm.uiState.collect {} }

                val playing = vm.awaitPlaying()
                // Startpunkt kommt an ...
                assertEquals(startScore, playing.startScore)
                assertTrue(playing.players.all { it.remaining == startScore })
                // ... die uebrige Regel-Konfiguration bleibt jedoch fix, egal
                // welcher der drei Startpunkte gewaehlt wurde.
                assertEquals(2, playing.legsToWin)
                assertEquals(1, playing.setsToWin)

                val match = matchRepository.getMatches().last()
                assertEquals(startScore, match.startScore)
                assertEquals(true, match.doubleOut)
                assertEquals(2, match.legsToWin)
                assertEquals(1, match.setsToWin)
            }
        }

    @Test
    fun startScore_301Und701_restBerechnungSkaliertKorrektMitGroessenordnung() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            START_SCORES.forEach { startScore ->
                val vm = viewModel(listOf(tom, anna), configFor(startScore))
                backgroundScope.launch { vm.uiState.collect {} }
                vm.awaitPlaying()

                // Tom: 3x Triple 20 = 180, unabhaengig von der Groessenordnung
                // des Startpunkts muss der Rest exakt startScore - 180 sein.
                // Der Modifier faellt nach jedem akzeptierten Dart auf SINGLE
                // zurueck (siehe DartInputState.appendAndReset), daher vor
                // jedem Wurf erneut TRIPLE anschalten.
                vm.onToggleTriple(); vm.onNumber(20)
                vm.onToggleTriple(); vm.onNumber(20)
                vm.onToggleTriple(); vm.onNumber(20)

                val playing = vm.uiState.value as GameUiState.Playing
                val tomRemaining = playing.players.first { it.playerId == tom }.remaining
                assertEquals(startScore - 180, tomRemaining)
            }
        }

    // --- Domainseitig kein Guard auf die drei angebotenen Karten -------------

    @Test
    fun startScore_ausserhalbDerSetupAuswahl_laueftDomainseitigDennochUnveraendertDurch() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            // 170 ist keine der drei Setup-Karten (301/501/701). Die
            // Einschraenkung auf diese drei Werte lebt ausschliesslich in der
            // Setup-UI (START_SCORES); GameConfig/GameViewModel selbst
            // validieren nicht dagegen. Das ist IST-Verhalten, kein Fix hier.
            val untypischerStartScore = 170
            assertTrue(untypischerStartScore !in START_SCORES)

            val vm = viewModel(listOf(tom, anna), configFor(untypischerStartScore))
            val playing = vm.awaitPlaying()

            assertEquals(untypischerStartScore, playing.startScore)
            assertTrue(playing.players.all { it.remaining == untypischerStartScore })

            val match = matchRepository.getMatches().single()
            assertEquals(untypischerStartScore, match.startScore)
        }
}
