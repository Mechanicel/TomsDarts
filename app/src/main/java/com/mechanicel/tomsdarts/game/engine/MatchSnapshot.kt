package com.mechanicel.tomsdarts.game.engine

/**
 * Aggregierter Match-Zustand EINES Spielers ueber Legs und Sets hinweg.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-/Compose-Bezug, dadurch mit
 * reinem JUnit testbar. Buendelt die fuer Anzeige und Persistenz noetigen
 * Aggregate eines Spielers mit seinem aktuellen Leg-Zustand.
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. [com.mechanicel.tomsdarts.game.X01State]).
 * @param playerId Stabile Spieler-Kennung, wie im [MatchEngine]-Konstruktor uebergeben.
 * @param state Aktueller Modus-Zustand des Spielers im LAUFENDEN Leg
 *   (entspricht [LegEngineSnapshot.state]).
 * @param legsWonInSet Anzahl der vom Spieler im AKTUELLEN Set gewonnenen Legs.
 *   Wird bei Set-Gewinn fuer alle Spieler zurueckgesetzt.
 * @param setsWon Anzahl der vom Spieler im Match gewonnenen Sets.
 * @param legSnapshot Vollstaendige Momentaufnahme der [LegEngine] des Spielers im
 *   laufenden Leg (inkl. Throw-/Turn-Daten fuer throw-level-Persistenz).
 */
data class PlayerMatchState<S>(
    val playerId: Long,
    val state: S,
    val legsWonInSet: Int,
    val setsWon: Int,
    val legSnapshot: LegEngineSnapshot<S>,
)

/**
 * Unveraenderliche Momentaufnahme des gesamten Match-Zustands einer [MatchEngine].
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-/Compose-Bezug. Liefert alles,
 * was UI und Persistenz zur Anzeige des aktuellen Standes und zum Speichern der
 * Leg-/Set-/Match-Struktur brauchen.
 *
 * @param S Modus-spezifischer Spielerzustand.
 * @param playerStates Zustaende aller Spieler in Reihenfolge der Konstruktor-Liste.
 * @param currentPlayerIndex Index des aktuell werfenden Spielers in [playerStates].
 * @param currentPlayerId Kennung des aktuell werfenden Spielers.
 * @param currentSetNumber 1-basierte Nummer des laufenden Sets im Match.
 * @param currentLegNumber 1-basierte Nummer des laufenden Legs IM aktuellen Set
 *   (wird bei jedem Set-Wechsel auf 1 zurueckgesetzt).
 * @param isMatchWon True, wenn das Match entschieden ist; danach sind weitere
 *   Wuerfe No-ops.
 * @param matchWinnerId Kennung des Match-Gewinners, sonst null.
 */
data class MatchSnapshot<S>(
    val playerStates: List<PlayerMatchState<S>>,
    val currentPlayerIndex: Int,
    val currentPlayerId: Long,
    val currentSetNumber: Int,
    val currentLegNumber: Int,
    val isMatchWon: Boolean,
    val matchWinnerId: Long?,
)
