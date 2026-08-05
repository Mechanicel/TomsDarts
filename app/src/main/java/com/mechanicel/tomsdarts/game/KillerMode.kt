package com.mechanicel.tomsdarts.game

/**
 * Killer-Spielmodus als konkrete [GameMode]-Strategie.
 *
 * Reine Domaenenlogik, kein Android-/Room-Bezug, mit reinem JUnit testbar. Killer
 * ist der erste Modus mit **Spieler-Identitaet** (jeder Spieler hat eine eigene
 * Zahl), **Fremdwirkung** (Treffer kosten MITSPIELER Leben) und **Eliminierung**
 * (wer keine Leben mehr hat, wirft nicht mehr) - die Vertragsgrundlagen dafuer
 * stammen aus ADR-0031.
 *
 * Regeln pro Dart (v1, siehe [applyDart]):
 * - Jeder Spieler bekommt zu Match-Beginn eine eindeutige Zahl 1..20, abgeleitet
 *   aus dem in der [GameConfig.killerSeed] EINGEFRORENEN Seed und seinem
 *   Sitzplatz ([KillerState.numberFor]). Kein Zufall im Modus selbst - der
 *   Startzustand wird bei Undo-Replay und Leg-Wechsel neu erzeugt und muss
 *   deterministisch dieselbe Zahl liefern.
 * - **Phase 1 (noch kein Killer):** Nur das Double der EIGENEN Zahl wirkt - es
 *   macht den Spieler zum Killer. Single/Triple der eigenen Zahl, fremde Zahlen,
 *   Bull und Miss sind wirkungslos.
 * - **Phase 2 (Killer):** Das Double der Zahl eines noch lebenden MITSPIELERS
 *   kostet diesen ein Leben ([KillerState.hitsOn] wird erhoeht). Das eigene
 *   Double ist als Killer wirkungslos (keine Selbst-Treffer-Variante in v1),
 *   ebenso das Double eines bereits ausgeschiedenen Spielers - sonst wuerde die
 *   Kappung der Lebens-Formel verrutschen.
 * - **Leben (Inversions-Muster):** Die Leben eines Spielers stehen nicht in
 *   seinem Zustand, sondern folgen aus den Treffern der anderen
 *   ([KillerState.livesOf]). [isEliminated] ist genau dann true, wenn davon
 *   nichts mehr uebrig ist.
 * - `scored` ist 1 je WIRKSAMEM Dart (Killer-Werdung oder Lebensabzug), sonst 0 -
 *   analog Around the Clock. Damit ist die Zug-Summe in der Kontroll-Pause die
 *   Anzahl der wirksamen Darts dieser Aufnahme. (Praezisierung gegenueber dem
 *   pauschalen `scored=0` in ADR-0031.)
 * - Sieg (legWon): Sobald nach einem Dart ALLE Mitspieler eliminiert sind,
 *   gewinnt der Werfer das Leg. Kein Bust, kein [DartOutcome.legEnded], kein
 *   Checkout.
 */
class KillerMode : GameMode<KillerState> {

    override val key: String = "KILLER"
    override val displayName: String = "Killer"

    /**
     * Index-loser Startzustand des Interfaces. Killer braucht den Sitzplatz, um
     * die Zielzahl zu bestimmen; diese Variante liefert daher die Zahl des ERSTEN
     * Sitzplatzes. Die Engines rufen stets die indexbewusste Variante
     * [initialState] mit `playerIndex` - Aufrufer, die hier landen (z.B. schnelle
     * Tests), bekommen bewusst fuer alle Spieler dieselbe Zahl.
     */
    override fun initialState(config: GameConfig): KillerState =
        initialState(config, FIRST_PLAYER_INDEX)

    /**
     * Indexbewusster Startzustand: die Zielzahl folgt DETERMINISTISCH aus dem
     * eingefrorenen [GameConfig.killerSeed] und dem [playerIndex]. Kein Zufall -
     * Undo-Replay und Leg-Wechsel erzeugen die LegEngines neu und rufen diese
     * Methode erneut auf.
     */
    override fun initialState(config: GameConfig, playerIndex: Int): KillerState =
        KillerState.initial(seed = config.killerSeed, playerIndex = playerIndex)

