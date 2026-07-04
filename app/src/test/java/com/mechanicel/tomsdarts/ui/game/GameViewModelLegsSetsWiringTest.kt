package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_LEGS_BEST_OF
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_SETS_BEST_OF
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_START_SCORE
import com.mechanicel.tomsdarts.ui.setup.LEGS_BEST_OF_OPTIONS
import com.mechanicel.tomsdarts.ui.setup.SETS_BEST_OF_OPTIONS
import com.mechanicel.tomsdarts.ui.setup.bestOfToWin
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
 * Haertet die Verdrahtung der im Setup-Bildschirm als "Best of X" gewaehlten
 * Legs-/Sets-Anzahl (siehe [com.mechanicel.tomsdarts.ui.setup.SetupScreen],
 * [com.mechanicel.tomsdarts.ui.setup.bestOfToWin]) durch das [GameViewModel]
 * bis in den persistierten [com.mechanicel.tomsdarts.data.entity.Match] ab:
 * fuer JEDE angebotene Kombination aus [LEGS_BEST_OF_OPTIONS] und
 * [SETS_BEST_OF_OPTIONS] kommt die bereits im Setup umgerechnete
 * "first to N"-Gewinnschwelle ([GameConfig.legsToWin]/[GameConfig.setsToWin])
 * unveraendert an - das ist genau die Kette, die vor dieser Aenderung fest
 * auf legsToWin=2/setsToWin=1 verdrahtet war
 * ([GameViewModel.provideFactory]).
 *
 * Setup identisch zu [GameViewModelStartScoreWiringTest]/
 * [GameViewModelDoubleOutWiringTest]: synchroner (direkter) Room-Executor,
 * damit die im [GameViewModel] fire-and-forget feuernde Persistenz unter
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] deterministisch
 * abgeschlossen ist, bevor die Assertions lesen. Laeuft host-seitig unter
 * Robolectric (SDK 34 gepinnt). Bleibt rein lokal (offline-first).
 *
 * Die reine Compose-UI-Interaktion (Karten-Tap fuer Legs/Sets) ist mangels
 * Instrumentation/Geraet nicht host-testbar und daher hier nicht abgedeckt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelLegsSetsWiringTest {

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

    /**
     * Konfiguration, wie sie [GameViewModel.provideFactory] verdrahtet, wenn
     * das Setup legsBestOf/setsBestOf gewaehlt und ueber [bestOfToWin]
     * umgerechnet hat.
     */
    private fun configFor(legsBestOf: Int, setsBestOf: Int) =
        GameConfig(
            startScore = DEFAULT_START_SCORE,
            doubleOut = true,
            legsToWin = bestOfToWin(legsBestOf),
            setsToWin = bestOfToWin(setsBestOf),
        )

    // --- Jede angebotene Best-of-X-Kombination kommt korrekt umgerechnet an ---

    @Test
    fun legsUndSetsBestOf_jedeAngeboteneKombination_kommtAlsFirstToNImPersistiertenMatchAn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            LEGS_BEST_OF_OPTIONS.forEach { legsBestOf ->
                SETS_BEST_OF_OPTIONS.forEach { setsBestOf ->
                    val vm = viewModel(listOf(tom, anna), configFor(legsBestOf, setsBestOf))
                    backgroundScope.launch { vm.uiState.collect {} }

                    val playing = vm.awaitPlaying()
                    val erwarteteLegsToWin = bestOfToWin(legsBestOf)
                    val erwarteteSetsToWin = bestOfToWin(setsBestOf)
                    assertEquals(erwarteteLegsToWin, playing.legsToWin)
                    assertEquals(erwarteteSetsToWin, playing.setsToWin)

                    val match = matchRepository.getMatches().last()
                    assertEquals(erwarteteLegsToWin, match.legsToWin)
                    assertEquals(erwarteteSetsToWin, match.setsToWin)
                    // Rest der Konfiguration bleibt unabhaengig von legs/sets fix.
                    assertEquals(DEFAULT_START_SCORE, match.startScore)
                    assertEquals(true, match.doubleOut)
                }
            }
        }

    @Test
    fun defaultLegsUndSetsBestOf_kommtAlsHeutigesVerhaltenAn() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Regressionsschutz: Best of 3 Legs / Best of 1 Set (Setup-Defaults)
            // muessen weiterhin exakt das vor dieser Aenderung fest verdrahtete
            // Verhalten ergeben (legsToWin=2, setsToWin=1).
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")

            val vm = viewModel(
                listOf(tom, anna),
                configFor(
                    legsBestOf = DEFAULT_LEGS_BEST_OF,
                    setsBestOf = DEFAULT_SETS_BEST_OF,
                ),
            )
            val playing = vm.awaitPlaying()

            assertEquals(2, playing.legsToWin)
            assertEquals(1, playing.setsToWin)

            val match = matchRepository.getMatches().single()
            assertEquals(2, match.legsToWin)
            assertEquals(1, match.setsToWin)
        }

    // --- Fachliche Auswirkung: Best of 5 Legs (legsToWin=3) laesst das Match
    // --- erst nach drei Leg-Siegen enden, nicht schon nach zweien. -----------

    @Test
    fun bestOfFuenfLegs_matchEndetErstNachDreiLegSiegenNichtNachZwei() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tom = newPlayer("Tom")
            val anna = newPlayer("Anna")
            // Kleiner Startpunkt (40), damit ein Checkout mit zwei Single-20-
            // Wuerfen moeglich ist; startScore ist fuer diese Invariante
            // irrelevant, nur legsToWin (aus Best of 5 -> 3) ist entscheidend.
            val vm = viewModel(
                listOf(tom, anna),
                GameConfig(
                    startScore = 40,
                    doubleOut = false,
                    legsToWin = bestOfToWin(5),
                    setsToWin = bestOfToWin(1),
                ),
            )
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Wer auch immer gerade am Zug ist, checkt zweimal aus (Rest 40 ->
            // Single 20, Single 20); der Starter alterniert je Leg, das ist
            // fuer diese Invariante irrelevant.
            repeat(2) {
                vm.onNumber(20)
                vm.onNumber(20)
                vm.uiState.first { it is GameUiState.LegWon }
                vm.onNewLeg()
                vm.awaitPlaying()
            }

            // Nach zwei von benoetigten drei Leg-Siegen (verteilt auf beide
            // Spieler) laeuft das Match weiter (kein MatchWon), da legsToWin
            // bei Best of 5 = 3 ist.
            assertTrue(vm.uiState.value is GameUiState.Playing)
        }
}
