package com.mechanicel.tomsdarts.game.engine

/**
 * Ergebnis eines [MatchEngine.applyDart]-Aufrufs.
 *
 * Buendelt das [DartResult] der LegEngine des werfenden Spielers mit den von der
 * MatchEngine ermittelten Leg-/Set-/Match-Uebergangs-Informationen und dem
 * Spielerwechsel-Signal. Reines Domaenen-Value-Object (kein Room-Bezug); liefert
 * alles, was UI und Persistenz pro Wurf brauchen:
 * - throw-level: ueber [dartResult] (dartIndex, scored, Modus-Zustand, Snapshot),
 * - turn-/leg-level: [turnEnded], [bust], [legWon], [legSnapshot] des Werfers,
 * - match-level: [setWon], [matchWon], [matchWinnerId],
 * - Spielerwechsel: [playerId] (wer warf) und [nextPlayerId] (wer als Naechstes dran ist).
 *
 * Spielerwechsel-Modell: Der Wechsel erfolgt AUTOMATISCH innerhalb von
 * [MatchEngine.applyDart], sobald die Aufnahme endet (3 Darts, Bust oder
 * Leg-Gewinn). Der Aufrufer erkennt den Wechsel an `playerId != nextPlayerId`
 * bzw. an [turnEnded]; nach der Rueckkehr ist bereits der naechste Spieler aktiv.
 *
 * @param S Modus-spezifischer Spielerzustand.
 * @param accepted True, wenn der Dart verarbeitet wurde. False bei No-op
 *   (Match bereits gewonnen); dann ist [dartResult] == null.
 * @param dartResult Ergebnis der zugrunde liegenden [LegEngine.applyDart]; null
 *   bei No-op. Enthaelt die throw-level-Daten fuer die Persistenz.
 * @param playerId Kennung des Spielers, der diesen Dart geworfen hat.
 * @param turnEnded True, wenn die Aufnahme mit diesem Dart endet (3 Darts, Bust
 *   oder Leg-Gewinn) und damit (sofern das Match nicht entschieden ist) ein
 *   Spielerwechsel ausgeloest wurde.
 * @param bust True, wenn dieser Dart die Aufnahme zum Bust macht.
 * @param legWon True, wenn dieser Dart das Leg gewinnt.
 * @param setWon True, wenn mit diesem Leg-Gewinn auch das Set gewonnen wurde.
 * @param matchWon True, wenn mit diesem Set-Gewinn das Match entschieden wurde.
 * @param matchWinnerId Kennung des Match-Gewinners, sonst null.
 * @param nextPlayerId Kennung des Spielers, der nach Verarbeitung dieses Darts
 *   am Zug ist. Bei Match-Gewinn die Kennung des aktuellen (Gewinner-)Spielers.
 * @param legSnapshot Momentaufnahme der LegEngine des WERFERS direkt nach diesem
 *   Dart (vor einem etwaigen Leg-Reset) - fuer die throw-/leg-level-Persistenz.
 * @param snapshot Vollstaendiger Match-Zustand nach Verarbeitung dieses Darts.
 */
data class MatchDartResult<S>(
    val accepted: Boolean,
    val dartResult: DartResult<S>?,
    val playerId: Long,
    val turnEnded: Boolean,
    val bust: Boolean,
    val legWon: Boolean,
    val setWon: Boolean,
    val matchWon: Boolean,
    val matchWinnerId: Long?,
    val nextPlayerId: Long,
    val legSnapshot: LegEngineSnapshot<S>,
    val snapshot: MatchSnapshot<S>,
)
