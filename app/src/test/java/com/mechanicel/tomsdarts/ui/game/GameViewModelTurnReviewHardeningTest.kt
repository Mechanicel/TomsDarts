package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Test-Gate-Haertung der Kontroll-Pause ([GameUiState.Playing.turnReview]) im
 * Mehrspieler-[GameViewModel], ergaenzend zu den Basis-/Happy-Path-Faellen in
 * [GameViewModelTest]. Schwerpunkte:
 * - Exaktheit der Pausen-Ausloesung (3-Dart-Bust loest weiterhin KEINE Pause aus).
 * - Tap-vs-Timer-Race: manuelles "Weiter" VOR Timer-Ablauf, danach verstreicht der
 *   Timer wirkungslos (Idempotenz, kein Doppelwechsel, keine Doppel-Persistenz).
 * - Umgekehrte Reihenfolge: Timer laeuft zuerst ab, ein spaeterer "Weiter"-Tap
 *   bleibt wirkungslos.
 * - Eingabe-Sperre waehrend der Pause (Zahl/Bull/Toggle werden ignoriert).
 * - Erneute Pause nach "Korrigieren" + erneutem drittem Dart.
 *
 * Setup identisch zu den bestehenden Game-Tests: In-Memory-Room mit synchronem
 * (direktem) Executor, damit die im [GameViewModel] fire-and-forget feuernde
 * Persistenz unter [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * deterministisch abgeschlossen ist, bevor die Assertions lesen. Laeuft
 * host-seitig unter Robolectric (SDK 34 gepinnt). Bleibt rein lokal
 * (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTurnReviewHardeningTest {

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

    private suspend fun twoPlayers(): Pair<Long, Long> =
        newPlayer("Tom") to newPlayer("Anna")

    private fun viewModel(
        playerIds: List<Long>,
        config: GameConfig = GameConfig(startScore = 501, doubleOut = true, legsToWin = 2, setsToWin = 1),
    ) = GameViewModel(matchRepository, playerRepository, playerIds, config, X01Mode(), X01UiAdapter())

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    private fun GameUiState.Playing.player(name: String): PlayerScoreUi =
        players.first { it.name == name }

    private fun GameUiState.Playing.remainingOf(playerId: Long): Int =
        players.first { it.playerId == playerId }.remaining

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    // --- Pausen-Ausloesung exakt: 3-Dart-Bust bleibt ohne Pause ---------------

    @Test
    fun dreiDartBust_loestKeineKontrollPauseAusUndWechseltSofort() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            // Start 100, Double-Out: Single 20, Single 20 (Rest 60), Triple 20 = 60
            // -> exakt 0, aber ohne Double -> Bust GENAU beim dritten Dart.
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 100, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val bustBefore = vm.bustEvents.value
            vm.onNumber(20); vm.onNumber(20)
            vm.onToggleTriple(); vm.onNumber(20)

            val playing = vm.uiState.value as GameUiState.Playing
            assertNull("3-Dart-Bust loest keine Kontroll-Pause aus", playing.turnReview)
            assertEquals(bustBefore + 1, vm.bustEvents.value)
            assertEquals("Anna", playing.currentName)
            // Rest auf Aufnahme-Start zurueckgesetzt (100), nicht auf 0/negativ.
            assertEquals(100, playing.remainingOf(tom))

            val turn = matchRepository.getTurns(singleLegId()).single()
            assertTrue(turn.bust)
            assertEquals(3, matchRepository.getThrows(turn.id).size)
        }

    // --- Tap VOR Timer-Ablauf: Timer verstreicht danach wirkungslos -----------

    @Test
    fun onContinuePerTap_vorTimerAblauf_timerBleibtDanachOhneWeiterenEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            assertNotNull((vm.uiState.value as GameUiState.Playing).turnReview)

            // Tap VOR Timer-Ablauf.
            vm.onContinue()
            val afterTap = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", afterTap.currentName)
            assertNull(afterTap.turnReview)
            assertEquals(441, afterTap.remainingOf(tom))
            val turnsAfterTap = matchRepository.getTurns(singleLegId())
            assertEquals(1, turnsAfterTap.size)

            // Timer laeuft jetzt ab -- darf KEINEN zweiten Wechsel und KEINE
            // doppelte Persistenz ausloesen (Anna bleibt aktiv, nicht uebersprungen).
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()

            val afterTimer = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna wird nicht uebersprungen", "Anna", afterTimer.currentName)
            assertNull(afterTimer.turnReview)
            assertEquals(441, afterTimer.remainingOf(tom))
            assertEquals(501, afterTimer.remainingOf(anna))
            assertEquals(
                "Kein doppelter Persistenz-Schreib durch den nachlaufenden Timer",
                1,
                matchRepository.getTurns(singleLegId()).size,
            )
        }

    // --- Timer laeuft zuerst ab: spaeterer Tap bleibt wirkungslos --------------

    @Test
    fun timerAblauf_vorSpaeteremTap_tapBleibtDanachOhneEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            assertNotNull((vm.uiState.value as GameUiState.Playing).turnReview)

            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()
            val afterTimer = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", afterTimer.currentName)
            assertNull(afterTimer.turnReview)
            assertEquals(1, matchRepository.getTurns(singleLegId()).size)

            // Ein "verspaeteter" Tap (z.B. Doppel-Klick) danach bleibt wirkungslos.
            vm.onContinue()
            val afterLateTap = vm.uiState.value as GameUiState.Playing
            assertEquals(afterTimer, afterLateTap)
            assertEquals(1, matchRepository.getTurns(singleLegId()).size)
        }

    // --- Mehrfaches onContinue() ist durchgehend harmlos -----------------------

    @Test
    fun onContinue_mehrfachHintereinander_bleibtNachDemErstenWirkungslos() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onContinue()
            val first = vm.uiState.value as GameUiState.Playing

            vm.onContinue()
            vm.onContinue()
            vm.onContinue()

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(first, after)
            assertEquals(1, matchRepository.getTurns(singleLegId()).size)
        }

    // --- Eingabe-Sperre waehrend der Pause --------------------------------------

    @Test
    fun eingabenWaehrendDerPause_werdenIgnoriertUndGreifenNachOnContinueWiederNormal() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            val duringPause = vm.uiState.value as GameUiState.Playing
            assertNotNull(duringPause.turnReview)

            // Zahl, Bull, Out, Toggle-Double, Toggle-Triple waehrend der Pause:
            // alle wirkungslos (kein Dart an Anna, kein State-Bruch).
            vm.onNumber(5)
            vm.onBull()
            vm.onOut()
            vm.onToggleDouble()
            vm.onToggleTriple()

            val stillPaused = vm.uiState.value as GameUiState.Playing
            assertEquals(duringPause, stillPaused)
            assertEquals(1, matchRepository.getTurns(singleLegId()).size)
            assertEquals(441, stillPaused.remainingOf(tom))
            assertEquals(501, stillPaused.remainingOf(anna))

            // Nach "Weiter" funktioniert die Eingabe fuer Anna wieder normal.
            vm.onContinue()
            vm.onNumber(19)
            val afterContinue = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", afterContinue.currentName)
            assertEquals(482, afterContinue.remainingOf(anna))
            assertEquals(1, afterContinue.input.darts.size)
        }

    // --- Erneute Pause nach "Korrigieren" + erneutem drittem Dart --------------

    @Test
    fun onUndoAusDerPause_gefolgtVonErneutemDrittenDart_loestErneutDiePauseAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onUndo() // "Korrigieren": Pause verwerfen, Toms Aufnahme wieder offen.

            val afterUndo = vm.uiState.value as GameUiState.Playing
            assertNull(afterUndo.turnReview)
            assertEquals("Tom", afterUndo.currentName)
            assertEquals(2, afterUndo.input.darts.size)
            assertTrue(matchRepository.getTurns(singleLegId()).isEmpty())

            // Dritter Dart erneut werfen (diesmal ein anderer Wert) -> Pause
            // erneut, mit korrekt aktualisierten Werten.
            vm.onNumber(5)
            val afterSecondThird = vm.uiState.value as GameUiState.Playing
            val review = afterSecondThird.turnReview
            assertNotNull("Pause laeuft erneut", review)
            assertEquals("Tom", review!!.throwerName)
            assertEquals(listOf(Dart.single(20), Dart.single(20), Dart.single(5)), review.darts)
            assertEquals(45, review.turnSum)
            assertEquals("Anna", review.nextPlayerName)
            assertEquals(1, matchRepository.getTurns(singleLegId()).size)

            // Auch der Timer der erneuten Pause funktioniert (kein "toter" Job
            // aus dem verworfenen ersten Durchlauf).
            advanceTimeBy(GameViewModel.TURN_REVIEW_MILLIS + 1)
            runCurrent()
            val afterTimer = vm.uiState.value as GameUiState.Playing
            assertNull(afterTimer.turnReview)
            assertEquals("Anna", afterTimer.currentName)
        }

    // --- turnReview.darts konsistent zu lastTurnDarts waehrend der Pause -------

    @Test
    fun turnReviewDarts_sindWaehrendDerPauseKonsistentZuLastTurnDartsDesWerfers() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onToggleTriple(); vm.onNumber(19)
            vm.onNumber(1)
            vm.onBull()

            val playing = vm.uiState.value as GameUiState.Playing
            val review = playing.turnReview
            assertNotNull(review)
            assertEquals(review!!.darts, playing.player("Tom").lastTurnDarts)
            assertEquals(
                listOf(Dart.triple(19), Dart.single(1), Dart.bull()),
                review.darts,
            )
        }

    // --- Volle Mehrspieler-Runde ueber onContinue, keine verschluckten Wuerfe --

    @Test
    fun volleRundeUeberDreiSpieler_mitOnContinue_korrekterReihumWechselOhneVerschluckteWuerfe() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val bjoern = newPlayer("Bjoern")
            val vm = viewModel(listOf(tom, anna, bjoern))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Runde 1: Tom, Anna, Bjoern werfen je eine volle Aufnahme.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20); vm.onContinue()
            vm.onNumber(19); vm.onNumber(19); vm.onNumber(19); vm.onContinue()
            vm.onNumber(18); vm.onNumber(18); vm.onNumber(18); vm.onContinue()

            var playing = vm.uiState.value as GameUiState.Playing
            // Zurueck bei Tom (reihum), genau ein aktiver Spieler.
            assertEquals(1, playing.players.count { it.isCurrent })
            assertEquals("Tom", playing.currentName)
            assertEquals(441, playing.remainingOf(tom))
            assertEquals(444, playing.remainingOf(anna))
            assertEquals(447, playing.remainingOf(bjoern))

            // Runde 2: alle drei werfen erneut.
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            vm.onNumber(2); vm.onNumber(2); vm.onNumber(2); vm.onContinue()
            vm.onNumber(3); vm.onNumber(3); vm.onNumber(3); vm.onContinue()

            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertEquals(438, playing.remainingOf(tom))
            assertEquals(438, playing.remainingOf(anna))
            assertEquals(438, playing.remainingOf(bjoern))

            // Kein Dart verschluckt: genau 6 Turns, je 3 Darts persistiert.
            val turns = matchRepository.getTurns(singleLegId())
            assertEquals(6, turns.size)
            turns.forEach { assertEquals(3, matchRepository.getThrows(it.id).size) }
            assertEquals(
                listOf(tom, anna, bjoern, tom, anna, bjoern),
                turns.sortedBy { it.turnIndex }.map { it.playerId },
            )

            // lastTurnByPlayer zeigt je Spieler die zuletzt abgeschlossene (2.)
            // Aufnahme.
            assertEquals(List(3) { Dart.single(1) }, playing.player("Tom").lastTurnDarts)
            assertEquals(List(3) { Dart.single(2) }, playing.player("Anna").lastTurnDarts)
            assertEquals(List(3) { Dart.single(3) }, playing.player("Bjoern").lastTurnDarts)
        }
}
