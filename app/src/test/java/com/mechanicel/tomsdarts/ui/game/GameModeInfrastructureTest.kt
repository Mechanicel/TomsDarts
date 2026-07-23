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
import com.mechanicel.tomsdarts.game.CricketMode
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
    fun provideFactory_leererModeKey_wirftIllegalArgument() {
        val app = ApplicationProvider.getApplicationContext<TomsDartsApp>()
        val factory = GameViewModel.provideFactory(
            modeKey = "",
            playerIds = listOf(1L, 2L),
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
        )
        val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, app) }

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(GameViewModel::class.java, extras)
        }
    }

    @Test
    fun provideFactory_modeKeyGrossKleinschreibungAbweichend_wirftIllegalArgument() {
        // Regression: die Aufloesung ist case-sensitiv. "x01" (klein) ist kein
        // gueltiger Schluessel, obwohl "X01" (der Katalog-Wert) es ist.
        val app = ApplicationProvider.getApplicationContext<TomsDartsApp>()
        val factory = GameViewModel.provideFactory(
            modeKey = "x01",
            playerIds = listOf(1L, 2L),
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
        )
        val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, app) }

        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.create(GameViewModel::class.java, extras)
        }
        assertTrue(error.message.orEmpty().contains("x01"))
    }

    @Test
    fun provideFactory_x01_wirftNicht_undLoestAufDenErwartetenModusTypAuf() {
        // Positiver Gegenpol zu den unbekannter-Schluessel-Faellen oben: der
        // when-Zweig fuer GameModeCatalog.X01 liefert eine echte GameViewModel-
        // Instanz statt zu werfen. Bewusst OHNE die uiState-Kette abzuwarten (das
        // wuerde den echten AppContainer/dessen Datenbank anfassen, siehe Kommentar
        // beim unbekannten-Schluessel-Test oben) - reiner Konstruktions-Smoke.
        val app = ApplicationProvider.getApplicationContext<TomsDartsApp>()
        val factory = GameViewModel.provideFactory(
            modeKey = "X01",
            playerIds = listOf(1L, 2L),
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
        )
        val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, app) }

        val vm = factory.create(GameViewModel::class.java, extras)
        assertEquals(GameViewModel::class.java, vm.javaClass)
    }

    @Test
    fun provideFactory_cricket_wirftNicht_undLoestAufDenErwartetenModusTypAuf() {
        // Positiver Gegenpol analog zum X01-Fall: der when-Zweig fuer
        // GameModeCatalog.CRICKET liefert eine echte GameViewModel-Instanz. Reiner
        // Konstruktions-Smoke ohne die uiState-Kette (kein echter AppContainer).
        val app = ApplicationProvider.getApplicationContext<TomsDartsApp>()
        val factory = GameViewModel.provideFactory(
            modeKey = "CRICKET",
            playerIds = listOf(1L, 2L),
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
        )
        val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, app) }

        val vm = factory.create(GameViewModel::class.java, extras)
        assertEquals(GameViewModel::class.java, vm.javaClass)
    }

    @Test
    fun cricketSmoke_boardStartetLeer_alleMarksNullUndKeinePunkte() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = db.playerDao().insert(Player(name = "Tom", createdAt = 1L))
            val anna = db.playerDao().insert(Player(name = "Anna", createdAt = 1L))
            val vm = GameViewModel(
                matchRepository = matchRepository,
                playerRepository = playerRepository,
                playerIds = listOf(tom, anna),
                config = GameConfig(legsToWin = 1, setsToWin = 1),
                mode = CricketMode(),
                uiAdapter = CricketUiAdapter(),
            )

            val start = vm.uiState.first { it is GameUiState.Playing } as GameUiState.Playing
            // Zu Leg-Beginn traegt jede Karte ein Cricket-Board mit 0 Punkten und
            // 7 Feldern (feste Reihenfolge 20..15, Bull) ohne Marks.
            start.players.forEach { player ->
                val board = player.board
                assertTrue("Board ist Cricket", board is PlayerBoardUi.Cricket)
                board as PlayerBoardUi.Cricket
                assertEquals(0, board.points)
                assertEquals(listOf(20, 19, 18, 17, 16, 15, 25), board.fields.map { it.target })
                assertTrue("alle Marks 0", board.fields.all { it.marks == 0 })
            }
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
