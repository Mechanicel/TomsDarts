package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.EliminationFakeMode
import com.mechanicel.tomsdarts.testing.EliminationState
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Basistests der Gegner-Sicht in der Anzeige (Killer-Infrastruktur, PR A): das
 * [GameViewModel] muss jeder Spieler-Karte ueber die zweistellige
 * [ModeUiAdapter.board]-Variante die Zustaende ALLER ANDEREN Spieler mitgeben -
 * sonst koennen Modi mit abgeleiteten Cross-Player-Werten (z.B. Killer-Leben) gar
 * nicht anzeigen, wie es um einen Spieler steht.
 *
 * Als Modus dient der geteilte [EliminationFakeMode] (Test-Fixture, kein
 * Produktions-Modus) mit zwei Leben je Spieler; der Test-Adapter unten bildet die
 * ABGELEITETEN Leben auf das vorhandene [PlayerBoardUi.X01]-Board ab, damit dieser
 * Infrastruktur-Test ohne neue UI-Bausteine auskommt.
 *
 * Setup identisch zu den bestehenden Game-Tests: In-Memory-Room mit synchronem
 * (direktem) Executor. Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 * Bleibt rein lokal (offline-first).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelOpponentBoardTest {

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
     * Leben sind eine reine Ableitung aus den GEGNER-Zustaenden und werden auf das
     * vorhandene [PlayerBoardUi.X01]-Board abgebildet.
     *
     * Die einstellige [board]-Variante liefert bewusst den unmoeglichen
     * Markierungswert [NO_OPPONENTS] - taucht er in einer Karte auf, hat das
     * ViewModel die Gegner-Zustaende NICHT durchgereicht.
     */
    private class EliminationUiAdapter(
        private val mode: EliminationFakeMode,
    ) : ModeUiAdapter<EliminationState> {

        override fun board(state: EliminationState): PlayerBoardUi =
            PlayerBoardUi.X01(NO_OPPONENTS)

        override fun board(
            state: EliminationState,
            opponents: List<EliminationState>,
        ): PlayerBoardUi = PlayerBoardUi.X01(mode.livesOf(state, opponents))

        override fun checkout(state: EliminationState, config: GameConfig): List<Dart>? = null

        companion object {
            /** Markierung "ohne Gegner-Sicht gerendert" (kein gueltiger Lebensstand). */
            const val NO_OPPONENTS: Int = -1
        }
    }

    private suspend fun threePlayers(): Triple<Long, Long, Long> = Triple(
        db.playerDao().insert(Player(name = "Tom", createdAt = 1L)),
        db.playerDao().insert(Player(name = "Anna", createdAt = 1L)),
        db.playerDao().insert(Player(name = "Ben", createdAt = 1L)),
    )

    private fun viewModel(playerIds: List<Long>, lives: Int = 2): GameViewModel<EliminationState> {
        val mode = EliminationFakeMode(lives = lives)
        return GameViewModel(
            matchRepository = matchRepository,
            playerRepository = playerRepository,
            playerIds = playerIds,
            config = GameConfig(legsToWin = 1, setsToWin = 1),
            mode = mode,
            uiAdapter = EliminationUiAdapter(mode),
        )
    }

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    /** Lebensstand (als X01-Board abgebildet) der Karte von [playerId]. */
    private fun GameUiState.Playing.livesOf(playerId: Long): Int =
        (players.first { it.playerId == playerId }.board as PlayerBoardUi.X01).remaining

    // --- Tests ----------------------------------------------------------------

    @Test
    fun buildPlayers_zuLegBeginn_zeigtJedeKarteDieVollenAbgeleitetenLeben() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna, ben) = threePlayers()
            val vm = viewModel(listOf(tom, anna, ben))

            val start = vm.awaitPlaying()

            // Gaebe das ViewModel keine Gegner mit, stuende hier ueberall -1.
            assertEquals(2, start.livesOf(tom))
            assertEquals(2, start.livesOf(anna))
            assertEquals(2, start.livesOf(ben))
        }

    @Test
    fun buildPlayers_reichtDieGegnerZustaendeDurch_trefferSenktDieKarteDesGetroffenen() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna, ben) = threePlayers()
            val vm = viewModel(listOf(tom, anna, ben))
            vm.awaitPlaying()

            // Tom (Zielzahl 1) trifft die Zielzahl von Anna (Sitzplatz 1 -> 2).
            vm.onNumber(2)

            val after = vm.uiState.value as GameUiState.Playing
            // Annas Leben stehen NICHT in ihrem eigenen Zustand - sie ergeben sich
            // aus Toms Treffern. Nur mit durchgereichten Gegnern sichtbar.
            assertEquals(1, after.livesOf(anna))
            assertEquals(2, after.livesOf(tom))
            assertEquals(2, after.livesOf(ben))
        }

    @Test
    fun buildPlayers_zweiterTrefferSenktWeiter_undUndoStelltDieAnzeigeWiederHer() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna, ben) = threePlayers()
            val vm = viewModel(listOf(tom, anna, ben))
            vm.awaitPlaying()

            vm.onNumber(2)
            vm.onNumber(2)
            assertEquals(0, (vm.uiState.value as GameUiState.Playing).livesOf(anna))

            vm.onUndo()

            val afterUndo = vm.uiState.value as GameUiState.Playing
            assertEquals(1, afterUndo.livesOf(anna))
            assertEquals(2, afterUndo.livesOf(ben))
        }
}
