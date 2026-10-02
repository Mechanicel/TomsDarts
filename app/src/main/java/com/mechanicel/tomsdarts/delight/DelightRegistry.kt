package com.mechanicel.tomsdarts.delight

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameModeCatalog

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
 * Die Produkt-Trigger der App (ADR-0006, ADR-0041, ADR-0042). Ein neuer Trigger
 * braucht genau einen zusaetzlichen Eintrag in [ALL] (vollstaendiges Rezept
 * inkl. Text-Schluessel und Strings: ADR-0042).
 *
 * Gemeinsame Regel der Muster-Trigger (180, Waschmaschine, Rentnerdreieck): nur
 * vollstaendige Aufnahmen mit genau drei Darts und ohne Bust, in ALLEN Modi
 * (bewertet werden die physischen Wuerfe [DelightVisit.darts], nicht die
 * Modus-Wertung). Ein Fehlwurf (Segment 0) liegt in keinem Muster.
 *
 * Finish-Trigger (Madhouse, Bull-Finish) verlangen einen Checkout des Werfers
 * ([DelightVisit.checkout]) und bewerten nur den LETZTEN Dart; die Anzahl der
 * Darts ist egal. Madhouse gilt nur in X01 (in Shanghai/Killer ist ein D1 kein
 * Finish), Bull-Finish bewusst in allen Modi (z.B. Cricket-Leg-Gewinn mit
 * Doppel-Bull). Ton gilt nur in X01 und bewertet die gewertete Summe
 * ([DelightVisit.scored]).
 *
 * Prioritaetsschema (hoeher gewinnt, siehe ADR-0042):
 * [PRIORITY_MAX_SCORE] (180) > [PRIORITY_MADHOUSE] > [PRIORITY_BULL_FINISH] >
 * [PRIORITY_PATTERN] (Waschmaschine, Rentnerdreieck; schliessen sich
 * gegenseitig aus) > [PRIORITY_TON]. Seltene Sonderfaelle schlagen also
 * Zahlenmuster, und die haeufige Ton-Feier laeuft nur, wenn nichts
 * Spezielleres passt (z.B. T20/T20/S5 = 125 -> Waschmaschine statt Ton).
 */
object ProductDelightTriggers {

    /** Prioritaet fuer Hoechstleistungen (180). */
    const val PRIORITY_MAX_SCORE: Int = 100

    /** Prioritaet fuer das Madhouse-Finish (Doppel 1). */
    const val PRIORITY_MADHOUSE: Int = 80

    /** Prioritaet fuer das Bull-Finish (Doppel-Bull). */
    const val PRIORITY_BULL_FINISH: Int = 70

    /** Prioritaet fuer Zahlenmuster (Waschmaschine, Rentnerdreieck). */
    const val PRIORITY_PATTERN: Int = 50

    /** Prioritaet fuer die Ton (ab 100 in X01) - die haeufigste, allgemeinste Feier. */
    const val PRIORITY_TON: Int = 10

    /** Mindestpunktzahl einer Ton. */
    const val TON_MIN_SCORE: Int = 100

    /** Trigger-ID der 180. */
    const val ID_ONE_EIGHTY: String = "180"

    /** Trigger-ID der Waschmaschine. */
    const val ID_WASHING_MACHINE: String = "washing_machine"

    /** Trigger-ID des Rentnerdreiecks. */
    const val ID_RENTNERDREIECK: String = "rentnerdreieck"

    /** Trigger-ID des Madhouse-Finishs. */
    const val ID_MADHOUSE: String = "madhouse"

    /** Trigger-ID des Bull-Finishs. */
    const val ID_BULL_FINISH: String = "bull_finish"

    /** Trigger-ID der Ton. */
    const val ID_TON: String = "ton"

    /** Segmente der Waschmaschine (20 und ihre Nachbarn 5 und 1). */
    private val WASHING_MACHINE_SEGMENTS: Set<Int> = setOf(20, 5, 1)

    /** Segmente des Rentnerdreiecks (19 und ihre Nachbarn 7 und 3). */
    private val RENTNERDREIECK_SEGMENTS: Set<Int> = setOf(19, 7, 3)

    /** Vollstaendige Aufnahme: genau drei Darts, kein Bust. */
    private fun isCompleteVisit(visit: DelightVisit): Boolean =
        !visit.bust && visit.darts.size == 3

