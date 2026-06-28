package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.DartOutcome

/**
 * Ergebnis eines [LegEngine.applyDart]-Aufrufs.
 *
 * Buendelt das rohe [DartOutcome] des Modus mit den von der Engine ermittelten
 * Aufnahme-/Leg-Informationen. Reines Domaenen-Value-Object (kein Room-Bezug),
 * liefert die noetigen Ints fuer eine spaetere throw-level-Persistenz:
 * - pro Dart: [dartIndex] in der Aufnahme, gewerteter [scored]-Wert sowie ueber
 *   `outcome.newState` der resultierende Modus-Zustand,
 * - beim Aufnahme-Ende: [turnEnded], [bust], [legWon] und die gewertete
 *   Aufnahmen-Summe [totalScored].
 *
 * @param S Modus-spezifischer Spielerzustand.
 * @param accepted True, wenn der Dart verarbeitet wurde. False bei No-op
 *   (Leg bereits gewonnen oder Aufnahme bereits beendet, ohne vorheriges
 *   [LegEngine.startNewTurn]); dann ist [outcome] == null und [dartIndex] == -1.
 * @param outcome Rohes Modus-Ergebnis des Wurfs; null bei No-op. Hinweis: Bei
 *   Bust enthaelt `outcome.newState` den unveraenderten Modus-Eingangszustand;
 *   der von der Engine gueltige (zurueckgesetzte) Zustand steht in [snapshot].
 * @param dartIndex 0-basierter Index dieses Darts in der Aufnahme; -1 bei No-op.
 * @param scored Gewerteter Punktwert dieses Darts (0 bei Bust oder No-op).
 * @param totalScored Gewertete Summe der aktuellen Aufnahme inkl. dieses Darts
 *   (0 bei Bust).
 * @param turnEnded True, wenn die Aufnahme mit diesem Dart endet (3 Darts, Bust
 *   oder Leg-Gewinn).
 * @param bust True, wenn dieser Dart die Aufnahme zum Bust macht.
 * @param legWon True, wenn dieser Dart das Leg gewinnt.
 * @param snapshot Vollstaendiger Engine-Zustand nach Verarbeitung dieses Darts.
 */
data class DartResult<S>(
    val accepted: Boolean,
    val outcome: DartOutcome<S>?,
    val dartIndex: Int,
    val scored: Int,
    val totalScored: Int,
    val turnEnded: Boolean,
    val bust: Boolean,
    val legWon: Boolean,
    val snapshot: LegEngineSnapshot<S>,
)
