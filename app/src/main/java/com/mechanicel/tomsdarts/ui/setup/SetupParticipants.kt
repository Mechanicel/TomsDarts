package com.mechanicel.tomsdarts.ui.setup

/**
 * Mindestanzahl Teilnehmer fuer einen Match-Start. Geteilte Konstante fuer die
 * Profilauswahl ([com.mechanicel.tomsdarts.ui.profile.ProfileScreen]) und die
 * Teilnehmerverwaltung im Setup - bewusst nur einmal deklariert, damit die
 * Regel nicht dupliziert wird.
 */
const val MIN_MATCH_PLAYERS: Int = 2

/**
 * UI-Modell eines Teilnehmers im Spiel-Setup: die Spieler-ID zusammen mit dem
 * aufgeloesten Anzeigenamen. Die Reihenfolge in der Liste ist fachlich relevant
 * (Starter-Rotation der [com.mechanicel.tomsdarts.game.engine.MatchEngine]).
 *
 * @param id Spieler-ID (Domaenenschluessel des Teilnehmers; nicht mehr der
 *   Compose-`key` der Zeile - die Zeilen sind positions-stabil gerendert).
 * @param name Anzeigename des Spielers.
 */
data class SetupPlayer(val id: Long, val name: String)

/**
 * Tauscht den Teilnehmer an [index] mit seinem Vorgaenger (Nachbar-Swap nach
 * oben). Ist [index] das erste Element oder ausserhalb der Liste, bleibt die
 * Liste unveraendert (defensiv, kein Wurf). Reine Funktion - host-testbar ohne
 * Android-/Compose-Laufzeit.
 */
fun List<SetupPlayer>.movePlayerUp(index: Int): List<SetupPlayer> {
    if (index <= 0 || index >= size) return this
    return toMutableList().apply {
        val tmp = this[index - 1]
        this[index - 1] = this[index]
        this[index] = tmp
    }
}

/**
 * Tauscht den Teilnehmer an [index] mit seinem Nachfolger (Nachbar-Swap nach
 * unten). Ist [index] das letzte Element oder ausserhalb der Liste, bleibt die
 * Liste unveraendert. Reine Funktion.
 */
fun List<SetupPlayer>.movePlayerDown(index: Int): List<SetupPlayer> {
    if (index < 0 || index >= size - 1) return this
    return toMutableList().apply {
        val tmp = this[index + 1]
        this[index + 1] = this[index]
        this[index] = tmp
    }
}

/**
 * Entfernt den Teilnehmer an [index]. Die [MIN_MATCH_PLAYERS]-Invariante wird
 * gewahrt: Sinkt die Groesse dadurch unter das Minimum, bleibt die Liste
 * unveraendert. Ein [index] ausserhalb der Liste laesst sie ebenfalls
 * unveraendert. Reine Funktion.
 */
fun List<SetupPlayer>.removePlayerAt(index: Int): List<SetupPlayer> {
    if (index < 0 || index >= size) return this
    if (size <= MIN_MATCH_PLAYERS) return this
    return toMutableList().apply { removeAt(index) }
}
