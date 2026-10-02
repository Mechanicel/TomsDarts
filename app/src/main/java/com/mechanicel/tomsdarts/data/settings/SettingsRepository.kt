package com.mechanicel.tomsdarts.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Repository der lokalen App-Einstellungen (ADR-0040) auf Basis eines
 * Preferences-[DataStore]. Room bleibt Source of Truth fuer Spieldaten; hier
 * liegen nur geraete-lokale Schalter.
 *
 * Lesefehler ([IOException], z.B. korrupte Datei) fuehren nicht zum Absturz,
 * sondern liefern die Defaults aus [AppSettings]. Jeder Schalter bekommt einen
 * eigenen, typsicheren Setter (kein generisches Update nach aussen).
 *
 * Bleibt rein lokal (offline, keine Cloud/Tracking).
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    /** Aktuelle Einstellungen als Stream; emittiert nur bei echten Aenderungen. */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                delightEnabled = prefs[Keys.DELIGHT_ENABLED] ?: AppSettings.DEFAULT.delightEnabled,
            )
        }
        .distinctUntilChanged()

    /** Schaltet die Feier-Animationen ein bzw. aus. Wirft bei Schreibfehlern. */
    suspend fun setDelightEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DELIGHT_ENABLED] = enabled }
    }

    /**
     * Schluessel der gespeicherten Werte. Die Key-Strings NIE umbenennen: sie
     * stehen so in der Datei auf den Geraeten der Nutzer; eine Umbenennung
     * setzt die Einstellung stillschweigend auf den Default zurueck.
     */
    private object Keys {
        val DELIGHT_ENABLED = booleanPreferencesKey("delight_enabled")
    }
}
