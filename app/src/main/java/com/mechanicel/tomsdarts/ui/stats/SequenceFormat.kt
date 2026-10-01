package com.mechanicel.tomsdarts.ui.stats

import androidx.compose.ui.unit.Dp
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.analytics.SequenceStats
import com.mechanicel.tomsdarts.analytics.Transition
import com.mechanicel.tomsdarts.analytics.VisitPattern
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.ui.input.dartShortLabel
import com.mechanicel.tomsdarts.ui.input.dartSpokenLabel

// Pure Aufbereitung des Abschnitts "Wurfmuster" (Sequenz-Auswertungen, ADR-0036/
// ADR-0037) — kein Compose-Laufzeitbezug, reines JUnit-testbar. Feld-Kurzlabels,
// Pfeil und Trenner sind (wie in ui/input/DartLabel.kt) nicht-lokalisierte Glyphen.

/** Mindestanzahl eines Musters/Uebergangs, um angezeigt zu werden (Rauschfilter). */
internal const val SEQUENCE_MIN_COUNT = 2

/** Anzahl der initial sichtbaren Zeilen je Muster-/Folgen-Liste. */
internal const val SEQUENCE_ROWS_COLLAPSED = 5

/** Maximal sichtbare Felder beim Gleichstand des haeufigsten ersten Treffers. */
internal const val FAVORITE_TIE_VISIBLE = 2

/** Ab dieser effektiven Breite (dp / fontScale) zeigt das Positions-Raster 3 Spalten. */
internal const val POSITION_GRID_THREE_COLUMNS_MIN_DP = 264f

/** Trenner zwischen den Feldern eines Aufnahme-Musters ("T-20 · 20 · 5"). */
internal const val SEQUENCE_FIELD_SEPARATOR = " · "

/** Pfeil zwischen Von- und Nach-Feld eines Uebergangs ("T-20 → 20"). */
internal const val SEQUENCE_TRANSITION_ARROW = " → "

/** Absteigende Feld-Ordnung (T-20 vor 20, D-Bull vor Bull, Out zuletzt). */
private val FIELD_ORDER_DESCENDING: Comparator<HitField> =
    compareByDescending<HitField> { it.segment }.thenByDescending { it.multiplier }

/**
 * Anzeigedaten des Abschnitts "Wurfmuster".
 *
 * @param visitsCounted Ausgewertete Aufnahmen; 0 = Leerfall (nur Hinweistext).
 * @param favoriteFirst Haeufigste erste Treffer ohne Miss, absteigend nach
 *   Feld-Ordnung (mehrere bei Gleichstand); leer, wenn der erste Dart nur daneben ging.
 * @param favoriteFirstShare Anteil eines Favoriten an [visitsCounted], `null` ohne Favorit.
 * @param firstMissCount Aufnahmen, deren erster Dart daneben ging.
 * @param firstMissShare Anteil von [firstMissCount] an [visitsCounted], `null` ohne Aufnahmen.
 * @param positionAverages Ø Punkte je Dart fuer Dart 1/2/3 (einzelne `null` ohne
 *   X01-Darts an der Position); insgesamt `null`, wenn keine Position X01-Darts hat —
 *   der Positions-Block entfaellt dann.
 * @param completeVisits Vollstaendige 3-Dart-Aufnahmen (Basis der Muster).
 * @param patterns Haeufigste Kombinationen (Reihenfolge egal), nur mit Anzahl
 *   >= [SEQUENCE_MIN_COUNT].
 * @param transitions Haeufigste Folgen innerhalb einer Aufnahme, nur mit Anzahl
 *   >= [SEQUENCE_MIN_COUNT].
 */
data class SequenceSectionUi(
    val visitsCounted: Int,
    val favoriteFirst: List<HitField>,
    val favoriteFirstShare: Double?,
    val firstMissCount: Int,
    val firstMissShare: Double?,
    val positionAverages: List<Double?>?,
    val completeVisits: Int,
    val patterns: List<VisitPattern>,
    val transitions: List<Transition>,
) {
    /** Sichtbare Favoriten (hoechstens [FAVORITE_TIE_VISIBLE]). */
    val favoriteVisible: List<HitField> get() = favoriteFirst.take(FAVORITE_TIE_VISIBLE)

    /** Anzahl nicht sichtbarer Favoriten bei Gleichstand ("+N"), sonst 0. */
    val favoriteHidden: Int get() = (favoriteFirst.size - FAVORITE_TIE_VISIBLE).coerceAtLeast(0)
}

