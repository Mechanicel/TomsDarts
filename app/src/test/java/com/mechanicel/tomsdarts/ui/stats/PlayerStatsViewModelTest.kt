package com.mechanicel.tomsdarts.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
 * Empty, PlayerNotFound), Filter-Liste und Filterwechsel.
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
        assertEquals(3, content.sections.size)
        assertEquals(StatsSectionUi.Overview(matches = 2, wins = 1), content.sections[0])
        val x01 = content.sections[1] as StatsSectionUi.X01
        assertEquals(3, x01.metrics.dartsThrown)
        assertEquals(180.0, x01.metrics.threeDartAverage!!, 1e-9)
        val distribution = content.sections[2] as StatsSectionUi.Distribution
        assertEquals(4, distribution.distribution.totalDarts)
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
        assertEquals(listOf("overview", "x01", "distribution"), content.sections.map { it.key })
        assertEquals(StatsSectionUi.X01Empty, content.sections[1])
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
            assertEquals(listOf("overview", "distribution"), cricket.sections.map { it.key })
            assertEquals(StatsSectionUi.Overview(matches = 1, wins = 0), cricket.sections[0])
            val cricketDist = (cricket.sections[1] as StatsSectionUi.Distribution).distribution
            assertEquals(1, cricketDist.totalDarts)
            assertEquals(1.0, cricketDist.doubleShare!!, 1e-9)
            // Filter-Liste bleibt unveraendert.
            assertEquals(listOf(GameModeCatalog.X01, GameModeCatalog.CRICKET), cricket.modeFilters)

            vm.selectMode(GameModeCatalog.X01)
            val x01 = vm.awaitContent { it.selectedMode == GameModeCatalog.X01 }
            assertEquals(listOf("overview", "x01", "distribution"), x01.sections.map { it.key })
            assertEquals(StatsSectionUi.Overview(matches = 1, wins = 1), x01.sections[0])
            assertEquals(3, (x01.sections[1] as StatsSectionUi.X01).metrics.dartsThrown)
            assertEquals(3, (x01.sections[2] as StatsSectionUi.Distribution).distribution.totalDarts)

            vm.selectMode(null)
            val all = vm.awaitContent { it.selectedMode == null }
            assertEquals(StatsSectionUi.Overview(matches = 2, wins = 1), all.sections[0])
            assertEquals(4, (all.sections[2] as StatsSectionUi.Distribution).distribution.totalDarts)
        }
}
