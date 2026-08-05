package com.mechanicel.tomsdarts.game

import kotlin.random.Random

/**
 * Spielerzustand fuer den Killer-Modus: die eigene Zielzahl, der Killer-Status und
 * die eigenen Treffer auf den Zahlen der Mitspieler.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-Bezug, mit reinem JUnit
 * testbar. Der Zustand folgt dem **Inversions-Muster** (ADR-0031): die eigenen
 * LEBEN stehen bewusst NICHT im eigenen Zustand, sondern ergeben sich aus den
 * Treffern der MITSPIELER auf die eigene [number] ([livesOf]). Dadurch bleibt der
 * Zustand redundanzfrei und eine reine Funktion der Wurf-Historie - Undo/Replay
 * bleibt automatisch konsistent.
 *
 * @param number Eigene Zielzahl (1..20), die Identitaet des Spielers im Match.
 *   Deterministisch aus dem eingefrorenen Seed der [GameConfig] und dem Sitzplatz
 *   abgeleitet ([numberFor]) - nie gewuerfelt (siehe [GameMode.initialState]).
 * @param isKiller True, sobald der Spieler das Double seiner eigenen [number]
 *   getroffen hat. Erst dann kosten seine Treffer die Mitspieler Leben.
 * @param hitsOn Treffer DIESES Spielers je Gegner-Zahl (Segment -> Anzahl). Jeder
 *   Eintrag kostet dem Spieler mit dieser Zahl ein Leben.
 */
data class KillerState(
    val number: Int,
    val isKiller: Boolean = false,
    val hitsOn: Map<Int, Int> = emptyMap(),
) {

    companion object {

        /** Leben je Spieler zu Leg-Beginn (v1: fest, nicht konfigurierbar). */
        const val LIVES: Int = 3

        /** Kleinste vergebbare Zielzahl. */
        const val FIRST_NUMBER: Int = 1

        /** Groesste vergebbare Zielzahl (Bull ist bewusst keine Killer-Zahl). */
        const val LAST_NUMBER: Int = 20

        /**
         * Zielzahl des Sitzplatzes [playerIndex] (0-basiert) fuer den in der
         * [GameConfig.killerSeed] eingefrorenen [seed]. Einzige Quelle der
         * Zuweisung (Modus UND Tests leiten hierueber ab).
         *
         * Die Zahlen 1..20 werden mit dem Seed EINMAL gemischt; jeder Sitzplatz
         * bekommt die Zahl an seiner Position. Damit ist die Zuweisung
         * - **eindeutig** (Permutation, keine Zahl doppelt),
         * - **deterministisch** (gleicher Seed -> gleiche Zahlen, auch nach
         *   Undo-Replay und Leg-Wechsel, die den Startzustand neu erzeugen),
         * - **zufaellig genug** ueber Matches hinweg (der Seed wird pro Match in
         *   [com.mechanicel.tomsdarts.ui.game.GameViewModel.provideFactory]
         *   gezogen).
         *
         * Defensiv: Sitzplaetze ausserhalb 0..19 (in der App unerreichbar, die
         * Teilnehmerzahl liegt weit darunter) werden zyklisch abgebildet, statt
         * eine Exception zu werfen.
         */
        fun numberFor(seed: Long, playerIndex: Int): Int {
            val numbers = (FIRST_NUMBER..LAST_NUMBER).shuffled(Random(seed))
            return numbers[playerIndex.mod(numbers.size)]
        }

        /**
         * Verbleibende Leben des Spielers mit der Zielzahl [number]: [LIVES]
         * abzueglich der Treffer ALLER [opponents] auf diese Zahl, gekappt auf
         * 0..[LIVES].
         *
         * Einzige Quelle der Lebens-Formel: [KillerMode.isEliminated] und der
         * [com.mechanicel.tomsdarts.ui.game.KillerUiAdapter] rechnen sie bewusst
         * nicht nach. Reine Ableitung aus den Zustaenden (kein Zufall, keine
         * Historie) - Voraussetzung fuer deterministisches Undo-Replay.
         */
        fun livesOf(number: Int, opponents: List<KillerState>): Int =
            (LIVES - opponents.sumOf { it.hitsOn[number] ?: 0 }).coerceIn(0, LIVES)

        /**
         * Startzustand des Sitzplatzes [playerIndex]: eigene Zahl aus [seed]
         * abgeleitet, noch kein Killer, keine Treffer.
         */
        fun initial(seed: Long, playerIndex: Int): KillerState = KillerState(
            number = numberFor(seed, playerIndex),
            isKiller = false,
            hitsOn = emptyMap(),
        )
    }
}
