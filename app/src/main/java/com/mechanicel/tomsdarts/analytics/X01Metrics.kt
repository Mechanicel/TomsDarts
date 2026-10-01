package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.game.checkoutSuggestion

// X01-Kennzahlen (Phase 5, ADR-0035) als pure Funktionen auf dem Analytics-
// Domaenenmodell (ADR-0034) — kein Android, kein Room, reines JUnit-testbar.
// Definitionen (Bust-Darts, First-9, dartbasierte Checkout-Quote) siehe ADR-0035.

/**
 * X01-Kennzahlen eines Spielers ueber eine Menge von Legs.
 *
 * @param dartsThrown Alle geworfenen Darts in X01-Legs, inkl. Bust-Darts
 *   (zaehlen mit 0 Punkten) und der real geworfenen Darts der gewinnenden Aufnahme.
 * @param pointsScored Summe der gewerteten Punkte (Bust-Aufnahmen = 0).
 * @param threeDartAverage `pointsScored / dartsThrown * 3`, null ohne geworfene Darts.
 * @param firstNineAverage 3-Dart-Average ueber die ersten bis zu 3 eigenen Aufnahmen
 *   je Leg, null ohne geworfene Darts.
 * @param checkoutAttempts Darts, die bei einem mit genau einem Dart beendbaren Rest
 *   geworfen wurden.
 * @param checkoutHits Darts, die den Rest auf 0 gebracht und das Leg gewonnen haben.
 * @param checkoutRate `checkoutHits / checkoutAttempts`, null ohne Versuche.
 * @param highestCheckout Hoechster Rest vor einer gewinnenden Aufnahme, null ohne Checkout.
 * @param legsPlayed Abgeschlossene X01-Legs, in denen der Spieler geworfen hat.
 * @param legsWon Davon vom Spieler gewonnene Legs.
 */
data class X01Metrics(
    val dartsThrown: Int,
    val pointsScored: Int,
    val threeDartAverage: Double?,
    val firstNineAverage: Double?,
    val checkoutAttempts: Int,
    val checkoutHits: Int,
    val checkoutRate: Double?,
    val highestCheckout: Int?,
    val legsPlayed: Int,
    val legsWon: Int,
)

/**
 * Berechnet die [X01Metrics] von [playerId] ueber [legs].
 *
 * - Nur Legs mit `modeType == GameModeCatalog.X01` werden ausgewertet; Legs anderer
 *   Modi werden ignoriert.
 * - Nur Aufnahmen mit `playerId == playerId` zaehlen — damit funktioniert die
 *   Funktion sowohl mit `StatsRepository.legsForPlayer` (bereits gefiltert) als auch
 *   mit `legsForMatch` (alle Spieler, Match-Ansicht).
 * - Aufnahmen ohne Darts (keine persistierten Wuerfe) werden komplett uebersprungen:
 *   weder Darts noch Punkte, und sie belegen keinen First-9-Platz.
 * - Bust-Aufnahmen: alle real geworfenen Darts zaehlen, Punkte 0 (das persistierte
 *   `totalScored` einer Bust-Aufnahme ist bereits 0; zur Sicherheit wird es bei
 *   `bust == true` nicht gelesen).
 * - Unbeendete Legs zaehlen fuer Averages und Checkout-Versuche, nicht aber fuer
 *   [X01Metrics.legsPlayed] und Checkout-Erfolge.
 *
 * Leere Eingabe liefert Nullwerte bzw. `null` statt einer Division durch 0.
 */
