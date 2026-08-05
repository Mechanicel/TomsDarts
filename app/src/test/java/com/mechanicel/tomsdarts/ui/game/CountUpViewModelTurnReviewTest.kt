package com.mechanicel.tomsdarts.ui.game

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.CountUpMode
import com.mechanicel.tomsdarts.game.CountUpState
import com.mechanicel.tomsdarts.game.GameConfig
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
 * VM-Smoke fuer den ECHTEN [CountUpMode] (statt eines Test-Fakes): belegt die
 * Wechselwirkung mit der Kontroll-Pause ([GameUiState.Playing.turnReview]),
 * analog zu [ShanghaiViewModelTurnReviewTest].
 *
 * Ein regulaeres 3-Dart-Aufnahmeende loest wie bei jedem anderen Modus die Pause
 * aus - auch mitten im Leg (Runde 3). Ein `legEnded`-Aufnahmeende (Count Ups
 * rundenbasiertes Leg-Ende nach [CountUpState.ROUNDS] Runden) unterdrueckt sie
 * dagegen bewusst: der leg-beendende Dart fuehrt direkt zu [GameUiState.LegWon],
 * niemals ueber einen Playing-Zwischenzustand mit `turnReview` (siehe
 * [GameViewModel.onDart]: der `legWinnerId != null`-Zweig baut kein [TurnReviewUi]).
 *
 * Konstruiert das [GameViewModel] wie [GameViewModel.provideFactory] fuer
 * `COUNT_UP` verdrahten wuerde (echter [CountUpMode] + [CountUpUiAdapter]), aber
 * ueber den Konstruktor mit In-Memory-Room - identisch zum etablierten Muster der
 * uebrigen VM-Tests (z.B. [GameModeInfrastructureTest], [ShanghaiViewModelTurnReviewTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CountUpViewModelTurnReviewTest {

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

    private suspend fun twoPlayers(): Pair<Long, Long> =
        db.playerDao().insert(Player(name = "Tom", createdAt = 1L)) to
            db.playerDao().insert(Player(name = "Anna", createdAt = 1L))

    private fun viewModel(playerIds: List<Long>) = GameViewModel(
        matchRepository = matchRepository,
        playerRepository = playerRepository,
        playerIds = playerIds,
        // legsToWin = 2, damit der erste Leg-Gewinn NICHT zugleich das Match
        // entscheidet - sonst wuerde direkt GameUiState.MatchWon statt LegWon
        // erscheinen (siehe GameViewModel.onDart: matchWon hat Vorrang).
        config = GameConfig(legsToWin = 2, setsToWin = 1),
        mode = CountUpMode(),
        uiAdapter = CountUpUiAdapter(),
    )

    private suspend fun GameViewModel<*>.awaitPlaying(): GameUiState.Playing =
        uiState.first { it is GameUiState.Playing } as GameUiState.Playing

    private fun PlayerScoreUi.countUpBoard(): PlayerBoardUi.CountUp = board as PlayerBoardUi.CountUp

    /** Wirft mit dem TRIPLE-Modifier: der Modifier resettet sich nach jedem Dart. */
    private fun GameViewModel<*>.throwTriple(segment: Int) {
        onToggleTriple(); onNumber(segment)
    }

    // --- Regulaeres Aufnahme-Ende loest die Kontroll-Pause aus ------------------

    @Test
    fun regulaeresAufnahmeEnde_inRundeDrei_loestKontrollPauseAus_boardZeigtBereitsRundeVier() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Runden 1 und 2 zuegig durchspielen (Pause jeweils per "Weiter" beenden).
            for (round in 1..2) {
                vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
                assertNotNull((vm.uiState.value as GameUiState.Playing).turnReview)
                vm.onContinue()
                vm.onOut(); vm.onOut(); vm.onOut()
                assertNotNull((vm.uiState.value as GameUiState.Playing).turnReview)
                vm.onContinue()
            }

            // Runde 3: Toms dritter Dart beendet die Aufnahme regulaer - Anna
            // hat Runde 3 noch nicht gespielt, also KEIN Leg-Ende.
            vm.onNumber(20); vm.onNumber(20); vm.onNumber(20)
            val playing = vm.uiState.value as GameUiState.Playing
            val review = playing.turnReview
            assertNotNull("Regulaeres Aufnahme-Ende loest die Pause aus", review)
            assertEquals("Tom", review!!.throwerName)
            assertEquals(60, review.turnSum) // 3x Single 20

            // Board zeigt bereits Runde 4 (State-Vertrag: naechste Runde).
            val tomBoard = playing.players.first { it.name == "Tom" }.countUpBoard()
            assertEquals(4, tomBoard.round)
            assertEquals(180, tomBoard.points) // 3 volle Runden a 60
        }

    // --- legEnded unterdrueckt die Kontroll-Pause -------------------------------

    @Test
    fun legEnded_amEndeDerAchtenRunde_unterdruecktDieKontrollPause_undGehtDirektZuLegWon() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (tom, anna) = twoPlayers()
            val vm = viewModel(listOf(tom, anna))
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitPlaying()

            // Runden 1..7: Tom trifft Triple 20 (60/Aufnahme) und baut eine
            // klare Fuehrung auf, Anna verfehlt komplett. Nach jeder Aufnahme "Weiter".
            for (round in 1 until CountUpState.ROUNDS) {
                vm.throwTriple(20); vm.throwTriple(20); vm.throwTriple(20)
                vm.onContinue()
                vm.onOut(); vm.onOut(); vm.onOut()
                vm.onContinue()
            }

            // Runde 8: Toms Aufnahme endet weiterhin regulaer (Pause), erst
            // Annas 3. Dart entscheidet das Leg per Rangvergleich - OHNE Pause.
            vm.throwTriple(20); vm.throwTriple(20); vm.throwTriple(20)
            assertNotNull(
                "Toms reguleares Rundenende loest weiterhin die Pause aus",
                (vm.uiState.value as GameUiState.Playing).turnReview,
            )
            vm.onContinue()

            vm.onOut(); vm.onOut()
            vm.onOut() // Annas 3. Dart -> legEnded, sofort GameUiState.LegWon (keine Pause dazwischen)

            val legWon = vm.uiState.first { it is GameUiState.LegWon } as GameUiState.LegWon
            assertEquals("Gewinner ist Tom, nicht die Werferin Anna", "Tom", legWon.legWinnerName)
            assertEquals("Anna", legWon.nextStarterName)
            assertEquals(24, legWon.dartsUsed) // Toms Darts ueber die gesamte 8-Runden-Fuehrung
        }
}
