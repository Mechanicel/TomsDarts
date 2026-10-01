package com.mechanicel.tomsdarts.ui.stats

import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.analytics.AnalyticsDart
import com.mechanicel.tomsdarts.analytics.AnalyticsLeg
import com.mechanicel.tomsdarts.analytics.AnalyticsVisit
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.analytics.PositionStats
import com.mechanicel.tomsdarts.analytics.SequenceStats
import com.mechanicel.tomsdarts.analytics.Transition
import com.mechanicel.tomsdarts.analytics.VisitPattern
import com.mechanicel.tomsdarts.analytics.computeSequenceStats
import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JVM-Tests fuer die Aufbereitung des Abschnitts "Wurfmuster"
 * ([toSequenceSectionUi] und Hilfen aus `SequenceFormat.kt`): Favoriten ohne
 * Miss, Gleichstand inkl. "+N", Miss-Anteil, Positions-Block, Rauschfilter,
 * Top-5/alle, Feld-Labels und Spalten-Grenze des Positions-Rasters.
 */
class SequenceFormatTest {

    private fun f(segment: Int, multiplier: Int = 1) = HitField(segment, multiplier)

    private fun positions(vararg averages: Double?) = averages.mapIndexed { index, avg ->
        PositionStats(
            position = index + 1,
            darts = 10,
            byField = emptyMap(),
            x01Darts = if (avg == null) 0 else 10,
            x01Points = avg?.let { (it * 10).toInt() } ?: 0,
            averagePoints = avg,
        )
    }

    private fun stats(
        visitsCounted: Int = 10,
        firstDart: Map<HitField, Int> = mapOf(f(20, 3) to 10),
        byPosition: List<PositionStats> = positions(20.0, 18.0, 15.0),
        transitions: List<Transition> = emptyList(),
        topCombinations: List<VisitPattern> = emptyList(),
        completeVisits: Int = visitsCounted,
    ) = SequenceStats(
        visitsCounted = visitsCounted,
        firstDart = firstDart,
        mostFrequentFirstDart = null,
        mostFrequentFirstDartShare = null,
        byPosition = byPosition,
        transitions = transitions,
        transitionMatrix = emptyMap(),
        topOrderedVisits = emptyList(),
        topCombinations = topCombinations,
        completeVisits = completeVisits,
        incompleteVisits = visitsCounted - completeVisits,
    )

    // --- Erster Dart ---

    @Test
    fun favoriteIgnoresMissEvenIfMostFrequent() {
        val ui = stats(firstDart = mapOf(f(0) to 6, f(20) to 3, f(19) to 1)).toSequenceSectionUi()

        assertEquals(listOf(f(20)), ui.favoriteFirst)
        assertEquals(0.3, ui.favoriteFirstShare!!, 1e-9)
        assertEquals(6, ui.firstMissCount)
        assertEquals(0.6, ui.firstMissShare!!, 1e-9)
    }

    @Test
    fun favoriteTieIsSortedDescendingByFieldOrder() {
        val ui = stats(
            firstDart = mapOf(f(20) to 4, f(20, 3) to 4, f(25) to 4, f(25, 2) to 4, f(1) to 2),
            visitsCounted = 18,
        ).toSequenceSectionUi()

        assertEquals(listOf(f(25, 2), f(25), f(20, 3), f(20)), ui.favoriteFirst)
        assertEquals(listOf(f(25, 2), f(25)), ui.favoriteVisible)
        assertEquals(2, ui.favoriteHidden)
        assertEquals(4.0 / 18, ui.favoriteFirstShare!!, 1e-9)
    }

    @Test
    fun twoWayTieHasNoHiddenFields() {
        val ui = stats(firstDart = mapOf(f(20) to 5, f(20, 3) to 5)).toSequenceSectionUi()

        assertEquals(listOf(f(20, 3), f(20)), ui.favoriteVisible)
        assertEquals(0, ui.favoriteHidden)
    }

    @Test
    fun threeWayTieHidesOne() {
        val ui = stats(firstDart = mapOf(f(5) to 2, f(1) to 2, f(20) to 2)).toSequenceSectionUi()

        assertEquals(listOf(f(20), f(5)), ui.favoriteVisible)
        assertEquals(1, ui.favoriteHidden)
    }

