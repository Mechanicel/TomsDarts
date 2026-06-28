package com.mechanicel.tomsdarts.game

/**
 * Ergebnis der Verarbeitung GENAU EINES Darts durch einen [GameMode].
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. Restpunktzahl bei X01).
 * @param newState Der nach dem Wurf resultierende Spielerzustand. Bei
 *   [bust] == true ist dieser Zustand von der aufrufenden Engine zu verwerfen
 *   (Rueckkehr zum Aufnahme-Startzustand) - siehe Vertrag in [GameMode.applyDart].
 * @param bust True, wenn dieser Wurf einen Bust ausloest (Aufnahme ungueltig).
 *   Bei [bust] == true ist [legWon] immer false.
 * @param legWon True, wenn mit diesem Wurf das Leg gewonnen ist.
 *   Bei [legWon] == true ist [bust] immer false.
 * @param scored Tatsaechlich gewerteter Punktwert dieses Wurfs (bei Bust i.d.R. 0).
 */
data class DartOutcome<S>(
    val newState: S,
    val bust: Boolean,
    val legWon: Boolean,
    val scored: Int,
)
