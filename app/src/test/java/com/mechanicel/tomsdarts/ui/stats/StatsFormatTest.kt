package com.mechanicel.tomsdarts.ui.stats

import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.game.GameModeCatalog
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reine JVM-Tests fuer [StatsFormat]-Hilfen: Zahlenformat (deutsche Locale auch
 * bei fremder Default-Locale), `null`-Faelle, Spaltenzahl-Grenzen inkl.
 * fontScale, Felder-Liste und Filter-Reihenfolge.
 */
class StatsFormatTest {

    private lateinit var previousLocale: Locale

    /** Geschuetztes Leerzeichen der deutschen Prozent-Formatierung. */
    private val nbsp = ' '

    @Before
    fun setUp() {
        // Fremde Default-Locale: die Formatierung muss trotzdem deutsch bleiben.
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun formatAverageRoundsToOneDecimalWithComma() {
        assertEquals("58,3", formatAverage(58.25))
        assertEquals("0,0", formatAverage(0.0))
        assertEquals("180,0", formatAverage(180.0))
        assertNull(formatAverage(null))
    }

    @Test
    fun formatPercentUsesGermanLocaleAndAtMostOneDecimal() {
        assertEquals("33,3${nbsp}%", formatPercent(0.333))
        assertEquals("50${nbsp}%", formatPercent(0.5))
        assertEquals("0${nbsp}%", formatPercent(0.0))
        assertEquals("100${nbsp}%", formatPercent(1.0))
        assertNull(formatPercent(null))
    }

    @Test
    fun formatPercentNumberHasNoPercentSign() {
        assertEquals("33,3", formatPercentNumber(0.333))
        assertEquals("50", formatPercentNumber(0.5))
        assertEquals("12,5", formatPercentNumber(0.125))
        assertNull(formatPercentNumber(null))
    }

    @Test
    fun formatCountUsesThousandsSeparator() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1.234", formatCount(1234))
        assertEquals("1.000.000", formatCount(1_000_000))
    }

    @Test
    fun formatMatchDateUsesPatternAndZone() {
        val millis = ZonedDateTime.of(2026, 3, 7, 9, 5, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("07.03.2026, 09:05", formatMatchDate(millis, ZoneOffset.UTC))
        assertEquals("07.03.2026, 10:05", formatMatchDate(millis, ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun statGridColumnsBoundariesAtFontScaleOne() {
        assertEquals(1, statGridColumns(0.dp, 1f))
        assertEquals(1, statGridColumns(239.dp, 1f))
        assertEquals(2, statGridColumns(240.dp, 1f))
        assertEquals(2, statGridColumns(479.dp, 1f))
        assertEquals(3, statGridColumns(480.dp, 1f))
        assertEquals(3, statGridColumns(1000.dp, 1f))
    }

    @Test
    fun statGridColumnsDividesWidthByFontScale() {
        // 360 dp bei 200 % Schrift -> effektiv 180 -> einspaltig.
        assertEquals(1, statGridColumns(360.dp, 2f))
        // 568 dp (600 abzueglich Padding) bei 130 % -> effektiv ~437 -> 2 Spalten.
        assertEquals(2, statGridColumns(568.dp, 1.3f))
        // 960 dp bei 200 % -> effektiv 480 -> 3 Spalten (Grenze inklusive).
        assertEquals(3, statGridColumns(960.dp, 2f))
        // Kleine Schrift vergroessert die effektive Breite.
        assertEquals(3, statGridColumns(400.dp, 0.8f))
    }

    @Test
    fun statGridColumnsTreatsNonPositiveFontScaleAsOne() {
        assertEquals(2, statGridColumns(300.dp, 0f))
        assertEquals(2, statGridColumns(300.dp, -1f))
    }

    @Test
    fun hitSegmentRowsSortByCountThenSegmentDescending() {
        val fields = mapOf(
            HitField(0, 1) to 4,
            HitField(20, 1) to 3,
            HitField(20, 3) to 2,
            HitField(19, 1) to 5,
            HitField(5, 1) to 5,
            HitField(25, 1) to 1,
            HitField(25, 2) to 1,
        )
        val distribution = distributionOf(fields)

        val rows = hitSegmentRows(distribution)

        assertEquals(listOf(20, 19, 5, 0, 25), rows.map { it.segment })
        assertEquals(HitSegmentRow(segment = 20, count = 5, singles = 3, doubles = 0, triples = 2), rows[0])
        assertEquals(HitSegmentRow(segment = 25, count = 2, singles = 1, doubles = 1, triples = 0), rows[4])
        assertEquals(HitSegmentRow(segment = 0, count = 4, singles = 4, doubles = 0, triples = 0), rows[3])
    }

    @Test
    fun hitSegmentRowsEmptyWithoutDarts() {
        assertTrue(hitSegmentRows(distributionOf(emptyMap())).isEmpty())
    }

    @Test
    fun orderModeFiltersFollowsCatalogThenUnknownSorted() {
        val ordered = orderModeFilters(
            listOf("ZZZ", GameModeCatalog.KILLER, GameModeCatalog.X01, "AAA", GameModeCatalog.X01, GameModeCatalog.CRICKET),
        )
        assertEquals(
            listOf(GameModeCatalog.X01, GameModeCatalog.CRICKET, GameModeCatalog.KILLER, "AAA", "ZZZ"),
            ordered,
        )
        assertTrue(orderModeFilters(emptyList()).isEmpty())
    }

    private fun distributionOf(fields: Map<HitField, Int>): HitDistribution = HitDistribution(
        totalDarts = fields.values.sum(),
        byField = fields,
        bySegment = fields.entries.groupBy({ it.key.segment }, { it.value }).mapValues { it.value.sum() },
        byMultiplier = fields.filterKeys { it.segment != 0 }.entries
            .groupBy({ it.key.multiplier }, { it.value }).mapValues { it.value.sum() },
        misses = fields[HitField(0, 1)] ?: 0,
    )
}
