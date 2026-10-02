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
import com.mechanicel.tomsdarts.testing.EliminationFakeMode
import com.mechanicel.tomsdarts.testing.EliminationState
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Test-Gate-Haertung der Eliminierungs-Infrastruktur auf [GameViewModel]-Ebene
 * (Killer-Infrastruktur, PR A), ergaenzend zu [GameViewModelOpponentBoardTest]
 * (Gegner-Sicht des Boards). Schwerpunkt hier: die Wechselwirkung des
 * Eliminierungs-Skips mit der Kontroll-Pause ([GameUiState.Playing.turnReview])
 * sowie mit den Sieg-Pfaden ([GameUiState.LegWon]/[GameViewModel.onNewLeg]/
 * [GameViewModel.onUndoWin]).
 *
 * Als Modus dient der geteilte [EliminationFakeMode] (Test-Fixture, kein
 * Produktions-Modus). Setup identisch zu den bestehenden Game-Tests:
 * In-Memory-Room mit synchronem (direktem) Executor. Laeuft host-seitig unter
 * Robolectric (SDK 34 gepinnt). Bleibt rein lokal (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelEliminationTest {

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
     * UI-Adapter fuer den Eliminierungs-Fake: nur Test-Code. Die verbleibenden
     * Leben sind eine reine Ableitung aus den GEGNER-Zustaenden und werden auf
     * das vorhandene [PlayerBoardUi.X01]-Board abgebildet (analog zu
     * [GameViewModelOpponentBoardTest]).
     */
    private class EliminationUiAdapter(
        private val mode: EliminationFakeMode,
    ) : ModeUiAdapter<EliminationState> {

        override fun board(state: EliminationState): PlayerBoardUi = PlayerBoardUi.X01(-1)

        override fun board(
            state: EliminationState,
            opponents: List<EliminationState>,
        ): PlayerBoardUi = PlayerBoardUi.X01(mode.livesOf(state, opponents))

        override fun checkout(state: EliminationState, config: GameConfig): List<Dart>? = null
    }

    private suspend fun newPlayer(name: String): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    private fun viewModel(
        playerIds: List<Long>,
        lives: Int = EliminationFakeMode.DEFAULT_LIVES,
        legsToWin: Int = 1,
    ): GameViewModel<EliminationState> {
        val mode = EliminationFakeMode(lives = lives)
        return GameViewModel(
            matchRepository = matchRepository,
            playerRepository = playerRepository,
            playerIds = playerIds,
            config = GameConfig(legsToWin = legsToWin, setsToWin = 1),
            mode = mode,
            uiAdapter = EliminationUiAdapter(mode),
            delightRegistry = DelightRegistry.EMPTY,
        )
    }

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private fun GameUiState.Playing.livesOf(playerId: Long): Int =
        (players.first { it.playerId == playerId }.board as PlayerBoardUi.X01).remaining

    private val GameUiState.Playing.currentName: String
        get() = players.first { it.isCurrent }.name

    // --- Skip x Kontrollpause ---------------------------------------------------

    @Test
    fun regulaeresAufnahmeEndeMitEliminiertemFolgespieler_turnReviewZeigtDenUeberspungenenSpielerKorrekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val ben = newPlayer("Ben")
            val chris = newPlayer("Chris")
            // 4 Spieler: Tom eliminiert Anna, aber Ben und Chris leben noch ->
            // kein Sieg, die Aufnahme endet regulaer -> Kontroll-Pause.
            val vm = viewModel(listOf(tom, anna, ben, chris))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(2) // Annas Zielzahl (Sitzplatz 1 -> 2) -> Anna raus.
            vm.onOut()
            vm.onOut()

            val playing = vm.uiState.value as GameUiState.Playing
            assertNotNull("regulaeres Ende loest die Kontroll-Pause aus", playing.turnReview)
            // Anna wird uebersprungen - der naechste Spieler ist Ben, NICHT Anna.
            assertEquals("Ben", playing.turnReview!!.nextPlayerName)

            vm.onContinue()

            val after = vm.uiState.value as GameUiState.Playing
            assertEquals("Ben", after.currentName)
        }

    // --- Skip x Sieg-Pfade -------------------------------------------------------

    @Test
    fun legSieg_waehrendAndererSpielerEliminiertIst_onNewLeg_alleWiederAktiv_starterKorrekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val ben = newPlayer("Ben")
            // legsToWin = 2: der erste Leg-Sieg fuehrt zu LegWon (nicht MatchWon).
            val vm = viewModel(listOf(tom, anna, ben), legsToWin = 2)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(2) // Anna raus.
            vm.onNumber(3) // Ben raus -> alle Gegner eliminiert -> Tom gewinnt.

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Tom", legWon.legWinnerName)
            // Rotation zeigt auf Anna - obwohl sie eben in diesem Leg eliminiert wurde.
            assertEquals("Anna", legWon.nextStarterName)
            assertEquals(2, legWon.dartsUsed)

            vm.onNewLeg()
            val playing = vm.awaitPlaying()

            // Anna ist die aktive Werferin des neuen Legs - kein Skip, da im
            // frischen Leg niemand mehr eliminiert ist.
            assertEquals("Anna", playing.currentName)
            assertEquals(EliminationFakeMode.DEFAULT_LIVES, playing.livesOf(tom))
            assertEquals(EliminationFakeMode.DEFAULT_LIVES, playing.livesOf(anna))
            assertEquals(EliminationFakeMode.DEFAULT_LIVES, playing.livesOf(ben))

            // Anna kann regulaer werfen (keine Blockade durch die alte Eliminierung).
            vm.onOut()
            val afterThrow = vm.uiState.value as GameUiState.Playing
            assertEquals(1, afterThrow.input.darts.size)
        }

    @Test
    fun onUndoWin_nachEliminierungsbehaftetemSieg_stelltEliminierungUndWerferKorrektWiederHer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            val ben = newPlayer("Ben")
            val vm = viewModel(listOf(tom, anna, ben), legsToWin = 2)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            vm.onNumber(2) // Dart 1: Anna raus.
            vm.onNumber(3) // Dart 2: Ben raus -> Tom gewinnt (2 Darts).

            vm.uiState.first { it is GameUiState.LegWon }

            vm.onUndoWin()
            val playing = vm.awaitPlaying()

            // Der SIEG-Dart (Bens Eliminierung) wird zurueckgenommen: Tom ist
            // wieder mitten in seiner Aufnahme (1 Dart), Ben wieder lebendig -
            // Anna bleibt eliminiert (ihr Dart liegt weiter vorn in der Historie).
            assertEquals("Tom", playing.currentName)
            assertEquals(1, playing.input.darts.size)
            assertEquals(0, playing.livesOf(anna))
            assertEquals(EliminationFakeMode.DEFAULT_LIVES, playing.livesOf(ben))
        }
}
