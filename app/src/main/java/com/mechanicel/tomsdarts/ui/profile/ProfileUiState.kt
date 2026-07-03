package com.mechanicel.tomsdarts.ui.profile

import com.mechanicel.tomsdarts.data.entity.Player

/**
 * UI-Zustand der Profilverwaltung. Wird aus dem reaktiven Spieler-Stream
 * abgeleitet und vom [ProfileViewModel] als StateFlow bereitgestellt.
 */
sealed interface ProfileUiState {

    /** Initialer Ladezustand, bevor die erste Emission vorliegt. */
    data object Loading : ProfileUiState

    /** Es sind keine Spieler vorhanden. */
    data object Empty : ProfileUiState

    /** Es liegen Spieler vor (nach Name sortiert). */
    data class Content(val players: List<Player>) : ProfileUiState

    /** Der Spieler-Stream ist fehlgeschlagen. */
    data class Error(val message: String? = null) : ProfileUiState
}

/**
 * Zustand des Auswahlmodus (Match-Start). Separater StateFlow neben dem
 * [ProfileUiState], damit der Spieler-Stream unberuehrt bleibt.
 *
 * @param active True, wenn der Auswahlmodus aktiv ist (Zeilen-Taps selektieren,
 *   das Overflow-Menue ist ausgeblendet).
 * @param selectedIds Markierte Spieler in Markierungs-Reihenfolge (= spaetere
 *   Sitzreihenfolge im Match). Mindestens 2 fuer einen Match-Start.
 */
data class ProfileSelectionState(
    val active: Boolean = false,
    val selectedIds: List<Long> = emptyList(),
)

/**
 * Aktuell sichtbarer Dialog der Profilverwaltung. Steuert Hinzufuegen,
 * Bearbeiten und das Bestaetigen des Loeschens.
 */
sealed interface ProfileDialog {

    /** Kein Dialog sichtbar. */
    data object None : ProfileDialog

    /** Dialog zum Anlegen eines neuen Spielers. */
    data object Add : ProfileDialog

    /** Dialog zum Bearbeiten des angegebenen Spielers. */
    data class Edit(val player: Player) : ProfileDialog

    /** Bestaetigungsdialog vor dem Loeschen des angegebenen Spielers. */
    data class ConfirmDelete(val player: Player) : ProfileDialog
}
