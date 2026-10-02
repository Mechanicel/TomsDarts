# 0040 — App-Einstellungen: DataStore Preferences, Schalter „Feier-Animationen"

**Status:** Akzeptiert

## Kontext

[ADR-0038](0038-delight-trigger-system.md) verlangt, dass Feiern abschaltbar sind, bevor die
Produkt-Trigger kommen. [ADR-0039](0039-delight-overlay.md) hat dafür
`GameScreen(delightEnabled = true)` vorbereitet: Bei `false` quittiert der Bildschirm jedes
Event sofort per `onDelightDismissed`, die Kontrollpause wartet also nicht. Es fehlte ein Ort,
an dem App-weite, geräte-lokale Schalter gespeichert werden, und ein Bildschirm dafür. Bisher
gab es weder DataStore noch SharedPreferences noch Einstellungen.

## Entscheidung

1. **Persistenz: DataStore Preferences** (`androidx.datastore:datastore-preferences` 1.2.1,
   neueste stabile Version; Kotlin-Stdlib-Anforderung 2.0.21 passt zu Kotlin 2.2.10).
   Die Datei heißt `settings.preferences_pb` und liegt lokal im App-Speicher. Kein Netz,
   kein Tracking.
   - **Room bleibt Source of Truth für Spieldaten** (Spieler, Matches, Würfe). Einstellungen sind
     keine Spieldaten. Room hätte für jeden neuen Schalter eine Schema-Migration gebraucht.
   - **SharedPreferences abgelehnt:** synchrone API, keine Flows, Fehler beim Lesen sind
     schlecht behandelbar.
2. **Datenschicht `data.settings`:**
   - `AppSettings` (data class) ist die **einzige Stelle mit Default-Werten**
     (`delightEnabled = true`); `AppSettings.DEFAULT` dient als Initialwert vor dem ersten Lesen.
   - `Context.settingsDataStore` ist ein top-level `preferencesDataStore`-Delegate: genau eine
     Instanz pro Prozess, wie DataStore es verlangt. Eine korrupte Datei ersetzt ein
     `ReplaceFileCorruptionHandler` durch leere Preferences, damit Defaults gelten und der
     nächste Schreibvorgang wieder gelingt.
   - `SettingsRepository(dataStore)`: `settings: Flow<AppSettings>` mappt fehlende Werte auf die
     Defaults, fängt `IOException` beim Lesen ab (→ Defaults, kein Absturz; andere Fehler
     werden weitergeworfen) und emittiert per `distinctUntilChanged` nur echte Änderungen.
     Jeder Schalter hat einen eigenen typsicheren Setter (`setDelightEnabled`), kein generisches
     Update nach außen. Die Key-Strings werden **nie umbenannt**, sonst fällt die gespeicherte
     Einstellung stillschweigend auf den Default zurück.
   - `AppContainer.settingsRepository` (lazy, über den Application-Context).
3. **Wert ins Spiel (Option A).** `MainActivity` sammelt `settingsRepository.settings` per
   `collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)` und reicht
   `delightEnabled` an `GameScreen` durch. `GameViewModel` und seine Factory bleiben unverändert.
   Bis zum ersten Lesen gilt beim Kaltstart der Default (Feiern an); das ist unkritisch, weil
   im ersten Augenblick keine Feier fällig ist. Wird der Schalter während einer Feier
   umgelegt, beendet `planDelightIntake` sie sofort und quittiert sie.
4. **Einstieg und Bildschirm.** Zahnrad (`Icons.Filled.Settings`) ganz rechts in der
   TopAppBar der Spielerliste, nur im Normalmodus. `ui.settings.SettingsScreen` nach dem
   Unterseiten-Muster (TopAppBar mit „Zurück", `BackHandler`, zustandslose
   `SettingsScreenContent` + `SettingsScreenCallbacks`, Previews). Abschnitt „Spiel" mit einer
   Schalter-Zeile: Die ganze `ListItem`-Zeile ist **ein** `toggleable` mit `Role.Switch`,
   der `Switch` selbst hat keinen eigenen Klick, sodass TalkBack Titel, Beschreibung und Zustand
   als Einheit liest. Sind Animationen systemweit aus (`rememberReducedMotion()`), erscheint
   unter dem Schalter ein Hinweis.
5. **Zustände.** `Loading` zeigt eine leere Fläche (kein Spinner, keine Default-Werte).
   `Content` zeigt immer den **gespeicherten** Wert, ohne optimistischen Zwischenstand.
   Ein Schreibfehler setzt `SettingsViewModel.saveError` → Snackbar „Einstellung konnte nicht
   gespeichert werden.", danach `onSaveErrorShown()`. Es gibt keine „Gespeichert"-Rückmeldung.

### Rezept: neuer Schalter

1. Feld mit Default in `AppSettings`.
2. Key in `SettingsRepository.Keys` (neuer, nie wieder umbenannter String), Mapping in
   `settings`, eigener `suspend fun setXyz(...)`.
3. Setter im `SettingsViewModel` (gleiches try/catch-Muster → `saveError`), Callback in
   `SettingsScreenCallbacks`.
4. `SwitchSettingItem` im passenden Abschnitt von `SettingsScreenContent` plus Strings.
5. Wirkt der Schalter in einem Screen, den Wert wie `delightEnabled` aus `MainActivity`
   durchreichen.
6. Tests in `SettingsRepositoryTest`/`SettingsViewModelTest` ergänzen.

## Konsequenzen

- Neue Abhängigkeit DataStore Preferences (rein lokal). Room-Schema unverändert.
- Der Offline-Kern bleibt werbe-, tracking- und netzfrei.
- Tests: `SettingsRepositoryTest` läuft gegen eine echte Datei im Temp-Verzeichnis, aber über
  `OkioStorage` mit dem `PreferencesSerializer` statt der `File`-Storage der App. Grund: Die
  `File`-Storage ersetzt die Datei per `File.renameTo`, und das scheitert auf Windows-Hosts
  bei jedem zweiten Schreibvorgang (Zieldatei existiert). Auf Android tritt das nicht auf.
  Das Dateiformat ist dasselbe. Der DataStore läuft in einem Kind-Scope von `backgroundScope`
  und wird spätestens am Testende beendet. `SettingsViewModelTest` nutzt einen In-Memory-Fake.
- Einen Compose-UI-Test für den Bildschirm gibt es nicht (keine Infrastruktur im Projekt);
  abgesichert sind Repository, ViewModel, die pure Annahme-Logik und Previews.

## Verweise

- [ADR-0038](0038-delight-trigger-system.md) — Delight-Trigger-System (Schalter vor Produkt-Triggern)
- [ADR-0039](0039-delight-overlay.md) — Delight-Overlay (`delightEnabled`, `planDelightIntake`)
- [ADR-0009](0009-persistenz-tech.md) — Persistenz-Technik (Room)
