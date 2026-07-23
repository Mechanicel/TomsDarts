package com.mechanicel.tomsdarts.game

/**
 * Around-the-Clock-Spielmodus als konkrete [GameMode]-Strategie.
 *
 * Reine Domaenenlogik, kein Android-/Room-Bezug, mit reinem JUnit testbar. Wie
 * X01 ist der Modus gegner-unabhaengig: die [opponents] werden bewusst ignoriert.
 *
 * Regeln pro Dart (siehe [applyDart]):
 * - Ziel ist eine Zahl in 1..[AroundTheClockState.TOTAL], Start == 1.
 * - Trifft der Dart genau die Zielzahl (`dart.segment == state.target`), rueckt
 *   das Ziel um 1 vor. Der Multiplier ist dabei egal: Single, Double und Triple
 *   der Zielzahl zaehlen gleich (je genau ein Vorruecken).
 * - Jede andere Zahl, Bull (25) und Miss (0) sind ein No-Op: der Zustand bleibt
 *   unveraendert, `scored == 0`, weder Bust noch Leg-Gewinn.
 * - `scored` ist bei erfolgreichem Vorruecken 1 (sonst 0). Damit ist die
 *   Zug-Summe in der UI gleich der Anzahl der Vorrueckungen in dieser Aufnahme.
 * - Kein Bust (immer false), kein Checkout.
 * - Sieg (legWon): erreicht das Ziel nach dem Vorruecken einen Wert > [TOTAL]
 *   (also wurde die 20 getroffen), ist das Leg gewonnen.
 */
class AroundTheClockMode : GameMode<AroundTheClockState> {

    override val key: String = "AROUND_THE_CLOCK"
    override val displayName: String = "Around the Clock"

    override fun initialState(config: GameConfig): AroundTheClockState =
        AroundTheClockState.initial()

    override fun applyDart(
        state: AroundTheClockState,
        dart: Dart,
        config: GameConfig,
        // Around the Clock wertet rein aus dem eigenen Ziel; die Gegner-Zustaende
        // sind fuer diesen Modus irrelevant und werden bewusst ignoriert.
        opponents: List<AroundTheClockState>,
    ): DartOutcome<AroundTheClockState> {
        // Nur die exakte Zielzahl (Multiplier egal) rueckt vor; alles andere
        // (falsche Zahl, Bull 25, Miss 0) ist ein No-Op.
        if (dart.segment != state.target) {
            return DartOutcome(state, bust = false, legWon = false, scored = 0)
        }

        val newTarget = state.target + 1
        // Ueber die 20 hinaus == alle Zahlen getroffen -> Leg gewonnen.
        val legWon = newTarget > AroundTheClockState.TOTAL
        return DartOutcome(
            AroundTheClockState(newTarget),
            bust = false,
            legWon = legWon,
            scored = 1,
        )
    }
}
