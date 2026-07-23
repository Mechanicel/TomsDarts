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
 * Auswahl). Aktuell rein additiv vorbereitet: enthaelt nur X01, sodass die App
 * sich exakt wie zuvor verhaelt (die Modus-Auswahl im Setup bleibt unsichtbar,
 * solange nur ein Eintrag existiert). Ein zweiter Modus (z.B. Cricket) laesst
 * sich spaeter allein durch einen weiteren [entries]-Eintrag plus einen
 * Factory-Zweig andocken.
 */
object GameModeCatalog {

    /** Kennung des X01-Modus (identisch zu [X01Mode.key]). */
    const val X01: String = "X01"

    /**
     * Alle auswaehlbaren Modi in Anzeigereihenfolge. Aktuell nur X01. Solange die
     * Liste genau einen Eintrag hat, blendet die Setup-UI die Modus-Auswahl aus.
     */
    val entries: List<GameModeInfo> = listOf(
        GameModeInfo(key = X01, usesStartScore = true, usesDoubleOut = true),
    )

    /** Vorbelegter Modus im Setup (heutiges Verhalten: X01). */
    const val DEFAULT: String = X01
}
