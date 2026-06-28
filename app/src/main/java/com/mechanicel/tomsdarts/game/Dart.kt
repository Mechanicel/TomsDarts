package com.mechanicel.tomsdarts.game

/**
 * Ein einzelner Dartwurf als reines Domaenen-Value-Object.
 *
 * Bewusst entkoppelt von der Persistenz-Entity (`data.entity.Throw`): kein
 * Room-/Android-Bezug, keine IDs, kein Timestamp. Dadurch ist die Domaenenlogik
 * mit reinem JUnit (ohne Robolectric) testbar.
 *
 * @param segment Getroffenes Segment: 1-20, 25 = Bull, 0 = daneben/Miss.
 * @param multiplier Faktor des Felds: 1 = Single, 2 = Double, 3 = Triple.
 */
data class Dart(val segment: Int, val multiplier: Int) {

    /**
     * Erzielter Punktwert dieses Wurfs (segment * multiplier).
     *
     * Gilt generisch ueber alle Modi: Miss = 0*1 = 0, Bull = 25*1 = 25,
     * Doppel-Bull = 25*2 = 50, Triple-20 = 20*3 = 60.
     */
    val value: Int get() = segment * multiplier

    /** True, wenn dieser Wurf ein Double ist (multiplier == 2). */
    val isDouble: Boolean get() = multiplier == 2

    /** True, wenn dieser Wurf ein Triple ist (multiplier == 3). */
    val isTriple: Boolean get() = multiplier == 3

    /**
     * Pruefung auf physikalische Gueltigkeit eines Wurfs auf dem Dartboard:
     * - Segment muss 0 (Miss), 1..20 oder 25 (Bull) sein.
     * - Multiplier muss 1..3 sein.
     * - Miss (segment 0) nur als Single (multiplier 1).
     * - Bull (segment 25) nur als Single oder Double (kein Triple-Bull).
     * - Segmente 1..20 erlauben Single/Double/Triple.
     */
    val isValid: Boolean
        get() = when {
            multiplier !in 1..3 -> false
            segment == 0 -> multiplier == 1
            segment == 25 -> multiplier in 1..2
            segment in 1..20 -> true
            else -> false
        }

    companion object {
        /** Single auf Segment [n] (n*1). */
        fun single(n: Int): Dart = Dart(n, 1)

        /** Double auf Segment [n] (n*2). */
        fun double(n: Int): Dart = Dart(n, 2)

        /** Triple auf Segment [n] (n*3). */
        fun triple(n: Int): Dart = Dart(n, 3)

        /** Bull (25*1 = 25). */
        fun bull(): Dart = Dart(25, 1)

        /** Doppel-Bull (25*2 = 50). */
        fun doubleBull(): Dart = Dart(25, 2)

        /** Daneben/Miss (0*1 = 0). */
        fun miss(): Dart = Dart(0, 1)
    }
}
