package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.game.GameModeCatalog

// Sequenz-/Reihenfolge-Auswertungen (Phase 5, ADR-0036) als pure Funktionen auf dem
// Analytics-Domaenenmodell (ADR-0034) — erster Dart, Dart-Positionen, Feld-Uebergaenge
// und haeufigste Aufnahmen. Konventionen (Filter per playerId, Bust-Darts, null statt
// Division durch 0) wie bei den Kennzahlen (ADR-0035).

/**
 * Sequenz-Auswertung eines Spielers ueber eine Menge von Legs (alle Modi).
 *
 * @param visitsCounted Ausgewertete eigene Aufnahmen (mit mindestens einem Dart).
 * @param firstDart Anzahl je Feld fuer den ersten Dart jeder Aufnahme; nur getroffene
 *   Felder enthalten, sortiert nach Feld-Ordnung (Segment, dann Multiplier).
 * @param mostFrequentFirstDart Meistgetroffenes Feld beim ersten Dart; bei Gleichstand
 *   das in Feld-Ordnung kleinere Feld. null ohne Aufnahmen.
 * @param mostFrequentFirstDartShare Anteil von [mostFrequentFirstDart] an allen ersten
 *   Darts (0..1), null ohne Aufnahmen.
 * @param byPosition Je Dart-Position 1, 2, 3 genau ein Eintrag (aufsteigend), auch bei
 *   0 Darts.
 * @param transitions Top-N-Uebergaenge Feld -> naechstes Feld innerhalb derselben
 *   Aufnahme, sortiert nach Anzahl absteigend, dann Feld-Ordnung (von, dann nach).
 * @param transitionMatrix Alle Uebergaenge als Lookup `von -> (nach -> Anzahl)`,
 *   beide Ebenen nach Feld-Ordnung sortiert.
 * @param topOrderedVisits Top-N geordnete Sequenzen vollstaendiger 3-Dart-Aufnahmen
 *   (Felder in Wurf-Reihenfolge).
 * @param topCombinations Top-N ungeordnete Kombinationen vollstaendiger 3-Dart-
 *   Aufnahmen (Multiset, Felder kanonisch nach Feld-Ordnung sortiert).
 * @param completeVisits Aufnahmen mit genau 3 Darts (Basis von [topOrderedVisits] und
 *   [topCombinations]).
 * @param incompleteVisits Aufnahmen mit weniger als 3 Darts (Checkout, Bust-Abbruch,
 *   Leg-Ende) — gehen in erster Dart, Positionen und Uebergaenge ein, nicht aber in die
 *   3-Dart-Muster.
 */
data class SequenceStats(
    val visitsCounted: Int,
    val firstDart: Map<HitField, Int>,
    val mostFrequentFirstDart: HitField?,
    val mostFrequentFirstDartShare: Double?,
    val byPosition: List<PositionStats>,
    val transitions: List<Transition>,
    val transitionMatrix: Map<HitField, Map<HitField, Int>>,
    val topOrderedVisits: List<VisitPattern>,
    val topCombinations: List<VisitPattern>,
    val completeVisits: Int,
    val incompleteVisits: Int,
)

/**
 * Auswertung einer Dart-Position (1..3) innerhalb der Aufnahme.
 *
 * @param position Dart-Position 1, 2 oder 3.
 * @param darts Anzahl Darts an dieser Position ueber alle Modi.
 * @param byField Anzahl je Feld an dieser Position ueber alle Modi (wie
 *   [HitDistribution.byField]: nur getroffene Felder, nach Feld-Ordnung sortiert).
 * @param x01Darts Davon Darts in X01-Legs (Basis von [averagePoints]).
 * @param x01Points Gewertete Punkte dieser Darts in X01-Legs; Bust-Darts zaehlen 0.
 * @param averagePoints Durchschnittliche Punkte je Dart an dieser Position
 *   (`x01Points / x01Darts`), null ohne X01-Darts (z.B. reine Nicht-X01-Eingabe).
 */
data class PositionStats(
    val position: Int,
    val darts: Int,
    val byField: Map<HitField, Int>,
    val x01Darts: Int,
    val x01Points: Int,
    val averagePoints: Double?,
)