/** Ob dieses Feld ein Fehlwurf (Out) ist. */
private fun HitField.isMiss(): Boolean = segment == MISS_SEGMENT

/**
 * Bereitet [SequenceStats] fuer den Abschnitt "Wurfmuster" auf: Favoriten des ersten
 * Darts ohne Miss, Miss-Anteil, Positions-Averages (oder `null`) und die per
 * [SEQUENCE_MIN_COUNT] gefilterten Muster/Folgen. Reihenfolge der Listen bleibt die
 * der Berechnung (Anzahl absteigend).
 */
fun SequenceStats.toSequenceSectionUi(): SequenceSectionUi {
    val favorites = favoriteFirstFields(firstDart)
    val favoriteCount = favorites.firstOrNull()?.let { firstDart[it] }
    val missCount = firstDart.filterKeys { it.isMiss() }.values.sum()
    val averages = byPosition.sortedBy { it.position }.map { it.averagePoints }
    return SequenceSectionUi(
        visitsCounted = visitsCounted,
        favoriteFirst = favorites,
        favoriteFirstShare = shareOf(favoriteCount, visitsCounted),
        firstMissCount = missCount,
        firstMissShare = shareOf(missCount, visitsCounted),
        positionAverages = averages.takeIf { list -> list.any { it != null } },
        completeVisits = completeVisits,
        patterns = topCombinations.filter { it.count >= SEQUENCE_MIN_COUNT },
        transitions = transitions.filter { it.count >= SEQUENCE_MIN_COUNT },
    )
}

/** Anteil [count] / [total]; `null` ohne [count] oder bei [total] <= 0. */
private fun shareOf(count: Int?, total: Int): Double? =
    if (count == null || total <= 0) null else count.toDouble() / total

/**
 * Felder mit der hoechsten Anzahl in [firstDart] ohne Miss, absteigend nach
 * Feld-Ordnung (T-20 vor 20, D-Bull vor Bull). Leer ohne getroffene Felder.
 */
fun favoriteFirstFields(firstDart: Map<HitField, Int>): List<HitField> {
    val hits = firstDart.filterKeys { !it.isMiss() }.filterValues { it > 0 }
    val max = hits.values.maxOrNull() ?: return emptyList()
    return hits.filterValues { it == max }.keys.sortedWith(FIELD_ORDER_DESCENDING)
}

/** Kurzlabel eines Felds wie im Eingabe-Ziffernblock ("T-20", "D-Bull", "Out"). */
fun hitFieldShortLabel(field: HitField): String = dartShortLabel(Dart(field.segment, field.multiplier))

/** Ausgeschriebenes TalkBack-Label eines Felds ("Triple 20", "Doppel-Bull", "Daneben"). */
fun hitFieldSpokenLabel(field: HitField): String = dartSpokenLabel(Dart(field.segment, field.multiplier))

/** Felder eines Musters in absteigender Feld-Ordnung (Anzeige; Daten bleiben kanonisch). */
fun patternDisplayFields(pattern: VisitPattern): List<HitField> = pattern.fields.asReversed()

/** Anzeige-Label eines Musters, z.B. "T-20 · 20 · 5". */
fun patternLabel(pattern: VisitPattern): String =
    patternDisplayFields(pattern).joinToString(SEQUENCE_FIELD_SEPARATOR, transform = ::hitFieldShortLabel)

/** Anzeige-Label eines Uebergangs, z.B. "T-20 → 20". */
fun transitionLabel(transition: Transition): String =
    hitFieldShortLabel(transition.from) + SEQUENCE_TRANSITION_ARROW + hitFieldShortLabel(transition.to)

/**
 * Sichtbare Zeilen einer Muster-/Folgen-Liste: alle bei [expanded], sonst die ersten
 * [SEQUENCE_ROWS_COLLAPSED].
 */
fun <T> visibleSequenceRows(rows: List<T>, expanded: Boolean): List<T> =
    if (expanded) rows else rows.take(SEQUENCE_ROWS_COLLAPSED)

/**
 * Spaltenzahl des Positions-Rasters (Dart 1/2/3): effektiv (`maxWidth / fontScale`)
 * >= 264 dp -> 3 Spalten nebeneinander, sonst 1 (keine 2+1-Aufteilung). Ein
 * nicht-positiver [fontScale] wird defensiv wie 1 behandelt.
 */
internal fun positionGridColumns(maxWidth: Dp, fontScale: Float): Int {
    val scale = if (fontScale > 0f) fontScale else 1f
    return if (maxWidth.value / scale >= POSITION_GRID_THREE_COLUMNS_MIN_DP) 3 else 1
}
