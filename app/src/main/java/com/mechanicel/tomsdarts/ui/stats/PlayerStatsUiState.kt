package com.mechanicel.tomsdarts.ui.stats

import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.X01Metrics

/**
 * UI-Zustand des Spieler-Statistik-Screens (Phase 5, ADR-0037).
 */
sealed interface PlayerStatsUiState {

    /** Daten werden geladen. */
    data object Loading : PlayerStatsUiState

    /** Der Spieler existiert (nicht mehr), z.B. nach dem Loeschen. */
    data object PlayerNotFound : PlayerStatsUiState

    /** Der Spieler hat noch kein Leg mit eigenen Aufnahmen. */
    data class Empty(val playerName: String) : PlayerStatsUiState

    /**
     * Statistik vorhanden.
     *
     * @param playerName Anzeigename des Spielers (Titel).
     * @param modeFilters Gespielte Modus-Kennungen in Katalog-Reihenfolge. Der
     *   Filter wird nur bei mindestens zwei Eintraegen angezeigt
     *   ([showModeFilter]); "Alle" ist implizit (`null`).
     * @param selectedMode Gewaehlter Modus-Filter, `null` = Alle.
     * @param sections Abschnitte in Anzeigereihenfolge (bestimmt das ViewModel).
     */
    data class Content(
        val playerName: String,
        val modeFilters: List<String>,
        val selectedMode: String?,
        val sections: List<StatsSectionUi>,
    ) : PlayerStatsUiState {
        /** Ob die Filter-Leiste sichtbar ist (erst ab zwei gespielten Modi sinnvoll). */
        val showModeFilter: Boolean get() = modeFilters.size >= 2
    }

    /** Laden fehlgeschlagen; [message] nur zur Diagnose. */
    data class Error(val message: String? = null) : PlayerStatsUiState
}

/**
 * Ein Abschnitt des Statistik-Screens. Bewusst erweiterbar (sealed): die
 * Match-Liste (Lieferung 2) dockt als weitere Variante an, ohne die bestehenden
 * zu aendern.
 */
sealed interface StatsSectionUi {

    /** Stabiler Schluessel fuer `LazyColumn`-Items. */
    val key: String

    /**
     * Uebersicht: Matches und Siege (Modus-Filter beachtet).
     *
     * @param matches Anzahl Matches (inkl. nicht beendeter).
     * @param wins Davon vom Spieler gewonnen.
     */
    data class Overview(val matches: Int, val wins: Int) : StatsSectionUi {
        override val key: String get() = "overview"
    }

    /** X01-Kennzahlen (nur bei Filter "Alle" oder X01). */
    data class X01(val metrics: X01Metrics) : StatsSectionUi {
        override val key: String get() = "x01"
    }

    /** Filter "Alle"/X01 gewaehlt, aber keine X01-Darts vorhanden. */
    data object X01Empty : StatsSectionUi {
        override val key: String get() = "x01"
    }

    /** Trefferverteilung (Modus-Filter beachtet); leer bei `totalDarts == 0`. */
    data class Distribution(val distribution: HitDistribution) : StatsSectionUi {
        override val key: String get() = "distribution"
    }

    /**
     * Wurfmuster aus den Sequenz-Auswertungen (ADR-0036), Modus-Filter beachtet;
     * leer bei `visitsCounted == 0`. Steht nach [Distribution].
     */
    data class Sequences(val sequences: SequenceSectionUi) : StatsSectionUi {
        override val key: String get() = "sequences"
    }
}
