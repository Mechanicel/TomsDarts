package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.data.dao.StatsMatchRow
import com.mechanicel.tomsdarts.data.dao.StatsThrowRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM-Tests fuer das Mapping flacher [StatsThrowRow]s auf das
 * Analytics-Domaenenmodell (ADR-0034). Kein Robolectric noetig.
 */
class AnalyticsMappingTest {

    private fun row(
        matchId: Long = 1L,
        legId: Long = 10L,
        turnId: Long = 100L,
        turnIndex: Int = 0,
        playerId: Long? = 7L,
        bust: Boolean = false,
        totalScored: Int = 0,
        dartIndex: Int? = 1,
        segment: Int? = 20,
        multiplier: Int? = 1,
        value: Int? = 20,
        modeType: String = "501",
        setNumber: Int? = null,
        legNumber: Int = 1,
        legWinnerId: Long? = null,
        legEndedAt: Long? = null,
    ) = StatsThrowRow(
        matchId = matchId,
        modeType = modeType,
        startScore = 501,
        doubleOut = true,
        matchStartedAt = 1000L,
        matchEndedAt = null,
        legId = legId,
        setNumber = setNumber,
        legNumber = legNumber,
        legWinnerId = legWinnerId,
        legEndedAt = legEndedAt,
        turnId = turnId,
        turnIndex = turnIndex,
        playerId = playerId,
        bust = bust,
        totalScored = totalScored,
        dartIndex = dartIndex,
        segment = segment,
        multiplier = multiplier,
        value = value,
        timestamp = if (dartIndex == null) null else 5L,
    )

    @Test
    fun emptyRowsGiveEmptyLegs() {
        assertTrue(emptyList<StatsThrowRow>().toAnalyticsLegs().isEmpty())
    }

    @Test
    fun groupsRowsIntoLegVisitsAndDartsWithAllFields() {
        val rows = listOf(
            row(turnId = 100, turnIndex = 0, totalScored = 100, dartIndex = 1, segment = 20, multiplier = 3, value = 60),
            row(turnId = 100, turnIndex = 0, totalScored = 100, dartIndex = 2, segment = 20, multiplier = 1, value = 20),
            row(turnId = 100, turnIndex = 0, totalScored = 100, dartIndex = 3, segment = 20, multiplier = 1, value = 20),
            row(turnId = 101, turnIndex = 1, playerId = 8L, bust = true, totalScored = 0, dartIndex = 1, segment = 25, multiplier = 2, value = 50),
        ).map { it.copy(setNumber = 2, legNumber = 3, legWinnerId = 7L, legEndedAt = 99L) }

        val legs = rows.toAnalyticsLegs()

        assertEquals(1, legs.size)
        val leg = legs[0]
        assertEquals(
            AnalyticsLeg(
                legId = 10L,
                matchId = 1L,
                modeType = "501",
                startScore = 501,
                doubleOut = true,
                matchStartedAt = 1000L,
                setNumber = 2,
                legNumber = 3,
                winnerId = 7L,
                finished = true,
                visits = listOf(
                    AnalyticsVisit(
                        turnId = 100L,
                        turnIndex = 0,
                        playerId = 7L,
                        bust = false,
                        totalScored = 100,
                        darts = listOf(
                            AnalyticsDart(1, 20, 3, 60),
                            AnalyticsDart(2, 20, 1, 20),
                            AnalyticsDart(3, 20, 1, 20),
                        ),
                    ),
                    AnalyticsVisit(
                        turnId = 101L,
                        turnIndex = 1,
                        playerId = 8L,
                        bust = true,
                        totalScored = 0,
                        darts = listOf(AnalyticsDart(1, 25, 2, 50)),
                    ),
                ),
            ),
            leg,
        )
    }

    @Test
    fun unfinishedLegHasFinishedFalse() {
        val leg = listOf(row(legEndedAt = null)).toAnalyticsLegs().single()
        assertFalse(leg.finished)
        assertNull(leg.winnerId)
    }

    @Test
    fun legsKeepOrderOfFirstAppearance() {
        val rows = listOf(
            row(legId = 30, turnId = 300),
            row(legId = 10, turnId = 100),
            row(legId = 30, turnId = 301, turnIndex = 1),
            row(legId = 20, turnId = 200),
        )

        assertEquals(listOf(30L, 10L, 20L), rows.toAnalyticsLegs().map { it.legId })
    }

    @Test
    fun visitsAndDartsSortedEvenForUnsortedRows() {
        val rows = listOf(
            row(turnId = 102, turnIndex = 2, dartIndex = 2, value = 2),
            row(turnId = 100, turnIndex = 0, dartIndex = 3, value = 3),
            row(turnId = 102, turnIndex = 2, dartIndex = 1, value = 1),
            row(turnId = 100, turnIndex = 0, dartIndex = 1, value = 1),
            row(turnId = 101, turnIndex = 1, dartIndex = 1, value = 1),
        )

        val visits = rows.toAnalyticsLegs().single().visits

        assertEquals(listOf(0, 1, 2), visits.map { it.turnIndex })
        assertEquals(listOf(1, 3), visits[0].darts.map { it.dartIndex })
        assertEquals(listOf(1, 2), visits[2].darts.map { it.dartIndex })
    }

    @Test
    fun equalTurnIndexTieBrokenByTurnId() {
        val rows = listOf(
            row(turnId = 205, turnIndex = 4),
            row(turnId = 201, turnIndex = 4),
        )

        assertEquals(listOf(201L, 205L), rows.toAnalyticsLegs().single().visits.map { it.turnId })
    }

    @Test
    fun turnWithoutThrowsBecomesVisitWithEmptyDarts() {
        val rows = listOf(row(dartIndex = null, segment = null, multiplier = null, value = null))

        val visit = rows.toAnalyticsLegs().single().visits.single()

        assertTrue(visit.darts.isEmpty())
        assertEquals(100L, visit.turnId)
    }

    @Test
    fun incompleteDartRowIsIgnored() {
        val rows = listOf(
            row(dartIndex = 1),
            row(dartIndex = 2, segment = null),
        )

        assertEquals(listOf(1), rows.toAnalyticsLegs().single().visits.single().darts.map { it.dartIndex })
    }

    @Test
    fun deletedPlayerVisitKeepsNullPlayerId() {
        val visit = listOf(row(playerId = null)).toAnalyticsLegs().single().visits.single()
        assertNull(visit.playerId)
    }

    @Test
    fun legsOfDifferentMatchesCarryOwnMatchData() {
        val rows = listOf(
            row(matchId = 1, legId = 10, modeType = "501"),
            row(matchId = 2, legId = 20, modeType = "cricket"),
        )

        val legs = rows.toAnalyticsLegs()

        assertEquals(listOf(1L, 2L), legs.map { it.matchId })
        assertEquals(listOf("501", "cricket"), legs.map { it.modeType })
    }

    @Test
    fun matchRowMapsToSummary() {
        val summary = StatsMatchRow(id = 3L, modeType = "301", startedAt = 5L, endedAt = null, winnerId = 9L)
            .toMatchSummary()

        assertEquals(AnalyticsMatchSummary(3L, "301", 5L, null, 9L), summary)
    }
}
