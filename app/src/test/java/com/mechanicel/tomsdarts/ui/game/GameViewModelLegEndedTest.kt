package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.delight.DelightRegistry
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
 * Basistests der Gewinner-Aufloesung im [GameViewModel] fuer Legs, die OHNE
 * Werfer-Sieg enden (`legEnded`): Anzeige, Darts-Zahl und Persistenz muessen dem
 * per Rangvergleich ermittelten Gewinner folgen - nicht dem Werfer des letzten
 * Darts -, waehrend die Aufnahme selbst weiterhin dem Werfer gehoert.
 *
 * Als Modus dient der geteilte [RoundLimitFakeMode] (Test-Fixture, kein
 * Produktions-Modus) mit `dartLimit = 2`. Ablauf in jedem Test: Tom wirft eine
 * volle Aufnahme (3 Darts, das Kontingent bremst ihn nicht, weil Anna noch nicht
 * geworfen hat), "Weiter" beendet die Kontroll-Pause, dann beendet ANNA mit
 * ihrem zweiten Dart das Leg - gewinnen tut es aber TOM (mehr Punkte, und mit 3
 * statt 2 Darts auch unterscheidbar in `dartsUsed`).
 *
 * Setup identisch zu den bestehenden Game-Tests: In-Memory-Room mit synchronem
 * (direktem) Executor, damit die im [GameViewModel] fire-and-forget feuernde
 * Persistenz deterministisch abgeschlossen ist, bevor die Assertions lesen.
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt). Bleibt rein lokal
 * (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelLegEndedTest {

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

    /**
     * UI-Adapter fuer den Rundenlimit-Fake: nur Test-Code. Der Punktestand wird
     * bewusst auf das vorhandene [PlayerBoardUi.X01]-Board abgebildet, damit
     * dieser Infrastruktur-Test ohne neue UI-Bausteine auskommt.
     */
    private class RoundLimitUiAdapter : ModeUiAdapter<RoundLimitState> {
        override fun board(state: RoundLimitState): PlayerBoardUi =
            PlayerBoardUi.X01(state.points)

        override fun checkout(state: RoundLimitState, config: GameConfig): List<Dart>? = null
    }

    private suspend fun twoPlayers(): Pair<Long, Long> =
        db.playerDao().insert(Player(name = "Tom", createdAt = 1L)) to
            db.playerDao().insert(Player(name = "Anna", createdAt = 1L))

    private fun viewModel(
        playerIds: List<Long>,
        legsToWin: Int = 2,
        setsToWin: Int = 1,
    ) = GameViewModel(
        matchRepository = matchRepository,
        playerRepository = playerRepository,
        playerIds = playerIds,
        config = GameConfig(legsToWin = legsToWin, setsToWin = setsToWin),
        mode = RoundLimitFakeMode(dartLimit = 2),
        uiAdapter = RoundLimitUiAdapter(),
        delightRegistry = DelightRegistry.EMPTY,
    )

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    /**
     * Spielt bis zum leg-beendenden Dart: Tom wirft 3x 20 (Kontroll-Pause +
     * "Weiter"), Anna wirft 1x 1 und beendet danach mit einem weiteren Dart das
     * Leg (Tom fuehrt mit 60:2).
     */
    private fun GameViewModel<*>.playUntilLegEnd() {
        onNumber(20); onNumber(20); onNumber(20)
        onContinue()
        onNumber(1)
        onNumber(1)
    }

    private suspend fun singleLegId(): Long =
        matchRepository.getLegs(matchRepository.getMatches().first().id).first().id

    // --- Leg-Ende ohne Werfer-Sieg --------------------------------------------

    @Test
    fun legEnded_zeigtDenRangSiegerAlsLegGewinner_nichtDenWerfer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.playUntilLegEnd()

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            // Den letzten Dart warf Anna - gewonnen hat aber Tom (60:2).
            assertEquals("Tom", legWon.legWinnerName)
            // Darts des GEWINNERS (Tom: 3), nicht des Werfers (Anna: 2).
            assertEquals(3, legWon.dartsUsed)
            // Rotation und Leg-Nummer bleiben gewinner-agnostisch.
            assertEquals("Anna", legWon.nextStarterName)
            assertEquals(2, legWon.nextLegNumber)
            assertEquals(1, legWon.players.first { it.playerId == tom }.legsWon)
            assertEquals(0, legWon.players.first { it.playerId == anna }.legsWon)
        }

    @Test
    fun legEnded_schliesstDasLegMitDerGewinnerIdAb_undBuchtDieAufnahmeAufDenWerfer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()
            val legId = singleLegId()

            vm.playUntilLegEnd()
            vm.uiState.first { it is GameUiState.LegWon }

            // Leg abgeschlossen auf den Rang-Sieger Tom.
            val leg = matchRepository.getLegs(matchRepository.getMatches().first().id).single()
            assertEquals(tom, leg.winnerId)
            assertTrue("Leg ist beendet", (leg.endedAt ?: 0L) > 0L)

            // Zwei Aufnahmen: Toms regulaere und Annas leg-beendende - die
            // Sieg-Aufnahme gehoert dem WERFER (Anna), nicht dem Gewinner.
            val turns = matchRepository.getTurns(legId)
            assertEquals(2, turns.size)
            assertEquals(tom, turns[0].playerId)
            assertEquals(anna, turns[1].playerId)
            assertEquals(2, matchRepository.getThrows(turns[1].id).size)
        }

    @Test
    fun legEnded_alsMatchEnde_zeigtUndPersistiertDenRangSieger() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            // legsToWin = 1 -> das erste Leg entscheidet zugleich das Match.
            val vm = viewModel(listOf(tom, anna), legsToWin = 1)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.playUntilLegEnd()

            val matchWon = vm.uiState.first { it is GameUiState.MatchWon } as GameUiState.MatchWon
            assertEquals("Tom", matchWon.matchWinnerName)
            assertEquals(3, matchWon.dartsUsed)

            val match = matchRepository.getMatches().single()
            assertEquals(tom, match.winnerId)
            assertEquals(tom, matchRepository.getLegs(match.id).single().winnerId)
        }

    // --- Sieg zuruecknehmen nach einem Leg-Ende ohne Werfer-Sieg ---------------

    @Test
    fun onUndoWin_nachLegEnded_oeffnetDasLegWiederUndLoeschtDieAufnahmeDesWerfers() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()
            val legId = singleLegId()

            vm.playUntilLegEnd()
            vm.uiState.first { it is GameUiState.LegWon }
            assertEquals(2, matchRepository.getTurns(legId).size)

            vm.onUndoWin()
            val playing = vm.awaitPlaying()

            // Leg wieder offen, Leg-Gewinn von Tom zurueckgenommen.
            val leg = matchRepository.getLegs(matchRepository.getMatches().first().id).single()
            assertNull(leg.winnerId)
            assertNull(leg.endedAt)
            assertEquals(0, playing.players.first { it.playerId == tom }.legsWon)
            // Geloescht wird die Aufnahme des WERFERS (Anna) - Toms Aufnahme bleibt.
            val turns = matchRepository.getTurns(legId)
            assertEquals(1, turns.size)
            assertEquals(tom, turns.single().playerId)
            // Annas Aufnahme ist wieder offen: sie ist am Zug mit einem Dart.
            assertEquals("Anna", playing.players.first { it.isCurrent }.name)
            assertEquals(1, playing.input.darts.size)
            assertTrue(playing.canUndo)
        }

    @Test
    fun onUndoWin_nachLegEnded_erneuterAbschlussFuehrtZumSelbenErgebnis() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()
            val legId = singleLegId()

            vm.playUntilLegEnd()
            vm.uiState.first { it is GameUiState.LegWon }

            vm.onUndoWin()
            vm.awaitPlaying()

            // Anna wirft ihren zweiten Dart erneut -> gleiches Ergebnis, keine
            // Verdopplung in der Persistenz.
            vm.onNumber(1)
            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            assertEquals(3, legWon.dartsUsed)
            assertEquals(2, matchRepository.getTurns(legId).size)
            assertEquals(
                tom,
                matchRepository.getLegs(matchRepository.getMatches().first().id).single().winnerId,
            )
        }
}
