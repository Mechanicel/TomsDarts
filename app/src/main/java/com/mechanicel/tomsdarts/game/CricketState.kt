package com.mechanicel.tomsdarts.game

/**
 * Spielerzustand fuer den Standard-Cricket-Modus: die je Feld gesammelten Marks
 * und der bisher erzielte Punktestand in einem Leg.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-Bezug, mit reinem JUnit
 * testbar. Die Marks liegen als Map ueber den in Play befindlichen Feldern vor
 * ({15,16,17,18,19,20} sowie 25 = Bull); jedes Feld traegt 0..3 Marks
 * (3 == geschlossen). Die Anzeige-Reihenfolge (20..15, Bull) ist bewusst NICHT
 * Teil des Zustands, sondern Sache der UI-Schicht.
 *
 * @param marks Marks je Feld. Schluessel exakt [FIELDS] (15,16,17,18,19,20,25),
 *   Werte 0..3 (auf 3 gekappt == geschlossen).
 * @param points Bisher erzielter Punktestand des Spielers im laufenden Leg.
 */
data class CricketState(
    val marks: Map<Int, Int>,
    val points: Int,
) {

    /** Anzahl Marks des Feldes [target] (0, falls kein bekanntes Feld). */
    fun marksOf(target: Int): Int = marks[target] ?: 0

    /** True, wenn das Feld [target] mit 3 Marks geschlossen ist. */
    fun isClosed(target: Int): Boolean = marksOf(target) >= CLOSED_MARKS

    /** True, wenn alle in Play befindlichen Felder geschlossen sind. */
    fun allClosed(): Boolean = FIELDS.all { isClosed(it) }

    companion object {

        /** In Play befindliche Felder eines Standard-Cricket-Spiels. */
        val FIELDS: List<Int> = listOf(15, 16, 17, 18, 19, 20, 25)

        /** Bull-Segment (zaehlt als eigenes Feld, Feldwert 25). */
        const val BULL: Int = 25

        /** Marks, ab denen ein Feld geschlossen ist (auf diesen Wert gekappt). */
        const val CLOSED_MARKS: Int = 3

        /** Startzustand: alle Felder auf 0 Marks, 0 Punkte. */
        fun initial(): CricketState =
            CricketState(marks = FIELDS.associateWith { 0 }, points = 0)
    }
}
