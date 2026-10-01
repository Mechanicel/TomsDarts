package com.mechanicel.tomsdarts.ui.stats

import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.X01Metrics

/**
 * Teilnehmer-Schluessel fuer geloeschte Spieler im Match-Screen. Alle
 * Teilnehmer und Aufnahmen mit `playerId = null` (SET_NULL nach dem Loeschen)
 * werden unter diesem Schluessel zu **einem** Abschnitt zusammengefasst; echte
 * Spieler-IDs sind immer positiv (autoGenerate).
 */
const val DELETED_PARTICIPANT_KEY: Long = -1L

/**
 * UI-Zustand des Match-Statistik-Screens (Phase 5, Lieferung 2/2, ADR-0037).
 */
sealed interface MatchStatsUiState {

    /** Daten werden geladen. */
    data object Loading : MatchStatsUiState

    /** Das Match existiert (nicht mehr). */
    data object NotFound : MatchStatsUiState

    /** Match vorhanden, aber ohne erfasste Aufnahmen; der Kopf bleibt sichtbar. */
    data class Empty(val header: MatchHeaderUi) : MatchStatsUiState

    /**
     * Statistik vorhanden.
     *
     * @param header Kopf-Panel (Modus, Datum, Status).
     * @param isX01 Ob das Match ein X01-Match ist (volle Kacheln je Spieler);
     *   sonst nur Legs/Darts-Kacheln plus einmaliger Hinweis.
     * @param players Ein Abschnitt je Teilnehmer in Sitzreihenfolge (geloeschte
     *   Spieler zusammengefasst unter [DELETED_PARTICIPANT_KEY]).
     * @param selectedPlayerKey Teilnehmer, dessen Trefferverteilung und Wurfmuster
     *   gezeigt werden (eine Chip-Reihe fuer beide Abschnitte).
     * @param distribution Trefferverteilung des gewaehlten Teilnehmers.
     * @param sequences Wurfmuster des gewaehlten Teilnehmers.
     * @param legs Alle Legs des Matches in Spielreihenfolge.
     */
    data class Content(
        val header: MatchHeaderUi,
        val isX01: Boolean,
        val players: List<MatchPlayerStatsUi>,
        val selectedPlayerKey: Long,
        val distribution: HitDistribution,
        val sequences: SequenceSectionUi,
        val legs: List<MatchLegUi>,
    ) : MatchStatsUiState {
        /** Ob die Spieler-Chip-Reihe sichtbar ist (erst ab zwei Abschnitten sinnvoll). */
        val showPlayerChips: Boolean get() = players.size >= 2
    }

    /** Laden fehlgeschlagen; [message] nur zur Diagnose. */
    data class Error(val message: String? = null) : MatchStatsUiState
}

/**
 * Kopf-Panel des Match-Screens.
 *
 * @param modeType Spielmodus-Key.
 * @param startedAt Start in Epoch-Millis.
 * @param result Status des Matches (Sieger / kein Sieger / nicht beendet).
 * @param legsPlayed Anzahl abgeschlossener Legs (Zeile nur bei > 0).
 */
data class MatchHeaderUi(
    val modeType: String,
    val startedAt: Long,
    val result: MatchResultUi,
    val legsPlayed: Int,
)

/** Ergebnis eines Matches oder Legs. */
sealed interface MatchResultUi {

    /** Beendet mit Sieger; [name] `null` = geloeschter Spieler. */
    data class Winner(val name: String?) : MatchResultUi

    /** Beendet, aber ohne (zuordenbaren) Sieger. */
    data object NoWinner : MatchResultUi

    /** Noch nicht beendet (`endedAt == null`). */
    data object Open : MatchResultUi
}

/**
 * Kennzahlen eines Teilnehmers im Match.
 *
 * @param key Spieler-ID bzw. [DELETED_PARTICIPANT_KEY].
 * @param name Anzeigename; `null` = geloeschter Spieler.
 * @param isWinner Ob der Teilnehmer das Match gewonnen hat.
 * @param x01 X01-Kennzahlen (nur X01-Match), sonst `null`.
 * @param legsWon Gewonnene Legs (alle Modi).
 * @param legsPlayed Abgeschlossene Legs des Matches.
 * @param dartsThrown Geworfene Darts; `null`, wenn der Teilnehmer keine Aufnahme hat.
 */
data class MatchPlayerStatsUi(
    val key: Long,
    val name: String?,
    val isWinner: Boolean,
    val x01: X01Metrics?,
    val legsWon: Int,
    val legsPlayed: Int,
    val dartsThrown: Int?,
)

/**
 * Eine Zeile der Legs-Liste.
 *
 * @param legId Leg-ID (Listen-Schluessel).
 * @param setNumber Set-Nummer; nur gesetzt, wenn mit Sets gespielt wird
 *   (`setsToWin > 1`), sonst `null` ("Leg N" statt "Set S · Leg N").
 * @param legNumber Leg-Nummer.
 * @param result Leg-Sieger / kein Sieger / nicht beendet.
 */
data class MatchLegUi(
    val legId: Long,
    val setNumber: Int?,
    val legNumber: Int,
    val result: MatchResultUi,
)
