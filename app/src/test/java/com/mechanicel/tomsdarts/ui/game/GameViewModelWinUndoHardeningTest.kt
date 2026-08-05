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
 * Test-Gate-Haertung fuer [GameViewModel.onUndoWin] ("Sieg zuruecknehmen").
 * Ergaenzt [GameViewModelTest] und [GameViewModelUndoHardeningTest] um:
 * - mehrfache Sieg-Undo-Zyklen (Sieg -> Undo -> erneuter Sieg -> Undo ->
 *   regulaeres Weiterspielen) mit Zaehler- und Persistenz-Konsistenz,
 * - einen tieferen Undo als der Sieg-Dart, der ueber die Aufnahme-Grenze hinweg
 *   in die vorige Aufnahme eines ANDEREN Spielers zurueckspult, der danach
 *   stattdessen gewinnt (Turn-Persistenz muss exakt dem neuen Gewinner folgen),
 * - die Set-Grenze (ein Leg-Sieg, der zugleich einen Set abschliesst) inkl.
 *   Persistenz-Check auf ein fehlendes Orphan-Leg,
 * - einen Match-Sieg-Undo-Zyklus ueber mehrere Runden inkl. eines Wechsels der
 *   Checkout-Sequenz (andere Anzahl Darts) mit Persistenz-Konsistenz,
 * - Idempotenz/Race-Absicherung: doppeltes [GameViewModel.onUndoWin] sowie der
 *   Aufruf NACH bereits gedruecktem [GameViewModel.onNewLeg] bleiben No-ops,
 *   ebenso [GameViewModel.onUndo] im [GameUiState.MatchWon]-Zustand,
 * - die Wechselwirkung mit der Kontroll-Pause (ADR-0026): nach [GameViewModel.onUndoWin]
 *   loest ein regulaeres (nicht siegreiches) Aufnahme-Ende wieder die normale
 *   Kontroll-Pause aus; ein erneuter Sieg tut es NICHT.
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
class GameViewModelWinUndoHardeningTest {

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
        config: GameConfig = GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
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

    /** Spielt eine volle Checkout-Aufnahme: Double (startScore/2) in einem Dart. */
    private fun GameViewModel<*>.checkout(half: Int) {
        onToggleDouble()
        onNumber(half)
    }

    // --- Mehrfacher Sieg-Undo-Zyklus, dann regulaeres Weiterspielen -----------

    @Test
    fun onUndoWin_siegUndoErneuterSiegUndoWeiterspielen_zaehlerUndPersistenzKonsistent() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val matchId = matchRepository.getMatches().single().id
            val legId = singleLegId()

