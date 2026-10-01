package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.data.dao.StatsMatchRow
import com.mechanicel.tomsdarts.data.dao.StatsThrowRow

/**
 * Gruppiert flache Join-Zeilen ([StatsThrowRow]) zu [AnalyticsLeg]s (pur, ohne
 * Android/Room).
 *
 * Reihenfolge-Vertrag (stabil):
 * - Legs in der Reihenfolge ihres **ersten Auftretens** in der Eingabe (die
 *   `StatsDao`-Queries liefern bereits chronologisch sortiert).
 * - Aufnahmen je Leg aufsteigend nach (turnIndex, turnId) — unabhaengig von der
 *   Zeilen-Reihenfolge innerhalb des Legs.
 * - Darts je Aufnahme aufsteigend nach dartIndex.
 *
 * Zeilen mit `dartIndex == null` (Aufnahme ohne persistierte Wuerfe, LEFT JOIN)
 * erzeugen eine Aufnahme mit leerer Dart-Liste. Unvollstaendige Dart-Zeilen
 * (einzelne Dart-Felder null) werden ignoriert.
 */
fun List<StatsThrowRow>.toAnalyticsLegs(): List<AnalyticsLeg> {
    val rowsByLeg = LinkedHashMap<Long, MutableList<StatsThrowRow>>()
    for (row in this) rowsByLeg.getOrPut(row.legId) { mutableListOf() }.add(row)

    return rowsByLeg.values.map { legRows ->
        val first = legRows.first()
        val visits = legRows
            .groupBy { it.turnId }
            .values
            .map { turnRows -> turnRows.toVisit() }
            .sortedWith(compareBy({ it.turnIndex }, { it.turnId }))
        AnalyticsLeg(
            legId = first.legId,
            matchId = first.matchId,
            modeType = first.modeType,
            startScore = first.startScore,
            doubleOut = first.doubleOut,
            matchStartedAt = first.matchStartedAt,
            setNumber = first.setNumber,
            legNumber = first.legNumber,
            winnerId = first.legWinnerId,
            finished = first.legEndedAt != null,
            visits = visits,
        )
    }
}

private fun List<StatsThrowRow>.toVisit(): AnalyticsVisit {
    val first = first()
    val darts = mapNotNull { row ->
        val dartIndex = row.dartIndex
        val segment = row.segment
        val multiplier = row.multiplier
        val value = row.value
        if (dartIndex == null || segment == null || multiplier == null || value == null) {
            null
        } else {
            AnalyticsDart(dartIndex, segment, multiplier, value)
        }
    }.sortedBy { it.dartIndex }
    return AnalyticsVisit(
        turnId = first.turnId,
        turnIndex = first.turnIndex,
        playerId = first.playerId,
        bust = first.bust,
        totalScored = first.totalScored,
        darts = darts,
    )
}

/** Mappt eine [StatsMatchRow] auf das Domaenenmodell [AnalyticsMatchSummary]. */
fun StatsMatchRow.toMatchSummary(): AnalyticsMatchSummary = AnalyticsMatchSummary(
    matchId = id,
    modeType = modeType,
    startedAt = startedAt,
    endedAt = endedAt,
    winnerId = winnerId,
)
