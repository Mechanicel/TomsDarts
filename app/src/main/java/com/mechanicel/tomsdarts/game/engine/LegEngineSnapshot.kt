package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart

/**
 * Lesbarer, unveraenderlicher Momentaufnahme-Zustand einer [LegEngine] fuer
 * EINEN Spieler in EINEM Leg.
 *
 * Reines Domaenen-Value-Object: kein Android-/Room-/Compose-Bezug, dadurch mit
 * reinem JUnit testbar. Liefert sowohl den fuer die Anzeige relevanten Zustand
 * als auch genug Throw-/Turn-Daten, damit ein Aufrufer spaeter throw-level
 * persistieren kann (Darts der Aufnahme inkl. eines etwaigen Bust-Darts).
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. [com.mechanicel.tomsdarts.game.X01State]).
 * @param state Aktueller, fuer die Wertung gueltiger Modus-Zustand. Bei Bust ist
 *   dies bereits der zurueckgesetzte Aufnahme-Startzustand.
 * @param turnStartState Modus-Zustand zu Beginn der aktuellen Aufnahme. Ziel des
 *   Bust-Reverts.
 * @param turnDarts Alle in der aktuellen Aufnahme geworfenen Darts in Wurf-
 *   Reihenfolge. Enthaelt bei Bust auch den ueberwerfenden Dart (fuer die
 *   Persistenz als "geworfen, aber Aufnahme = bust").
 * @param dartsInTurn Anzahl der bisher in der Aufnahme geworfenen Darts (0..3).
 * @param turnScored Gewertete Aufnahmen-Summe der aktuellen Aufnahme; bei Bust 0.
 * @param turnBust True, wenn die aktuelle Aufnahme als Bust endete.
 * @param isTurnEnded True, wenn die aktuelle Aufnahme abgeschlossen ist (3 Darts,
 *   Bust oder Leg-Gewinn) und vor weiteren Darts [LegEngine.startNewTurn] noetig ist.
 * @param isLegWon True, wenn das Leg gewonnen wurde.
 */
data class LegEngineSnapshot<S>(
    val state: S,
    val turnStartState: S,
    val turnDarts: List<Dart>,
    val dartsInTurn: Int,
    val turnScored: Int,
    val turnBust: Boolean,
    val isTurnEnded: Boolean,
    val isLegWon: Boolean,
)
