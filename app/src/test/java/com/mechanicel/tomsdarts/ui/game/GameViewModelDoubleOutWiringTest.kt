package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_START_SCORE
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
 * Haertet die Verdrahtung des im Setup-Bildschirm gewaehlten Double-Out
 * (siehe [com.mechanicel.tomsdarts.ui.setup.SetupScreen]) durch das
 * [GameViewModel] bis in den persistierten
 * [com.mechanicel.tomsdarts.data.entity.Match] ab: fuer BEIDE Werte (an/aus)
 * kommt [GameConfig.doubleOut] unveraendert in [Match.doubleOut] an, waehrend die
 * uebrige Regel-Konfiguration ([GameConfig.startScore]/[GameConfig.legsToWin]/
 * [GameConfig.setsToWin]) unabhaengig davon fix bleibt (auch in Kombination mit
 * allen [com.mechanicel.tomsdarts.ui.setup.START_SCORES]).
 *
 * Deckt zusaetzlich die fachliche Auswirkung des Schalters end-to-end durch das
 * [GameViewModel] ab (nicht nur auf X01Mode-/LegEngine-Unit-Ebene): mit
 * `doubleOut = false` gewinnt ein Checkout OHNE Double das Leg, mit
 * `doubleOut = true` fuehrt dieselbe Dart-Folge stattdessen zu einem Bust.
 *
 * Setup identisch zu [GameViewModelStartScoreWiringTest]: synchroner (direkter)
 * Room-Executor, damit die im [GameViewModel] fire-and-forget feuernde
 * Persistenz unter [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * deterministisch abgeschlossen ist, bevor die Assertions lesen. Laeuft
 * host-seitig unter Robolectric (SDK 34 gepinnt). Bleibt rein lokal
 * (offline-first).
 *
 * Die reine Compose-UI-Interaktion (Switch-Tap / Row-Toggle) ist mangels
 * Instrumentation/Geraet nicht host-testbar und daher hier nicht abgedeckt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelDoubleOutWiringTest {

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
        GameViewModel(matchRepository, playerRepository, playerIds, config, X01Mode(), X01UiAdapter())

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    /** Konfiguration, wie sie [GameViewModel.provideFactory] fest verdrahtet - bis auf doubleOut. */
    private fun configFor(doubleOut: Boolean) =
        GameConfig(
            startScore = DEFAULT_START_SCORE,
            doubleOut = doubleOut,
            legsToWin = 2,
            setsToWin = 1,
        )

    // --- Ausschliesslich doubleOut variiert, Rest-Konfiguration bleibt fix ----

    @Test
    fun doubleOut_variiertPersistiertenMatchNurUmDenWert_restKonfigurationBleibtUnveraendert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            listOf(true, false).forEach { doubleOut ->
                val vm = viewModel(listOf(tom, anna), configFor(doubleOut))
                backgroundScope.launch { vm.uiState.collect {} }

                val playing = vm.awaitPlaying()
                // Die uebrige Regel-Konfiguration bleibt fix, egal welcher
                // Double-Out-Wert gewaehlt wurde ...
                assertEquals(DEFAULT_START_SCORE, playing.startScore)
                assertEquals(2, playing.legsToWin)
                assertEquals(1, playing.setsToWin)

                // ... waehrend doubleOut selbst unveraendert bis in den
                // persistierten Match durchlaeuft.
                val match = matchRepository.getMatches().last()
                assertEquals(doubleOut, match.doubleOut)
                assertEquals(DEFAULT_START_SCORE, match.startScore)
                assertEquals(2, match.legsToWin)
                assertEquals(1, match.setsToWin)
            }
        }

    @Test
    fun doubleOut_an_kommtAlsTrueImPersistiertenMatchAn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            val vm = viewModel(listOf(tom, anna), configFor(doubleOut = true))
            vm.awaitPlaying()

            val match = matchRepository.getMatches().single()
            assertTrue(match.doubleOut)
        }

    @Test
    fun doubleOut_aus_kommtAlsFalseImPersistiertenMatchAn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            val vm = viewModel(listOf(tom, anna), configFor(doubleOut = false))
            vm.awaitPlaying()

            val match = matchRepository.getMatches().single()
            assertEquals(false, match.doubleOut)
        }

    @Test
    fun doubleOut_variiertUnabhaengigVonStartScore_matchBleibtKorrektFuerAlleKombinationen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            START_SCORES.forEach { startScore ->
                listOf(true, false).forEach { doubleOut ->
                    val config = GameConfig(
                        startScore = startScore,
                        doubleOut = doubleOut,
                        legsToWin = 2,
                        setsToWin = 1,
                    )
                    val vm = viewModel(listOf(tom, anna), config)
                    backgroundScope.launch { vm.uiState.collect {} }
                    vm.awaitPlaying()

                    val match = matchRepository.getMatches().last()
                    assertEquals(startScore, match.startScore)
                    assertEquals(doubleOut, match.doubleOut)
                    assertEquals(2, match.legsToWin)
                    assertEquals(1, match.setsToWin)
                }
            }
        }

    // --- Fachliche Auswirkung: doubleOut steuert, ob ein Checkout ohne Double
    // --- als Leg-Sieg zaehlt - end-to-end durch das GameViewModel (nicht nur
    // --- auf X01Mode/LegEngine-Unit-Ebene). --------------------------------

    @Test
    fun doubleOut_aus_checkoutOhneDoubleGewinntDasLegUeberDasViewModel() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = false, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Rest 40: Single 20 -> Rest 20 (regulaer) -> Single 20 -> Rest 0.
            // Ohne Double-Out gewinnt dieser Checkout OHNE Double das Leg.
            vm.onNumber(20)
            vm.onNumber(20)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            assertEquals(2, legWon.dartsUsed)
        }

    @Test
    fun doubleOut_an_derSelbeCheckoutOhneDoubleIstBustStattLegSieg() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val bustBefore = vm.bustEvents.value
            // Exakt dieselbe Dart-Folge wie im doubleOut=false-Fall: mit
            // Double-Out ist Rest 0 ohne Double kein Leg-Sieg, sondern Bust.
            vm.onNumber(20)
            vm.onNumber(20)

            // Kein LegWon: die Aufnahme endet als Bust, die Engine wechselt
            // zurueck zu Playing mit dem naechsten Spieler (Anna).
            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals(bustBefore + 1, vm.bustEvents.value)
            // Toms Rest ist auf den Aufnahme-Start (40) zurueckgesetzt, nicht 0.
            assertEquals(40, playing.players.first { it.playerId == tom }.remaining)
            assertTrue(playing.players.first { it.playerId == anna }.isCurrent)
        }
}
