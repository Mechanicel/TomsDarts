package com.mechanicel.tomsdarts.data.repository

import com.mechanicel.tomsdarts.analytics.AnalyticsLeg
import com.mechanicel.tomsdarts.analytics.AnalyticsMatchSummary
import com.mechanicel.tomsdarts.analytics.toAnalyticsLegs
import com.mechanicel.tomsdarts.analytics.toMatchSummary
import com.mechanicel.tomsdarts.data.dao.StatsDao

/**
 * Lesendes Repository fuer die Analytics-Schicht (Phase 5, ADR-0034). Rein lokal
 * (offline-first).
 *
 * Bewusst **duenn** (ADR-0011): reicht an [StatsDao] durch und mappt die flachen
 * Zeilen auf das pure Domaenenmodell (`com.mechanicel.tomsdarts.analytics`).
 * Keine Kennzahlen-Berechnung — die folgt als pure Funktionen auf
 * `List<AnalyticsLeg>`.
 */
class StatsRepository(private val statsDao: StatsDao) {

    /**
     * Legs mit den Aufnahmen des Spielers [playerId] (nur dessen eigene Visits),
     * optional auf den Spielmodus [modeType] (GameModeCatalog-Key) gefiltert.
     * Chronologisch sortiert; Legs ohne Aufnahme des Spielers fehlen.
     */
    suspend fun legsForPlayer(playerId: Long, modeType: String? = null): List<AnalyticsLeg> =
        statsDao.getThrowRowsForPlayer(playerId, modeType).toAnalyticsLegs()

    /** Legs des Matches [matchId] mit den Aufnahmen aller Spieler. */
    suspend fun legsForMatch(matchId: Long): List<AnalyticsLeg> =
        statsDao.getThrowRowsForMatch(matchId).toAnalyticsLegs()

    /** Matches des Spielers [playerId], neueste zuerst. */
    suspend fun matchesForPlayer(playerId: Long): List<AnalyticsMatchSummary> =
        statsDao.getMatchesForPlayer(playerId).map { it.toMatchSummary() }
}
