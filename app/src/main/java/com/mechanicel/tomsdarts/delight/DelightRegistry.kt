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
 * Die Produkt-Trigger der App (ADR-0006, ADR-0041). Ein neuer Trigger braucht
 * genau einen zusaetzlichen Eintrag in [ALL].
 *
 * Gemeinsame Regel der Muster-Trigger (180, Waschmaschine, Rentnerdreieck): nur
 * vollstaendige Aufnahmen mit genau drei Darts und ohne Bust, in ALLEN Modi
 * (bewertet werden die physischen Wuerfe [DelightVisit.darts], nicht die
 * Modus-Wertung). Ein Fehlwurf (Segment 0) liegt in keinem Muster.
 *
 * Prioritaetsschema (hoeher gewinnt, siehe ADR-0041): [PRIORITY_MAX_SCORE] fuer
 * Hoechstleistungen (180), [PRIORITY_PATTERN] fuer Zahlenmuster
 * (Waschmaschine, Rentnerdreieck; schliessen sich gegenseitig aus), Werte
 * unter [PRIORITY_PATTERN] fuer kuenftige generische Kleinigkeiten (Ton ab 100,
 * Bull). Seltene Sonderfaelle (z.B. Madhouse) koennen zwischen den Stufen
 * einsortiert werden.
 */
object ProductDelightTriggers {

    /** Prioritaet fuer Hoechstleistungen (180). */
    const val PRIORITY_MAX_SCORE: Int = 100

    /** Prioritaet fuer Zahlenmuster (Waschmaschine, Rentnerdreieck). */
    const val PRIORITY_PATTERN: Int = 50

    /** Trigger-ID der 180. */
    const val ID_ONE_EIGHTY: String = "180"

    /** Trigger-ID der Waschmaschine. */
    const val ID_WASHING_MACHINE: String = "washing_machine"

    /** Trigger-ID des Rentnerdreiecks. */
    const val ID_RENTNERDREIECK: String = "rentnerdreieck"

    /** Segmente der Waschmaschine (20 und ihre Nachbarn 5 und 1). */
    private val WASHING_MACHINE_SEGMENTS: Set<Int> = setOf(20, 5, 1)

    /** Segmente des Rentnerdreiecks (19 und ihre Nachbarn 7 und 3). */
    private val RENTNERDREIECK_SEGMENTS: Set<Int> = setOf(19, 7, 3)

    /** Vollstaendige Aufnahme: genau drei Darts, kein Bust. */
    private fun isCompleteVisit(visit: DelightVisit): Boolean =
        !visit.bust && visit.darts.size == 3

    /**
     * Alle drei Darts auf Segmenten aus [segments] (beliebiger Multiplier), aber
     * nicht alle drei auf [excludedAllOn] (sonst waere es schlicht "drei Mal
     * dieselbe Zahl", kein Muster).
     */
    private fun isSegmentPattern(visit: DelightVisit, segments: Set<Int>, excludedAllOn: Int): Boolean =
        isCompleteVisit(visit) &&
            visit.darts.all { it.segment in segments } &&
            !visit.darts.all { it.segment == excludedAllOn }

    /** 180: drei Mal Triple 20 in einer vollstaendigen Aufnahme ohne Bust. */
    fun isOneEighty(visit: DelightVisit): Boolean =
        isCompleteVisit(visit) && visit.darts.all { it.segment == 20 && it.multiplier == 3 }

    /** Waschmaschine: alle drei Darts in {20, 5, 1}, nicht alle auf 20 (ADR-0006). */
    fun isWashingMachine(visit: DelightVisit): Boolean =
        isSegmentPattern(visit, WASHING_MACHINE_SEGMENTS, excludedAllOn = 20)

    /** Rentnerdreieck: alle drei Darts in {19, 7, 3}, nicht alle auf 19 (ADR-0041). */
    fun isRentnerdreieck(visit: DelightVisit): Boolean =
        isSegmentPattern(visit, RENTNERDREIECK_SEGMENTS, excludedAllOn = 19)

    /** 180 mit Konfetti. */
    val ONE_EIGHTY: DelightTrigger = DelightTrigger(
        id = ID_ONE_EIGHTY,
        priority = PRIORITY_MAX_SCORE,
        condition = ::isOneEighty,
        presentation = DelightPresentation(DelightAnimation.CONFETTI, DelightTextKeys.ONE_EIGHTY),
    )

    /** Waschmaschine mit Dreh-Animation. */
    val WASHING_MACHINE: DelightTrigger = DelightTrigger(
        id = ID_WASHING_MACHINE,
        priority = PRIORITY_PATTERN,
        condition = ::isWashingMachine,
        presentation = DelightPresentation(DelightAnimation.SPIN, DelightTextKeys.WASHING_MACHINE),
    )

    /** Rentnerdreieck mit Dreieck-Animation. */
    val RENTNERDREIECK: DelightTrigger = DelightTrigger(
        id = ID_RENTNERDREIECK,
        priority = PRIORITY_PATTERN,
        condition = ::isRentnerdreieck,
        presentation = DelightPresentation(DelightAnimation.TRIANGLE, DelightTextKeys.RENTNERDREIECK),
    )

    /** Alle Produkt-Trigger in Registrierungsreihenfolge. */
    val ALL: List<DelightTrigger> = listOf(ONE_EIGHTY, WASHING_MACHINE, RENTNERDREIECK)
}
