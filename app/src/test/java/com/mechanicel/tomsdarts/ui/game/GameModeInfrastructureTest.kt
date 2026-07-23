package com.mechanicel.tomsdarts.ui.game

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Basistests der Modus-Infrastruktur auf ViewModel-Ebene: die Modus-Aufloesung in
 * [GameViewModel.provideFactory] (unbekannter Schluessel -> Fehler) sowie ein
 * Ende-zu-Ende-Smoke, der belegt, dass sich X01 nach dem Board-Refactor exakt wie
 * zuvor verhaelt (Restpunkte im [PlayerBoardUi.X01] sinken pro Dart). Laeuft
 * host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameModeInfrastructureTest {

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

    @Test
    fun provideFactory_unbekannterModus_wirftIllegalArgumentMitSchluessel() {
        val app = ApplicationProvider.getApplicationContext<TomsDartsApp>()
        val factory = GameViewModel.provideFactory(
            modeKey = "GIBT_ES_NICHT",
            playerIds = listOf(1L, 2L),
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
        )
        // APPLICATION_KEY setzen; der lazy container wird im else-Zweig NICHT
        // beruehrt, daher entsteht keine echte DB.
        val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, app) }

        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(GameViewModel::class.java, extras)
        }
        assertTrue(
            "Fehlermeldung nennt den unbekannten Schluessel",
            error.message.orEmpty().contains("GIBT_ES_NICHT"),
        )
    }

    @Test
    fun x01Smoke_boardZeigtRestpunkte_undSinktProDart() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = db.playerDao().insert(Player(name = "Tom", createdAt = 1L))
            val anna = db.playerDao().insert(Player(name = "Anna", createdAt = 1L))
            val vm = GameViewModel(
                matchRepository = matchRepository,
                playerRepository = playerRepository,
                playerIds = listOf(tom, anna),
                config = GameConfig(startScore = 501, doubleOut = true, legsToWin = 1, setsToWin = 1),
                mode = X01Mode(),
                uiAdapter = X01UiAdapter(),
            )

            val start = vm.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
            // Zu Leg-Beginn traegt jede Karte ein X01-Board mit dem Startscore.
            assertTrue(start.players.all { it.board == PlayerBoardUi.X01(501) })

            // Ein Wurf T-20 senkt Toms Rest auf 441 - im Board sichtbar.
            vm.onToggleTriple(); vm.onNumber(20)
            val after = vm.uiState.value as GameUiState.Playing
            assertEquals(
                PlayerBoardUi.X01(441),
                after.players.first { it.playerId == tom }.board,
            )
        }
}
