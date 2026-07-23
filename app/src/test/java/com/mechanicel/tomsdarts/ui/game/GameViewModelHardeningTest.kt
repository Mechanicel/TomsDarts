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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Zusaetzliche Haertungstests fuer das Mehrspieler-[GameViewModel] (Test-Gate)
 * neben [GameViewModelTest] (Happy-Path) und [GameViewModelEdgeCasesTest].
 * Schwerpunkte: genau ein aktiver Spieler, Miss-Aufnahme-Wechsel,
 * No-op nach Match-Ende, dartIndex-Persistenz, Turn-Zuordnung ueber zwei Legs,
 * dartsUsed ueber mehrere Aufnahmen sowie die NoPlayer-/Reihenfolge-Validierung.
 *
 * Setup identisch zu den bestehenden Game-Tests: In-Memory-Room mit synchronem
 * (direktem) Executor, damit die fire-and-forget-Persistenz unter
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] vor den Assertions
 * abgeschlossen ist. Laeuft host-seitig unter Robolectric (SDK 34). Offline-first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelHardeningTest {

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
    ) = GameViewModel(matchRepository, playerRepository, playerIds, config, X01Mode(), X01UiAdapter())

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    private fun GameUiState.Playing.remainingOf(playerId: Long): Int =
        players.first { it.playerId == playerId }.remaining

    private suspend fun matchId(): Long = matchRepository.getMatches().single().id

    private suspend fun legIdByNumber(number: Int): Long =
        matchRepository.getLegs(matchId()).first { it.legNumber == number }.id

    // --- Genau ein aktiver Spieler -------------------------------------------

    @Test
    fun playing_markiertImmerGenauEinenAktivenSpieler_auchNachWechsel() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val bjoern = newPlayer("Bjoern")
            val vm = viewModel(listOf(tom, anna, bjoern))
            backgroundScope.launch { vm.uiState.collect {} }
            val playing = vm.awaitPlaying()

            assertEquals(1, playing.players.count { it.isCurrent })
            assertEquals("Tom", playing.currentName)

            // Toms Aufnahme abschliessen + "Weiter" -> genau Anna ist jetzt aktiv.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onContinue()
            val afterFirst = vm.uiState.value as GameUiState.Playing
            assertEquals(1, afterFirst.players.count { it.isCurrent })
            assertEquals("Anna", afterFirst.currentName)

            // Annas Aufnahme abschliessen + "Weiter" -> genau Bjoern ist jetzt aktiv.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onContinue()
            val afterSecond = vm.uiState.value as GameUiState.Playing
            assertEquals(1, afterSecond.players.count { it.isCurrent })
            assertEquals("Bjoern", afterSecond.currentName)
        }

    // --- Miss-Aufnahme (3 Fehlwuerfe) ----------------------------------------

    @Test
    fun dreiMisses_wechselnSpielerUndPersistierenNullAufnahmeOhneBust() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            val bustBefore = vm.bustEvents.value
            vm.onOut(); vm.onOut(); vm.onOut() // Tom: 3 Fehlwuerfe -> Kontroll-Pause
            vm.onContinue()

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            // Miss aendert den Rest nicht und loest KEINEN Bust aus.
            assertEquals(501, playing.remainingOf(tom))
            assertEquals(bustBefore, vm.bustEvents.value)
            assertTrue(playing.input.darts.isEmpty())

            val turns = matchRepository.getTurns(legIdByNumber(1))
            assertEquals(1, turns.size)
            val turn = turns.single()
            assertEquals(tom, turn.playerId)
            assertEquals(0, turn.totalScored)
            assertFalse(turn.bust)
            assertEquals(listOf(0, 0, 0), matchRepository.getThrows(turn.id).map { it.value })
        }

    // --- dartIndex-Persistenz -------------------------------------------------

    @Test
    fun persistierteThrows_tragenAufsteigendenDartIndexAbEins() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(19); vm.onNumber(18)

            val turn = matchRepository.getTurns(legIdByNumber(1)).single { it.playerId == tom }
            val throws = matchRepository.getThrows(turn.id)
            assertEquals(listOf(1, 2, 3), throws.map { it.dartIndex }.sorted())
            // dartIndex korrespondiert mit der Wurf-Reihenfolge (Segmente 20,19,18).
            assertEquals(
                listOf(20, 19, 18),
                throws.sortedBy { it.dartIndex }.map { it.segment },
            )
        }

    // --- Turn-Zuordnung ueber zwei Legs --------------------------------------

    @Test
    fun turnsUeberZweiLegs_landenAmJeweilsRichtigenLeg() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Leg 1: Tom checkt sofort aus (Double 20) -> LegWon, Turn am Leg 1.
            vm.onToggleDouble(); vm.onNumber(20)
            vm.uiState.first { it is GameUiState.LegWon }
            vm.onNewLeg()
            vm.awaitPlaying() // Leg 2, Anna beginnt (Rotation)

            // Leg 2: Anna wirft eine Aufnahme (3 Fehlwuerfe) -> Turn am Leg 2.
            vm.onOut(); vm.onOut(); vm.onOut()

            val leg1Id = legIdByNumber(1)
            val leg2Id = legIdByNumber(2)

            val leg1Turns = matchRepository.getTurns(leg1Id)
            assertEquals(1, leg1Turns.size)
            assertEquals(tom, leg1Turns.single().playerId)
            assertTrue(leg1Turns.all { it.legId == leg1Id })

            val leg2Turns = matchRepository.getTurns(leg2Id)
            assertEquals(1, leg2Turns.size)
            assertEquals(anna, leg2Turns.single().playerId)
            assertTrue(leg2Turns.all { it.legId == leg2Id })
        }

    // --- dartsUsed ueber mehrere Aufnahmen im Gewinn-Leg ---------------------

    @Test
    fun legWon_dartsUsedZaehltAlleDartsDesGewinnersImLegUeberMehrereAufnahmen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 100, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom (100): 3x Single 20 = 60 -> 40 (3 Darts), "Weiter" -> Anna.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            vm.onContinue()
            // Anna: 3 Fehlwuerfe, "Weiter" -> zurueck zu Tom.
            vm.onOut(); vm.onOut(); vm.onOut()
            vm.onContinue()
            // Tom (40): Double 20 = 40 -> Checkout (1 Dart), Leg gewonnen.
            vm.onToggleDouble(); vm.onNumber(20)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            // IST-Verhalten: dartsUsed = Toms Darts im Gewinn-Leg = 3 + 1 = 4.
            assertEquals(4, legWon.dartsUsed)
        }

    // --- No-op nach Match-Ende ------------------------------------------------

    @Test
    fun nachMatchWon_sindWeitereEingabenNoOps_ohneCrashUndOhneWeiterenDbSchreib() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom checkt sofort aus -> Match gewonnen (legsToWin 1, setsToWin 1).
            vm.onToggleDouble(); vm.onNumber(20)
            vm.uiState.first { it is GameUiState.MatchWon }

            val turnsBefore = matchRepository.getTurns(legIdByNumber(1))
            val legsBefore = matchRepository.getLegs(matchId()).size
            assertEquals(1, turnsBefore.size)

            // Saemtliche weiteren Eingaben muessen wirkungslos bleiben.
            vm.onNumber(20)
            vm.onBull()
            vm.onOut()
            vm.onToggleDouble()
            vm.onToggleTriple()
            vm.onUndo()
            vm.onNewLeg()

            assertTrue(vm.uiState.value is GameUiState.MatchWon)
            assertEquals(turnsBefore.size, matchRepository.getTurns(legIdByNumber(1)).size)
            assertEquals(legsBefore, matchRepository.getLegs(matchId()).size)

            // Match bleibt mit Toms Gewinn abgeschlossen.
            val match = matchRepository.getMatches().single()
            assertNotNull(match.endedAt)
            assertEquals(tom, match.winnerId)
        }

    // --- NoPlayer-/Reihenfolge-Validierung -----------------------------------

    @Test
    fun leereSpielerliste_fuehrtZuNoPlayerOhneMatch() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel(emptyList())

            val state = vm.uiState.first { it !is GameUiState.Loading }
            assertTrue(state is GameUiState.NoPlayer)
            assertTrue(matchRepository.getMatches().isEmpty())
        }

    @Test
    fun einzigerGueltigerSpieler_fuehrtZuNoPlayerOhneMatch() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val vm = viewModel(listOf(tom))

            val state = vm.uiState.first { it !is GameUiState.Loading }
            assertTrue(state is GameUiState.NoPlayer)
            assertTrue(matchRepository.getMatches().isEmpty())
        }

    @Test
    fun dreiSpieler_werdenInEingabeReihenfolgeMitFortlaufenderPositionAngelegt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val bjoern = newPlayer("Bjoern")
            val vm = viewModel(listOf(bjoern, tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            val playing = vm.awaitPlaying()

            // Reihenfolge im Scoreboard == Eingabereihenfolge.
            assertEquals(listOf(bjoern, tom, anna), playing.players.map { it.playerId })

            val matchPlayers = matchRepository.getMatchPlayers(matchId())
            assertEquals(3, matchPlayers.size)
            assertEquals(
                listOf(bjoern, tom, anna),
                matchPlayers.sortedBy { it.position }.map { it.playerId },
            )
            assertEquals(listOf(0, 1, 2), matchPlayers.sortedBy { it.position }.map { it.position })
        }
}
