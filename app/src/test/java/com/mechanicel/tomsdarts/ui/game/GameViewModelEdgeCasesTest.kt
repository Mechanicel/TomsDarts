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
 * Edge-Case-, Fehlerpfad- und Regressionstests fuer das Mehrspieler-[GameViewModel]
 * als Ergaenzung zu den Happy-Path-Basistests in [GameViewModelTest]. Haertet
 * Spielerwechsel, Bust/Checkout, In-Turn-Undo, Leg-/Match-Uebergaenge und die
 * throw-level-Persistenz pro Spieler ueber In-Memory-Room (echte Repositories) ab.
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

    private fun GameUiState.Playing.remainingOf(playerId: Long): Int =
        players.first { it.playerId == playerId }.remaining

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    // --- Mehrere Aufnahmen mit Spielerwechsel ---------------------------------

    @Test
    fun mehrereAufnahmen_wechselnSpielerUndPersistierenProAufnahmeGenauEinenTurn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20); vm.onContinue() // Tom -> 441
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20); vm.onContinue() // Anna -> 441
            vm.onNumber(19); vm.onNumber(19); vm.onNumber(19); vm.onContinue() // Tom -> 384

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            assertEquals(384, playing.remainingOf(tom))
            assertEquals(441, playing.remainingOf(anna))

            val turns = matchRepository.getTurns(singleLegId())
            assertEquals(3, turns.size)
            assertEquals(listOf(0, 1, 2), turns.map { it.turnIndex })
            assertEquals(listOf(tom, anna, tom), turns.sortedBy { it.turnIndex }.map { it.playerId })
            turns.forEach { assertEquals(3, matchRepository.getThrows(it.id).size) }
        }

    // --- Bust mitten im Leg ---------------------------------------------------

    @Test
    fun bust_feuertEreignisRevertiertAufAufnahmeStartUndWechseltSpieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 100, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20); vm.onContinue() // Tom 100 -> 40
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20); vm.onContinue() // Anna 100 -> 40

            val bustBefore = vm.bustEvents.value
            // Tom (Start 40): Single 5 -> 35, Triple 20 = 60 -> Bust.
            vm.onNumber(5)
            vm.onToggleTriple()
            vm.onNumber(20)

            assertEquals(bustBefore + 1, vm.bustEvents.value)

            val playing = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", playing.currentName)
            // Toms Rest zurueck auf Aufnahme-Start (40), nicht Leg-Start (100).
            assertEquals(40, playing.remainingOf(tom))

            val turns = matchRepository.getTurns(singleLegId())
            val bustTurn = turns.single { it.turnIndex == 2 }
            assertEquals(tom, bustTurn.playerId)
            assertTrue(bustTurn.bust)
            assertEquals(0, bustTurn.totalScored)
            assertEquals(listOf(5, 60), matchRepository.getThrows(bustTurn.id).map { it.value })
        }

    // --- Checkout zaehlt Bust-Darts des Gewinners mit ------------------------

    @Test
    fun checkout_dartsUsedZaehltBustDartsDesGewinnersMit() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 1, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Tom (40): Single 20 -> 20, Triple 20 = 60 -> Bust (2 Darts), Wechsel.
            vm.onNumber(20)
            vm.onToggleTriple()
            vm.onNumber(20)
            // Anna: 3 Fehlwuerfe, "Weiter" -> Wechsel zurueck zu Tom.
            vm.onOut(); vm.onOut(); vm.onOut()
            vm.onContinue()
            // Tom (40): Double 20 = 40 -> Checkout = Match (legsToWin 1).
            vm.onToggleDouble()
            vm.onNumber(20)

            val matchWon = vm.uiState.first { it is GameUiState.MatchWon } as GameUiState.MatchWon
            assertEquals("Tom", matchWon.matchWinnerName)
            // Toms Leg-Darts: 2 (Bust) + 1 (Checkout) = 3.
            assertEquals(3, matchWon.dartsUsed)

            val match = matchRepository.getMatches().single()
            assertNotNull(match.endedAt)
            assertEquals(tom, match.winnerId)
        }

    // --- onUndo ---------------------------------------------------------------

    @Test
    fun onUndo_zweimal_stelltBeideDartsDesAktuellenWerfersZurueck() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20) // 481
            vm.onToggleTriple()
            vm.onNumber(20) // Triple 20 -> 421
            assertEquals(421, (vm.uiState.value as GameUiState.Playing).remainingOf(tom))

            vm.onUndo()
            assertEquals(481, (vm.uiState.value as GameUiState.Playing).remainingOf(tom))
            vm.onUndo()
            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(501, after.remainingOf(tom))
            assertEquals("Tom", after.currentName)
            assertTrue(after.input.darts.isEmpty())
        }

    @Test
    fun onUndo_aufLeererAufnahme_istKeinEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            val playing = vm.awaitPlaying()
            assertTrue(playing.input.darts.isEmpty())

            vm.onUndo()

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(501, after.remainingOf(tom))
            assertTrue(after.input.darts.isEmpty())
        }

    @Test
    fun onUndo_nachAufnahmeEndeUndSpielerwechsel_oeffnetVorigeAufnahmeWieder() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Bewusste Verhaltensaenderung: Undo spult jetzt ueber die
            // Aufnahme-/Spielerwechsel-Grenze zurueck (frueher: kein Effekt).
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20) // Tom fertig
            vm.onContinue() // "Weiter" -> Anna
            val ended = vm.uiState.value as GameUiState.Playing
            assertEquals("Anna", ended.currentName)
            assertEquals(1, matchRepository.getTurns(singleLegId()).size)

            vm.onUndo() // Toms abgeschlossene Aufnahme wird wieder geoeffnet

            val after = vm.uiState.value as GameUiState.Playing
            // Tom wieder am Zug mit zwei Darts (Rest 461), sein Turn ist aus der DB.
            assertEquals("Tom", after.currentName)
            assertEquals(2, after.input.darts.size)
            assertEquals(461, after.remainingOf(tom))
            assertTrue(matchRepository.getTurns(singleLegId()).isEmpty())
        }

    // --- onNewLeg -------------------------------------------------------------

    @Test
    fun onNewLeg_ausLegWon_legtZweitesLegAnUndRotiertStartspieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onToggleDouble(); vm.onNumber(20) // Tom gewinnt Leg 1
            vm.uiState.first { it is GameUiState.LegWon }

            val matchId = matchRepository.getMatches().single().id
            vm.onNewLeg()

            val playing = vm.awaitPlaying()
            assertEquals(2, playing.currentLegNumber)
            assertEquals("Anna", playing.currentName)
            assertTrue(playing.players.all { it.remaining == 40 })

            val legs = matchRepository.getLegs(matchId)
            assertEquals(2, legs.size)
            assertEquals(listOf(1, 2), legs.map { it.legNumber }.sorted())
        }

    @Test
    fun onNewLeg_ausPlaying_istKeinEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNewLeg() // im Playing-Zustand wirkungslos

            assertTrue(vm.uiState.value is GameUiState.Playing)
            assertEquals(1, matchRepository.getLegs(matchRepository.getMatches().single().id).size)
        }

    // --- NoPlayer -------------------------------------------------------------

    @Test
    fun keineGueltigenSpieler_fuehrtZuNoPlayerOhneMatch() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel(listOf(-1L, -2L))

            val state = vm.uiState.first { it !is GameUiState.Loading }
            assertTrue(state is GameUiState.NoPlayer)
            assertTrue(matchRepository.getMatches().isEmpty())
        }

    // Regression fuer die SET_NULL-Anonymisierung: eine zuvor real existierende,
    // dann geloeschte Spieler-ID darf beim Match-Start nicht crashen, sondern
    // muss (mangels zweitem gueltigen Spieler) zu NoPlayer fuehren.
    @Test
    fun matchStartMitEchtGeloeschterSpielerId_fuehrtZuNoPlayerOhneMatch() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val ghost = newPlayer("Ghost")
            playerRepository.deletePlayer(playerRepository.getPlayer(ghost)!!)

            val vm = viewModel(listOf(tom, ghost))

            val state = vm.uiState.first { it !is GameUiState.Loading }
            assertTrue(state is GameUiState.NoPlayer)
            assertTrue(matchRepository.getMatches().isEmpty())
        }

    // Drei Spieler, einer davon zuvor geloescht: die verbliebenen zwei starten
    // regulaer, der geloeschte faellt heraus (dokumentiertes Verhalten im
    // GameViewModel: "unbekannte IDs fallen heraus").
    @Test
    fun matchStartMitDreiSpielernEinerGeloescht_startetMitDenVerbleibendenZweien() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val ghost = newPlayer("Ghost")
            playerRepository.deletePlayer(playerRepository.getPlayer(ghost)!!)

            val vm = viewModel(listOf(tom, ghost, anna))
            backgroundScope.launch { vm.uiState.collect {} }

            val playing = vm.awaitPlaying()
            assertEquals(2, playing.players.size)
            assertEquals(listOf(tom, anna), playing.players.map { it.playerId })
        }

    // --- Toggle-Modifier ------------------------------------------------------

    @Test
    fun onToggleDouble_wertetNaechstenDartDoppeltUndResettetDanachAufSingle() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onToggleDouble()
            vm.onNumber(20) // Double 20 = 40 -> 461

            val afterDouble = vm.uiState.value as GameUiState.Playing
            assertEquals(461, afterDouble.remainingOf(tom))
            assertEquals(Dart(20, 2), afterDouble.input.darts.last())
            assertEquals(DartModifier.SINGLE, afterDouble.input.modifier)

            vm.onNumber(20) // wieder Single 20 -> 441
            val afterSingle = vm.uiState.value as GameUiState.Playing
            assertEquals(441, afterSingle.remainingOf(tom))
            assertEquals(Dart(20, 1), afterSingle.input.darts.last())
            assertFalse(afterSingle.input.darts.isEmpty())
        }
}
