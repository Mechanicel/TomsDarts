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
import com.mechanicel.tomsdarts.testing.RoundLimitFakeMode
import com.mechanicel.tomsdarts.testing.RoundLimitState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * Test-Gate-Haertung fuer die Vertragserweiterung "Leg-Ende ohne Werfer-Sieg"
 * (`legEnded`) auf [GameViewModel]-Ebene, ergaenzt [GameViewModelLegEndedTest] um:
 * - die Kontroll-Pause (ADR-0026): `legEnded` darf sie NIE ausloesen - auch nicht
 *   dann, wenn der leg-beendende Dart zufaellig mit dem natuerlichen 3-Dart-
 *   Aufnahme-Ende zusammenfaellt (die kritischste Ueberschneidung der beiden
 *   Mechanismen); nach einem Zuruecknehmen greift die Pause fuer regulaere
 *   Aufnahmen-Enden im naechsten Leg wieder normal,
 * - Persistenz einer Teil-Aufnahme, die bereits beim ERSTEN Dart per `legEnded`
 *   endet, und deren vollstaendige Entfernung durch [GameViewModel.onUndoWin],
 * - Cross-Turn-Undo-Tiefe nach einem `legEnded`-Sieg ueber mehrere Rewind-/
 *   Replay-Zyklen (inkl. [GameViewModel.onUndoWin] gefolgt von regulaerem
 *   [GameViewModel.onUndo]) mit Persistenz-Konsistenz je Zyklus.
 *
 * Als Modus dient der geteilte [RoundLimitFakeMode] (Test-Fixture, kein
 * Produktions-Modus). Setup identisch zu [GameViewModelLegEndedTest]: In-Memory-
 * Room mit synchronem Executor, host-seitig unter Robolectric (SDK 34 gepinnt).
 * Bleibt rein lokal (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelLegEndedHardeningTest {

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

    // --- Helfer -----------------------------------------------------------

    private class RoundLimitUiAdapter : ModeUiAdapter<RoundLimitState> {
        override fun board(state: RoundLimitState): PlayerBoardUi = PlayerBoardUi.X01(state.points)

        override fun checkout(state: RoundLimitState, config: GameConfig): List<Dart>? = null
    }

    private suspend fun twoPlayers(): Pair<Long, Long> =
        db.playerDao().insert(Player(name = "Tom", createdAt = 1L)) to
            db.playerDao().insert(Player(name = "Anna", createdAt = 1L))

    private fun viewModel(
        playerIds: List<Long>,
        dartLimit: Int,
        legsToWin: Int = 2,
        setsToWin: Int = 1,
    ) = GameViewModel(
        matchRepository = matchRepository,
        playerRepository = playerRepository,
        playerIds = playerIds,
        config = GameConfig(legsToWin = legsToWin, setsToWin = setsToWin),
        mode = RoundLimitFakeMode(dartLimit = dartLimit),
        uiAdapter = RoundLimitUiAdapter(),
    )

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    // --- Kontroll-Pause (ADR-0026): legEnded ueberspringt sie IMMER --------

    @Test
    fun legEnded_aufDemDrittenDartDerAufnahme_ueberspringtDieKontrollpauseTrotzNatuerlichemAufnahmeEnde() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // dartLimit=3 richtet das legEnded-Signal exakt auf den natuerlichen
            // 3-Dart-Aufnahme-Cap aus - die kritischste Ueberschneidung: ein
            // regulaeres 3-Dart-Ende wuerde sonst IMMER die Kontroll-Pause
            // ausloesen.
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna), dartLimit = 3)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom (Starter): volle Aufnahme, opponent (Anna) noch bei 0 -> kein
            // legEnded, regulaeres 3-Dart-Ende -> Kontroll-Pause laeuft normal.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            val afterTom = vm.uiState.value as GameUiState.Playing
            assertNotNull("Regulaeres Ende loest die Pause normal aus", afterTom.turnReview)
            assertEquals("Tom", afterTom.turnReview!!.throwerName)
            vm.onContinue()

            // Anna: 1. und 2. Dart lassen das Leg offen (eigenes Kontingent noch
            // nicht voll) -> keine Pause, da die Aufnahme selbst noch laeuft.
            vm.onNumber(1)
            assertNull((vm.uiState.value as GameUiState.Playing).turnReview)
            vm.onNumber(1)
            assertNull((vm.uiState.value as GameUiState.Playing).turnReview)

            // Annas 3. Dart faellt mit dem natuerlichen 3-Dart-Cap zusammen UND
            // beendet das Leg per Rangvergleich (Tom fuehrt: 60 zu 2) -> direkt
            // GameUiState.LegWon, OHNE jemals eine Kontroll-Pause zu zeigen.
            vm.onNumber(1)
            val state = vm.uiState.value
            assertTrue("Direkt LegWon, keine Playing-Zwischenphase mit Pause", state is GameUiState.LegWon)
            val legWon = state as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            assertEquals(3, legWon.dartsUsed)

            // Persistenz: Toms 3-Dart-Aufnahme + Annas 3-Dart-Aufnahme (die zweite
            // ist die leg-beendende, gehoert aber weiterhin ihr als Werferin).
            val legId = singleLegId()
            val turns = matchRepository.getTurns(legId)
            assertEquals(2, turns.size)
            assertEquals(anna, turns[1].playerId)
            assertEquals(3, matchRepository.getThrows(turns[1].id).size)
        }

    // --- Teil-Aufnahme: legEnded beim ALLERERSTEN Dart einer Aufnahme ------

    @Test
    fun legEnded_aufDemErstenDartEinerAufnahme_persistiertNurEinenWurf_undOnUndoWinEntferntIhnVollstaendig() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // dartLimit=1: Toms volle Aufnahme reicht, damit Annas ALLERERSTER
            // Dart das Leg sofort per Rangvergleich entscheidet (dartsInTurn=1).
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna), dartLimit = 1)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()
            val legId = singleLegId()

            vm.onNumber(5); vm.onNumber(5); vm.onNumber(5) // Tom: 15 Punkte
            vm.onContinue()

            vm.onNumber(1) // Anna: 1. (und einziger) Dart -> legEnded, Tom fuehrt (15:1)
            val state = vm.uiState.value
            assertTrue(state is GameUiState.LegWon)
            val legWon = state as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            assertEquals(3, legWon.dartsUsed)

            val turns = matchRepository.getTurns(legId)
            assertEquals(2, turns.size)
            assertEquals(anna, turns[1].playerId)
            assertEquals(1, matchRepository.getThrows(turns[1].id).size)

            vm.onUndoWin()
            val playing = vm.awaitPlaying()

            // Annas Ein-Wurf-Aufnahme ist vollstaendig aus der Persistenz entfernt.
            val turnsAfterUndo = matchRepository.getTurns(legId)
            assertEquals(1, turnsAfterUndo.size)
            assertEquals(tom, turnsAfterUndo.single().playerId)
            // Annas Aufnahme ist wieder komplett offen (0 Darts, nicht 1 uebrig).
            assertEquals("Anna", playing.players.first { it.isCurrent }.name)
            assertTrue(playing.input.darts.isEmpty())
            assertTrue(playing.canUndo)
            val leg = matchRepository.getLegs(matchRepository.getMatches().first().id).single()
            assertNull(leg.winnerId)
            assertNull(leg.endedAt)
        }

    // --- Undo-Tiefe: Cross-Turn ueber mehrere Zyklen ------------------------

    @Test
    fun legEnded_undoTiefe_onUndoWinGefolgtVonCrossTurnOnUndo_mehrfacherZyklusBleibtPersistenzKonsistent() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna), dartLimit = 2)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()
            val legId = singleLegId()

            fun playUntilLegEnd() {
                vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
                vm.onContinue()
                vm.onNumber(1)
                vm.onNumber(1)
            }

            repeat(2) { cycle ->
                playUntilLegEnd()
                val legWon = vm.uiState.value
                assertTrue("Zyklus ${cycle + 1}: LegWon erwartet", legWon is GameUiState.LegWon)
                assertEquals("Tom", (legWon as GameUiState.LegWon).legWinnerName)
                assertEquals(3, legWon.dartsUsed)
                assertEquals("Zyklus ${cycle + 1}: 2 Aufnahmen persistiert", 2, matchRepository.getTurns(legId).size)

                // Schritt 1: onUndoWin nimmt Annas leg-beendenden 2. Dart zurueck
                // (ihre Aufnahme bleibt mit dem 1. Dart offen).
                vm.onUndoWin()
                var playing = vm.awaitPlaying()
                assertEquals(1, playing.input.darts.size)
                assertEquals(1, matchRepository.getTurns(legId).size)

                // Schritt 2: regulaeres onUndo (intra-turn) nimmt Annas letzten
                // verbliebenen Dart zurueck -> ihre Aufnahme ist leer.
                vm.onUndo()
                playing = vm.uiState.value as GameUiState.Playing
                assertTrue(playing.input.darts.isEmpty())
                assertEquals("Anna", playing.players.first { it.isCurrent }.name)

                // Schritt 3: Cross-Turn-onUndo oeffnet Toms abgeschlossene
                // 3-Dart-Aufnahme wieder (2 Darts uebrig, Persistenz geloescht).
                vm.onUndo()
                playing = vm.uiState.value as GameUiState.Playing
                assertEquals("Tom", playing.players.first { it.isCurrent }.name)
                assertEquals(2, playing.input.darts.size)
                assertEquals(0, matchRepository.getTurns(legId).size)

                // Schritt 4+5: Toms verbleibende zwei Darts einzeln zurueck bis
                // zum Leg-Anfang.
                vm.onUndo()
                vm.onUndo()
                playing = vm.uiState.value as GameUiState.Playing
                assertFalse("Zyklus ${cycle + 1}: nichts mehr zurueckzunehmen", playing.canUndo)
                assertTrue(playing.input.darts.isEmpty())
                assertEquals("Tom", playing.players.first { it.isCurrent }.name)

                // Weiteres onUndo bleibt ein No-op.
                vm.onUndo()
                assertEquals(playing, vm.uiState.value)
            }
        }

    // --- Kontroll-Pause funktioniert nach onUndoWin im NAECHSTEN Leg normal ---

    @Test
    fun onUndoWin_nachLegEnded_dannOnNewLeg_kontrollpauseFunktioniertImNeuenLegWiederNormal() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna), dartLimit = 2)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Leg 1: Tom gewinnt per Rangvergleich (Werferin ist Anna).
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onContinue()
            vm.onNumber(1); vm.onNumber(1)
            vm.uiState.first { it is GameUiState.LegWon }

            // Versehentlich zurueckgenommen, dann erneut bestaetigt.
            vm.onUndoWin()
            vm.awaitPlaying()
            vm.onNumber(1)
            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)

            // Naechstes Leg: Rotation macht Anna zur Starterin (frische
            // Dart-Kontingente fuer beide -> kein legEnded moeglich, bevor Tom
            // ueberhaupt geworfen hat).
            vm.onNewLeg()
            val leg2Start = vm.awaitPlaying()
            assertEquals("Anna", leg2Start.currentName)
            assertNull(leg2Start.turnReview)

            // Annas volle Aufnahme im neuen Leg ist ein GANZ REGULAERES
            // 3-Dart-Ende (Tom hat noch nichts geworfen) -> die Kontroll-Pause
            // muss wieder ganz normal greifen.
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1)
            val afterAnna = vm.uiState.value as GameUiState.Playing
            assertNotNull("Kontroll-Pause greift im neuen Leg wieder normal", afterAnna.turnReview)
            assertEquals("Anna", afterAnna.turnReview!!.throwerName)
            vm.onContinue()
            assertEquals("Tom", (vm.uiState.value as GameUiState.Playing).currentName)
        }

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name
}
