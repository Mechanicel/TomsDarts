package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart

/**
 * Pure Label-Formatierung fuer den Eingabe-Ziffernblock.
 *
 * Bewusst ohne Compose-/Android-Bezug und damit rein per JUnit testbar. Die
 * erzeugten Kurzlabels sind nicht-lokalisierte Glyphen (z. B. "T-20", "D-Bull")
 * und liegen daher bewusst nicht in strings.xml.
 */

/** Praefix-Glyphe fuer einen Multiplikator: ""/"D-"/"T-". */
private fun multiplierPrefix(multiplier: Int): String = when (multiplier) {
    2 -> "D-"
    3 -> "T-"
    else -> ""
}

/**
 * Kurzlabel fuer einen bereits gesetzten [Dart].
 *
 * - Segment 0 (Miss) -> "Out"
 * - Segment 25 (Bull) -> "Bull" (Single) bzw. "D-Bull" (Double)
 * - sonst Praefix (""/"D-"/"T-") + Segmentzahl, z. B. "20", "D-19", "T-20"
 */
fun dartShortLabel(dart: Dart): String = when (dart.segment) {
    0 -> "Out"
    25 -> if (dart.multiplier == 2) "D-Bull" else "Bull"
    else -> multiplierPrefix(dart.multiplier) + dart.segment
}

/**
 * Label einer Zahltaste [n] im aktuellen [modifier]:
 * SINGLE -> "n", DOUBLE -> "D-n", TRIPLE -> "T-n".
 */
fun numberKeyLabel(n: Int, modifier: DartModifier): String = when (modifier) {
    DartModifier.SINGLE -> "$n"
    DartModifier.DOUBLE -> "D-$n"
    DartModifier.TRIPLE -> "T-$n"
}

/**
 * Label der Bull-Taste im aktuellen [modifier]: "D-Bull" bei DOUBLE, sonst
 * "Bull". TRIPLE besitzt kein Triple-Bull und wird wie SINGLE als "Bull"
 * dargestellt (die Taste ist im TRIPLE-Modus ohnehin deaktiviert).
 */
fun bullKeyLabel(modifier: DartModifier): String =
    if (modifier == DartModifier.DOUBLE) "D-Bull" else "Bull"
