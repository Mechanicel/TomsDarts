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
 * Test-Gate-Haertung fuer das Cross-Turn-Undo im [GameViewModel]: Ergaenzt
 * [GameViewModelTest] und [GameViewModelEdgeCasesTest] um mehrfache,
 * unmittelbar aufeinanderfolgende Cross-Turn-Undos (mehrere abgeschlossene
 * Aufnahmen hintereinander zurueckgenommen), die Konsistenz der
 * "letzte Aufnahme je Spieler"-Anzeige ueber diese Cross-Turn-Undos hinweg, die
 * Korrektheit von [GameUiState.LegWon.dartsUsed] nach einem Undo-und-Neu-Wurf-
 * Zyklus sowie die No-op-Garantien von [GameViewModel.onUndo] im LegWon-Zustand
 * und direkt zu Beginn eines per [GameViewModel.onNewLeg] gestarteten Legs.
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
class GameViewModelUndoHardeningTest {

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

    // --- Mehrfaches Cross-Turn-Undo hintereinander -----------------------------

    @Test
    fun mehrfachesCrossTurnUndoHintereinander_loeschtBeideAufnahmenUndTurnIndexBleibtLueckenlos() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 41, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Turn 0 (Tom): 3x Single 1 -> Rest 38, "Weiter".
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            // Turn 1 (Anna): 3x Single 1 -> Rest 38, "Weiter".
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            // Turn 2 (Tom): Triple 20 = 60 > Rest 38 -> Sofort-Bust mit nur EINEM
            // Dart, Wechsel zu Anna (Bust ohne Kontroll-Pause).
            vm.onToggleTriple(); vm.onNumber(20)

            var playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            val legId = singleLegId()
            assertEquals(3, matchRepository.getTurns(legId).size)

            // Erstes Cross-Turn-Undo: Annas Aufnahme ist leer -> Toms Bust-Turn
            // (nur 1 Dart) wird komplett zurueckgenommen und aus der DB entfernt.
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertTrue(playing.input.darts.isEmpty())
            assertEquals(2, matchRepository.getTurns(legId).size)

            // Zweites Cross-Turn-Undo DIREKT hintereinander (Toms Aufnahme ist nun
            // wieder leer): Annas Turn 1 wird ebenfalls wieder geoeffnet und
            // aus der DB entfernt.
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            assertEquals(2, playing.input.darts.size)
            assertEquals(1, matchRepository.getTurns(legId).size)
            val remaining = matchRepository.getTurns(legId).single()
            assertEquals(tom, remaining.playerId)
            assertEquals(0, remaining.turnIndex)

