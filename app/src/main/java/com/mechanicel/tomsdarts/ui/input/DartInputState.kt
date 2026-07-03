package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart

/**
 * Aktiver Multiplikator-Modus des Ziffernblocks.
 *
 * Bestimmt, mit welchem Faktor die naechste Zahl- bzw. Bull-Eingabe gewertet
 * wird. Nach jeder Wurf-Eingabe faellt der Modus automatisch auf [SINGLE]
 * zurueck (Auto-Reset).
 */
enum class DartModifier {
    SINGLE,
    DOUBLE,
    TRIPLE,
}

/**
 * Immutabler State-Holder des Eingabe-Ziffernblocks fuer einen Aufnahme-Zug
 * (max. drei Darts).
 *
 * Bewusst als reine Kotlin-Logik ohne Compose-/Android-Bezug gehalten, damit
 * die Eingabe-Transitionen mit reinem JUnit (ohne Robolectric) testbar sind.
 * Alle Transitionen sind pur und geben einen neuen [DartInputState] zurueck.
 *
 * Sobald drei Darts gesetzt sind ([isComplete]), sind alle Eingabe-Transitionen
 * No-ops; nur [undo] und [startNewTurn] bleiben wirksam.
 *
 * @param modifier Aktiver Multiplikator-Modus fuer die naechste Eingabe.
 * @param darts Bisher gesetzte Darts dieses Zugs (maximal drei).
 */
data class DartInputState(
    val modifier: DartModifier = DartModifier.SINGLE,
    val darts: List<Dart> = emptyList(),
) {

    /** True, wenn der Zug voll ist (drei Darts gesetzt). */
    val isComplete: Boolean get() = darts.size >= MAX_DARTS

    /** Summe der Punktwerte aller bisher gesetzten Darts. */
    val turnSum: Int get() = darts.sumOf { it.value }

    /** True, wenn ein letzter Dart zurueckgenommen werden kann. */
    val canUndo: Boolean get() = darts.isNotEmpty()

    /**
     * True, wenn die Bull-Taste benutzbar ist: nur sinnvoll ausserhalb von
     * TRIPLE (kein Triple-Bull) und solange der Zug nicht voll ist.
     */
    val bullEnabled: Boolean get() = modifier != DartModifier.TRIPLE && !isComplete

    /** True, solange weitere Eingaben moeglich sind (Zug nicht voll). */
    val inputEnabled: Boolean get() = !isComplete

    /**
     * Schaltet den DOUBLE-Modus exklusiv um: aktiviert DOUBLE (und deaktiviert
     * dabei TRIPLE), erneutes Tippen schaltet zurueck auf SINGLE. No-op bei
     * vollem Zug.
     */
    fun toggleDouble(): DartInputState {
        if (isComplete) return this
        val next = if (modifier == DartModifier.DOUBLE) DartModifier.SINGLE else DartModifier.DOUBLE
        return copy(modifier = next)
    }

    /**
     * Schaltet den TRIPLE-Modus exklusiv um: aktiviert TRIPLE (und deaktiviert
     * dabei DOUBLE), erneutes Tippen schaltet zurueck auf SINGLE. No-op bei
     * vollem Zug.
     */
    fun toggleTriple(): DartInputState {
        if (isComplete) return this
        val next = if (modifier == DartModifier.TRIPLE) DartModifier.SINGLE else DartModifier.TRIPLE
        return copy(modifier = next)
    }

    /**
     * Haengt einen Dart auf Segment [n] mit dem aktuellen Multiplikator an und
     * setzt den Modus auf SINGLE zurueck (Auto-Reset).
     *
     * Defensiv: nur [n] in 1..20 wird akzeptiert; ausserhalb sowie bei vollem
     * Zug ist die Transition ein No-op.
     */
    fun pressNumber(n: Int): DartInputState {
        if (isComplete) return this
        if (n !in 1..20) return this
        return appendAndReset(Dart(n, modifier.multiplier))
    }

    /**
     * Haengt einen Bull-Dart an: bei DOUBLE Doppel-Bull (50), sonst einfaches
     * Bull (25), jeweils mit Auto-Reset auf SINGLE. No-op bei TRIPLE
     * ([bullEnabled] == false) oder bei vollem Zug.
     */
    fun pressBull(): DartInputState {
        if (!bullEnabled) return this
        val dart = if (modifier == DartModifier.DOUBLE) Dart.doubleBull() else Dart.bull()
        return appendAndReset(dart)
    }

    /**
     * Haengt einen Fehlwurf (Miss/Out, 0 Punkte) an und setzt den Modus auf
     * SINGLE zurueck. Der Modifikator wird dabei ignoriert. No-op bei vollem
     * Zug.
     */
    fun pressOut(): DartInputState {
        if (isComplete) return this
        return appendAndReset(Dart.miss())
    }

    /**
     * Nimmt den zuletzt gesetzten Dart zurueck. No-op, wenn kein Dart gesetzt
     * ist. Der Modifikator bleibt unveraendert.
     */
    fun undo(): DartInputState {
        if (!canUndo) return this
        return copy(darts = darts.dropLast(1))
    }

    /** Startet einen neuen Zug: leert die Darts und setzt den Modus auf SINGLE. */
    fun startNewTurn(): DartInputState =
        DartInputState(modifier = DartModifier.SINGLE, darts = emptyList())

    private fun appendAndReset(dart: Dart): DartInputState =
        copy(modifier = DartModifier.SINGLE, darts = darts + dart)

    companion object {
        /** Maximale Anzahl Darts pro Aufnahme-Zug. */
        const val MAX_DARTS: Int = 3
    }
}

/** Zahl-Multiplikator des Modus: SINGLE=1, DOUBLE=2, TRIPLE=3. */
val DartModifier.multiplier: Int
    get() = when (this) {
        DartModifier.SINGLE -> 1
        DartModifier.DOUBLE -> 2
        DartModifier.TRIPLE -> 3
    }