            // Sieg 1: Tom checkt sofort aus.
            vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }
            assertEquals(1, matchRepository.getTurns(legId).size)

            vm.onUndoWin()
            vm.awaitPlaying()
            assertEquals(0, matchRepository.getTurns(legId).size)

            // Sieg 2 (derselbe Dart, gleicher Spieler): erneut LegWon, keine
            // Verdopplung in der Persistenz.
            vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }
            assertEquals(1, matchRepository.getTurns(legId).size)

            vm.onUndoWin()
            val afterSecondUndo = vm.awaitPlaying()
            assertEquals(0, matchRepository.getTurns(legId).size)
            assertEquals(0, afterSecondUndo.player("Tom").legsWon)
            assertEquals(1, afterSecondUndo.currentLegNumber)
            assertEquals("Tom", afterSecondUndo.currentName)
            assertEquals(40, afterSecondUndo.remainingOf(tom))

            // Weiterspielen (regulaer, kein Sieg): normaler Wechsel via
            // Kontroll-Pause, throw-level Persistenz laeuft wie gewohnt.
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1)
            assertNotNull((vm.uiState.value as GameUiState.Playing).turnReview)
            vm.onContinue()

            val afterContinue = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", afterContinue.currentName)
            assertEquals(37, afterContinue.remainingOf(tom))
            assertEquals(1, matchRepository.getTurns(legId).size)
            // Kein zusaetzliches Leg wurde angelegt.
            assertEquals(1, matchRepository.getLegs(matchId).size)
        }

    // --- Tieferer Undo als der Sieg-Dart: anderer Spieler gewinnt -------------

    @Test
    fun onUndoWin_tieferAlsSiegDart_andererSpielerGewinnt_persistenzFolgtNeuemGewinner() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val legId = singleLegId()

            // Tom wirft eine volle harmlose Aufnahme (3x Single 1); "Weiter"
            // persistiert seinen Turn und wechselt zu Anna.
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1)
            vm.onContinue()
            assertEquals("Anna", (vm.uiState.value as GameUiState.Playing).currentName)
            assertEquals(1, matchRepository.getTurns(legId).size)

            // Anna checkt SOFORT aus (Rest 40, D20) -> LegWon (Anna).
            vm.checkout(20)
            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Anna", legWon.legWinnerName)
            assertEquals(2, matchRepository.getTurns(legId).size)

            // Sieg zurueck: Annas Aufnahme wieder offen (leer).
            vm.onUndoWin()
            var playing = vm.awaitPlaying()
            assertEquals("Anna", playing.currentName)
            assertTrue(playing.input.darts.isEmpty())
            assertEquals(1, matchRepository.getTurns(legId).size)

            // Weiter zurueck (Cross-Turn): Toms Aufnahme wird wieder geoeffnet.
            vm.onUndo()
            playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Tom", playing.currentName)
            assertEquals(2, playing.input.darts.size)
            assertEquals(0, matchRepository.getTurns(legId).size)

            // Tom checkt STATTDESSEN aus: sein Rest nach 2x Single 1 ist 38 ->
            // Double 19 checkt exakt.
            vm.onToggleDouble(); vm.onNumber(19)

            val legWon2 = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon2.legWinnerName)
            assertEquals(3, legWon2.dartsUsed)

            // Persistenz folgt exakt dem NEUEN Gewinner: genau EIN Turn (Tom,
            // 3 Darts), Annas verworfener Sieg-Turn ist unwiederbringlich weg.
            val turns = matchRepository.getTurns(legId)
            assertEquals(1, turns.size)
            val turn = turns.single()
            assertEquals(tom, turn.playerId)
            assertEquals(3, matchRepository.getThrows(turn.id).size)

            val leg = matchRepository.getLegs(matchRepository.getMatches().single().id).single()
            assertEquals(tom, leg.winnerId)
            assertNotNull(leg.endedAt)
        }

    // --- Set-Grenze: Leg-Sieg, der zugleich den Set abschliesst ---------------

    @Test
    fun onUndoWin_anSetGrenze_revertiertSetZaehlerOhneOrphanLeg() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 2),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Leg 1: Tom gewinnt sofort.
            vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }
            vm.onNewLeg()
            vm.awaitPlaying()

            val matchId = matchRepository.getMatches().single().id
            assertEquals(2, matchRepository.getLegs(matchId).size)

            // Leg 2: Anna (Rotation) verfehlt regulaer (Kontroll-Pause), dann
            // checkt Tom aus -> Leg 2 UND Set 1 (legsToWin=2) sind komplett.
            vm.onOut(); vm.onOut(); vm.onOut()
            vm.onContinue()
            vm.checkout(20)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals(1, legWon.players.first { it.name == "Tom" }.setsWon)
            assertEquals(0, legWon.players.first { it.name == "Tom" }.legsWon)
            assertEquals(2, matchRepository.getLegs(matchId).size)

            vm.onUndoWin()

            val playing = vm.awaitPlaying()
            assertEquals(0, playing.player("Tom").setsWon)
            assertEquals(1, playing.player("Tom").legsWon)
            assertEquals(1, playing.currentSetNumber)
            assertEquals(2, playing.currentLegNumber)

            // Kein Orphan-Leg: weiterhin genau 2 Legs (Leg 1 + Leg 2), Leg 2
            // wieder offen.
            assertEquals(2, matchRepository.getLegs(matchId).size)
            val leg2 = matchRepository.getLegs(matchId).first { it.legNumber == 2 }
            assertNull(leg2.endedAt)
            assertNull(leg2.winnerId)

            // Erneuter Sieg -> Set wieder komplett, immer noch kein Orphan-Leg.
            vm.checkout(20)
            val legWonAgain = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals(1, legWonAgain.players.first { it.name == "Tom" }.setsWon)
            assertEquals(2, matchRepository.getLegs(matchId).size)
        }

    // --- Match-Sieg-Undo ueber mehrere Runden mit wechselnder Dart-Anzahl -----

    @Test
    fun onUndoWin_matchGewinnMehrfachZyklusMitAndererCheckoutSequenz_persistenzKonsistent() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 60, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val matchId = matchRepository.getMatches().single().id
            val legId = singleLegId()

            // Runde 1: Single 10, Single 10, Double 20 -> MatchWon (3 Darts).
            vm.onNumber(10); vm.onNumber(10); vm.checkout(20)
            var matchWon = vm.uiState.first { it is GameUiState.MatchWon } as GameUiState.MatchWon
            assertEquals(3, matchWon.dartsUsed)
            assertEquals(1, matchRepository.getTurns(legId).size)

            vm.onUndoWin()
            vm.awaitPlaying()
            assertEquals(0, matchRepository.getTurns(legId).size)
            var match = matchRepository.getMatches().single()
            assertNull(match.endedAt)
            assertNull(match.winnerId)
            var leg = matchRepository.getLegs(matchId).single()
            assertNull(leg.endedAt)
            assertNull(leg.winnerId)

            // Runde 2: Rest ist bereits 40 (2 Darts stehen noch) -> ein
            // einzelner weiterer Double-20-Dart checkt erneut aus.
            vm.checkout(20)
            matchWon = vm.uiState.first { it is GameUiState.MatchWon } as GameUiState.MatchWon
            assertEquals(3, matchWon.dartsUsed)
            assertEquals(1, matchRepository.getTurns(legId).size)

            vm.onUndoWin()
            vm.awaitPlaying()

            // Runde 3: komplett bis zum Leg-Anfang zurueckrudern und mit einer
            // ANDEREN Dart-Sequenz gewinnen (Single 20, Single 20, Double 10).
            vm.onUndo(); vm.onUndo()
            val rewound = vm.uiState.value as GameUiState.Playing
            assertTrue(rewound.input.darts.isEmpty())
            assertEquals(60, rewound.remainingOf(tom))
            assertEquals(0, matchRepository.getTurns(legId).size)

            vm.onNumber(20); vm.onNumber(20)
            vm.onToggleDouble(); vm.onNumber(10)

            val matchWon2 = vm.uiState.first { it is GameUiState.MatchWon } as GameUiState.MatchWon
            assertEquals(3, matchWon2.dartsUsed)

            // Persistenz ueber alle drei Runden: genau EIN Match, EIN Leg, EIN
            // Turn mit den DREI Darts der letzten (dritten) Sequenz.
            assertEquals(1, matchRepository.getMatches().size)
            assertEquals(1, matchRepository.getLegs(matchId).size)
            val finalTurns = matchRepository.getTurns(legId)
            assertEquals(1, finalTurns.size)
            val throws = matchRepository.getThrows(finalTurns.single().id)
            assertEquals(3, throws.size)
            assertEquals(listOf(20, 20, 10), throws.map { it.segment })

            match = matchRepository.getMatches().single()
            assertNotNull(match.endedAt)
            assertEquals(tom, match.winnerId)
        }

    // --- Idempotenz / Race-Absicherung -----------------------------------------

    @Test
    fun onUndoWin_zweimalHintereinander_zweiterAufrufIstNoOp() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }

            vm.onUndoWin()
            val afterFirst = vm.awaitPlaying()

            // Zustand ist jetzt Playing -> ein zweiter Aufruf ist ein No-op.
            vm.onUndoWin()
            assertEquals(afterFirst, vm.uiState.value)
        }

    @Test
    fun onUndoWin_nachBereitsGedruecktemOnNewLeg_istNoOp() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }
            vm.onNewLeg()
            val afterNewLeg = vm.awaitPlaying()

            // Zustand ist Playing (naechstes Leg laeuft bereits) -> No-op.
            vm.onUndoWin()
            assertEquals(afterNewLeg, vm.uiState.value)

            // Der Sieg von Leg 1 ist endgueltig: dessen Leg-Zeile bleibt
            // abgeschlossen, es existiert zusaetzlich Leg 2 (kein Loeschen/
            // Zuruecksetzen durch den No-op-Aufruf).
            val matchId = matchRepository.getMatches().single().id
            val legs = matchRepository.getLegs(matchId)
            assertEquals(2, legs.size)
            val leg1 = legs.single { it.legNumber == 1 }
            assertEquals(tom, leg1.winnerId)
            assertNotNull(leg1.endedAt)
        }

    @Test
    fun onUndo_imMatchWonZustand_bleibtWirkungslos() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.checkout(20)
            val matchWon = vm.uiState.first { it is GameUiState.MatchWon }
            val legId = singleLegId()

            // Das regulaere Undo bleibt im MatchWon-Zustand wirkungslos.
            vm.onUndo()
            assertEquals(matchWon, vm.uiState.value)
            assertEquals(1, matchRepository.getTurns(legId).size)

            // Nur onUndoWin nimmt den Match-Sieg zurueck.
            vm.onUndoWin()
            vm.awaitPlaying()
            assertEquals(0, matchRepository.getTurns(legId).size)
        }

    // --- Wechselwirkung mit der Kontroll-Pause (ADR-0026) ----------------------

    @Test
    fun onUndoWin_dannRegulaeresAufnahmeEndeOhneSieg_loestKontrollPauseAus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 60, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom: Single 10, Single 10, Double 20 -> LegWon.
            vm.onNumber(10); vm.onNumber(10); vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }

            vm.onUndoWin()
            vm.awaitPlaying()

            // Statt erneut zu checken, wirft Tom regulaer (kein Sieg, kein
            // Bust) weiter -> normale Kontroll-Pause, KEIN LegWon.
            vm.onNumber(1)

            val playing = vm.uiState.value as GameUiState.Playing
            assertNotNull("Regulaeres Ende nach Sieg-Undo loest Kontroll-Pause aus", playing.turnReview)
            assertEquals("Tom", playing.turnReview!!.throwerName)
            assertEquals(39, playing.remainingOf(tom))
        }

    @Test
    fun onUndoWin_zweiDartsZurueckgenommen_neuGeworfen_regulaerePauseFunktioniert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 60, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(10); vm.onNumber(10); vm.checkout(20)
            vm.uiState.first { it is GameUiState.LegWon }

            vm.onUndoWin()
            vm.awaitPlaying()

            // Beide verbliebenen Darts der wieder geoeffneten Aufnahme zurueck.
            vm.onUndo(); vm.onUndo()
            val rewound = vm.uiState.value as GameUiState.Playing
            assertTrue(rewound.input.darts.isEmpty())
            assertEquals(60, rewound.remainingOf(tom))

            // Neu werfen: 3 harmlose Darts -> regulaere Kontroll-Pause greift
            // unveraendert.
            vm.onNumber(1); vm.onNumber(1); vm.onNumber(1)
            val playing = vm.uiState.value as GameUiState.Playing
            assertNotNull(playing.turnReview)
            vm.onContinue()
            assertEquals("Anna", (vm.uiState.value as GameUiState.Playing).currentName)
        }
}
