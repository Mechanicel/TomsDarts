package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.ui.input.DartInputState

/**
 * Modus-spezifischer Anzeige-Kern EINER Spieler-Karte im Scoreboard.
 *
 * Kapselt genau die Werte, die je Spielmodus unterschiedlich dargestellt werden
 * (bei X01 die Restpunktzahl). Dadurch bleibt [PlayerScoreUi] modus-agnostisch:
 * modus-uebergreifende Felder (Name, gewonnene Legs/Sets) liegen dort, der
 * variable Teil hier. Ein weiterer Modus (z.B. Cricket) ergaenzt rein additiv
 * eine neue Unterart. Reines UI-Value-Object (offline-first).
 */
sealed interface PlayerBoardUi {

    /**
     * X01-Anzeige: die verbleibende Restpunktzahl im laufenden Leg.
     *
     * @param remaining Aktuell verbleibende Restpunktzahl.
     */
    data class X01(val remaining: Int) : PlayerBoardUi

    /**
     * Cricket-Anzeige: die Marks je Feld plus der erzielte Punktestand.
     *
     * @param fields Genau 7 Felder in fester Anzeigereihenfolge
     *   (20,19,18,17,16,15,Bull). Jedes Feld traegt seinen Anzeige-Markwert 0..3.
     * @param points Erzielter Punktestand des Spielers im laufenden Leg.
     */
    data class Cricket(val fields: List<CricketFieldUi>, val points: Int) : PlayerBoardUi
}

/**
 * Ein einzelnes Cricket-Feld einer Spieler-Karte als reines UI-Value-Object.
 *
 * @param target Feldkennung (15..20 bzw. 25 == Bull), zugleich der Feldwert.
 * @param marks Anzeige-Markwert 0..3 (3 == geschlossen).
 */
data class CricketFieldUi(val target: Int, val marks: Int)

/**
 * Anzeige-Zeile EINES Spielers im Mehrspieler-Scoreboard.
 *
 * Reines UI-Value-Object, vom [GameViewModel] aus dem Match-Snapshot und der
 * Namens-Zuordnung gebaut. Bleibt rein lokal (offline-first).
 *
 * @param playerId Stabile Spieler-Kennung.
 * @param name Anzeigename des Spielers.
 * @param board Modus-spezifischer Anzeige-Kern (bei X01 die Restpunktzahl).
 * @param legsWon Im aktuellen Set gewonnene Legs.
 * @param setsWon Im Match gewonnene Sets.
 * @param isCurrent True, wenn dieser Spieler aktuell am Zug ist (Hervorhebung).
 * @param lastTurnDarts Tatsaechlich geworfene Darts der zuletzt abgeschlossenen
 *   Aufnahme DIESES Spielers im laufenden Leg (bis zu 3). Leer bedeutet: dieser
 *   Spieler hat im laufenden Leg noch keine Aufnahme beendet; die Karte zeigt
 *   dann einen dezenten Platzhalter.
 * @param lastTurnBust True, wenn die zuletzt abgeschlossene Aufnahme dieses
 *   Spielers ([lastTurnDarts]) ein Bust war.
 */
data class PlayerScoreUi(
    val playerId: Long,
    val name: String,
    val board: PlayerBoardUi,
    val legsWon: Int,
    val setsWon: Int,
    val isCurrent: Boolean,
    val lastTurnDarts: List<Dart> = emptyList(),
    val lastTurnBust: Boolean = false,
)

/**
 * UI-Zustand des Spiel-Bildschirms (Mehrspieler, X01, Legs/Sets). Wird vom
 * [GameViewModel] als StateFlow bereitgestellt und von der Compose-Screen-UI
 * gerendert.
 *
 * Bleibt rein lokal (offline-first, keine Cloud/Backend/Tracking).
 */
sealed interface GameUiState {

    /** Initialer Ladezustand, bevor Match/Leg angelegt sind. */
    data object Loading : GameUiState

    /** Das Anlegen von Match/Leg ist fehlgeschlagen. */
    data object Error : GameUiState