    @Test
    fun onlyMissesYieldNoFavoriteAndFullMissShare() {
        val ui = stats(firstDart = mapOf(f(0) to 4), visitsCounted = 4).toSequenceSectionUi()

        assertTrue(ui.favoriteFirst.isEmpty())
        assertNull(ui.favoriteFirstShare)
        assertEquals(0, ui.favoriteHidden)
        assertEquals(4, ui.firstMissCount)
        assertEquals(1.0, ui.firstMissShare!!, 1e-9)
    }

    @Test
    fun noMissesYieldZeroMissShare() {
        val ui = stats(firstDart = mapOf(f(20) to 10)).toSequenceSectionUi()

        assertEquals(0, ui.firstMissCount)
        assertEquals(0.0, ui.firstMissShare!!, 1e-9)
    }

    @Test
    fun emptyStatsYieldNullShares() {
        val ui = stats(visitsCounted = 0, firstDart = emptyMap(), byPosition = positions(null, null, null))
            .toSequenceSectionUi()

        assertEquals(0, ui.visitsCounted)
        assertTrue(ui.favoriteFirst.isEmpty())
        assertNull(ui.favoriteFirstShare)
        assertNull(ui.firstMissShare)
        assertNull(ui.positionAverages)
    }

    @Test
    fun favoriteFirstFieldsIgnoresZeroCounts() {
        assertTrue(favoriteFirstFields(mapOf(f(20) to 0)).isEmpty())
        assertTrue(favoriteFirstFields(emptyMap()).isEmpty())
    }

    // --- Positionen ---

    @Test
    fun positionsKeptWhenAtLeastOneAverageExists() {
        val ui = stats(byPosition = positions(20.0, null, null)).toSequenceSectionUi()

        assertEquals(listOf(20.0, null, null), ui.positionAverages)
    }

    @Test
    fun positionsNullWithoutX01Darts() {
        val ui = stats(byPosition = positions(null, null, null)).toSequenceSectionUi()

        assertNull(ui.positionAverages)
    }

    @Test
    fun positionsNullForNonX01LegsEndToEnd() {
        val cricket = leg(GameModeCatalog.CRICKET, listOf(20 to 3, 20 to 1, 19 to 1), listOf(20 to 3, 20 to 1, 19 to 1))
        val ui = computeSequenceStats(listOf(cricket), playerId = 1L).toSequenceSectionUi()

        assertNull(ui.positionAverages)
        assertEquals(listOf(f(20, 3)), ui.favoriteFirst)
        assertEquals(1, ui.patterns.size)
        assertEquals(2, ui.patterns.single().count)
    }

    @Test
    fun positionsPresentForX01LegsEndToEnd() {
        val x01 = leg(GameModeCatalog.X01, listOf(20 to 3, 20 to 1, 1 to 1))
        val ui = computeSequenceStats(listOf(x01), playerId = 1L).toSequenceSectionUi()

        assertEquals(listOf(60.0, 20.0, 1.0), ui.positionAverages)
    }

    private fun leg(modeType: String, vararg visits: List<Pair<Int, Int>>) = AnalyticsLeg(
        legId = 1L,
        matchId = 1L,
        modeType = modeType,
        startScore = 501,
        doubleOut = true,
        matchStartedAt = 0L,
        setNumber = null,
        legNumber = 1,
        winnerId = null,
        finished = false,
        visits = visits.mapIndexed { index, darts ->
            AnalyticsVisit(
                turnId = index.toLong(),
                turnIndex = index,
                playerId = 1L,
                bust = false,
                totalScored = darts.sumOf { it.first * it.second },
                darts = darts.mapIndexed { i, (segment, multiplier) ->
                    AnalyticsDart(dartIndex = i + 1, segment = segment, multiplier = multiplier, value = segment * multiplier)
                },
            )
        },
    )

    // --- Rauschfilter / Listen ---

