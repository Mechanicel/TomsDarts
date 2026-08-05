package com.mechanicel.tomsdarts.game

/**
 * Ergebnis der Verarbeitung GENAU EINES Darts durch einen [GameMode].
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. Restpunktzahl bei X01).
 * @param newState Der nach dem Wurf resultierende Spielerzustand. Bei
 *   [bust] == true ist dieser Zustand von der aufrufenden Engine zu verwerfen
 *   (Rueckkehr zum Aufnahme-Startzustand) - siehe Vertrag in [GameMode.applyDart].
 * @param bust True, wenn dieser Wurf einen Bust ausloest (Aufnahme ungueltig).
 *   Bei [bust] == true sind [legWon] und [legEnded] immer false.
 * @param legWon True, wenn mit diesem Wurf das Leg gewonnen ist - und zwar vom
 *   WERFER selbst (klassischer Checkout/Ziel-Abschluss). Bei [legWon] == true
 *   sind [bust] und [legEnded] immer false.
 * @param legEnded True, wenn das Leg durch diesen Dart ENTSCHIEDEN ist, aber
 *   NICHT zwingend zugunsten des Werfers; der Gewinner wird von der Engine via
 *   [GameMode.legScore] ueber ALLE Spieler ermittelt (hoechster Rangwert
 *   gewinnt). Signal fuer rundenbasierte Modi, die nach fester Rundenzahl mit
 *   Punktvergleich enden. Bei [legEnded] == true sind [bust] und [legWon] immer
 *   false. Default `false`: Modi mit klassischem Werfer-Sieg (X01, Cricket,
 *   Around the Clock) bleiben unveraendert.
 * @param scored Tatsaechlich gewerteter Punktwert dieses Wurfs (bei Bust i.d.R. 0).
 */
data class DartOutcome<S>(
    val newState: S,
    val bust: Boolean,
    val legWon: Boolean,
    val legEnded: Boolean = false,
    val scored: Int,
)