    override fun applyDart(
        state: KillerState,
        dart: Dart,
        config: GameConfig,
        opponents: List<KillerState>,
    ): DartOutcome<KillerState> {
        val newState = nextState(state, dart, opponents)
        // Wirksam war der Dart genau dann, wenn er den Zustand veraendert hat
        // (Killer-Werdung oder ein Treffer auf einer Gegner-Zahl).
        val scored = if (newState == state) 0 else EFFECTIVE_DART_SCORE

        // Sicht NACH diesem Dart: fuer jeden Gegner sind dessen Gegner die
        // uebrigen Mitspieler plus der Werfer in seinem NEUEN Zustand.
        val allOpponentsOut = opponents.isNotEmpty() && opponents.indices.all { i ->
            isOpponentEliminated(i, opponents, newState)
        }

        return DartOutcome(
            newState = newState,
            bust = false,
            legWon = allOpponentsOut,
            legEnded = false,
            scored = scored,
        )
    }

    /**
     * Eliminiert, sobald die Treffer der [opponents] auf die eigene Zahl die
     * Leben aufgebraucht haben. Nutzt die EINE Lebens-Formel aus
     * [KillerState.livesOf].
     */
    override fun isEliminated(state: KillerState, opponents: List<KillerState>): Boolean =
        KillerState.livesOf(state.number, opponents) <= 0

    /**
     * Der Zustand des Werfers NACH diesem Dart - oder unveraendert [state], wenn
     * der Dart wirkungslos war (siehe Regeln im Klassen-KDoc).
     */
    private fun nextState(
        state: KillerState,
        dart: Dart,
        opponents: List<KillerState>,
    ): KillerState {
        // Nur Doubles wirken - Single/Triple/Bull/Miss sind immer wirkungslos.
        if (!dart.isDouble) return state

        if (!state.isKiller) {
            // Phase 1: ausschliesslich das Double der EIGENEN Zahl macht zum Killer.
            return if (dart.segment == state.number) state.copy(isKiller = true) else state
        }

        // Phase 2: das eigene Double ist als Killer wirkungslos (keine
        // Selbst-Treffer-Variante in v1).
        if (dart.segment == state.number) return state

        // Getroffen wird nur die Zahl eines noch LEBENDEN Mitspielers; Treffer auf
        // bereits Ausgeschiedene bleiben ungezaehlt, damit die Kappung der
        // Lebens-Formel nicht verrutscht.
        val hitsLivingOpponent = opponents.indices.any { i ->
            opponents[i].number == dart.segment && !isOpponentEliminated(i, opponents, state)
        }
        if (!hitsLivingOpponent) return state

        return state.copy(
            hitsOn = state.hitsOn + (dart.segment to (state.hitsOn[dart.segment] ?: 0) + 1),
        )
    }

    /**
     * True, wenn der Mitspieler an Position [index] der [opponents] eliminiert
     * ist. Aus dessen Sicht sind die Gegner die uebrigen Mitspieler plus der
     * Werfer im Zustand [thrower].
     */
    private fun isOpponentEliminated(
        index: Int,
        opponents: List<KillerState>,
        thrower: KillerState,
    ): Boolean {
        val others = opponents.filterIndexed { i, _ -> i != index } + thrower
        return isEliminated(opponents[index], others)
    }

    private companion object {

        /** Sitzplatz, den der index-lose [initialState]-Fallback annimmt. */
        const val FIRST_PLAYER_INDEX: Int = 0

        /**
         * Gewerteter Punktwert eines WIRKSAMEN Darts. Wie bei Around the Clock
         * zaehlt Killer keine Board-Punkte, sondern die Wirkung: die Zug-Summe ist
         * damit die Anzahl der wirksamen Darts einer Aufnahme.
         */
        const val EFFECTIVE_DART_SCORE: Int = 1
    }
}