    /** Es konnten weniger als zwei gueltige Spieler aufgeloest werden. */
    data object NoPlayer : GameUiState

    /**
     * Aktives Leg: laufende Eingabe des aktuellen Werfers gegen die Spiel-Logik.
     *
     * @param players Stand aller Spieler (Reihenfolge = Sitzreihenfolge); der
     *   aktuelle Werfer ist ueber [PlayerScoreUi.isCurrent] markiert.
     * @param startScore Startpunktzahl des Modus (z.B. 501).
     * @param input Gehoisteter Eingabe-Zustand des Ziffernblocks (nur aktueller Werfer).
     * @param currentLegNumber 1-basierte Nummer des laufenden Legs im aktuellen Set.
     * @param currentSetNumber 1-basierte Nummer des laufenden Sets.
     * @param legsToWin Anzahl Legs fuer einen Set-Gewinn (Best-of-N: N = 2*legsToWin-1).
     * @param setsToWin Anzahl Sets fuer den Match-Gewinn.
     * @param checkout Empfohlene Checkout-Kombination (1-3 Darts) fuer den aktuellen
     *   Werfer bei einem per Double-Out auscheckbaren Rest, sonst `null` (kein
     *   Vorschlag). Gilt genau fuer den aktuellen Werfer, nicht pro [PlayerScoreUi].
     * @param canUndo True, solange im laufenden Leg mindestens ein Dart
     *   zurueckgenommen werden kann. Undo spult unbegrenzt innerhalb des Legs
     *   zurueck - auch ueber Aufnahme- und Spielerwechsel-Grenzen -, aber nicht
     *   ueber Leg-/Set-Grenzen. Zu Leg-Beginn `false`, sonst i.d.R. `true`.
     */
    data class Playing(
        val players: List<PlayerScoreUi>,
        val startScore: Int,
        val input: DartInputState,
        val currentLegNumber: Int,
        val currentSetNumber: Int,
        val legsToWin: Int,
        val setsToWin: Int,
        val checkout: List<Dart>? = null,
        val canUndo: Boolean = false,
    ) : GameUiState

    /**
     * Leg entschieden, das Match laeuft weiter.
     *
     * @param players Stand aller Spieler nach dem Leg-Gewinn.
     * @param legWinnerName Anzeigename des Leg-Gewinners.
     * @param nextStarterName Anzeigename des Spielers, der das naechste Leg beginnt.
     * @param nextLegNumber Nummer des naechsten Legs im aktuellen Set.
     * @param dartsUsed Anzahl der vom Gewinner im Gewinn-Leg geworfenen Darts.
     */
    data class LegWon(
        val players: List<PlayerScoreUi>,
        val legWinnerName: String,
        val nextStarterName: String,
        val nextLegNumber: Int,
        val dartsUsed: Int?,
    ) : GameUiState

    /**
     * Match entschieden.
     *
     * @param players Endstand aller Spieler.
     * @param matchWinnerName Anzeigename des Match-Gewinners.
     * @param dartsUsed Anzahl der vom Gewinner im letzten Leg geworfenen Darts.
     */
    data class MatchWon(
        val players: List<PlayerScoreUi>,
        val matchWinnerName: String,
        val dartsUsed: Int?,
    ) : GameUiState
}

/**
 * Bündelt die Callbacks des Spiel-Bildschirms fuer die Compose-Screen-UI.
 * Alle mit No-op-Defaults, damit Previews/Tests sie selektiv setzen koennen.
 *
 * @param onNumber Eingabe einer Zahl-Taste (Segment 1..20).
 * @param onBull Eingabe der Bull-Taste.
 * @param onOut Eingabe eines Fehlwurfs (Miss/Out).
 * @param onToggleDouble Umschalten des DOUBLE-Modus.
 * @param onToggleTriple Umschalten des TRIPLE-Modus.
 * @param onUndo Zuruecknehmen des zuletzt gesetzten Darts.
 * @param onNewLeg Start des naechsten Legs (aus dem [GameUiState.LegWon]-Zustand).
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
