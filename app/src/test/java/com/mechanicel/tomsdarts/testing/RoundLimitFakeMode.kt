package com.mechanicel.tomsdarts.testing

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.DartOutcome
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode

/**
 * Zustand des [RoundLimitFakeMode] fuer EINEN Spieler.
 *
 * @param darts Anzahl der in diesem Leg bereits geworfenen Darts.
 * @param points Summe der gewerteten Punkte dieses Spielers im Leg.
 */
data class RoundLimitState(val darts: Int = 0, val points: Int = 0)

/**
 * Rundenbasierter Fake-Modus AUSSCHLIESSLICH fuer Tests (kein Produktions-Modus,
 * kein Katalog-Eintrag): jeder Spieler hat ein festes Dart-Kontingent
 * ([dartLimit]); ist es bei ALLEN Spielern aufgebraucht, ist das Leg entschieden
 * und der hoechste Punktestand gewinnt.
 *
 * Der Fake ist der Vertrags-Beweis fuer die Erweiterung "Leg-Ende ohne
 * Werfer-Sieg": er meldet nie [DartOutcome.legWon], sondern
 * [DartOutcome.legEnded] und ueberlaesst die Gewinner-Ermittlung der Engine
 * ueber [legScore]. Damit ist der Gewinner regelmaessig NICHT der Spieler, der
 * den letzten Dart geworfen hat - genau der Fall, den ein spaeterer
 * rundenbasierter Modus (z.B. Shanghai) braucht.
 *
 * Genutzt vom Vertrags-Test des Interfaces sowie von den Engine- und
 * ViewModel-Tests, damit es nur EINE Referenz-Implementierung des erweiterten
 * Vertrags gibt.
 *
 * @param dartLimit Dart-Kontingent je Spieler. Bewusst frei waehlbar: ein Limit,
 *   das kein Vielfaches von 3 ist, laesst das Leg mitten in einer Aufnahme enden
 *   (und die Spieler mit unterschiedlich vielen Darts dastehen) - hilfreich, um
 *   Werfer und Gewinner in Tests sauber zu unterscheiden.
 */
class RoundLimitFakeMode(
    private val dartLimit: Int = DEFAULT_DART_LIMIT,
) : GameMode<RoundLimitState> {

    override val key: String = "FAKE_ROUND_LIMIT"

    override val displayName: String = "Rundenlimit (Fake)"

    override fun initialState(config: GameConfig): RoundLimitState = RoundLimitState()

    /**
     * Zaehlt Dart und Punkte hoch und meldet [DartOutcome.legEnded], sobald
     * dieser Spieler UND alle Gegner ihr Kontingent aufgebraucht haben. Bustet
     * nie und gewinnt nie direkt ([DartOutcome.legWon] bleibt immer `false`).
     */
    override fun applyDart(
        state: RoundLimitState,
        dart: Dart,
        config: GameConfig,
        opponents: List<RoundLimitState>,
    ): DartOutcome<RoundLimitState> {
        val newState = RoundLimitState(
            darts = state.darts + 1,
            points = state.points + dart.value,
        )
        val allDone = newState.darts >= dartLimit && opponents.all { it.darts >= dartLimit }
        return DartOutcome(
            newState = newState,
            bust = false,
            legWon = false,
            legEnded = allDone,
            scored = dart.value,
        )
    }

    /** Rangwert fuer den Gewinner-Vergleich: der gesammelte Punktestand. */
    override fun legScore(state: RoundLimitState): Int = state.points

    companion object {
        /** Standard-Kontingent: 2 Darts je Spieler (endet mitten in der Aufnahme). */
        const val DEFAULT_DART_LIMIT: Int = 2
    }
}