/**
 * Ein Uebergang: Auf einen Dart in [from] folgte in derselben Aufnahme direkt ein
 * Dart in [to] — [count]-mal.
 */
data class Transition(val from: HitField, val to: HitField, val count: Int)

/**
 * Ein Aufnahme-Muster aus drei Feldern mit seiner Haeufigkeit.
 *
 * @param fields Bei geordneten Sequenzen in Wurf-Reihenfolge, bei Kombinationen
 *   kanonisch nach Feld-Ordnung sortiert.
 * @param count Anzahl Aufnahmen mit diesem Muster.
 */
data class VisitPattern(val fields: List<HitField>, val count: Int)

/**
 * Berechnet die [SequenceStats] von [playerId] ueber [legs].
 *
 * - **Alle Modi** werden ausgewertet (physische Treffer); nur die Positions-Punkte
 *   ([PositionStats.x01Points]/[PositionStats.averagePoints]) beschraenken sich auf Legs mit
 *   `modeType == GameModeCatalog.X01`. Ein Modus-Filter ist Sache des Aufrufers (Legs
 *   vorher filtern).
 * - Nur Aufnahmen mit `playerId == playerId` zaehlen (Match-Ansicht mit allen Spielern
 *   funktioniert); Aufnahmen ohne Darts werden uebersprungen.
 * - **Position** = 1-basierter Platz des Darts in der nach `dartIndex` sortierten
 *   Aufnahme (bei konsistenten Daten identisch mit `dartIndex`). Darts jenseits der
 *   dritten Position werden fuer die Positions-Auswertung ignoriert.
 * - **Dart-Punkte (X01):** In einer Nicht-Bust-Aufnahme zaehlt der Wurfwert `value`; in
 *   einer Bust-Aufnahme zaehlt jeder Dart **0** — der Dart-`value` wird ignoriert,
 *   konsistent mit der Aufnahmen-Wertung (Bust-Aufnahme = 0 Punkte, ADR-0035). Bust-Darts
 *   zaehlen trotzdem als geworfen.
 * - Uebergaenge nur innerhalb einer Aufnahme (Dart n -> Dart n+1), nie ueber
 *   Aufnahmen hinweg.
 * - Geordnete Sequenzen und Kombinationen nur aus Aufnahmen mit genau 3 Darts; kuerzere
 *   werden in [SequenceStats.incompleteVisits] gezaehlt.
 *
 * Feld-Ordnung = `HitField` nach Segment (0 = Miss, 1..20, 25 = Bull), dann Multiplier.
 * Leere Eingabe liefert leere Maps/Listen, Zaehler 0 und `null` statt Division durch 0.
 *
 * @param topN Maximale Laenge von [SequenceStats.transitions],
 *   [SequenceStats.topOrderedVisits] und [SequenceStats.topCombinations]; muss >= 0 sein.
 * @throws IllegalArgumentException bei negativem [topN].
 */
