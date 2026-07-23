package com.mechanicel.tomsdarts.game

/**
 * Standard-Cricket-Spielmodus als konkrete [GameMode]-Strategie.
 *
 * Reine Domaenenlogik, kein Android-/Room-Bezug, mit reinem JUnit testbar. Im
 * Gegensatz zu X01 haengt die Wertung von den MITSPIELERN ab: ein geschlossenes
 * Feld bringt nur dann Punkte, wenn noch mindestens ein Gegner es offen hat.
 * Diese Gegner-Zustaende werden ueber den lesenden [opponents]-Parameter
 * eingespeist (siehe [GameMode.applyDart]).
 *
 * Regeln pro Dart (siehe [applyDart]):
 * - In Play sind nur die Felder [CricketState.FIELDS] (15,16,17,18,19,20 und Bull
 *   25). Alle anderen Segmente (1-14) und Miss (0) sind No-Ops (kein
 *   Zustandswechsel, `scored == 0`).
 * - Marks pro Treffer == `dart.multiplier` (Single 1, Double 2, Triple 3;
 *   Bull: Single 1 Mark, Doppel-Bull 2 Marks).
 * - Ein Feld ist bei 3 Marks geschlossen (auf 3 gekappt).
 * - Ein Dart kann zugleich schliessen UND punkten (Overflow): mit `m` == bisherige
 *   Marks des Feldes gilt `toClose = min(marks, 3 - m)`, `newMarks = m + toClose`,
 *   `overflow = marks - toClose`.
 * - Punkte: `overflow`-Marks bringen `overflow * Feldwert` Punkte (Bull == 25),
 *   aber nur wenn das Feld fuer die Wertung offen ist, d.h. `overflow > 0` und
 *   (keine Gegner bekannt ODER mindestens ein Gegner hat das Feld noch offen).
 * - Kein Bust, kein Checkout.
 * - Sieg (legWon): ALLE Felder des Spielers geschlossen UND `newPoints` >= jeder
 *   Gegner-Punktestand (bei leerer Gegnerliste genuegt "alle geschlossen").
 */
class CricketMode : GameMode<CricketState> {

    override val key: String = "CRICKET"
    override val displayName: String = "Cricket"

    override fun initialState(config: GameConfig): CricketState = CricketState.initial()

    override fun applyDart(
        state: CricketState,
        dart: Dart,
        config: GameConfig,
        opponents: List<CricketState>,
    ): DartOutcome<CricketState> {
        val target = dart.segment
        // Nur Felder in Play (15-20, Bull 25) wirken; alles andere ist ein No-Op.
        if (target !in CricketState.FIELDS) {
            return DartOutcome(state, bust = false, legWon = false, scored = 0)
        }

        val addedMarks = dart.multiplier
        val current = state.marksOf(target)
        // Marks, die noch zum Schliessen fehlen, und der ueberschiessende Rest.
        val toClose = minOf(addedMarks, CricketState.CLOSED_MARKS - current)
        val newMarks = current + toClose
        val overflow = addedMarks - toClose

        // Overflow zaehlt nur, wenn das Feld fuer die Wertung offen ist: entweder
        // gibt es keine Gegner (Solo -> Punkte akkumulieren) oder mindestens ein
        // Gegner hat das Feld noch nicht geschlossen.
        val scorable = overflow > 0 &&
            (opponents.isEmpty() || opponents.any { it.marksOf(target) < CricketState.CLOSED_MARKS })
        val scored = if (scorable) overflow * target else 0

        val newPoints = state.points + scored
        val newState = CricketState(
            marks = state.marks + (target to newMarks),
            points = newPoints,
        )

        // Sieg: alle eigenen Felder geschlossen UND Punkte mindestens gleichauf mit
        // jedem Gegner (bei leerer Gegnerliste reicht "alle geschlossen").
        val legWon = newState.allClosed() && opponents.all { newPoints >= it.points }

        return DartOutcome(newState, bust = false, legWon = legWon, scored = scored)
    }
}
