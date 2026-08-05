package com.mechanicel.tomsdarts.testing

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.DartOutcome
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode

/**
 * Zustand des [EliminationFakeMode] fuer EINEN Spieler.
 *
 * Kern des Fakes ist das **Inversions-Muster**: Der eigene Lebensstand steht
 * NICHT im eigenen Zustand, sondern ergibt sich aus den Treffern der MITSPIELER
 * auf die eigene [target]-Zahl. Genau dadurch braucht sowohl die Eliminierungs-
 * Frage ([GameMode.isEliminated]) als auch die Anzeige
 * ([com.mechanicel.tomsdarts.ui.game.ModeUiAdapter.board] mit Gegnern) die
 * Gegner-Zustaende - der Vertragsbeweis fuer die Killer-Infrastruktur.
 *
 * @param target Eigene Zielzahl (Identitaet des Spielers), abgeleitet aus dem
 *   Sitzplatz: `playerIndex + 1`. Beweist, dass der Spieler-Index bis in den
 *   Startzustand durchgereicht wird.
 * @param hitsOn Treffer DIESES Spielers je Zielzahl (Segment -> Anzahl). Eine
 *   Erhoehung auf die Zielzahl eines Gegners kostet diesen ein Leben.
 */
data class EliminationState(
    val target: Int,
    val hitsOn: Map<Int, Int> = emptyMap(),
)

/**
 * Eliminierungs-Fake-Modus AUSSCHLIESSLICH fuer Tests (kein Produktions-Modus,
 * kein Katalog-Eintrag): jeder Spieler besitzt eine eigene Zielzahl
 * ([EliminationState.target] == Sitzplatz + 1) und [lives] Leben. Trifft ein
 * Spieler das Segment der Zielzahl eines Gegners, verliert dieser ein Leben; bei
 * 0 Leben wirft er nicht mehr ([isEliminated]) und die Engine ueberspringt ihn
 * bei der Aufnahme-Rotation. Sind ALLE Gegner eliminiert, gewinnt der Werfer das
 * Leg ([DartOutcome.legWon]).
 *
 * Der Fake ist der Vertrags-Beweis fuer die Modus-Infrastruktur "Spieler-
 * Identitaet + Eliminierung" (Vorarbeit fuer Killer):
 * - er nutzt die indexbewusste Variante [GameMode.initialState] mit `playerIndex`
 *   (deterministisch, ohne jede Zufaelligkeit - Voraussetzung fuers Undo-Replay),
 * - er implementiert [GameMode.isEliminated] als reine Funktion der Zustaende,
 * - er leitet Leben ausschliesslich aus GEGNER-Zustaenden ab (Inversions-Muster)
 *   und belegt damit den Cross-Player-Bedarf beider neuen Vertragsstellen.
 *
 * Bewusst NICHT abgebildet (Killer selbst bringt das in PR B mit): Selbst-
 * Eliminierung durch Treffer auf die eigene Zahl, Doppel-Pflicht, Killer-Status.
 * Ein Treffer auf die EIGENE Zielzahl bleibt hier folgenlos, weil nur Treffer der
 * Mitspieler in die Lebensrechnung eingehen.
 *
 * @param lives Leben je Spieler zu Leg-Beginn. Standard 1: ein Treffer
 *   eliminiert - kompakte Tests. Groessere Werte pruefen die Ableitung ueber
 *   mehrere Treffer.
 */
class EliminationFakeMode(
    private val lives: Int = DEFAULT_LIVES,
) : GameMode<EliminationState> {

    override val key: String = "FAKE_ELIMINATION"

    override val displayName: String = "Eliminierung (Fake)"

    /**
     * Index-loser Fallback des Interfaces: alle Spieler bekaemen dieselbe
     * Zielzahl. Bewusst so belassen - die Engines rufen stets die indexbewusste
     * Variante, sodass ein Test, der hier landet, die fehlende Identitaet sofort
     * an gleichen Zielzahlen erkennt.
     */
    override fun initialState(config: GameConfig): EliminationState =
        EliminationState(target = FIRST_TARGET)

    /**
     * Indexbewusster Startzustand: die Zielzahl folgt DETERMINISTISCH aus dem
     * Sitzplatz (`playerIndex + 1`). Kein Zufall - der Undo-Replay und jeder
     * Leg-Wechsel erzeugen die LegEngines neu und rufen diese Methode erneut auf.
     */
    override fun initialState(config: GameConfig, playerIndex: Int): EliminationState =
        EliminationState(target = playerIndex + FIRST_TARGET)

    /**
     * Verbucht den Treffer auf dem Segment des Darts (Miss zaehlt nicht) und
     * meldet [DartOutcome.legWon], sobald ALLE Gegner eliminiert sind. Bustet nie
     * und meldet nie [DartOutcome.legEnded].
     */
    override fun applyDart(
        state: EliminationState,
        dart: Dart,
        config: GameConfig,
        opponents: List<EliminationState>,
    ): DartOutcome<EliminationState> {
        val newState = if (dart.segment == MISS_SEGMENT) {
            state
        } else {
            state.copy(
                hitsOn = state.hitsOn + (dart.segment to (state.hitsOn[dart.segment] ?: 0) + 1),
            )
        }
        // Sicht NACH diesem Dart: fuer jeden Gegner sind dessen Gegner die
        // uebrigen Mitspieler plus der Werfer in seinem NEUEN Zustand.
        val allOpponentsOut = opponents.isNotEmpty() && opponents.indices.all { i ->
            val others = opponents.filterIndexed { j, _ -> j != i } + newState
            isEliminated(opponents[i], others)
        }
        return DartOutcome(
            newState = newState,
            bust = false,
            legWon = allOpponentsOut,
            legEnded = false,
            scored = dart.value,
        )
    }

    /** Eliminiert, sobald die Gegner-Treffer die Leben aufgebraucht haben. */
    override fun isEliminated(
        state: EliminationState,
        opponents: List<EliminationState>,
    ): Boolean = livesOf(state, opponents) <= 0

    /**
     * Verbleibende Leben des Spielers [state]: Startleben abzueglich der Treffer
     * ALLER [opponents] auf seine Zielzahl (nie negativ). Reine Ableitung aus den
     * Zustaenden - dieselbe Rechnung nutzen [isEliminated] und der Test-UI-Adapter.
     */
    fun livesOf(state: EliminationState, opponents: List<EliminationState>): Int =
        (lives - opponents.sumOf { it.hitsOn[state.target] ?: 0 }).coerceAtLeast(0)

    companion object {
        /** Standard-Leben je Spieler: ein Treffer eliminiert. */
        const val DEFAULT_LIVES: Int = 1

        /** Zielzahl des ersten Sitzplatzes (Index 0). */
        const val FIRST_TARGET: Int = 1

        /** Segment eines Fehlwurfs (trifft keine Zielzahl). */
        private const val MISS_SEGMENT: Int = 0
    }
}
