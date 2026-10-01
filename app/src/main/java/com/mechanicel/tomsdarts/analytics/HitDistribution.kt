package com.mechanicel.tomsdarts.analytics

// Trefferverteilung (Phase 5, ADR-0035) als pure Funktion auf dem Analytics-
// Domaenenmodell (ADR-0034) — modusuebergreifend, physische Treffer.

/**
 * Ein Feld des Dartboards.
 *
 * @param segment 1..20, 25 = Bull, 0 = daneben (Miss).
 * @param multiplier 1 = Single, 2 = Double, 3 = Triple (Miss immer 1).
 */
data class HitField(val segment: Int, val multiplier: Int)

/**
 * Trefferverteilung eines Spielers ueber alle Modi.
 *
 * @param totalDarts Alle gezaehlten Darts inkl. Misses und Bust-Darts.
 * @param byField Anzahl je Feld (segment, multiplier), inkl. Miss (`HitField(0, 1)`)
 *   und Bull (`HitField(25, 1)` / `HitField(25, 2)`). Nur getroffene Felder enthalten.
 * @param bySegment Anzahl je Segment (0 = Miss, 25 = Bull), Multiplier aufsummiert.
 * @param byMultiplier Anzahl je Multiplier (1/2/3) — **ohne** Misses, die separat in
 *   [misses] stehen, damit der Single-Anteil nicht durch Fehlwuerfe verfaelscht wird.
 * @param misses Anzahl Misses (segment 0).
 */
data class HitDistribution(
    val totalDarts: Int,
    val byField: Map<HitField, Int>,
    val bySegment: Map<Int, Int>,
    val byMultiplier: Map<Int, Int>,
    val misses: Int,
) {
    /** Anteil der Singles an [totalDarts] (0..1), null ohne Darts. */
    val singleShare: Double? get() = share(byMultiplier[1] ?: 0)

    /** Anteil der Doubles (inkl. Doppel-Bull) an [totalDarts], null ohne Darts. */
    val doubleShare: Double? get() = share(byMultiplier[2] ?: 0)

    /** Anteil der Triples an [totalDarts], null ohne Darts. */
    val tripleShare: Double? get() = share(byMultiplier[3] ?: 0)

    /** Anteil der Misses an [totalDarts], null ohne Darts. Summe aller vier Anteile = 1. */
    val missShare: Double? get() = share(misses)

    private fun share(count: Int): Double? =
        if (totalDarts == 0) null else count.toDouble() / totalDarts
}

/**
 * Zaehlt alle Darts der Aufnahmen von [playerId] in [legs] — ueber **alle** Modi und
 * inklusive Bust-Aufnahmen (physische Treffer, nicht gewertete Punkte). Aufnahmen
 * anderer Spieler (Match-Ansicht) werden per `playerId` herausgefiltert.
 * Leere Eingabe liefert eine leere Verteilung mit `totalDarts == 0`.
 */
fun computeHitDistribution(legs: List<AnalyticsLeg>, playerId: Long): HitDistribution {
    val byField = sortedMapOf<HitField, Int>(compareBy({ it.segment }, { it.multiplier }))
    val bySegment = sortedMapOf<Int, Int>()
    val byMultiplier = sortedMapOf<Int, Int>()
    var total = 0
    var misses = 0

    for (leg in legs) {
        for (visit in leg.visits) {
            if (visit.playerId != playerId) continue
            for (dart in visit.darts) {
                total++
                byField.merge(HitField(dart.segment, dart.multiplier), 1, Int::plus)
                bySegment.merge(dart.segment, 1, Int::plus)
                if (dart.segment == MISS_SEGMENT) {
                    misses++
                } else {
                    byMultiplier.merge(dart.multiplier, 1, Int::plus)
                }
            }
        }
    }

    return HitDistribution(
        totalDarts = total,
        byField = byField,
        bySegment = bySegment,
        byMultiplier = byMultiplier,
        misses = misses,
    )
}

/** Segment-Kennung fuer einen Fehlwurf. */
private const val MISS_SEGMENT = 0
