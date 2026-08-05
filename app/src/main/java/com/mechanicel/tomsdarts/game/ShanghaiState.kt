package com.mechanicel.tomsdarts.game

/**
 * Spielerzustand fuer den Shanghai-Modus: geworfene Darts, gesammelte Punkte und
 * die Treffer-Spur der LAUFENDEN Aufnahme.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-Bezug, mit reinem JUnit
 * testbar. Runde und Zielzahl sind bewusst NICHT gespeichert, sondern werden aus
 * [dartsThrown] abgeleitet ([round]/[target]) - so ist der Zustand eine reine
 * Funktion der Wurf-Historie und Undo/Replay bleibt automatisch konsistent.
 *
 * Aufnahme-Grenzen: Shanghai kennt weder Bust noch ein vorzeitiges Aufnahme-Ende,
 * jede Aufnahme umfasst also genau [DARTS_PER_ROUND] Darts. Damit gilt: eine
 * Aufnahme ist voll, sobald `dartsThrown % DARTS_PER_ROUND == 0`, und die
 * abgeschlossenen Runden sind `dartsThrown / DARTS_PER_ROUND`.
 *
 * @param dartsThrown Anzahl der in diesem Leg bereits geworfenen Darts.
 * @param points Bisher erzielter Punktestand des Spielers im laufenden Leg.
 * @param visitHits Multiplikatoren (1 = Single, 2 = Double, 3 = Triple) der
 *   Zielzahl-Treffer in der LAUFENDEN Aufnahme. Nach einer vollen Aufnahme leer,
 *   weil der Zustand dann bereits die naechste Runde beschreibt.
 */
data class ShanghaiState(
    val dartsThrown: Int,
    val points: Int,
    val visitHits: Set<Int> = emptySet(),
) {

    /**
     * 1-basierte Runde, in der sich dieser Spieler befindet. Nach einer vollen
     * Aufnahme zaehlt der Spieler bereits zur naechsten Runde. Werte > [ROUNDS]
     * bedeuten Sudden Death (Stechen nach Gleichstand).
     */
    val round: Int get() = roundOf(dartsThrown)

    /** Zielzahl der aktuellen [round] (1..[ROUNDS], ab Runde 8 zyklisch). */
    val target: Int get() = targetOf(round)

    /** Anzahl der von diesem Spieler vollstaendig gespielten Runden. */
    val completedRounds: Int get() = dartsThrown / DARTS_PER_ROUND

    companion object {

        /** Anzahl der regulaeren Runden (danach entscheidet der Punktvergleich). */
        const val ROUNDS: Int = 7

        /** Darts je Aufnahme bzw. Runde. */
        const val DARTS_PER_ROUND: Int = 3

        /**
         * 1-basierte Runde zu einer Anzahl geworfener Darts. Einzige Quelle der
         * Formel (Modus UND UI-Adapter leiten hierueber ab).
         */
        fun roundOf(dartsThrown: Int): Int = dartsThrown / DARTS_PER_ROUND + 1

        /**
         * Zielzahl der 1-basierten [round]: Runde 1..7 zielt auf 1..7, ab Runde 8
         * (Sudden Death) laeuft die Sequenz zyklisch weiter (8 -> 1, 9 -> 2, ...).
         */
        fun targetOf(round: Int): Int = (round - 1) % ROUNDS + 1

        /** Startzustand: kein Dart geworfen, 0 Punkte, leere Trefferspur. */
        fun initial(): ShanghaiState =
            ShanghaiState(dartsThrown = 0, points = 0, visitHits = emptySet())
    }
}
