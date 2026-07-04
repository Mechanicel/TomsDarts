package com.mechanicel.tomsdarts.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel des Spiel-Setup-Bildschirms fuer die Teilnehmerverwaltung. Loest zu
 * den uebergebenen [playerIds] die Anzeigenamen ueber das [PlayerRepository] auf
 * (analog zum [com.mechanicel.tomsdarts.ui.game.GameViewModel]) und haelt die im
 * Setup editierbare, geordnete Teilnehmerliste als [StateFlow].
 *
 * Die Reihenfolge ist fachlich relevant (Starter-Rotation der
 * [com.mechanicel.tomsdarts.game.engine.MatchEngine]); [orderedPlayerIds] gibt
 * die final sortierte/reduzierte Liste fuer den Match-Start zurueck. Die reine
 * Reorder-/Remove-Logik liegt bewusst in den host-testbaren Reducer-Funktionen
 * ([movePlayerUp]/[movePlayerDown]/[removePlayerAt]).
 *
 * Bleibt rein lokal (offline-first, keine Cloud/Backend/Tracking).
 *
 * @param repository Repository fuer Spieler (Namensaufloesung).
 * @param playerIds Teilnehmer in Eingangsreihenfolge (aus der Profilauswahl).
 */
class SetupViewModel(
    private val repository: PlayerRepository,
    private val playerIds: List<Long>,
) : ViewModel() {

    private val _participants = MutableStateFlow<List<SetupPlayer>>(emptyList())

    /** Reaktive, im Setup editierte Teilnehmerliste (geordnet). */
    val participants: StateFlow<List<SetupPlayer>> = _participants.asStateFlow()

    init {
        viewModelScope.launch {
            // Namen in Eingangsreihenfolge aufloesen; unbekannte IDs fallen heraus.
            _participants.value = playerIds.mapNotNull { id ->
                repository.getPlayer(id)?.let { SetupPlayer(id = id, name = it.name) }
            }
        }
    }

    /** Schiebt den Teilnehmer an [index] eine Position nach oben (Nachbar-Swap). */
    fun movePlayerUp(index: Int) {
        _participants.update { it.movePlayerUp(index) }
    }

    /** Schiebt den Teilnehmer an [index] eine Position nach unten (Nachbar-Swap). */
    fun movePlayerDown(index: Int) {
        _participants.update { it.movePlayerDown(index) }
    }

    /**
     * Entfernt den Teilnehmer an [index]; wahrt dabei die
     * [MIN_MATCH_PLAYERS]-Invariante (unter dem Minimum passiert nichts).
     */
    fun removePlayer(index: Int) {
        _participants.update { it.removePlayerAt(index) }
    }

    /** Die aktuell geordnete/reduzierte Teilnehmer-ID-Liste fuer den Match-Start. */
    fun orderedPlayerIds(): List<Long> = _participants.value.map { it.id }

    companion object {
        /**
         * Factory, die das [PlayerRepository] aus dem [AppContainer] der
         * [TomsDartsApp] bezieht und die uebergebenen [playerIds] als
         * Teilnehmer-Ausgangsliste setzt.
         *
         * @param playerIds Teilnehmer in Eingangsreihenfolge (aus der Auswahl).
         */
        fun provideFactory(playerIds: List<Long>): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                SetupViewModel(
                    repository = app.container.playerRepository,
                    playerIds = playerIds,
                )
            }
        }
    }
}
