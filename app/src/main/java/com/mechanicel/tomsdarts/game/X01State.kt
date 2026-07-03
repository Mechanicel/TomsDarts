package com.mechanicel.tomsdarts.game

/**
 * Spielerzustand fuer den X01-Modus: die verbleibende Restpunktzahl in einem Leg.
 *
 * Bewusst ein eigener Typ statt eines nackten Int, damit der Zustand lesbar und
 * spaeter erweiterbar bleibt (z.B. Statistik-Felder), ohne den [GameMode]-Vertrag
 * zu brechen. Reines Domaenen-Value-Object: kein Android-/Room-Bezug.
 *
 * @param remaining Verbleibende Restpunktzahl (Start == [GameConfig.startScore],
 *   bei Leg-Gewinn 0).
 */
data class X01State(val remaining: Int)
