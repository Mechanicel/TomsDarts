package com.mechanicel.tomsdarts.game

/**
 * Pures Domaenen-Konfigurationsobjekt fuer ein Match.
 *
 * Entkoppelt von der Persistenz-Entity (`data.entity.Match`), aber feldlich
 * analog: kapselt die Regel-Parameter, die ein [GameMode] zum Aufbau seines
 * Startzustands und zur Wurfverarbeitung braucht. Kein Android-/Room-Bezug.
 *
 * @param startScore Startpunktzahl des Modus (z.B. 501 fuer X01).
 * @param doubleOut Ob zum Auschecken ein Double noetig ist.
 * @param legsToWin Anzahl Legs fuer einen Set-/Match-Gewinn.
 * @param setsToWin Anzahl Sets fuer den Match-Gewinn.
 */
data class GameConfig(
    val startScore: Int = 501,
    val doubleOut: Boolean = true,
    val legsToWin: Int = 1,
    val setsToWin: Int = 1,
)
