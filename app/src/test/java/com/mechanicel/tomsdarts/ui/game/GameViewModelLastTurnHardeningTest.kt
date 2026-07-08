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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * Test-Gate-Haertung fuer die pro-Spieler-Aufnahme
 * ([PlayerScoreUi.lastTurnDarts]/[PlayerScoreUi.lastTurnBust]) auf
 * [GameUiState.Playing] (Phase 3.5, "letzte Aufnahme je Spieler-Karte").
 * Ergaenzt die Basis-Faelle aus [GameViewModelTest] (`letzteAufnahme_*`) um
 * Isolations- (Zug von A ruehrt B nicht an), Bust-, Undo- und
 * Mehrspieler-Verhalten sowie das dokumentierte Verhalten beim frueh beendeten
 * Leg (Checkout < 3 Darts).
 *
 * Setup identisch zu den bestehenden Game-Tests: In-Memory-Room mit
 * synchronem (direktem) Executor, damit die im [GameViewModel]
 * fire-and-forget feuernde Persistenz unter
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] deterministisch
 * abgeschlossen ist, bevor die Assertions lesen. Laeuft host-seitig unter
 * Robolectric (SDK 34 gepinnt). Bleibt rein lokal (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelLastTurnHardeningTest {

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

    private fun viewModel(
        playerIds: List<Long>,
        config: GameConfig = GameConfig(startScore = 501, doubleOut = true, legsToWin = 2, setsToWin = 1),
    ) = GameViewModel(matchRepository, playerRepository, playerIds, config)

    private suspend fun GameViewModel.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    /** Karte eines Spielers ueber seinen Namen (fuer die pro-Spieler-Aufnahme). */
    private fun GameUiState.Playing.player(name: String): PlayerScoreUi =
        players.first { it.name == name }

    /** Spielt eine volle Checkout-Aufnahme: Double (startScore/2) in einem Dart. */
    private fun GameViewModel.checkout(half: Int) {
        onToggleDouble()
        onNumber(half)
    }

    // --- Isolation: Zug von A ruehrt die letzte Aufnahme von B nicht an -------

    @Test
    fun letzteAufnahme_zugVonAAendertNichtDieAufnahmeVonB() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Toms Aufnahme: 3x Single 20 -> haengt an Toms Karte.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            val afterTom = vm.uiState.value as GameUiState.Playing
            assertEquals(List(3) { Dart.single(20) }, afterTom.player("Tom").lastTurnDarts)
            // Anna hat noch nichts geworfen.
            assertTrue(afterTom.player("Anna").lastTurnDarts.isEmpty())

            // Annas Aufnahme: 3x Single 19 -> nur ANNAS Karte aendert sich;
            // Toms Aufnahme bleibt unveraendert stehen.
            vm.onNumber(19); vm.onNumber(19); vm.onNumber(19)
            val afterAnna = vm.uiState.value as GameUiState.Playing
            assertEquals(List(3) { Dart.single(19) }, afterAnna.player("Anna").lastTurnDarts)
            assertEquals(List(3) { Dart.single(20) }, afterAnna.player("Tom").lastTurnDarts)
        }

    @Test
    fun letzteAufnahme_bustFlagBleibtProSpielerErhalten() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom (Start 40): Single 20 -> 20, Single 20 -> 0 ohne Double ->
            // ungueltiger Checkout -> Bust bereits nach 2 Darts (Aufnahme endet
            // sofort, der dritte Dart wuerde schon zu Anna gehoeren).
            vm.onNumber(20); vm.onNumber(20)
            val afterBust = vm.uiState.value as GameUiState.Playing
            assertTrue(afterBust.player("Tom").lastTurnBust)
            assertEquals("Anna", afterBust.currentName)

            // Anna: 3 Fehlwuerfe -> regulaeres (Nicht-Bust) Aufnahmeende. Annas
            // Karte ist NICHT-Bust; Toms Bust-Flag bleibt unabhaengig stehen.
            vm.onOut(); vm.onOut(); vm.onOut()
            val afterAnna = vm.uiState.value as GameUiState.Playing
            assertFalse(afterAnna.player("Anna").lastTurnBust)
            assertEquals(List(3) { Dart.miss() }, afterAnna.player("Anna").lastTurnDarts)
            assertTrue(afterAnna.player("Tom").lastTurnBust)
        }

    // --- onUndo ruehrt die bereits abgeschlossene letzte Aufnahme nicht an ----

    @Test
    fun letzteAufnahme_bleibtUnveraendertWennFolgenderZugPerUndoZurueckgenommenWird() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Toms Aufnahme abschliessen -> Toms Karte traegt seine 3 Darts.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            val afterTom = vm.uiState.value as GameUiState.Playing
            val tomsTurn = afterTom.player("Tom").lastTurnDarts
            assertEquals(3, tomsTurn.size)

            // Anna wirft einen (noch laufenden) Dart und nimmt ihn zurueck.
            vm.onNumber(5)
            vm.onUndo()

            val afterUndo = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", afterUndo.currentName)
            assertTrue(afterUndo.input.darts.isEmpty())
            // Toms bereits abgeschlossene "letzte Aufnahme" bleibt unangetastet;
            // Anna hat noch keine abgeschlossene Aufnahme.
            assertEquals(tomsTurn, afterUndo.player("Tom").lastTurnDarts)
            assertTrue(afterUndo.player("Anna").lastTurnDarts.isEmpty())
        }

    // --- Frueher Checkout (< 3 Darts, Leg-Gewinn): dokumentiertes Verhalten --

    @Test
    fun letzteAufnahme_beiFruehemCheckoutNieImPlayingSichtbarSondernNachNeuemLegLeer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom checkt Leg 1 direkt mit einem Dart aus (< 3 Darts). Die Engine
            // wechselt sofort in GameUiState.LegWon; es existiert dazwischen KEIN
            // Playing-Zustand mehr, in dem die Checkout-Darts als "letzte
            // Aufnahme" auftauchen koennten. Dokumentiertes/beabsichtigtes
            // Verhalten: der Checkout-Zug wird nie als lastTurnDarts sichtbar.
            vm.checkout(20)
            assertTrue(vm.uiState.value is GameUiState.LegWon)

            vm.onNewLeg()
            val playing = vm.awaitPlaying()
            // Nach dem Leg-Wechsel ist die letzte Aufnahme bei ALLEN leer
            // (kein Leak aus dem Gewinn-Leg, siehe onNewLeg-Reset).
            assertTrue(playing.players.all { it.lastTurnDarts.isEmpty() && !it.lastTurnBust })
        }

    // --- Drei Spieler: jede Karte traegt die eigene letzte Aufnahme ----------

    @Test
    fun letzteAufnahme_beiDreiSpielernTraegtJedeKarteDieEigeneAufnahme() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val bjoern = newPlayer("Bjoern")
            val vm = viewModel(listOf(tom, anna, bjoern))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20) // Tom
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1) // Anna
            vm.onBull(); vm.onBull(); vm.onBull() // Bjoern

            val afterBjoern = vm.uiState.value as GameUiState.Playing
            // Jede Karte zeigt die eigene letzte Aufnahme.
            assertEquals(List(3) { Dart.single(20) }, afterBjoern.player("Tom").lastTurnDarts)
            assertEquals(List(3) { Dart.single(1) }, afterBjoern.player("Anna").lastTurnDarts)
            assertEquals(List(3) { Dart.bull() }, afterBjoern.player("Bjoern").lastTurnDarts)
            assertEquals("Tom", afterBjoern.currentName)
        }
}
