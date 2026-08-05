package com.mechanicel.tomsdarts.game

/**
 * Count-Up-Spielmodus (High Score) als konkrete [GameMode]-Strategie.
 *
 * Reine Domaenenlogik, kein Android-/Room-Bezug, mit reinem JUnit testbar.
 * Count Up ist ein rundenbasierter Modus wie Shanghai, aber dessen einfachster
 * Fall: er endet NIE durch einen Werfer-Sieg, sondern ausschliesslich nach
 * [CountUpState.ROUNDS] Runden per Punktvergleich ([DartOutcome.legEnded] +
 * [legScore], siehe ADR-0028). Die [opponents] werden ausschliesslich fuer
 * dieses Rundenende gelesen (wer hat seine Runde schon beendet, gibt es einen
 * eindeutigen Fuehrenden) - die Wertung eines Darts selbst ist gegner-unabhaengig.
 *
 * Regeln pro Dart (siehe [applyDart]):
 * - Jeder Spieler wirft pro Runde eine Aufnahme (3 Darts). Kein Ziel, kein Bust,
 *   kein Checkout und bewusst KEIN Sofort-Sieg.
 * - JEDER Dart punktet seinen eigenen Wert ([Dart.value] == Segment mal
 *   Multiplikator): Single/Double/Triple zaehlen voll, Bull 25, Doppel-Bull 50,
 *   ein Fehlwurf 0 - der Dart zaehlt aber als geworfen (die Aufnahme laeuft
 *   weiter).
 * - Rundenende (legEnded): Nach dem 3. Dart einer Aufnahme, wenn der Werfer
 *   mindestens [CountUpState.ROUNDS] Runden gespielt hat, ALLE Gegner mindestens
 *   gleich viele Runden abgeschlossen haben (der Werfer ist der letzte der Runde)
 *   und es einen EINDEUTIGEN Punkte-Fuehrenden gibt. Bei Gleichstand an der
 *   Spitze endet das Leg bewusst nicht: es geht in die Verlaengerung (Sudden
 *   Death, Runde 9, 10, ... ohne Obergrenze), bis eine weitere volle Runde einen
 *   eindeutigen Fuehrenden ergibt.
 * - [legScore] ist der Punktestand; die Engine kuert damit den Leg-Gewinner.
 *
 * Flag-Invariante: `bust` und `legWon` sind hier IMMER false; hoechstens
 * [DartOutcome.legEnded] kann true werden.
 */
class CountUpMode : GameMode<CountUpState> {

    override val key: String = "COUNT_UP"
    override val displayName: String = "Count Up"

    override fun initialState(config: GameConfig): CountUpState = CountUpState.initial()

    override fun applyDart(
        state: CountUpState,
        dart: Dart,
        config: GameConfig,
        opponents: List<CountUpState>,
    ): DartOutcome<CountUpState> {
        // Count Up wertet ungefiltert: der volle Dart-Wert wandert auf das Konto
        // (Miss 0, Bull 25, Doppel-Bull 50, T-20 60).
        val scored = dart.value

        val dartsThrown = state.dartsThrown + 1
        val visitComplete = dartsThrown % CountUpState.DARTS_PER_ROUND == 0
        val newState = CountUpState(
            dartsThrown = dartsThrown,
            points = state.points + scored,
        )

        val legEnded = visitComplete &&
            newState.completedRounds >= CountUpState.ROUNDS &&
            // Der Werfer ist der letzte der Runde: kein Gegner hinkt hinterher.
            opponents.all { it.completedRounds >= newState.completedRounds } &&
            hasUniqueLeader(newState.points, opponents)

        return DartOutcome(
            newState = newState,
            bust = false,
            legWon = false,
            legEnded = legEnded,
            scored = scored,
        )
    }

    /** Rangwert fuer den Gewinner-Vergleich der Engine: der Punktestand. */
    override fun legScore(state: CountUpState): Int = state.points

    /**
     * True, wenn es GENAU EINEN hoechsten Punktestand ueber alle Spieler gibt
     * (Werfer mit [points] plus [opponents]). Bei Gleichstand an der Spitze ist
     * das Leg nicht entschieden -> Sudden Death.
     */
    private fun hasUniqueLeader(points: Int, opponents: List<CountUpState>): Boolean {
        val allPoints = opponents.map { it.points } + points
        val best = allPoints.max()
        return allPoints.count { it == best } == 1
    }
}