    @Test
    fun patternsAndTransitionsKeepOnlyCountAtLeastTwo() {
        val patterns = listOf(
            VisitPattern(listOf(f(1), f(5), f(20)), 3),
            VisitPattern(listOf(f(1), f(20), f(20)), 2),
            VisitPattern(listOf(f(5), f(5), f(5)), 1),
        )
        val transitions = listOf(
            Transition(f(20, 3), f(20), 2),
            Transition(f(20), f(1), 1),
        )
        val ui = stats(topCombinations = patterns, transitions = transitions).toSequenceSectionUi()

        assertEquals(patterns.take(2), ui.patterns)
        assertEquals(transitions.take(1), ui.transitions)
    }

    @Test
    fun onlySingleOccurrencesYieldEmptyLists() {
        val ui = stats(
            topCombinations = listOf(VisitPattern(listOf(f(1), f(5), f(20)), 1)),
            transitions = listOf(Transition(f(20), f(1), 1)),
        ).toSequenceSectionUi()

        assertTrue(ui.patterns.isEmpty())
        assertTrue(ui.transitions.isEmpty())
    }

    @Test
    fun visibleRowsShowFiveCollapsedAndAllExpanded() {
        val rows = (1..8).toList()

        assertEquals(listOf(1, 2, 3, 4, 5), visibleSequenceRows(rows, expanded = false))
        assertEquals(rows, visibleSequenceRows(rows, expanded = true))
        assertEquals(listOf(1, 2, 3), visibleSequenceRows(listOf(1, 2, 3), expanded = false))
        assertEquals(5, visibleSequenceRows((1..5).toList(), expanded = false).size)
    }

    // --- Labels ---

    @Test
    fun hitFieldShortLabels() {
        assertEquals("20", hitFieldShortLabel(f(20)))
        assertEquals("D-16", hitFieldShortLabel(f(16, 2)))
        assertEquals("T-20", hitFieldShortLabel(f(20, 3)))
        assertEquals("Bull", hitFieldShortLabel(f(25)))
        assertEquals("D-Bull", hitFieldShortLabel(f(25, 2)))
        assertEquals("Out", hitFieldShortLabel(f(0)))
    }

    @Test
    fun hitFieldSpokenLabels() {
        assertEquals("20", hitFieldSpokenLabel(f(20)))
        assertEquals("Double 16", hitFieldSpokenLabel(f(16, 2)))
        assertEquals("Triple 20", hitFieldSpokenLabel(f(20, 3)))
        assertEquals("Bull", hitFieldSpokenLabel(f(25)))
        assertEquals("Doppel-Bull", hitFieldSpokenLabel(f(25, 2)))
        assertEquals("Daneben", hitFieldSpokenLabel(f(0)))
    }

    @Test
    fun patternLabelShowsFieldsDescending() {
        val pattern = VisitPattern(listOf(f(0), f(20), f(20, 3)), 4)

        assertEquals("T-20 · 20 · Out", patternLabel(pattern))
        assertEquals(listOf(f(20, 3), f(20), f(0)), patternDisplayFields(pattern))
        // Daten bleiben kanonisch (aufsteigend).
        assertEquals(listOf(f(0), f(20), f(20, 3)), pattern.fields)
    }

    @Test
    fun transitionLabelUsesArrow() {
        assertEquals("T-20 → 20", transitionLabel(Transition(f(20, 3), f(20), 12)))
        assertEquals("Bull → D-Bull", transitionLabel(Transition(f(25), f(25, 2), 2)))
    }

    // --- Positions-Raster ---

    @Test
    fun positionGridColumnsBoundary() {
        assertEquals(1, positionGridColumns(0.dp, 1f))
        assertEquals(1, positionGridColumns(263.dp, 1f))
        assertEquals(3, positionGridColumns(264.dp, 1f))
        assertEquals(3, positionGridColumns(288.dp, 1f))
    }

    @Test
    fun positionGridColumnsRespectFontScale() {
        // 360 dp Screen (328 dp nutzbar) bei 200 % Schrift -> 1 Spalte.
        assertEquals(1, positionGridColumns(328.dp, 2f))
        assertEquals(3, positionGridColumns(528.dp, 2f))
        assertEquals(1, positionGridColumns(527.dp, 2f))
        // Nicht-positiver fontScale wird wie 1 behandelt.
        assertEquals(3, positionGridColumns(264.dp, 0f))
        assertEquals(1, positionGridColumns(263.dp, -1f))
    }
}
