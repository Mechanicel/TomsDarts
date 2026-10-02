package com.mechanicel.tomsdarts.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Ersetzt eine unlesbare (korrupte) Einstellungsdatei durch leere Preferences,
 * sodass die Defaults aus [AppSettings] gelten und der naechste Schreibvorgang
 * wieder gelingt (ohne Handler wuerde jedes `edit` an der korrupten Datei
 * scheitern).
 */
internal val settingsCorruptionHandler: ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }

/**
 * Prozessweiter Preferences-DataStore der App-Einstellungen (ADR-0040). Bewusst
 * top-level per [preferencesDataStore]-Delegate: pro Datei darf es nur EINE
 * aktive DataStore-Instanz im Prozess geben. Liegt lokal unter
 * `files/datastore/settings.preferences_pb`.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = settingsCorruptionHandler,
)
