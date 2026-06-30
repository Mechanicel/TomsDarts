package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.ui.input.DartInputState

/**
 * UI-Zustand des Spiel-Bildschirms (Einzelspieler, ein Leg). Wird vom
 * [GameViewModel] als StateFlow bereitgestellt und von der Compose-Screen-UI
 * (Folgeschritt B2) gerendert.
 *
 * Bleibt rein lokal (offline-first, keine Cloud/Backend/Tracking).
 */
sealed interface GameUiState {

    /** Initialer Ladezustand, bevor Match/Leg angelegt sind. */
    data object Loading : GameUiState

    /** Das Anlegen von Match/Leg ist fehlgeschlagen. */
    data object Error : GameUiState

    /** Der angeforderte Spieler existiert nicht. */
    data object NoPlayer : GameUiState

    /**
     * Aktives Leg: laufende Eingabe gegen die Spiel-Logik.
     *
     * @param playerName Anzeigename des werfenden Spielers.
     * @param startScore Startpunktzahl des Modus (z.B. 501).
     * @param remaining Aktuell verbleibende Restpunktzahl.
     * @param input Gehoisteter Eingabe-Zustand des Ziffernblocks.
     */
    data class Playing(
        val playerName: String,
        val startScore: Int,
        val remaining: Int,
        val input: DartInputState,
    ) : GameUiState

    /**
     * Leg gewonnen (Checkout erreicht), Match abgeschlossen.
     *
     * @param playerName Anzeigename des Gewinners.
     * @param dartsUsed Anzahl der im Gewinn-Leg geworfenen Darts, falls bekannt.
     */
    data class Won(
        val playerName: String,
        val dartsUsed: Int?,
    ) : GameUiState
}

/**
 * Bündelt die Callbacks des Spiel-Bildschirms fuer die Compose-Screen-UI
 * (Folgeschritt B2). Alle mit No-op-Defaults, damit Previews/Tests sie selektiv
 * setzen koennen.
 *
 * @param onNumber Eingabe einer Zahl-Taste (Segment 1..20).
 * @param onBull Eingabe der Bull-Taste.
 * @param onOut Eingabe eines Fehlwurfs (Miss/Out).
 * @param onToggleDouble Umschalten des DOUBLE-Modus.
 * @param onToggleTriple Umschalten des TRIPLE-Modus.
 * @param onUndo Zuruecknehmen des zuletzt gesetzten Darts.
 * @param onNewLeg Start eines neuen Legs (aus dem [GameUiState.Won]-Zustand).
 * @param onExit Verlassen des Spiel-Bildschirms.
 */
data class GameScreenCallbacks(
    val onNumber: (Int) -> Unit = {},
    val onBull: () -> Unit = {},
    val onOut: () -> Unit = {},
    val onToggleDouble: () -> Unit = {},
    val onToggleTriple: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onNewLeg: () -> Unit = {},
    val onExit: () -> Unit = {},
)
