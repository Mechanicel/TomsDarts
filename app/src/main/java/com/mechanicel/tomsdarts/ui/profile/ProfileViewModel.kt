package com.mechanicel.tomsdarts.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel der Profilverwaltung. Leitet den [ProfileUiState] reaktiv aus
 * [PlayerRepository.observePlayers] ab und kapselt die Spieler-Aktionen sowie
 * den aktuell sichtbaren [ProfileDialog].
 *
 * Bleibt rein lokal (offline-first, keine Cloud/Backend/Tracking).
 */
class ProfileViewModel(
    private val repository: PlayerRepository,
) : ViewModel() {

    /**
     * Trigger, der die Beobachtung von [PlayerRepository.observePlayers] neu
     * startet. Jede Erhoehung (siehe [retry]) laesst [uiState] den Stream
     * erneut sammeln und so aus einem [ProfileUiState.Error] herausfinden.
     */
    private val retryTrigger = MutableStateFlow(0)

    /** Reaktiver UI-Zustand, abgeleitet aus dem Spieler-Stream. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> = retryTrigger
        .flatMapLatest { repository.observePlayers() }
        .map { players ->
            if (players.isEmpty()) {
                ProfileUiState.Empty
            } else {
                ProfileUiState.Content(players)
            }
        }
        .catch { throwable -> emit(ProfileUiState.Error(throwable.message)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState.Loading,
        )

    /** Startet die Beobachtung des Spieler-Streams neu (z.B. nach einem Fehler). */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    private val _dialog = MutableStateFlow<ProfileDialog>(ProfileDialog.None)

    /** Aktuell sichtbarer Dialog. */
    val dialog: StateFlow<ProfileDialog> = _dialog.asStateFlow()

    /** Oeffnet den Dialog zum Anlegen eines neuen Spielers. */
    fun onAddClick() {
        _dialog.value = ProfileDialog.Add
    }

    /** Oeffnet den Dialog zum Bearbeiten von [player]. */
    fun onEditClick(player: Player) {
        _dialog.value = ProfileDialog.Edit(player)
    }

    /** Oeffnet den Bestaetigungsdialog zum Loeschen von [player]. */
    fun onDeleteClick(player: Player) {
        _dialog.value = ProfileDialog.ConfirmDelete(player)
    }

    /** Schliesst den aktuell sichtbaren Dialog. */
    fun onDismissDialog() {
        _dialog.value = ProfileDialog.None
    }

    /**
     * Legt einen Spieler mit dem getrimmten [name] an. Leere bzw. reine
     * Whitespace-Namen werden defensiv ignoriert. Schliesst danach den Dialog.
     */
    fun addPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onDismissDialog()
            return
        }
        viewModelScope.launch {
            repository.addPlayer(trimmed)
            onDismissDialog()
        }
    }

    /**
     * Aktualisiert [player] mit getrimmtem Namen. Leere bzw. reine
     * Whitespace-Namen werden defensiv ignoriert. Schliesst danach den Dialog.
     */
    fun updatePlayer(player: Player) {
        val trimmed = player.name.trim()
        if (trimmed.isEmpty()) {
            onDismissDialog()
            return
        }
        viewModelScope.launch {
            repository.updatePlayer(player.copy(name = trimmed))
            onDismissDialog()
        }
    }

    /** Loescht [player] und schliesst danach den Dialog. */
    fun deletePlayer(player: Player) {
        viewModelScope.launch {
            repository.deletePlayer(player)
            onDismissDialog()
        }
    }

    companion object {
        /**
         * Factory, die das [PlayerRepository] aus dem [AppContainer] der
         * [TomsDartsApp] bezieht.
         */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                ProfileViewModel(app.container.playerRepository)
            }
        }
    }
}
