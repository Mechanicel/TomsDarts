package com.mechanicel.tomsdarts.analytics

// Pures Analytics-Domaenenmodell (Phase 5, ADR-0034) — kein Android, kein Room.
// Grundlage fuer Kennzahlen (Averages, Checkout-Quote, Trefferverteilung) und
// Sequenz-Auswertungen, die als pure Funktionen auf `List<AnalyticsLeg>` arbeiten.
// Checkout-/Restpunkte werden bewusst NICHT gespeichert; sie sind bei Bedarf per
// Replay aus `AnalyticsLeg.startScore` und den Aufnahmen ableitbar.

/**
 * Ein Leg mit seinen (ggf. auf einen Spieler gefilterten) Aufnahmen.
 *
 * @param legId Leg-ID.
 * @param matchId Match-ID.
 * @param modeType Spielmodus-Key aus dem `GameModeCatalog`; X01-Kennzahlen
 *   filtern hierueber.
 * @param startScore Startpunktzahl des Matches.
 * @param doubleOut Ob Double-Out gilt.
 * @param matchStartedAt Match-Start in Epoch-Millis (fuer Zeitreihen/Filter).
 * @param setNumber Set-Nummer, null ohne Sets.
 * @param legNumber Leg-Nummer.
 * @param winnerId Leg-Gewinner, null wenn offen oder Gewinner geloescht.
 * @param finished Ob das Leg abgeschlossen ist (`endedAt != null`).
 * @param visits Aufnahmen, aufsteigend nach `turnIndex`.
 */
data class AnalyticsLeg(
    val legId: Long,
    val matchId: Long,
    val modeType: String,
    val startScore: Int,
    val doubleOut: Boolean,
    val matchStartedAt: Long,
    val setNumber: Int?,
    val legNumber: Int,
    val winnerId: Long?,
    val finished: Boolean,
    val visits: List<AnalyticsVisit>,
)

/**
 * Eine Aufnahme (Visit) eines Spielers.
 *
 * @param turnId Aufnahme-ID.
 * @param turnIndex Fortlaufender Index innerhalb des Legs (ueber alle Spieler).
 * @param playerId Werfer, null wenn der Spieler geloescht wurde.
 * @param bust Ob ueberworfen; die real geworfenen Darts sind trotzdem enthalten.
 * @param totalScored Gewertete Punkte der Aufnahme (wie persistiert).
 * @param darts Wuerfe, aufsteigend nach `dartIndex`; leer, falls keine persistiert.
 */
data class AnalyticsVisit(
    val turnId: Long,
    val turnIndex: Int,
    val playerId: Long?,
    val bust: Boolean,
    val totalScored: Int,
    val darts: List<AnalyticsDart>,
)

/**
 * Ein einzelner Dart.
 *
 * @param dartIndex Position in der Aufnahme (1..3).
 * @param segment 1..20, 25 = Bull, 0 = daneben.
 * @param multiplier 1 = Single, 2 = Double, 3 = Triple.
 * @param value Wurfwert (segment * multiplier).
 */
data class AnalyticsDart(
    val dartIndex: Int,
    val segment: Int,
    val multiplier: Int,
    val value: Int,
)

/**
 * Kompakter Match-Eintrag fuer die Match-Liste eines Spielers.
 *
 * @param matchId Match-ID.
 * @param modeType Spielmodus-Key.
 * @param startedAt Start in Epoch-Millis.
 * @param endedAt Ende in Epoch-Millis, null wenn nicht beendet.
 * @param winnerId Match-Gewinner, null wenn offen oder Spieler geloescht.
 */
data class AnalyticsMatchSummary(
    val matchId: Long,
    val modeType: String,
    val startedAt: Long,
    val endedAt: Long?,
    val winnerId: Long?,
)