fun computeX01Metrics(legs: List<AnalyticsLeg>, playerId: Long): X01Metrics {
    var darts = 0
    var points = 0
    var firstNineDarts = 0
    var firstNinePoints = 0
    var attempts = 0
    var hits = 0
    var highestCheckout: Int? = null
    var legsPlayed = 0
    var legsWon = 0

    for (leg in legs) {
        if (leg.modeType != GameModeCatalog.X01) continue
        val ownVisits = leg.visits
            .filter { it.playerId == playerId && it.darts.isNotEmpty() }
            .sortedWith(compareBy({ it.turnIndex }, { it.turnId }))
        if (ownVisits.isEmpty()) continue

        ownVisits.forEachIndexed { index, visit ->
            val scored = visit.scoredPoints()
            darts += visit.darts.size
            points += scored
            if (index < FIRST_NINE_VISITS) {
                firstNineDarts += visit.darts.size
                firstNinePoints += scored
            }
        }

        val replay = replayCheckouts(leg, ownVisits, playerId)
        attempts += replay.attempts
        hits += replay.hits
        replay.checkout?.let { highestCheckout = maxOf(highestCheckout ?: 0, it) }

        if (leg.finished) {
            legsPlayed++
            if (leg.winnerId == playerId) legsWon++
        }
    }

    return X01Metrics(
        dartsThrown = darts,
        pointsScored = points,
        threeDartAverage = threeDartAverage(points, darts),
        firstNineAverage = threeDartAverage(firstNinePoints, firstNineDarts),
        checkoutAttempts = attempts,
        checkoutHits = hits,
        checkoutRate = if (attempts == 0) null else hits.toDouble() / attempts,
        highestCheckout = highestCheckout,
        legsPlayed = legsPlayed,
        legsWon = legsWon,
    )
}

/**
 * True, wenn [remaining] mit genau einem Dart beendbar ist (= Checkout-Versuch).
 *
 * Mit Double-Out: genau dann, wenn die Checkout-Tabelle ([checkoutSuggestion]) eine
 * 1-Dart-Route liefert (gerade 2..40 oder 50). Ohne Double-Out: jeder Wert, den ein
 * einzelner gueltiger Treffer erzielen kann (1..20, Doppel, Triple, 25, 50).
 */
fun isOneDartCheckout(remaining: Int, doubleOut: Boolean): Boolean =
    if (doubleOut) {
        checkoutSuggestion(remaining, doubleOut = true)?.size == 1
    } else {
        remaining in SINGLE_DART_VALUES
    }

/** Anzahl Aufnahmen, die in den First-9-Average eingehen. */
private const val FIRST_NINE_VISITS = 3

/** Alle mit einem einzelnen gueltigen Treffer (ohne Miss) erzielbaren Punktwerte. */
private val SINGLE_DART_VALUES: Set<Int> = buildSet {
    for (segment in listOf(25) + (1..20)) {
        for (multiplier in 1..3) {
            val dart = Dart(segment, multiplier)
            if (dart.isValid) add(dart.value)
        }
    }
}

/** Gewertete Punkte einer Aufnahme: Bust = 0, sonst das persistierte `totalScored`. */
private fun AnalyticsVisit.scoredPoints(): Int = if (bust) 0 else totalScored

private fun threeDartAverage(points: Int, darts: Int): Double? =
    if (darts == 0) null else points.toDouble() / darts * 3

/** Ergebnis des Checkout-Replays eines Legs. */
private data class CheckoutReplay(val attempts: Int, val hits: Int, val checkout: Int?)

/**
 * Spielt die Restpunktzahl von [playerId] in [leg] aus `startScore` nach und zaehlt
 * Checkout-Versuche/-Erfolge dartgenau. Nach einer Bust-Aufnahme wird der Rest auf
 * den Stand vor der Aufnahme zurueckgesetzt. Ein Erfolg zaehlt nur, wenn der Dart
 * den Rest auf 0 bringt, die Aufnahme kein Bust ist, das Leg abgeschlossen ist und
 * `winnerId == playerId` (Kontrolle gegen inkonsistente Daten).
 */
private fun replayCheckouts(
    leg: AnalyticsLeg,
    ownVisits: List<AnalyticsVisit>,
    playerId: Long,
): CheckoutReplay {
    var remaining = leg.startScore
    var attempts = 0
    var hits = 0
    var checkout: Int? = null
    val wonByPlayer = leg.finished && leg.winnerId == playerId

    for (visit in ownVisits) {
        val before = remaining
        for (dart in visit.darts) {
            if (isOneDartCheckout(remaining, leg.doubleOut)) attempts++
            remaining -= dart.value
            if (remaining == 0 && !visit.bust && wonByPlayer) {
                hits++
                checkout = before
                break
            }
        }
        if (visit.bust) remaining = before
        if (checkout != null) break
    }
    return CheckoutReplay(attempts, hits, checkout)
}
