package com.mechanicel.tomsdarts.game

/**
 * Anzeige- und Konfigurations-Metadaten EINES auswaehlbaren Spielmodus.
 *
 * Bewusst ohne generischen Spielerzustand und ohne [GameMode]-Instanz: der
 * Katalog beschreibt nur, WAS ein Modus in der Setup-UI an Optionen benoetigt
 * (Startpunkt, Double-Out). Die typisierte Aufloesung des Modus (der konkrete
 * [GameMode]/UI-Adapter) lebt getrennt in der ViewModel-Factory, damit hier kein
 * `GameMode<*>` mit unbekanntem S aufgefuehrt werden muss. Reines Domaenen-
 * Value-Object: kein Android-/Room-/Compose-Bezug.
 *
 * @param key Stabile, persistierbare Kennung des Modus (identisch zu
 *   [GameMode.key], z.B. "X01"). Bindeglied zwischen Setup-Auswahl und Factory.
 * @param usesStartScore Ob der Modus einen konfigurierbaren Startpunkt hat
 *   (X01: true). Steuert die Sichtbarkeit der Startpunkt-Auswahl im Setup.
 * @param usesDoubleOut Ob der Modus die Double-Out-Option kennt (X01: true).
 *   Steuert die Sichtbarkeit des Double-Out-Schalters im Setup.
 */
data class GameModeInfo(
    val key: String,
    val usesStartScore: Boolean,
    val usesDoubleOut: Boolean,
)

/**
 * Registry der auswaehlbaren Spielmodi (Single Source of Truth fuer die Setup-
 * Auswahl). Rein additiv aufgebaut: X01 (Default), Cricket und Around the Clock.
 * Ein weiterer Modus dockt allein durch einen weiteren [entries]-Eintrag plus
 * einen Factory-Zweig ([GameViewModel.provideFactory]) an.
 */
object GameModeCatalog {

    /** Kennung des X01-Modus (identisch zu [X01Mode.key]). */
    const val X01: String = "X01"

    /** Kennung des Standard-Cricket-Modus (identisch zu [CricketMode.key]). */
    const val CRICKET: String = "CRICKET"

    /** Kennung des Around-the-Clock-Modus (identisch zu [AroundTheClockMode.key]). */
    const val AROUND_THE_CLOCK: String = "AROUND_THE_CLOCK"

    /**
     * Alle auswaehlbaren Modi in Anzeigereihenfolge (X01 zuerst == [DEFAULT]).
     * Sobald die Liste mehr als einen Eintrag hat, blendet die Setup-UI die
     * Modus-Auswahl ein. Cricket und Around the Clock kennen weder Startpunkt
     * noch Double-Out, blenden diese Abschnitte im Setup also ueber die Flags aus.
     */
    val entries: List<GameModeInfo> = listOf(
        GameModeInfo(key = X01, usesStartScore = true, usesDoubleOut = true),
        GameModeInfo(key = CRICKET, usesStartScore = false, usesDoubleOut = false),
        GameModeInfo(key = AROUND_THE_CLOCK, usesStartScore = false, usesDoubleOut = false),
    )

    /** Vorbelegter Modus im Setup (heutiges Verhalten: X01). */
    const val DEFAULT: String = X01
}
