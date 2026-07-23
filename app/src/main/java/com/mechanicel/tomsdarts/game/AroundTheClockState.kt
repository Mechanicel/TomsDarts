package com.mechanicel.tomsdarts.game

/**
 * Spielerzustand fuer den Around-the-Clock-Modus: die aktuell anzuvisierende
 * Zielnummer in einem Leg.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-Bezug, mit reinem JUnit
 * testbar. Around the Clock ist wie X01 gegner-unabhaengig; der Zustand traegt
 * daher nur das eigene Ziel. Gezaehlt wird von [FIRST_TARGET] (1) aufwaerts bis
 * einschliesslich [TOTAL] (20). Nach dem Treffer der 20 rueckt das Ziel auf
 * `TOTAL + 1` (== 21) vor, was den Leg-Gewinn markiert.
 *
 * @param target Aktuell anzuvisierende Zahl (1..[TOTAL]; nach dem Sieg > [TOTAL]).
 */
data class AroundTheClockState(val target: Int) {

    companion object {

        /** Erste Zielnummer zu Leg-Beginn. */
        const val FIRST_TARGET: Int = 1

        /** Letzte zu treffende Zahl (danach ist das Leg gewonnen). */
        const val TOTAL: Int = 20

        /** Startzustand: Ziel ist die 1. */
        fun initial(): AroundTheClockState = AroundTheClockState(FIRST_TARGET)
    }
}
