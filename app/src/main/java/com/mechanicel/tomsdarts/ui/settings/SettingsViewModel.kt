package com.mechanicel.tomsdarts.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel des Einstellungs-Bildschirms (ADR-0040). Leitet den
 * [SettingsUiState] aus [SettingsRepository.settings] ab und schreibt
 * Aenderungen zurueck.
 *
 * Kein optimistischer Zwischenstand: die Schalter zeigen immer den gespeicherten
 * Wert. Ein Schreibfehler setzt [saveError] (Snackbar), der Wert bleibt alt.
 *
 * Bleibt rein lokal (offline, keine Cloud/Tracking).
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    /** Reaktiver UI-Zustand, abgeleitet aus dem Einstellungs-Stream. */
    val uiState: StateFlow<SettingsUiState> = repository.settings
        .map<_, SettingsUiState> { SettingsUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState.Loading,
        )

    private val _saveError = MutableStateFlow(false)

    /** `true`, solange ein Schreibfehler noch nicht per Snackbar gemeldet wurde. */
    val saveError: StateFlow<Boolean> = _saveError.asStateFlow()

    /** Schaltet die Feier-Animationen ein bzw. aus (persistiert lokal). */
    fun setDelightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setDelightEnabled(enabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _saveError.value = true
            }
        }
    }

    /** Quittiert die angezeigte Fehlermeldung zum Schreibfehler. */
    fun onSaveErrorShown() {
        _saveError.value = false
    }

    companion object {
        /**
         * Factory, die das [SettingsRepository] aus dem [AppContainer] der
         * [TomsDartsApp] bezieht.
         */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                SettingsViewModel(app.container.settingsRepository)
            }
        }
    }
}
