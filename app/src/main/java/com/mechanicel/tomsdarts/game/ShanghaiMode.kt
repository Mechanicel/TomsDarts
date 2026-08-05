package com.mechanicel.tomsdarts.game

/**
 * Shanghai-Spielmodus als konkrete [GameMode]-Strategie.
 *
 * Reine Domaenenlogik, kein Android-/Room-Bezug, mit reinem JUnit testbar.
 * Shanghai ist der erste rundenbasierte Modus: er endet regulaer NICHT durch
 * einen Werfer-Sieg, sondern nach [ShanghaiState.ROUNDS] Runden per
 * Punktvergleich ([DartOutcome.legEnded] + [legScore], siehe ADR-0028). Die
 * [opponents] werden ausschliesslich fuer dieses Rundenende gelesen (wer hat
 * seine Runde schon beendet, gibt es einen eindeutigen Fuehrenden) - die Wertung
 * eines Darts selbst ist gegner-unabhaengig.
 *
 * Regeln pro Dart (siehe [applyDart]):
 * - Runde `r` (1-basiert) hat die Zielzahl `((r-1) % 7) + 1`: Runde 1..7 zielt
 *   auf 1..7, Sudden-Death-Runden ab 8 laufen zyklisch weiter.
 * - Jeder Spieler wirft pro Runde eine Aufnahme (3 Darts). Kein Bust, kein
 *   Checkout.
 * - Nur Treffer auf die AKTUELLE Zielzahl punkten: `target * dart.multiplier`.
 *   Jedes andere Segment, Bull und Miss bringen 0 Punkte - der Dart zaehlt aber
 *   als geworfen (die Aufnahme laeuft weiter).
 * - Shanghai (legWon): Single UND Double UND Triple der Zielzahl in EINER
 *   Aufnahme (Reihenfolge egal) gewinnen das Leg sofort. Da dafuer alle drei
 *   Darts der Aufnahme auf der Zielzahl liegen muessen, faellt ein Shanghai
 *   immer auf den 3. Dart einer Aufnahme.
 * - Rundenende (legEnded): Nach dem 3. Dart einer Aufnahme, wenn der Werfer
 *   mindestens [ShanghaiState.ROUNDS] Runden gespielt hat, ALLE Gegner
 *   mindestens gleich viele Runden abgeschlossen haben (der Werfer ist der
 *   letzte der Runde) und es einen EINDEUTIGEN Punkte-Fuehrenden gibt. Bei
 *   Gleichstand an der Spitze endet das Leg bewusst nicht: es geht in die
 *   Verlaengerung (Sudden Death), bis eine weitere volle Runde einen eindeutigen
 *   Fuehrenden ergibt.
 * - [legScore] ist der Punktestand; die Engine kuert damit den Leg-Gewinner.
 */
class ShanghaiMode : GameMode<ShanghaiState> {

    override val key: String = "SHANGHAI"
    override val displayName: String = "Shanghai"

    override fun initialState(config: GameConfig): ShanghaiState = ShanghaiState.initial()

    override fun applyDart(
        state: ShanghaiState,
        dart: Dart,
        config: GameConfig,
        opponents: List<ShanghaiState>,
    ): DartOutcome<ShanghaiState> {
        val target = state.target
        // Nur die exakte Zielzahl punktet (Bull 25 und Miss 0 liegen nie in 1..7,
        // treffen also nie); der Multiplier bestimmt den Wert.
        val hit = dart.segment == target
        val scored = if (hit) target * dart.multiplier else 0
        val visitHits = if (hit) state.visitHits + dart.multiplier else state.visitHits

        val dartsThrown = state.dartsThrown + 1
        val visitComplete = dartsThrown % ShanghaiState.DARTS_PER_ROUND == 0
        val newState = ShanghaiState(
            dartsThrown = dartsThrown,
            points = state.points + scored,
            // Nach einer vollen Aufnahme beschreibt der Zustand bereits die
            // naechste Runde - die Trefferspur der alten Aufnahme faellt weg.
            visitHits = if (visitComplete) emptySet() else visitHits,
        )

        // Shanghai schlaegt alles: Single + Double + Triple der Zielzahl in einer
        // Aufnahme ist ein direkter Werfer-Sieg (dann kein legEnded, Vertrag).
        if (visitHits.containsAll(SHANGHAI_MULTIPLIERS)) {
            return DartOutcome(newState, bust = false, legWon = true, scored = scored)
        }

        val legEnded = visitComplete &&
            newState.completedRounds >= ShanghaiState.ROUNDS &&
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
    override fun legScore(state: ShanghaiState): Int = state.points

    /**
     * True, wenn es GENAU EINEN hoechsten Punktestand ueber alle Spieler gibt
     * (Werfer mit [points] plus [opponents]). Bei Gleichstand an der Spitze ist
     * das Leg nicht entschieden -> Sudden Death.
     */
    private fun hasUniqueLeader(points: Int, opponents: List<ShanghaiState>): Boolean {
        val allPoints = opponents.map { it.points } + points
        val best = allPoints.max()
        return allPoints.count { it == best } == 1
    }

    private companion object {
        /** Fuer ein Shanghai noetige Multiplikatoren: Single, Double und Triple. */
        val SHANGHAI_MULTIPLIERS: Set<Int> = setOf(1, 2, 3)
    }
}