    /**
     * Alle drei Darts auf Segmenten aus [segments] (beliebiger Multiplier) und
     * mindestens zwei VERSCHIEDENE Segmente darunter - drei Darts auf derselben
     * Zahl (z.B. 20/20/20 oder 1/1/1) sind schlicht "drei Mal dieselbe Zahl",
     * kein Muster (ADR-0042).
     */
    private fun isSegmentPattern(visit: DelightVisit, segments: Set<Int>): Boolean =
        isCompleteVisit(visit) &&
            visit.darts.all { it.segment in segments } &&
            visit.darts.map { it.segment }.distinct().size >= 2

    /** 180: drei Mal Triple 20 in einer vollstaendigen Aufnahme ohne Bust. */
    fun isOneEighty(visit: DelightVisit): Boolean =
        isCompleteVisit(visit) && visit.darts.all { it.segment == 20 && it.multiplier == 3 }

    /**
     * Waschmaschine: alle drei Darts in {20, 5, 1}, mindestens zwei verschiedene
     * Segmente (ADR-0006, ADR-0042).
     */
    fun isWashingMachine(visit: DelightVisit): Boolean =
        isSegmentPattern(visit, WASHING_MACHINE_SEGMENTS)

    /**
     * Rentnerdreieck: alle drei Darts in {19, 7, 3}, mindestens zwei verschiedene
     * Segmente (ADR-0041, ADR-0042).
     */
    fun isRentnerdreieck(visit: DelightVisit): Boolean =
        isSegmentPattern(visit, RENTNERDREIECK_SEGMENTS)

    /** Checkout des Werfers ohne Bust, dessen letzter Dart [lastDart] ist. */
    private fun isCheckoutOn(visit: DelightVisit, lastDart: Dart): Boolean =
        !visit.bust && visit.checkout && visit.darts.lastOrNull() == lastDart

    /**
     * Madhouse: X01-Checkout mit dem letzten Dart auf Doppel 1. Nur X01 - in
     * anderen Modi (z.B. Shanghai Runde 1, Killer-Kill per D1) ist ein D1 zum
     * Leg-Ende kein Madhouse-Finish (ADR-0042).
     */
    fun isMadhouse(visit: DelightVisit): Boolean =
        visit.modeKey == GameModeCatalog.X01 && isCheckoutOn(visit, Dart.double(1))

    /**
     * Bull-Finish: Checkout mit dem letzten Dart auf Bullseye (Doppel-Bull,
     * Segment 25 x 2). Ein Checkout auf Single-Bull (z.B. X01 ohne Double-Out)
     * zaehlt bewusst NICHT (ADR-0042).
     */
    fun isBullFinish(visit: DelightVisit): Boolean = isCheckoutOn(visit, Dart.doubleBull())

    /**
     * Ton: X01-Aufnahme ohne Bust mit mindestens [TON_MIN_SCORE] gewerteten
     * Punkten; die Anzahl der Darts ist egal (auch ein Checkout zaehlt).
     */
    fun isTon(visit: DelightVisit): Boolean =
        visit.modeKey == GameModeCatalog.X01 && !visit.bust && visit.scored >= TON_MIN_SCORE

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

    /** Madhouse mit allgemeiner Feier. */
    val MADHOUSE: DelightTrigger = DelightTrigger(
        id = ID_MADHOUSE,
        priority = PRIORITY_MADHOUSE,
        condition = ::isMadhouse,
        presentation = DelightPresentation(DelightAnimation.GENERIC, DelightTextKeys.MADHOUSE),
    )

    /** Bull-Finish mit allgemeiner Feier. */
    val BULL_FINISH: DelightTrigger = DelightTrigger(
        id = ID_BULL_FINISH,
        priority = PRIORITY_BULL_FINISH,
        condition = ::isBullFinish,
        presentation = DelightPresentation(DelightAnimation.GENERIC, DelightTextKeys.BULL_FINISH),
    )

    /** Ton mit allgemeiner Feier. */
    val TON: DelightTrigger = DelightTrigger(
        id = ID_TON,
        priority = PRIORITY_TON,
        condition = ::isTon,
        presentation = DelightPresentation(DelightAnimation.GENERIC, DelightTextKeys.TON),
    )

    /** Alle Produkt-Trigger in Registrierungsreihenfolge. */
    val ALL: List<DelightTrigger> = listOf(
        ONE_EIGHTY,
        WASHING_MACHINE,
        RENTNERDREIECK,
        MADHOUSE,
        BULL_FINISH,
        TON,
    )
}
