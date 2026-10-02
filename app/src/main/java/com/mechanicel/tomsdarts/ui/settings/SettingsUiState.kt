package com.mechanicel.tomsdarts.ui.settings

import com.mechanicel.tomsdarts.data.settings.AppSettings

/** UI-Zustand des Einstellungs-Bildschirms (ADR-0040). */
sealed interface SettingsUiState {

    /** Einstellungen noch nicht gelesen: leere Flaeche, keine Default-Werte zeigen. */
    data object Loading : SettingsUiState

    /** Gelesene (gespeicherte) Einstellungen; Schalter zeigen genau diese Werte. */
    data class Content(val settings: AppSettings) : SettingsUiState
}
