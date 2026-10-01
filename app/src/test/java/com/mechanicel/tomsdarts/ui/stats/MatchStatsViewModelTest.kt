package com.mechanicel.tomsdarts.ui.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.dao.MatchDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
import com.mechanicel.tomsdarts.data.repository.MatchRepository
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
 * Tests fuer [MatchStatsViewModel]: In-Memory-Room ueber die echten
 * Repositories (Muster [PlayerStatsViewModelTest]). Prueft Zustaende (Loading,
 * Content, Empty, NotFound, Error inkl. Retry), Kennzahlen je Teilnehmer
 * (X01/Nicht-X01), Sieger-Markierung, geloeschte Spieler, Legs-Liste und den
 * Spieler-Chip-Wechsel fuer Trefferverteilung und Wurfmuster.
 *
 * Das ViewModel startet keine Coroutine im `init`; alle Collector laufen im
 * `backgroundScope` und enden mit dem Test (kein Nachlaufen gegen die DB).
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchStatsViewModelTest {

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

    private fun matchRepository(matchDao: MatchDao = db.matchDao()) = MatchRepository(
        matchDao = matchDao,
        legDao = db.legDao(),
        turnDao = db.turnDao(),
        throwDao = db.throwDao(),
        matchPlayerDao = db.matchPlayerDao(),
    )

    private fun viewModel(matchId: Long, matchDao: MatchDao = db.matchDao()) = MatchStatsViewModel(
        matchId = matchId,
        matchRepository = matchRepository(matchDao),
        playerRepository = PlayerRepository(db.playerDao()),
        statsRepository = StatsRepository(db.statsDao()),
        computeDispatcher = mainDispatcherRule.testDispatcher,
    )

    /** Legt ein Match mit den Teilnehmern [seats] (Sitzreihenfolge) an. */
    private suspend fun seedMatch(
        modeType: String = GameModeCatalog.X01,
        startScore: Int = 60,
        setsToWin: Int = 1,
        winner: Long? = null,
        ended: Boolean = winner != null,
        seats: List<Long> = listOf(tom, anna),
    ): Long {
        val matchId = db.matchDao().insert(
            Match(
                modeType = modeType,
                startScore = startScore,
                doubleOut = false,
                legsToWin = 1,
                setsToWin = setsToWin,
                startedAt = 1_000L,
                endedAt = if (ended) 2_000L else null,
                winnerId = winner,
            ),
        )
        seats.forEachIndexed { index, id ->
            db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = id, position = index))
        }
        return matchId
    }

    private suspend fun addLeg(
        matchId: Long,
        legNumber: Int,
        winner: Long? = null,
        ended: Boolean = winner != null,
        setNumber: Int? = 1,
    ): Long = db.legDao().insert(
        Leg(
            matchId = matchId,
            setNumber = setNumber,
            legNumber = legNumber,
            winnerId = winner,
            startedAt = 1_000L,
            endedAt = if (ended) 1_500L else null,
        ),
    )

    private suspend fun addTurn(legId: Long, playerId: Long, turnIndex: Int, darts: List<Pair<Int, Int>>) {
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

    /**
     * X01 (Start 60, ohne Double-Out): Tom wirft 20/20/1 (Rest 19), Anna 5/5/5,
     * Tom checkt mit 19 aus und gewinnt Leg und Match.
     */
    private suspend fun seedFinishedX01(): Long {
        val matchId = seedMatch(winner = tom)
        val legId = addLeg(matchId, legNumber = 1, winner = tom)
        addTurn(legId, tom, 0, listOf(20 to 1, 20 to 1, 1 to 1))
        addTurn(legId, anna, 1, listOf(5 to 1, 5 to 1, 5 to 1))
        addTurn(legId, tom, 2, listOf(19 to 1))
        return matchId
    }

    private suspend fun MatchStatsViewModel.awaitContent(
        predicate: (MatchStatsUiState.Content) -> Boolean = { true },
    ): MatchStatsUiState.Content =
        uiState.first { it is MatchStatsUiState.Content && predicate(it) } as MatchStatsUiState.Content

    @Test
    fun x01MatchWithTwoPlayersYieldsContent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val matchId = seedFinishedX01()
        val vm = viewModel(matchId)
        assertEquals(MatchStatsUiState.Loading, vm.uiState.value)
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        assertEquals(
            MatchHeaderUi(
                modeType = GameModeCatalog.X01,
                startedAt = 1_000L,
                result = MatchResultUi.Winner("Tom"),
                legsPlayed = 1,
            ),
            content.header,
        )
        assertTrue(content.isX01)
        assertTrue(content.showPlayerChips)
        assertEquals(listOf(tom, anna), content.players.map { it.key })
        assertEquals(listOf("Tom", "Anna"), content.players.map { it.name })

        val tomStats = content.players[0]
        assertTrue(tomStats.isWinner)
        assertEquals(4, tomStats.dartsThrown)
        assertEquals(1, tomStats.legsWon)
        assertEquals(1, tomStats.legsPlayed)
        val tomX01 = tomStats.x01!!
        assertEquals(4, tomX01.dartsThrown)
        assertEquals(60.0 / 4 * 3, tomX01.threeDartAverage!!, 1e-9)
        assertEquals(19, tomX01.highestCheckout)
        assertEquals(1, tomX01.legsWon)

        val annaStats = content.players[1]
        assertFalse(annaStats.isWinner)
        assertEquals(3, annaStats.dartsThrown)
        assertEquals(0, annaStats.legsWon)
        assertEquals(15.0, annaStats.x01!!.threeDartAverage!!, 1e-9)

        // Default-Auswahl der Chip-Reihe: erster Teilnehmer (Sitzreihenfolge).
        assertEquals(tom, content.selectedPlayerKey)
        assertEquals(4, content.distribution.totalDarts)
        assertEquals(2, content.sequences.visitsCounted)

        assertEquals(listOf(MatchLegUi(content.legs[0].legId, null, 1, MatchResultUi.Winner("Tom"))), content.legs)
    }

    @Test
    fun selectingPlayerChipUpdatesDistributionAndSequences() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel(seedFinishedX01())
            backgroundScope.launch { vm.uiState.collect {} }
            vm.awaitContent()

            vm.selectPlayer(anna)
            val annaContent = vm.awaitContent { it.selectedPlayerKey == anna }
            assertEquals(3, annaContent.distribution.totalDarts)
            assertEquals(3, annaContent.distribution.bySegment[5])
            assertEquals(1, annaContent.sequences.visitsCounted)
            assertEquals(listOf(HitField(5, 1)), annaContent.sequences.favoriteFirst)
            // Spieler-Abschnitte bleiben von der Auswahl unberuehrt.
            assertEquals(4, annaContent.players[0].dartsThrown)

            vm.selectPlayer(tom)
            val tomContent = vm.awaitContent { it.selectedPlayerKey == tom }
            assertEquals(4, tomContent.distribution.totalDarts)
            // Gleichstand 20/19 als erster Dart (je einmal), absteigend nach Feld-Ordnung.
            assertEquals(listOf(HitField(20, 1), HitField(19, 1)), tomContent.sequences.favoriteFirst)

            // Unbekannter Schluessel faellt auf den ersten Teilnehmer zurueck.
            vm.selectPlayer(9_999L)
            val fallback = vm.uiState.value as MatchStatsUiState.Content
            assertEquals(tom, fallback.selectedPlayerKey)
        }

    @Test
    fun nonX01MatchHasNoX01MetricsButLegsAndDarts() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val matchId = seedMatch(modeType = GameModeCatalog.CRICKET, startScore = 0, winner = anna)
        val legId = addLeg(matchId, legNumber = 1, winner = anna)
        addTurn(legId, tom, 0, listOf(20 to 3, 19 to 1))
        addTurn(legId, anna, 1, listOf(20 to 1, 20 to 1, 20 to 1))
        val vm = viewModel(matchId)
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        assertFalse(content.isX01)
        assertTrue(content.players.all { it.x01 == null })
        assertEquals(listOf(2, 3), content.players.map { it.dartsThrown })
        assertEquals(listOf(0, 1), content.players.map { it.legsWon })
        assertEquals(listOf(false, true), content.players.map { it.isWinner })
        assertEquals(MatchResultUi.Winner("Anna"), content.header.result)
        // Ohne X01-Legs entfaellt der Positions-Block im Wurfmuster.
        assertNull(content.sequences.positionAverages)
    }

    @Test
    fun unfinishedMatchIsOpenWithoutWinner() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val matchId = seedMatch(setsToWin = 2)
        val leg1 = addLeg(matchId, legNumber = 1, winner = tom)
        addTurn(leg1, tom, 0, listOf(20 to 3))
        val leg2 = addLeg(matchId, legNumber = 2)
        addTurn(leg2, anna, 0, listOf(1 to 1))
        val vm = viewModel(matchId)
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        assertEquals(MatchResultUi.Open, content.header.result)
        assertEquals(1, content.header.legsPlayed)
        assertTrue(content.players.none { it.isWinner })
        assertEquals(listOf(1, 0), content.players.map { it.legsWon })
        // Mit Sets gespielt: Legs tragen die Set-Nummer.
        assertEquals(
            listOf(
                MatchLegUi(leg1, 1, 1, MatchResultUi.Winner("Tom")),
                MatchLegUi(leg2, 1, 2, MatchResultUi.Open),
            ),
            content.legs,
        )
    }

    @Test
    fun playerWithoutVisitsShowsNoDarts() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val matchId = seedMatch()
        val legId = addLeg(matchId, legNumber = 1)
        addTurn(legId, tom, 0, listOf(20 to 1))
        val vm = viewModel(matchId)
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        assertEquals(1, content.players[0].dartsThrown)
        assertNull(content.players[1].dartsThrown)
        assertEquals(0, content.players[1].x01!!.dartsThrown)
        // Leg ohne Sieger (laufend), Match ohne Sets: Legs ohne Set-Nummer.
        assertEquals(listOf(MatchLegUi(legId, null, 1, MatchResultUi.Open)), content.legs)
    }

    @Test
    fun deletedPlayersAreMergedIntoOneSection() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val bert = db.playerDao().insert(Player(name = "Bert", createdAt = 1L))
        val carl = db.playerDao().insert(Player(name = "Carl", createdAt = 1L))
        val matchId = seedMatch(winner = carl, seats = listOf(bert, tom, carl))
        val legId = addLeg(matchId, legNumber = 1, winner = carl)
        addTurn(legId, bert, 0, listOf(1 to 1, 1 to 1))
        addTurn(legId, tom, 1, listOf(20 to 1))
        addTurn(legId, carl, 2, listOf(5 to 1, 5 to 1, 5 to 1))
        // Loeschen setzt playerId/winnerId per SET_NULL auf null.
        db.playerDao().delete(db.playerDao().getById(bert)!!)
        db.playerDao().delete(db.playerDao().getById(carl)!!)
        val vm = viewModel(matchId)
        backgroundScope.launch { vm.uiState.collect {} }

        val content = vm.awaitContent()

        // Ein Sammel-Abschnitt an der Position des ersten geloeschten Sitzes.
        assertEquals(listOf(DELETED_PARTICIPANT_KEY, tom), content.players.map { it.key })
        val deleted = content.players[0]
        assertNull(deleted.name)
        assertEquals(5, deleted.dartsThrown)
        // Abgeschlossenes Match/Leg ohne bekannten Sieger -> geloeschter Spieler.
        assertTrue(deleted.isWinner)
        assertEquals(1, deleted.legsWon)
        assertFalse(content.players[1].isWinner)
        assertEquals(MatchResultUi.Winner(null), content.header.result)
        assertEquals(MatchResultUi.Winner(null), content.legs.single().result)
        assertEquals(DELETED_PARTICIPANT_KEY, content.selectedPlayerKey)
        assertEquals(5, content.distribution.totalDarts)
    }

    @Test
    fun finishedMatchWithoutWinnerAndWithoutDeletedPlayerHasNoWinner() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val matchId = seedMatch(ended = true)
            val legId = addLeg(matchId, legNumber = 1, ended = true)
            addTurn(legId, tom, 0, listOf(20 to 1))
            val vm = viewModel(matchId)
            backgroundScope.launch { vm.uiState.collect {} }

            val content = vm.awaitContent()

            assertEquals(MatchResultUi.NoWinner, content.header.result)
            assertEquals(MatchResultUi.NoWinner, content.legs.single().result)
            assertTrue(content.players.none { it.isWinner })
        }

    @Test
    fun unknownMatchIsNotFound() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel(9_999L)
        backgroundScope.launch { vm.uiState.collect {} }

        assertEquals(MatchStatsUiState.NotFound, vm.uiState.first { it !is MatchStatsUiState.Loading })
    }

    @Test
    fun matchWithoutTurnsIsEmptyWithHeader() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val matchId = seedMatch()
        addLeg(matchId, legNumber = 1)
        val vm = viewModel(matchId)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { it !is MatchStatsUiState.Loading }

        assertEquals(
            MatchStatsUiState.Empty(
                MatchHeaderUi(GameModeCatalog.X01, 1_000L, MatchResultUi.Open, legsPlayed = 0),
            ),
            state,
        )
    }

    @Test
    fun closedDatabaseYieldsError() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel(1L)
        db.close()
        backgroundScope.launch { vm.uiState.collect {} }

        assertTrue(vm.uiState.first { it !is MatchStatsUiState.Loading } is MatchStatsUiState.Error)
    }

    @Test
    fun retryAfterErrorShowsLoadingThenContent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val matchId = seedFinishedX01()
        val vm = viewModel(matchId, matchDao = FailingOnceMatchDao(db.matchDao()))
        // Unconfined: jede Zustandsaenderung wird sofort mitgeschrieben (keine Konflation).
        val recorded = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.take(4).toList()
        }
        assertEquals(MatchStatsUiState.Error("DB kaputt"), vm.uiState.first { it is MatchStatsUiState.Error })

        vm.retry()
        val states = recorded.await()

        assertEquals(
            listOf(
                MatchStatsUiState.Loading::class,
                MatchStatsUiState.Error::class,
                MatchStatsUiState.Loading::class,
                MatchStatsUiState.Content::class,
            ),
            states.map { it::class },
        )
    }

    /** [MatchDao]-Delegat, dessen erstes [getById] fehlschlaegt (Retry-Test). */
    private class FailingOnceMatchDao(private val delegate: MatchDao) : MatchDao by delegate {
        private var failed = false

        override suspend fun getById(id: Long): Match? {
            if (!failed) {
                failed = true
                throw IllegalStateException("DB kaputt")
            }
            return delegate.getById(id)
        }
    }
}
