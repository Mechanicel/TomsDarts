package com.mechanicel.tomsdarts.game

/**
 * Spielerzustand fuer den Count-Up-Modus: geworfene Darts und gesammelte Punkte.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-Bezug, mit reinem JUnit
 * testbar. Die Runde ist bewusst NICHT gespeichert, sondern wird aus
 * [dartsThrown] abgeleitet ([round]) - so ist der Zustand eine reine Funktion der
 * Wurf-Historie und Undo/Replay bleibt automatisch konsistent (Muster wie
 * [ShanghaiState], nur ohne Zielzahl und ohne Trefferspur: bei Count Up punktet
 * jeder Dart mit seinem eigenen Wert).
 *
 * Aufnahme-Grenzen: Count Up kennt weder Bust noch ein vorzeitiges Aufnahme-Ende,
 * jede Aufnahme umfasst also genau [DARTS_PER_ROUND] Darts. Damit gilt: eine
 * Aufnahme ist voll, sobald `dartsThrown % DARTS_PER_ROUND == 0`, und die
 * abgeschlossenen Runden sind `dartsThrown / DARTS_PER_ROUND`.
 *
 * @param dartsThrown Anzahl der in diesem Leg bereits geworfenen Darts.
 * @param points Bisher erzielter Punktestand des Spielers im laufenden Leg.
 */
data class CountUpState(
    val dartsThrown: Int,
    val points: Int,
) {

    /**
     * 1-basierte Runde, in der sich dieser Spieler befindet. Nach einer vollen
     * Aufnahme zaehlt der Spieler bereits zur naechsten Runde. Werte > [ROUNDS]
     * bedeuten Sudden Death (Stechen nach Gleichstand).
     */
    val round: Int get() = roundOf(dartsThrown)

    /** Anzahl der von diesem Spieler vollstaendig gespielten Runden. */
    val completedRounds: Int get() = dartsThrown / DARTS_PER_ROUND

    companion object {

        /** Anzahl der regulaeren Runden (danach entscheidet der Punktvergleich). */
        const val ROUNDS: Int = 8

        /** Darts je Aufnahme bzw. Runde. */
        const val DARTS_PER_ROUND: Int = 3

        /**
         * 1-basierte Runde zu einer Anzahl geworfener Darts. Einzige Quelle der
         * Formel (Modus UND UI-Adapter leiten hierueber ab).
         */
        fun roundOf(dartsThrown: Int): Int = dartsThrown / DARTS_PER_ROUND + 1

        /** Startzustand: kein Dart geworfen, 0 Punkte. */
        fun initial(): CountUpState = CountUpState(dartsThrown = 0, points = 0)
    }
}