            // Weiterspielen: Annas dritter Dart erneut -> Kontroll-Pause, "Weiter";
            // ihr Turn wird mit turnIndex 1 (lueckenlos, kein Duplikat) neu persistiert.
            vm.onNumber(1)
            vm.onContinue()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            val turnsAfter = matchRepository.getTurns(legId)
            assertEquals(2, turnsAfter.size)
            assertEquals(listOf(0, 1), turnsAfter.map { it.turnIndex }.sorted())
            val annaTurn = turnsAfter.single { it.playerId == anna }
            assertEquals(1, annaTurn.turnIndex)
            assertEquals(3, matchRepository.getThrows(annaTurn.id).size)
        }

    // --- lastTurnByPlayer waehrend mehrfacher Cross-Turn-Undos -----------------

    @Test
    fun lastTurnByPlayer_zeigtVorherigeAufnahmeOderNichtsNachMehrerenCrossTurnUndos() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Turn 0 (Tom): 3x Single 5, "Weiter".
            vm.onNumber(5); vm.onNumber(5); vm.onNumber(5); vm.onContinue()
            // Turn 1 (Anna): 3x Single 1, "Weiter".
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            // Turn 2 (Tom): 3x Single 3, "Weiter".
            vm.onNumber(3); vm.onNumber(3); vm.onNumber(3); vm.onContinue()

            var playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            assertEquals(List(3) { Dart.single(3) }, playing.player("Tom").lastTurnDarts)
            assertEquals(List(3) { Dart.single(1) }, playing.player("Anna").lastTurnDarts)

            // Cross-Turn-Undo #1: Annas Aufnahme ist leer -> Toms Turn 2 wird
            // wieder geoeffnet. Toms "letzte Aufnahme" faellt auf die davor
            // abgeschlossene Aufnahme (Turn 0) zurueck.
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertEquals(2, playing.input.darts.size)
            assertEquals(List(3) { Dart.single(5) }, playing.player("Tom").lastTurnDarts)
            assertEquals(List(3) { Dart.single(1) }, playing.player("Anna").lastTurnDarts)

            // Zwei Intra-Turn-Undos raeumen Toms wieder geoeffnete Aufnahme
            // vollstaendig ab (dartsInTurn 2 -> 1 -> 0); lastTurnByPlayer bleibt
            // in diesem intra-turn-Zweig unangetastet.
            vm.onUndo()
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertTrue(playing.input.darts.isEmpty())
            assertEquals(List(3) { Dart.single(5) }, playing.player("Tom").lastTurnDarts)

            // Cross-Turn-Undo #2: Toms Aufnahme ist wieder leer -> Annas Turn 1
            // wird geoeffnet. Anna hatte davor KEINE abgeschlossene Aufnahme ->
            // ihre Karte zeigt jetzt nichts mehr.
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            assertEquals(2, playing.input.darts.size)
            assertTrue(playing.player("Anna").lastTurnDarts.isEmpty())
            assertFalse(playing.player("Anna").lastTurnBust)
            // Toms Anzeige bleibt von alldem unberuehrt.
            assertEquals(List(3) { Dart.single(5) }, playing.player("Tom").lastTurnDarts)
        }

    // --- legDartsByPlayer/dartsUsed-Konsistenz nach Undo + Neu-Werfen ----------

    @Test
    fun legWon_dartsUsed_zaehltNachCrossTurnUndoUndAndererCheckoutSequenzKorrekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 41, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Turn 0 (Tom): 3x Single 1 -> Rest 38, "Weiter".
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            // Turn 1 (Anna): 3x Single 1 -> Rest 38, "Weiter".
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            // Turn 2 (Tom, urspruenglich): 3x Single 1 (keine Aufnahme, kein
            // Checkout) -> Rest 35, "Weiter" -> Wechsel zu Anna.
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1); vm.onContinue()
            assertEquals("Anna", (vm.uiState.value as GameUiState.Playing).currentName)

            // Cross-Turn-Undo raeumt Toms Turn 2 komplett ab (1 Cross-Turn- +
            // 2 Intra-Turn-Undos), sein Rest ist danach wieder 38 (Stand nach
            // Turn 0).
            vm.onUndo()
            vm.onUndo()
            vm.onUndo()
            val afterRewind = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", afterRewind.currentName)
            assertTrue(afterRewind.input.darts.isEmpty())
            assertEquals(38, afterRewind.remainingOf(tom))

            // Tom wirft eine NEUE, kuerzere Turn-2-Sequenz: Single 18 (Rest 20),
            // Double 10 (Rest 0) -> Checkout mit NUR ZWEI Darts, Leg gewonnen.
            vm.onNumber(18)
            vm.onToggleDouble(); vm.onNumber(10)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            // Toms tatsaechlich verwendete Darts im Gewinn-Leg: 3 (Turn 0) + 2
            // (neue Turn 2') = 5 -- NICHT 3+3+2=8 (verworfene Turn-2-Darts
            // duerfen nicht mitgezaehlt werden) und NICHT weniger.
            assertEquals(5, legWon.dartsUsed)

            // Persistenz: die verworfene Turn-2-Aufnahme ist weg, nur Turn 0
            // (Tom), Turn 1 (Anna) und die neue Turn 2' (Tom, 2 Darts) bleiben.
            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(3, turns.size)
            val finalTomTurn = turns.filter { it.playerId == tom }.maxBy { it.turnIndex }
            assertEquals(2, matchRepository.getThrows(finalTomTurn.id).size)
        }

    // --- onUndo im LegWon-Zustand: kein Effekt, kein Crash ---------------------

    @Test
    fun onUndo_imLegWonZustand_hatKeinenEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onToggleDouble(); vm.onNumber(20) // Tom checkt Leg 1 sofort aus.
            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon

            vm.onUndo() // Im LegWon-Zustand ohne Effekt (kein Playing-State).

            assertEquals(legWon, vm.uiState.value)
            // Der abgeschlossene, siegreiche Turn bleibt persistiert.
            val legId = singleLegId()
            assertEquals(1, matchRepository.getTurns(legId).size)
        }

    // --- canUndo direkt nach onNewLeg: false ------------------------------------

    @Test
    fun canUndo_direktNachOnNewLeg_istFalse() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onToggleDouble(); vm.onNumber(20) // Tom checkt Leg 1 aus.
            vm.uiState.first { it is GameUiState.LegWon }

            vm.onNewLeg()
            val playing = vm.awaitPlaying()

            assertEquals(2, playing.currentLegNumber)
            assertFalse("Frisches Leg -> kein Undo moeglich", playing.canUndo)

            // onUndo direkt nach onNewLeg ist ebenfalls wirkungslos (leere
            // Leg-Historie in der Engine).
            vm.onUndo()
            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(playing, after)
        }
}
