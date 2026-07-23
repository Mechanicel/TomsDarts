package com.mechanicel.tomsdarts.game

/**
 * X01-Spielmodus (501, 301, 701, ... je nach [GameConfig.startScore]) als
 * konkrete [GameMode]-Strategie.
 *
 * Der Modus ist startScore-agnostisch: 501/301/701 unterscheiden sich nur ueber
 * [GameConfig.startScore], es gibt daher KEINE separaten Modi dafuer. Reine
 * Domaenenlogik, kein Android-/Room-Bezug, mit reinem JUnit testbar.
 *
 * Regeln pro Dart (siehe [applyDart]):
 * - Heruntergerechnet wird `remaining - dart.value`.
 * - Unter 0 -> Bust (ueberworfen).
 * - Mit Double-Out ([GameConfig.doubleOut] == true):
 *   - genau 0 mit Double-Wurf -> Leg gewonnen (Doppel-Bull zaehlt als Double),
 *   - genau 0 ohne Double -> Bust (Finish nur mit Double),
 *   - genau 1 -> Bust (Rest 1 ist nicht mehr per Double ausspielbar).
 * - Ohne Double-Out: genau 0 mit beliebigem Wurf -> Leg gewonnen.
 * - sonst: regulaerer Wurf, Rest reduziert.
 */
class X01Mode : GameMode<X01State> {

    override val key: String = "X01"
    override val displayName: String = "X01"

    override fun initialState(config: GameConfig): X01State =
        X01State(config.startScore)

    override fun applyDart(
        state: X01State,
        dart: Dart,
        config: GameConfig,
        // X01 wertet rein aus dem eigenen Rest; die Gegner-Zustaende sind fuer
        // diesen Modus irrelevant und werden bewusst ignoriert.
        opponents: List<X01State>,
    ): DartOutcome<X01State> {
        val newRemaining = state.remaining - dart.value
        return when {
            // Ueberworfen -> Bust.
            newRemaining < 0 -> bust(state)

            config.doubleOut -> when {
                // Finish nur mit Double (Doppel-Bull eingeschlossen, da isDouble).
                newRemaining == 0 && dart.isDouble -> legWon(dart.value)
                // Genau 0, aber kein Double -> Bust.
                newRemaining == 0 -> bust(state)
                // Rest 1 ist mit Double-Out nicht mehr ausspielbar -> Bust.
                newRemaining == 1 -> bust(state)
                // Regulaerer Wurf (Rest > 1).
                else -> regular(newRemaining, dart.value)
            }

            // Ohne Double-Out: genau 0 mit beliebigem Wurf gewinnt das Leg.
            newRemaining == 0 -> legWon(dart.value)

            // Regulaerer Wurf (Rest > 0).
            else -> regular(newRemaining, dart.value)
        }
    }

    /** Leg gewonnen: Rest 0, kein Bust, gewerteter Wert == dart.value. */
    private fun legWon(scored: Int): DartOutcome<X01State> =
        DartOutcome(X01State(0), bust = false, legWon = true, scored = scored)

    /** Regulaerer Wurf: neuer Rest, weder Bust noch Leg-Gewinn. */
    private fun regular(newRemaining: Int, scored: Int): DartOutcome<X01State> =
        DartOutcome(X01State(newRemaining), bust = false, legWon = false, scored = scored)

    /** Bust: unveraenderter Eingangszustand (Engine verwirft ohnehin), scored 0. */
    private fun bust(state: X01State): DartOutcome<X01State> =
        DartOutcome(state, bust = true, legWon = false, scored = 0)
}
