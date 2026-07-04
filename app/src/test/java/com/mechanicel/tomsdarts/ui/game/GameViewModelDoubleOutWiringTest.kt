package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_START_SCORE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Haertet die Verdrahtung des im Setup-Bildschirm gewaehlten Double-Out
 * (siehe [com.mechanicel.tomsdarts.ui.setup.SetupScreen]) durch das
 * [GameViewModel] bis in den persistierten
 * [com.mechanicel.tomsdarts.data.entity.Match] ab: fuer BEIDE Werte (an/aus)
 * kommt [GameConfig.doubleOut] unveraendert in [Match.doubleOut] an, waehrend die
 * uebrige Regel-Konfiguration ([GameConfig.startScore]/[GameConfig.legsToWin]/
 * [GameConfig.setsToWin]) unabhaengig davon fix bleibt.
 *
 * Setup identisch zu [GameViewModelStartScoreWiringTest]: synchroner (direkter)
 * Room-Executor, damit die im [GameViewModel] fire-and-forget feuernde
 * Persistenz unter [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * deterministisch abgeschlossen ist, bevor die Assertions lesen. Laeuft
 * host-seitig unter Robolectric (SDK 34 gepinnt). Bleibt rein lokal
 * (offline-first).
 *
 * Die reine Compose-UI-Interaktion (Switch-Tap / Row-Toggle) ist mangels
 * Instrumentation/Geraet nicht host-testbar und daher hier nicht abgedeckt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelDoubleOutWiringTest {

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

    private fun viewModel(playerIds: List<Long>, config: GameConfig) =
        GameViewModel(matchRepository, playerRepository, playerIds, config)

    private suspend fun GameViewModel.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    /** Konfiguration, wie sie [GameViewModel.provideFactory] fest verdrahtet - bis auf doubleOut. */
    private fun configFor(doubleOut: Boolean) =
        GameConfig(
            startScore = DEFAULT_START_SCORE,
            doubleOut = doubleOut,
            legsToWin = 2,
            setsToWin = 1,
        )

    // --- Ausschliesslich doubleOut variiert, Rest-Konfiguration bleibt fix ----

    @Test
    fun doubleOut_variiertPersistiertenMatchNurUmDenWert_restKonfigurationBleibtUnveraendert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            listOf(true, false).forEach { doubleOut ->
                val vm = viewModel(listOf(tom, anna), configFor(doubleOut))
                backgroundScope.launch { vm.uiState.collect {} }

                val playing = vm.awaitPlaying()
                // Die uebrige Regel-Konfiguration bleibt fix, egal welcher
                // Double-Out-Wert gewaehlt wurde ...
                assertEquals(DEFAULT_START_SCORE, playing.startScore)
                assertEquals(2, playing.legsToWin)
                assertEquals(1, playing.setsToWin)

                // ... waehrend doubleOut selbst unveraendert bis in den
                // persistierten Match durchlaeuft.
                val match = matchRepository.getMatches().last()
                assertEquals(doubleOut, match.doubleOut)
                assertEquals(DEFAULT_START_SCORE, match.startScore)
                assertEquals(2, match.legsToWin)
                assertEquals(1, match.setsToWin)
            }
        }

    @Test
    fun doubleOut_an_kommtAlsTrueImPersistiertenMatchAn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            val vm = viewModel(listOf(tom, anna), configFor(doubleOut = true))
            vm.awaitPlaying()

            val match = matchRepository.getMatches().single()
            assertTrue(match.doubleOut)
        }

    @Test
    fun doubleOut_aus_kommtAlsFalseImPersistiertenMatchAn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            val vm = viewModel(listOf(tom, anna), configFor(doubleOut = false))
            vm.awaitPlaying()

            val match = matchRepository.getMatches().single()
            assertEquals(false, match.doubleOut)
        }
}
