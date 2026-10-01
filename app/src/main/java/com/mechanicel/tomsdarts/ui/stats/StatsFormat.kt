package com.mechanicel.tomsdarts.ui.stats

import androidx.compose.ui.unit.Dp
import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.game.GameModeCatalog
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pure Formatierungs- und Aufbereitungs-Hilfen der Statistik-Screens (Phase 5,
// ADR-0037) — kein Compose-Laufzeitbezug, reines JUnit-testbar. Fehlende Werte
// liefern `null`, der Aufrufer setzt dafuer `stats_value_none` ("–") ein.

/** Locale aller Statistik-Zahlen (Dezimalkomma, Tausenderpunkt). */
private val STATS_LOCALE: Locale = Locale.GERMANY

/** Ab dieser effektiven Breite (dp / fontScale) zeigt ein Kachelraster 2 Spalten. */
internal const val STAT_GRID_TWO_COLUMNS_MIN_DP = 240f

/** Ab dieser effektiven Breite (dp / fontScale) zeigt ein Kachelraster 3 Spalten. */
internal const val STAT_GRID_THREE_COLUMNS_MIN_DP = 480f

/** Anzahl der in der Felder-Liste initial sichtbaren Zeilen. */
internal const val HIT_ROWS_COLLAPSED = 10

/** Segment-Kennung des Bulls. */
internal const val BULL_SEGMENT = 25

/** Segment-Kennung eines Fehlwurfs (Out). */
internal const val MISS_SEGMENT = 0

private val matchDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", STATS_LOCALE)

/**
 * Formatiert einen Average mit einer Nachkommastelle (58.25 -> "58,3").
 * `null` (keine Darts) -> `null`.
 */
fun formatAverage(value: Double?): String? =
    value?.let { String.format(STATS_LOCALE, "%.1f", it) }

/**
 * Formatiert einen Anteil 0..1 als Prozentwert mit hoechstens einer
 * Nachkommastelle (0.333 -> "33,3 %", 0.5 -> "50 %"; das Leerzeichen ist das
 * geschuetzte Leerzeichen der deutschen Locale). `null` -> `null`.
 */
fun formatPercent(rate: Double?): String? =
    rate?.let {
        NumberFormat.getPercentInstance(STATS_LOCALE).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }.format(it)
    }

/**
 * Wie [formatPercent], aber ohne Prozentzeichen (0.333 -> "33,3") — fuer
 * TalkBack-Texte, die "Prozent" ausschreiben. `null` -> `null`.
 */
fun formatPercentNumber(rate: Double?): String? =
    rate?.let {
        NumberFormat.getNumberInstance(STATS_LOCALE).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }.format(it * 100)
    }

/** Formatiert eine Anzahl mit Tausenderpunkt (1234 -> "1.234"). */
fun formatCount(count: Int): String =
    NumberFormat.getIntegerInstance(STATS_LOCALE).format(count)

/**
 * Formatiert einen Match-Zeitpunkt (Epoch-Millis) als "dd.MM.yyyy, HH:mm" in
 * der Zeitzone [zone] (Default: Systemzeitzone).
 */
fun formatMatchDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    matchDateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/**
 * Spaltenzahl eines Kachelrasters fuer die verfuegbare Breite [maxWidth].
 *
 * Die Breite wird durch [fontScale] geteilt, damit grosse Schrift frueher in
 * weniger Spalten umbricht (ADR-0037): effektiv >= 480 dp -> 3, >= 240 dp -> 2,
 * sonst 1. Ein nicht-positiver [fontScale] wird defensiv wie 1 behandelt.
 */
internal fun statGridColumns(maxWidth: Dp, fontScale: Float): Int {
    val scale = if (fontScale > 0f) fontScale else 1f
    val effective = maxWidth.value / scale
    return when {
        effective >= STAT_GRID_THREE_COLUMNS_MIN_DP -> 3
        effective >= STAT_GRID_TWO_COLUMNS_MIN_DP -> 2
        else -> 1
    }
}

/**
 * Eine Zeile der Felder-Liste: alle Treffer eines Segments, aufgeteilt nach Ring.
 *
 * @param segment 1..20, [BULL_SEGMENT] oder [MISS_SEGMENT].
 * @param count Treffer im Segment (Summe ueber alle Multiplier).
 * @param singles Treffer als Single (bzw. einfaches Bull).
 * @param doubles Treffer als Double (bzw. Bullseye).
 * @param triples Treffer als Triple.
 */
data class HitSegmentRow(
    val segment: Int,
    val count: Int,
    val singles: Int,
    val doubles: Int,
    val triples: Int,
)

/**
 * Baut die Felder-Liste aus [distribution]: eine Zeile je getroffenem Segment
 * (inkl. Out), absteigend nach Treffern, bei Gleichstand absteigend nach
 * Segment. Segmente ohne Treffer fehlen.
 */
fun hitSegmentRows(distribution: HitDistribution): List<HitSegmentRow> =
    distribution.bySegment
        .filterValues { it > 0 }
        .map { (segment, count) ->
            HitSegmentRow(
                segment = segment,
                count = count,
                singles = distribution.byField[HitField(segment, 1)] ?: 0,
                doubles = distribution.byField[HitField(segment, 2)] ?: 0,
                triples = distribution.byField[HitField(segment, 3)] ?: 0,
            )
        }
        .sortedWith(compareByDescending<HitSegmentRow> { it.count }.thenByDescending { it.segment })

/**
 * Ordnet die gespielten Modus-Kennungen [modeTypes] in Katalog-Reihenfolge
 * ([GameModeCatalog.entries]); unbekannte Kennungen folgen am Ende in ihrer
 * natuerlichen (alphabetischen) Reihenfolge. Duplikate werden entfernt.
 */
fun orderModeFilters(modeTypes: Collection<String>): List<String> {
    val distinct = modeTypes.toSet()
    val known = GameModeCatalog.entries.map { it.key }.filter { it in distinct }
    val unknown = (distinct - known.toSet()).sorted()
    return known + unknown
}
