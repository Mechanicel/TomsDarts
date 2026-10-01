package com.mechanicel.tomsdarts.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.analytics.Transition
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.dao.PlayerDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.data.repository.StatsRepository
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests fuer [PlayerStatsViewModel]: In-Memory-Room ueber die echten
 * [PlayerRepository]/[StatsRepository]. Prueft Zustaende (Loading, Content,
 * Empty, PlayerNotFound, Error inkl. Retry), Filter-Liste, Filterwechsel und
 * das Neuladen ohne Loading-Flackern nach dem `WhileSubscribed`-Timeout.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerStatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private var tom = 0L
    private var anna = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        tom = db.playerDao().insert(Player(name = "Tom", createdAt = 1L))
        anna = db.playerDao().insert(Player(name = "Anna", createdAt = 1L))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun viewModel(playerId: Long = tom) = PlayerStatsViewModel(
        playerId = playerId,
        playerRepository = PlayerRepository(db.playerDao()),
        statsRepository = StatsRepository(db.statsDao()),
        computeDispatcher = mainDispatcherRule.testDispatcher,
    )

    /**
     * Legt ein Match mit einem Leg an, in dem Tom die Darts [tomDarts] und Anna
     * einen Single-1 wirft. [winner] gewinnt Leg und Match (null = offen).
     */
    private suspend fun seedMatch(
        modeType: String,
        startedAt: Long,
        tomDarts: List<Pair<Int, Int>>,
        winner: Long?,
    ) {
        val matchId = db.matchDao().insert(
            Match(
                modeType = modeType,
                startScore = if (modeType == GameModeCatalog.X01) 501 else 0,
                doubleOut = modeType == GameModeCatalog.X01,
                legsToWin = 1,
                setsToWin = 1,
                startedAt = startedAt,
                endedAt = winner?.let { startedAt + 1 },
                winnerId = winner,
            ),
        )
        db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = tom, position = 0))
        db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = anna, position = 1))
        val legId = db.legDao().insert(
            Leg(
                matchId = matchId,
                legNumber = 1,
                winnerId = winner,
                startedAt = startedAt,
                endedAt = winner?.let { startedAt + 1 },
            ),
        )
        insertTurn(legId, tom, 0, tomDarts)
        insertTurn(legId, anna, 1, listOf(1 to 1))
    }

    private suspend fun insertTurn(legId: Long, playerId: Long, turnIndex: Int, darts: List<Pair<Int, Int>>) {
        val turnId = db.turnDao().insert(
            Turn(
                legId = legId,
                playerId = playerId,
                turnIndex = turnIndex,
                bust = false,
                totalScored = darts.sumOf { it.first * it.second },
            ),
        )
        darts.forEachIndexed { i, (segment, multiplier) ->
            db.throwDao().insert(
                Throw(
                    turnId = turnId,
                    dartIndex = i + 1,
                    segment = segment,
                    multiplier = multiplier,
                    value = segment * multiplier,
                    timestamp = 1L,
                ),
            )
        }
    }

    private suspend fun PlayerStatsViewModel.awaitContent(
        predicate: (PlayerStatsUiState.Content) -> Boolean = { true },
    ): PlayerStatsUiState.Content =
        uiState.first { it is PlayerStatsUiState.Content && predicate(it) } as PlayerStatsUiState.Content

    @Test
    fun loadingThenContentWithAllSections() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        seedMatch(GameModeCatalog.X01, startedAt = 10L, tomDarts = listOf(20 to 3, 20 to 3, 20 to 3), winner = tom)
        seedMatch(GameModeCatalog.CRICKET, startedAt = 20L, tomDarts = listOf(20 to 3), winner = anna)
        val vm = viewModel()
        assertEquals(PlayerStatsUiState.Loading, vm.uiState.value)

        backgroundScope.launch { vm.uiState.collect {} }
        val content = vm.awaitContent()

        assertEquals("Tom", content.playerName)
        assertEquals(listOf(GameModeCatalog.X01, GameModeCatalog.CRICKET), content.modeFilters)
        assertTrue(content.showModeFilter)
        assertNull(content.selectedMode)
        assertEquals(5, content.sections.size)
        assertEquals(StatsSectionUi.Overview(matches = 2, wins = 1), content.sections[0])
        val x01 = content.sections[1] as StatsSectionUi.X01
        assertEquals(3, x01.metrics.dartsThrown)
        assertEquals(180.0, x01.metrics.threeDartAverage!!, 1e-9)
        val distribution = content.sections[2] as StatsSectionUi.Distribution
        assertEquals(4, distribution.distribution.totalDarts)
        // Wurfmuster direkt nach der Trefferverteilung, ueber alle Modi.
        val sequences = (content.sections[3] as StatsSectionUi.Sequences).sequences
        assertEquals(2, sequences.visitsCounted)
        assertEquals(listOf(HitField(20, 3)), sequences.favoriteFirst)
        assertEquals(listOf(60.0, 60.0, 60.0), sequences.positionAverages)
        // Jedes Muster nur einmal -> Rauschfilter (Anzahl >= 2) leert die Liste.
        assertTrue(sequences.patterns.isEmpty())
        // T-20 -> T-20 kommt zweimal vor (Dart 1->2, 2->3).
        assertEquals(listOf(Transition(HitField(20, 3), HitField(20, 3), 2)), sequences.transitions)
        // Match-Liste am Ende, neueste zuerst.
        val matches = (content.sections[4] as StatsSectionUi.Matches).matches
        assertEquals(listOf(GameModeCatalog.CRICKET, GameModeCatalog.X01), matches.map { it.modeType })
        assertEquals(listOf(PlayerMatchResult.LOST, PlayerMatchResult.WON), matches.map { it.result })
    }

    @Test
    fun playerWithoutLegsIsEmpty() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { it !is PlayerStatsUiState.Loading }

        assertEquals(PlayerStatsUiState.Empty("Tom"), state)
    }

    @Test
    fun unknownPlayerIsNotFound() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel(playerId = 9_999L)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { it !is PlayerStatsUiState.Loading }

        assertEquals(PlayerStatsUiState.PlayerNotFound, state)
    }

    @Test
    fun filterListContainsOnlyPlayedModesInCatalogOrder() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        seedMatch(GameModeCatalog.KILLER, startedAt = 10L, tomDarts = listOf(5 to 1), winner = null)
        seedMatch(GameModeCatalog.CRICKET, startedAt = 20L, tomDarts = listOf(20 to 1), winner = tom)
        seedMatch(GameModeCatalog.X01, startedAt = 30L, tomDarts = listOf(19 to 1), winner = null)
        seedMatch(GameModeCatalog.CRICKET, startedAt = 40L, tomDarts = listOf(18 to 1), winner = anna)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        assertEquals(
            listOf(GameModeCatalog.X01, GameModeCatalog.CRICKET, GameModeCatalog.KILLER),
            content.modeFilters,
        )
        // Uebersicht ueber alle Modi: 4 Matches (auch unbeendete), 1 Sieg.
        assertEquals(StatsSectionUi.Overview(matches = 4, wins = 1), content.sections.first())
    }

    @Test
    fun singlePlayedModeHidesFilterAndShowsX01EmptyForAll() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            seedMatch(GameModeCatalog.CRICKET, startedAt = 10L, tomDarts = listOf(20 to 3), winner = tom)
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            val content = vm.awaitContent()

            assertEquals(listOf(GameModeCatalog.CRICKET), content.modeFilters)
            assertFalse(content.showModeFilter)
            assertEquals(StatsSectionUi.X01Empty, content.sections[1])
            assertTrue(content.sections[2] is StatsSectionUi.Distribution)

            // Bei ausgeblendetem Filter wird eine Modus-Wahl als "Alle" behandelt.
            vm.selectMode(GameModeCatalog.CRICKET)
            val after = vm.uiState.value as PlayerStatsUiState.Content
            assertNull(after.selectedMode)
            assertEquals(StatsSectionUi.X01Empty, after.sections[1])
        }

    @Test
    fun x01EmptyForAllWithoutX01Legs() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        seedMatch(GameModeCatalog.CRICKET, startedAt = 10L, tomDarts = listOf(20 to 3), winner = tom)
        seedMatch(GameModeCatalog.SHANGHAI, startedAt = 20L, tomDarts = listOf(1 to 1), winner = anna)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        assertTrue(content.showModeFilter)
        assertEquals(
            listOf("overview", "x01", "distribution", "sequences", "matches"),
            content.sections.map { it.key },
        )
        assertEquals(StatsSectionUi.X01Empty, content.sections[1])
        // Ohne X01-Legs entfaellt der Positions-Block.
        assertNull((content.sections[3] as StatsSectionUi.Sequences).sequences.positionAverages)
    }

    @Test
    fun filterChangeRecomputesSectionsAndDropsX01ForOtherModes() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            seedMatch(GameModeCatalog.X01, startedAt = 10L, tomDarts = listOf(20 to 3, 20 to 1, 0 to 1), winner = tom)
            seedMatch(GameModeCatalog.CRICKET, startedAt = 20L, tomDarts = listOf(19 to 2), winner = anna)
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitContent()

            vm.selectMode(GameModeCatalog.CRICKET)
            val cricket = vm.awaitContent { it.selectedMode == GameModeCatalog.CRICKET }
            assertEquals(
                listOf("overview", "distribution", "sequences", "matches"),
                cricket.sections.map { it.key },
            )
            assertEquals(StatsSectionUi.Overview(matches = 1, wins = 0), cricket.sections[0])
            val cricketDist = (cricket.sections[1] as StatsSectionUi.Distribution).distribution
            assertEquals(1, cricketDist.totalDarts)
            assertEquals(1.0, cricketDist.doubleShare!!, 1e-9)
            val cricketSeq = (cricket.sections[2] as StatsSectionUi.Sequences).sequences
            assertEquals(1, cricketSeq.visitsCounted)
            assertEquals(listOf(HitField(19, 2)), cricketSeq.favoriteFirst)
            assertNull(cricketSeq.positionAverages)
            // Filter-Liste bleibt unveraendert.
            assertEquals(listOf(GameModeCatalog.X01, GameModeCatalog.CRICKET), cricket.modeFilters)

            vm.selectMode(GameModeCatalog.X01)
            val x01 = vm.awaitContent { it.selectedMode == GameModeCatalog.X01 }
            assertEquals(
                listOf("overview", "x01", "distribution", "sequences", "matches"),
                x01.sections.map { it.key },
            )
            assertEquals(StatsSectionUi.Overview(matches = 1, wins = 1), x01.sections[0])
            assertEquals(3, (x01.sections[1] as StatsSectionUi.X01).metrics.dartsThrown)
            assertEquals(3, (x01.sections[2] as StatsSectionUi.Distribution).distribution.totalDarts)
            val x01Seq = (x01.sections[3] as StatsSectionUi.Sequences).sequences
            assertEquals(listOf(60.0, 20.0, 0.0), x01Seq.positionAverages)
            assertEquals(1, x01Seq.visitsCounted)
            assertEquals(0, x01Seq.firstMissCount)

            vm.selectMode(null)
            val all = vm.awaitContent { it.selectedMode == null }
            assertEquals(StatsSectionUi.Overview(matches = 2, wins = 1), all.sections[0])
            assertEquals(4, (all.sections[2] as StatsSectionUi.Distribution).distribution.totalDarts)
        }

    @Test
    fun matchesSectionRespectsFilterOrderAndResults() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        seedMatch(GameModeCatalog.X01, startedAt = 10L, tomDarts = listOf(20 to 1), winner = tom)
        seedMatch(GameModeCatalog.CRICKET, startedAt = 20L, tomDarts = listOf(20 to 1), winner = anna)
        seedMatch(GameModeCatalog.X01, startedAt = 30L, tomDarts = listOf(19 to 1), winner = null)
        seedMatch(GameModeCatalog.X01, startedAt = 40L, tomDarts = listOf(18 to 1), winner = anna)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        val all = vm.awaitContent()
        val allMatches = (all.sections.last() as StatsSectionUi.Matches).matches
        assertEquals(listOf(40L, 30L, 20L, 10L), allMatches.map { it.startedAt })
        assertEquals(
            listOf(PlayerMatchResult.LOST, PlayerMatchResult.OPEN, PlayerMatchResult.LOST, PlayerMatchResult.WON),
            allMatches.map { it.result },
        )
        assertEquals(allMatches.map { it.matchId }.distinct().size, allMatches.size)

        vm.selectMode(GameModeCatalog.X01)
        val x01 = vm.awaitContent { it.selectedMode == GameModeCatalog.X01 }
        val x01Matches = (x01.sections.last() as StatsSectionUi.Matches).matches
        assertEquals(listOf(40L, 30L, 10L), x01Matches.map { it.startedAt })
        assertTrue(x01Matches.all { it.modeType == GameModeCatalog.X01 })

        vm.selectMode(GameModeCatalog.CRICKET)
        val cricket = vm.awaitContent { it.selectedMode == GameModeCatalog.CRICKET }
        val cricketMatches = (cricket.sections.last() as StatsSectionUi.Matches).matches
        assertEquals(listOf(PlayerMatchResult.LOST), cricketMatches.map { it.result })
    }

    @Test
    fun playerWithMatchButWithoutOwnVisitsSeesContent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        // Anna nimmt teil und gewinnt, wirft aber selbst nie (nur Tom hat Aufnahmen).
        val matchId = db.matchDao().insert(
            Match(
                modeType = GameModeCatalog.X01,
                startScore = 501,
                doubleOut = true,
                legsToWin = 1,
                setsToWin = 1,
                startedAt = 10L,
                endedAt = 11L,
                winnerId = anna,
            ),
        )
        db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = tom, position = 0))
        db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = anna, position = 1))
        val legId = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 10L))
        insertTurn(legId, tom, 0, listOf(20 to 1))
        val vm = viewModel(playerId = anna)
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.uiState.first { it !is PlayerStatsUiState.Loading } as PlayerStatsUiState.Content

        assertEquals("Anna", content.playerName)
        assertEquals(StatsSectionUi.Overview(matches = 1, wins = 1), content.sections.first())
        assertEquals(StatsSectionUi.X01Empty, content.sections[1])
        assertEquals(0, (content.sections[2] as StatsSectionUi.Distribution).distribution.totalDarts)
        assertEquals(
            listOf(PlayerMatchItemUi(matchId, GameModeCatalog.X01, 10L, PlayerMatchResult.WON)),
            (content.sections.last() as StatsSectionUi.Matches).matches,
        )
    }

    @Test
    fun closedDatabaseYieldsError() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        db.close()
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { it !is PlayerStatsUiState.Loading }

        assertTrue(state is PlayerStatsUiState.Error)
    }

    @Test
    fun retryAfterErrorShowsLoadingThenContent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        seedMatch(GameModeCatalog.X01, startedAt = 10L, tomDarts = listOf(20 to 1), winner = tom)
        val dao = FailingOncePlayerDao(db.playerDao())
        val vm = PlayerStatsViewModel(
            playerId = tom,
            playerRepository = PlayerRepository(dao),
            statsRepository = StatsRepository(db.statsDao()),
            computeDispatcher = mainDispatcherRule.testDispatcher,
        )
        // Unconfined: jede Zustandsaenderung wird sofort mitgeschrieben (keine Konflation);
        // await wartet auf genau vier Zustaende, unabhaengig vom Thread, auf dem Room fortsetzt.
        val recorded = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.take(4).toList()
        }
        assertEquals(PlayerStatsUiState.Error("DB kaputt"), vm.uiState.first { it is PlayerStatsUiState.Error })

        vm.retry()
        val states = recorded.await()

        assertEquals(
            listOf(
                PlayerStatsUiState.Loading::class,
                PlayerStatsUiState.Error::class,
                PlayerStatsUiState.Loading::class,
                PlayerStatsUiState.Content::class,
            ),
            states.map { it::class },
        )
        assertEquals("Tom", (states[3] as PlayerStatsUiState.Content).playerName)
    }

    @Test
    fun resubscribeAfterTimeoutReloadsWithoutLoading() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        seedMatch(GameModeCatalog.X01, startedAt = 10L, tomDarts = listOf(20 to 1), winner = tom)
        val vm = viewModel()
        val first = backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(1, (vm.awaitContent().sections[0] as StatsSectionUi.Overview).matches)
        first.cancel()
        // Laenger als das WhileSubscribed-Timeout (5 s) weg: Upstream wird gestoppt.
        advanceTimeBy(6_000)
        seedMatch(GameModeCatalog.X01, startedAt = 20L, tomDarts = listOf(19 to 1), winner = anna)

        val states = Collections.synchronizedList(mutableListOf<PlayerStatsUiState>())
        // Unconfined: jede Zustandsaenderung wird sofort mitgeschrieben (keine Konflation).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { states += it } }
        val reloaded = vm.awaitContent { (it.sections[0] as StatsSectionUi.Overview).matches == 2 }

        assertEquals(2, (reloaded.sections[0] as StatsSectionUi.Overview).matches)
        assertFalse(states.any { it is PlayerStatsUiState.Loading })
    }

    /** [PlayerDao]-Delegat, dessen erstes [getById] fehlschlaegt (Retry-Test). */
    private class FailingOncePlayerDao(private val delegate: PlayerDao) : PlayerDao by delegate {
        private var failed = false

        override suspend fun getById(id: Long): Player? {
            if (!failed) {
                failed = true
                throw IllegalStateException("DB kaputt")
            }
            return delegate.getById(id)
        }
    }
}