fun computeSequenceStats(
    legs: List<AnalyticsLeg>,
    playerId: Long,
    topN: Int = DEFAULT_TOP_N,
): SequenceStats {
    require(topN >= 0) { "topN muss >= 0 sein, war $topN" }

    var visitsCounted = 0
    var completeVisits = 0
    var incompleteVisits = 0
    val firstDart = sortedMapOf<HitField, Int>(HIT_FIELD_ORDER)
    val positions = List(VISIT_DARTS) { PositionAccumulator() }
    val transitionMatrix = sortedMapOf<HitField, MutableMap<HitField, Int>>(HIT_FIELD_ORDER)
    val orderedCounts = mutableMapOf<List<HitField>, Int>()
    val combinationCounts = mutableMapOf<List<HitField>, Int>()

    for (leg in legs) {
        val isX01 = leg.modeType == GameModeCatalog.X01
        for (visit in leg.visits) {
            if (visit.playerId != playerId || visit.darts.isEmpty()) continue
            visitsCounted++
            val darts = visit.darts.sortedBy { it.dartIndex }
            val fields = darts.map { HitField(it.segment, it.multiplier) }

            firstDart.merge(fields.first(), 1, Int::plus)

            darts.take(VISIT_DARTS).forEachIndexed { index, dart ->
                val acc = positions[index]
                acc.darts++
                acc.byField.merge(fields[index], 1, Int::plus)
                if (isX01) {
                    acc.x01Darts++
                    acc.x01Points += if (visit.bust) 0 else dart.value
                }
            }

            fields.zipWithNext { from, to ->
                transitionMatrix.getOrPut(from) { sortedMapOf(HIT_FIELD_ORDER) }
                    .merge(to, 1, Int::plus)
            }

            if (fields.size == VISIT_DARTS) {
                completeVisits++
                orderedCounts.merge(fields, 1, Int::plus)
                combinationCounts.merge(fields.sortedWith(HIT_FIELD_ORDER), 1, Int::plus)
            } else {
                incompleteVisits++
            }
        }
    }

    val firstDartTotal = firstDart.values.sum()
    // Iteration in Feld-Ordnung + strikt-groesser => bei Gleichstand gewinnt das kleinere Feld.
    var topFirst: Map.Entry<HitField, Int>? = null
    for (entry in firstDart.entries) {
        if (topFirst == null || entry.value > topFirst.value) topFirst = entry
    }

    val transitions = transitionMatrix.flatMap { (from, targets) ->
        targets.map { (to, count) -> Transition(from, to, count) }
    }.sortedWith(
        compareByDescending<Transition> { it.count }
            .thenComparing({ it.from }, HIT_FIELD_ORDER)
            .thenComparing({ it.to }, HIT_FIELD_ORDER),
    ).take(topN)

    return SequenceStats(
        visitsCounted = visitsCounted,
        firstDart = firstDart,
        mostFrequentFirstDart = topFirst?.key,
        mostFrequentFirstDartShare = topFirst?.let { it.value.toDouble() / firstDartTotal },
        byPosition = positions.mapIndexed { index, acc -> acc.toStats(position = index + 1) },
        transitions = transitions,
        transitionMatrix = transitionMatrix,
        topOrderedVisits = topPatterns(orderedCounts, topN),
        topCombinations = topPatterns(combinationCounts, topN),
        completeVisits = completeVisits,
        incompleteVisits = incompleteVisits,
    )
}

/** Standard-Laenge der Top-N-Listen. */
const val DEFAULT_TOP_N = 10

/** Darts je vollstaendiger Aufnahme (= Anzahl ausgewerteter Positionen). */
private const val VISIT_DARTS = 3

/** Feld-Ordnung: Segment (0 = Miss, 1..20, 25 = Bull), dann Multiplier. */
private val HIT_FIELD_ORDER: Comparator<HitField> =
    compareBy({ it.segment }, { it.multiplier })

/** Lexikografischer Vergleich gleich langer Feld-Listen nach [HIT_FIELD_ORDER]. */
private val FIELD_LIST_ORDER: Comparator<List<HitField>> = Comparator { a, b ->
    for (i in 0 until minOf(a.size, b.size)) {
        val c = HIT_FIELD_ORDER.compare(a[i], b[i])
        if (c != 0) return@Comparator c
    }
    a.size.compareTo(b.size)
}

/** Top-N-Muster: Anzahl absteigend, bei Gleichstand lexikografisch nach Feld-Ordnung. */
private fun topPatterns(counts: Map<List<HitField>, Int>, topN: Int): List<VisitPattern> =
    counts.entries
        .sortedWith(
            compareByDescending<Map.Entry<List<HitField>, Int>> { it.value }
                .thenComparing({ it.key }, FIELD_LIST_ORDER),
        )
        .take(topN)
        .map { VisitPattern(it.key, it.value) }

/** Veraenderlicher Zwischenstand je Dart-Position. */
private class PositionAccumulator {
    var darts = 0
    val byField = sortedMapOf<HitField, Int>(HIT_FIELD_ORDER)
    var x01Darts = 0
    var x01Points = 0

    fun toStats(position: Int) = PositionStats(
        position = position,
        darts = darts,
        byField = byField,
        x01Darts = x01Darts,
        x01Points = x01Points,
        averagePoints = if (x01Darts == 0) null else x01Points.toDouble() / x01Darts,
    )
}
