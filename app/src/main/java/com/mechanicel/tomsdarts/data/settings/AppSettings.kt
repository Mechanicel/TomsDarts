package com.mechanicel.tomsdarts.data.settings

/**
 * Lokale App-Einstellungen (ADR-0040). Einzige Stelle, an der die Default-Werte
 * der Schalter definiert sind; das [SettingsRepository] faellt bei fehlenden
 * oder unlesbaren Werten auf diese Defaults zurueck.
 *
 * Bleibt rein lokal (DataStore auf dem Geraet, keine Cloud/Tracking).
 *
 * @param delightEnabled Ob Feier-Animationen bei besonderen Wuerfen angezeigt
 *   werden (ADR-0038/0039). Default `true`.
 */
data class AppSettings(
    val delightEnabled: Boolean = true,
) {
    companion object {
        /** Default-Einstellungen (z.B. als Initialwert vor dem ersten Lesen). */
        val DEFAULT = AppSettings()
    }
}
