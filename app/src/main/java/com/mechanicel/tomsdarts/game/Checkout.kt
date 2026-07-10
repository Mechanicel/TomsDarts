package com.mechanicel.tomsdarts.game

/**
 * Reine Checkout-Vorschlagslogik fuer X01 mit Double-Out.
 *
 * Bewusst ohne Android-/Room-/Compose-Bezug und damit vollstaendig mit reinem
 * JUnit testbar (analog [X01Mode]). Liefert fuer einen auscheckbaren Rest die
 * empfohlene 1-3-Dart-Kombination, mit der der Werfer per Double-Out (letzter
 * Dart ein Doppel, Doppel-Bull eingeschlossen) auf exakt 0 kommt.
 *
 * Die Vorschlagstabelle wird deterministisch aus allen physikalisch moeglichen
 * Wuerfen erzeugt (siehe [buildCheckoutTable]) und einmalig zwischengespeichert.
 * Dadurch ist jeder Eintrag konstruktionsbedingt korrekt: Die Summe der Dart-
 * Werte trifft den Rest exakt und der letzte Dart ist immer ein Doppel. Die
 * Bogey-Reste (mit 3 Darts nicht double-out-checkbar) und der Rest 1 tauchen
 * gar nicht erst in der Tabelle auf, weil fuer sie keine gueltige Route
 * existiert.
 */

/**
 * Empfohlene Checkout-Kombination fuer [remaining] bei Double-Out.
 *
 * @param remaining Aktuell verbleibende Restpunktzahl des Werfers.
 * @param doubleOut Ob der Modus Double-Out verlangt. Nur Double-Out ist
 *   abgedeckt: Bei `false` gibt es hier bewusst keinen Vorschlag.
 * @return Eine Liste aus 1-3 [Dart]s, die exakt auf [remaining] summiert und mit
 *   einem Doppel endet, oder `null`, wenn kein Vorschlag moeglich/sinnvoll ist:
 *   bei `doubleOut == false`, fuer `remaining > 170`, `remaining < 2`, den Rest
 *   1 sowie die Bogey-Reste 169, 168, 166, 165, 163, 162, 159.
 */
fun checkoutSuggestion(remaining: Int, doubleOut: Boolean): List<Dart>? {
    if (!doubleOut) return null
    return CHECKOUT_TABLE[remaining]
}

/**
 * Moegliche Setup-Darts (alle ersten/zweiten Darts einer Aufnahme) in
 * bevorzugter Reihenfolge: Triples zuerst (fuer hohe Finishes), dann Singles,
 * das einfache Bull, danach Doppel als Setup und zuletzt das Doppel-Bull als
 * Setup. Fuehrt bei gleicher Dart-Zahl zu konventionsnahen Routen (T-20-Setups
 * vor Bull).
 */
private val SETUP_DARTS: List<Dart> = buildList {
    for (n in 20 downTo 1) add(Dart.triple(n))
    for (n in 20 downTo 1) add(Dart.single(n))
    add(Dart.bull())
    for (n in 20 downTo 1) add(Dart.double(n))
    add(Dart.doubleBull())
}

/** Alle als Finish zulaessigen Doppel (D-1..D-20 sowie Doppel-Bull). */
private val FINISH_DOUBLES: List<Dart> = buildList {
    for (n in 1..20) add(Dart.double(n))
    add(Dart.doubleBull())
}

/** Schnelle Zuordnung Punktwert -> Finish-Doppel (Werte sind eindeutig). */
private val FINISH_BY_VALUE: Map<Int, Dart> = FINISH_DOUBLES.associateBy { it.value }

/**
 * Bevorzugte Reihenfolge der Ziel-Doppel als Rangliste ihrer Punktwerte
 * (kleiner Rang = bevorzugt): grosse, gerade Doppel zuerst, dann die ungeraden
 * Doppel, das Doppel-Bull zuletzt (wird nur genutzt, wenn eine Route es zwingend
 * verlangt). So endet z. B. die 100 auf D-20 (T-20 D-20) statt auf D-Bull.
 */
private val FINISH_PREFERENCE: List<Int> = listOf(
    40, 32, 36, 20, 16, 24, 8, 28, 12, 4, // D-20 D-16 D-18 D-10 D-8 D-12 D-4 D-14 D-6 D-2
    38, 34, 30, 26, 22, 18, 14, 10, 6, 2, // ungerade Doppel D-19..D-1
    50, // Doppel-Bull
)

private fun finishRank(dart: Dart): Int =
    FINISH_PREFERENCE.indexOf(dart.value).let { if (it < 0) FINISH_PREFERENCE.size else it }

/**
 * Ordnet gleich lange Routen nach "Komfort": bevorzugtes Finish-Doppel, dann
 * moeglichst wertvolle Setup-Darts (T-20 vor kleineren Wuerfen). Dient nur der
 * Auswahl einer konventionsnahen Route unter mehreren gueltigen.
 */
private val ROUTE_COMPARATOR: Comparator<List<Dart>> =
    compareBy<List<Dart>> { it.size }
        .thenBy { finishRank(it.last()) }
        .thenByDescending { it.first().value }
        .thenByDescending { it.getOrNull(1)?.value ?: 0 }

/** Vorab berechnete Vorschlagstabelle fuer alle auscheckbaren Reste 2..170. */
private val CHECKOUT_TABLE: Map<Int, List<Dart>> = buildCheckoutTable()

/**
 * Baut die Vorschlagstabelle: fuer jeden Rest 2..170 die kuerzeste, moeglichst
 * konventionelle Double-Out-Route (siehe [bestRoute]). Reste ohne gueltige Route
 * (Bogey-Reste, Rest 1) fallen automatisch heraus.
 */
private fun buildCheckoutTable(): Map<Int, List<Dart>> = buildMap {
    for (remaining in 2..170) {
        bestRoute(remaining)?.let { put(remaining, it) }
    }
}

/**
 * Beste Double-Out-Route fuer [remaining] oder `null`, wenn keine mit 1-3 Darts
 * existiert. Sucht gestaffelt nach Dart-Zahl (weniger Darts gewinnt immer) und
 * waehlt innerhalb einer Staffel per [ROUTE_COMPARATOR]. Jede erzeugte Route
 * summiert konstruktionsbedingt exakt auf [remaining] und endet auf ein Doppel.
 */
private fun bestRoute(remaining: Int): List<Dart>? {
    // 1 Dart: direkt ein Finish-Doppel.
    FINISH_BY_VALUE[remaining]?.let { return listOf(it) }

    // 2 Darts: ein Setup-Dart plus ein passendes Finish-Doppel.
    val twoDart = mutableListOf<List<Dart>>()
    for (first in SETUP_DARTS) {
        val rest = remaining - first.value
        if (rest <= 0) continue
        FINISH_BY_VALUE[rest]?.let { twoDart.add(listOf(first, it)) }
    }
    if (twoDart.isNotEmpty()) return twoDart.minWithOrNull(ROUTE_COMPARATOR)

    // 3 Darts: zwei Setup-Darts plus ein passendes Finish-Doppel.
    val threeDart = mutableListOf<List<Dart>>()
    for (first in SETUP_DARTS) {
        val restAfterFirst = remaining - first.value
        if (restAfterFirst <= 0) continue
        for (second in SETUP_DARTS) {
            val rest = restAfterFirst - second.value
            if (rest <= 0) continue
            FINISH_BY_VALUE[rest]?.let { threeDart.add(listOf(first, second, it)) }
        }
    }
    return threeDart.minWithOrNull(ROUTE_COMPARATOR)
}
