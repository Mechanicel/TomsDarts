package com.mechanicel.tomsdarts.delight

/**
 * Animations-Typ einer Delight-Feier. Datengetriebene Beschreibung fuer die
 * spaetere UI (stumme Vollbild-Animation, ADR-0006); die konkrete Umsetzung
 * je Wert lebt ausschliesslich in der UI-Schicht.
 */
enum class DelightAnimation {
    /** Konfetti-Regen (z.B. 180). */
    CONFETTI,

    /** Dreh-Animation (z.B. Waschmaschine). */
    SPIN,

    /** Dreieck-Animation (z.B. Rentnerdreieck). */
    TRIANGLE,

    /** Allgemeine Feier ohne eigenes Motiv. */
    GENERIC,
}

/**
 * Datengetriebene Darstellung eines Delight-Triggers: welche Animation laeuft
 * und welcher Text dazu angezeigt wird.
 *
 * Bewusst ohne Android-Ressourcen: [textKey] ist ein stabiler Text-Schluessel,
 * den die UI auf eine String-Ressource abbildet (z.B. `"delight_180"` ->
 * `R.string.delight_180`). So bleibt das Delight-Paket pures Kotlin.
 *
 * @param animation Animations-Typ der Feier.
 * @param textKey Stabiler Schluessel des anzuzeigenden Texts.
 */
data class DelightPresentation(
    val animation: DelightAnimation,
    val textKey: String,
)
