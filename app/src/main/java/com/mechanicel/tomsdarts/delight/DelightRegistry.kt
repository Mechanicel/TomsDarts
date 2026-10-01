package com.mechanicel.tomsdarts.delight

/**
 * Wertet [triggers] gegen eine abgeschlossene Aufnahme aus und liefert hoechstens
 * EIN Event.
 *
 * Deterministisch: Unter allen Triggern, deren Bedingung zutrifft, gewinnt der
 * mit der hoechsten [DelightTrigger.priority]; bei Gleichstand der zuerst in
 * [triggers] stehende (Registrierungsreihenfolge). Bedingungen werden in
 * Prioritaetsreihenfolge geprueft und nach dem ersten Treffer nicht weiter
 * ausgewertet.
 *
 * @param id Kennung, die das entstehende [DelightEvent] traegt (vergibt der
 *   Aufrufer, siehe [DelightEvent.id]).
 * @return Das Event des gewinnenden Triggers oder `null`, wenn keiner passt.
 */
fun evaluateDelight(
    visit: DelightVisit,
    triggers: List<DelightTrigger>,
    id: Long = 0L,
): DelightEvent? {
    // sortedByDescending ist stabil -> bei Gleichstand bleibt die
    // Registrierungsreihenfolge erhalten.
    return firstMatch(triggers.sortedByDescending { it.priority }, visit, id)
}

/**
 * Liefert das Event des ersten Triggers in [sortedTriggers] (bereits nach
 * Prioritaet absteigend, stabil sortiert), dessen Bedingung zutrifft.
 */
private fun firstMatch(
    sortedTriggers: List<DelightTrigger>,
    visit: DelightVisit,
    id: Long,
): DelightEvent? {
    val winner = sortedTriggers.firstOrNull { it.condition(visit) } ?: return null
    return DelightEvent(
        id = id,
        triggerId = winner.id,
        presentation = winner.presentation,
        visit = visit,
    )
}

/**
 * Feste Sammlung von [DelightTrigger]n, gegen die der Spielablauf jede
 * abgeschlossene Aufnahme auswertet.
 *
 * @param triggers Trigger in Registrierungsreihenfolge (entscheidet bei
 *   Prioritaets-Gleichstand).
 * @throws IllegalArgumentException wenn zwei Trigger dieselbe
 *   [DelightTrigger.id] tragen.
 */
class DelightRegistry(triggers: List<DelightTrigger>) {

    /** Unveraenderliche Kopie der registrierten Trigger. */
    val triggers: List<DelightTrigger> = triggers.toList()

    init {
        val duplicates = this.triggers.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Doppelte Delight-Trigger-IDs: $duplicates" }
    }

    /**
     * Einmal bei der Konstruktion nach Prioritaet absteigend vorsortiert (stabil,
     * Gleichstand in Registrierungsreihenfolge), damit nicht jede Aufnahme neu
     * sortiert.
     */
    private val sortedTriggers: List<DelightTrigger> =
        this.triggers.sortedByDescending { it.priority }

    /** Siehe [evaluateDelight]; gleiche Semantik, nutzt die vorsortierte Liste. */
    fun evaluate(visit: DelightVisit, id: Long = 0L): DelightEvent? =
        firstMatch(sortedTriggers, visit, id)

    companion object {
        /** Registry ohne Trigger (loest nie aus). */
        val EMPTY: DelightRegistry = DelightRegistry(emptyList())

        /** Produkt-Registry mit allen [ProductDelightTriggers.ALL]. */
        val DEFAULT: DelightRegistry = DelightRegistry(ProductDelightTriggers.ALL)
    }
}

/**
 * Die Produkt-Trigger der App (ADR-0006). Ein neuer Trigger braucht genau einen
 * zusaetzlichen Eintrag in [ALL].
 *
 * Vorerst bewusst leer: die konkreten Trigger (180, Waschmaschine,
 * Rentnerdreieck, ...) folgen als eigene Roadmap-Punkte.
 */
object ProductDelightTriggers {

    /** Alle Produkt-Trigger in Registrierungsreihenfolge. */
    val ALL: List<DelightTrigger> = emptyList()
}
