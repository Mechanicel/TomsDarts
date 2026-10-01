package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.game.GameModeCatalog

// Gemeinsame Test-Builder fuer die Kennzahlen-Tests (pur JVM, ADR-0035).

/** Single auf [n]. */
internal fun s(n: Int) = n to 1

/** Double auf [n]. */
internal fun d(n: Int) = n to 2

/** Triple auf [n]. */
internal fun t(n: Int) = n to 3

/** Fehlwurf. */
internal val MISS = 0 to 1

/** Bull (25). */
internal val BULL = 25 to 1

/** Doppel-Bull (50). */
internal val DBULL = 25 to 2

private var nextTurnId = 1000L

/**
 * Baut eine Aufnahme aus (segment, multiplier)-Paaren. `totalScored` ergibt sich wie
 * in der Engine: Summe der Wurfwerte, bei Bust 0 (ueberschreibbar fuer Defensiv-Tests).
 */
internal fun visit(
    playerId: Long?,
    turnIndex: Int,
    vararg darts: Pair<Int, Int>,
    bust: Boolean = false,
    totalScored: Int? = null,
): AnalyticsVisit = AnalyticsVisit(
    turnId = nextTurnId++,
    turnIndex = turnIndex,
    playerId = playerId,
    bust = bust,
    totalScored = totalScored ?: if (bust) 0 else darts.sumOf { it.first * it.second },
    darts = darts.mapIndexed { i, (segment, multiplier) ->
        AnalyticsDart(
            dartIndex = i + 1,
            segment = segment,
            multiplier = multiplier,
            value = segment * multiplier,
        )
    },
)

/** Baut ein Leg; Default: abgeschlossenes 501-X01-Leg mit Double-Out. */
internal fun leg(
    visits: List<AnalyticsVisit>,
    startScore: Int = 501,
    doubleOut: Boolean = true,
    winnerId: Long? = null,
    finished: Boolean = true,
    modeType: String = GameModeCatalog.X01,
    legId: Long = 1L,
): AnalyticsLeg = AnalyticsLeg(
    legId = legId,
    matchId = 1L,
    modeType = modeType,
    startScore = startScore,
    doubleOut = doubleOut,
    matchStartedAt = 0L,
    setNumber = null,
    legNumber = legId.toInt(),
    winnerId = winnerId,
    finished = finished,
    visits = visits,
)
