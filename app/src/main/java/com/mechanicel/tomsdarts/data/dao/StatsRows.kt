package com.mechanicel.tomsdarts.data.dao

/**
 * Flache Ergebniszeile der Analytics-Join-Query Turn -> Leg -> Match
 * (LEFT JOIN Throw), siehe [StatsDao]. Eine Zeile je Wurf; eine Aufnahme ohne
 * persistierte Wuerfe erscheint als genau eine Zeile mit `null`-Dart-Feldern.
 *
 * Bewusst KEINE Room-Entity und ohne Annotationen: Room bildet die Spalten per
 * Namen ab (Alias im SQL == Property-Name). Damit bleibt die Klasse pures Kotlin
 * und kann von der Android-freien Analytics-Schicht gemappt werden (ADR-0034).
 *
 * @param matchId Match-ID.
 * @param modeType Spielmodus-Key aus dem `GameModeCatalog` (z.B. "501", "cricket").
 * @param startScore Startpunktzahl des Matches.
 * @param doubleOut Ob Double-Out gilt.
 * @param matchStartedAt Match-Start in Epoch-Millis.
 * @param matchEndedAt Match-Ende in Epoch-Millis, null solange (oder falls nie) beendet.
 * @param legId Leg-ID.
 * @param setNumber Set-Nummer, null ohne Sets.
 * @param legNumber Leg-Nummer.
 * @param legWinnerId Leg-Gewinner, null wenn offen, ohne Werfer-Sieg oder Spieler geloescht.
 * @param legEndedAt Leg-Ende in Epoch-Millis, null solange laufend/abgebrochen.
 * @param turnId Aufnahme-ID.
 * @param turnIndex Fortlaufender Aufnahme-Index innerhalb des Legs.
 * @param playerId Werfender Spieler, null wenn der Spieler geloescht wurde (SET_NULL).
 * @param bust Ob die Aufnahme ueberworfen wurde.
 * @param totalScored In der Aufnahme gewertete Punkte.
 * @param dartIndex Position des Wurfs (1..3), null bei Aufnahme ohne Wuerfe.
 * @param segment Segment (0..20, 25), null bei Aufnahme ohne Wuerfe.
 * @param multiplier Faktor (1..3), null bei Aufnahme ohne Wuerfe.
 * @param value Wurfwert, null bei Aufnahme ohne Wuerfe.
 * @param timestamp Wurfzeitpunkt in Epoch-Millis, null bei Aufnahme ohne Wuerfe.
 */
data class StatsThrowRow(
    val matchId: Long,
    val modeType: String,
    val startScore: Int,
    val doubleOut: Boolean,
    val matchStartedAt: Long,
    val matchEndedAt: Long?,
    val legId: Long,
    val setNumber: Int?,
    val legNumber: Int,
    val legWinnerId: Long?,
    val legEndedAt: Long?,
    val turnId: Long,
    val turnIndex: Int,
    val playerId: Long?,
    val bust: Boolean,
    val totalScored: Int,
    val dartIndex: Int?,
    val segment: Int?,
    val multiplier: Int?,
    val value: Int?,
    val timestamp: Long?,
)

/**
 * Kompakte Match-Zeile fuer die Match-Liste eines Spielers (siehe
 * [StatsDao.getMatchesForPlayer]). Pures Kotlin, keine Room-Entity.
 *
 * @param id Match-ID.
 * @param modeType Spielmodus-Key.
 * @param startedAt Match-Start in Epoch-Millis.
 * @param endedAt Match-Ende in Epoch-Millis, null wenn nicht beendet.
 * @param winnerId Match-Gewinner, null wenn offen oder Spieler geloescht.
 */
data class StatsMatchRow(
    val id: Long,
    val modeType: String,
    val startedAt: Long,
    val endedAt: Long?,
    val winnerId: Long?,
)
