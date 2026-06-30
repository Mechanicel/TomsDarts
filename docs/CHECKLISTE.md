# TomsDarts — Projekt-Checkliste

> Diese Datei ist die **Single Source of Truth** für den Bau von TomsDarts.
> Sie enthält das Arbeitsprotokoll, alle Design-Entscheidungen, den aktuellen
> Status und die Bau-Roadmap als abhakbare Liste.

---

## Arbeitsprotokoll (für die Agents verbindlich)

**Orchestrator**
- Arbeitet diese Checkliste strikt von oben nach unten ab.
- Erledigt **genau eine** offene (`[ ]`) Aufgabe pro Durchlauf.
- **Stoppt danach und wartet auf das ausdrückliche „weiter"-Kommando von Tom.**
  Die nächste Aufgabe wird **nicht** automatisch begonnen.
- Startet keine Aufgabe, deren Vorbedingungen (z. B. Datenmodell) noch offen sind.
- Bei Unklarheit in einer Aufgabe: Rückfrage an Tom statt raten.

**Dokumentar**
- Ist Eigentümer dieser Datei und hält sie aktuell.
- Hakt nach Abschluss die erledigte Aufgabe ab (`[x]`) und ergänzt unter der
  Aufgabe eine knappe Notiz (was wurde gebaut, getroffene Detail-Entscheidungen).
- Trägt neu entdeckte Teil-Aufgaben an der passenden Stelle nach.
- Hält den Abschnitt „Design-Entscheidungen" aktuell, wenn Tom etwas ändert.
- Pflegt das Änderungslog am Ende.

**Design-Entscheidungen** gelten als festgelegt, solange Tom sie nicht ändert.

---

## Projekt-Überblick

- **App:** TomsDarts — native Android-App für Darts.
- **Charakter:** lokal, vollständig **offline**, **konfigurierbar**.
- **Bewusst nicht:** kein Login, kein Backend, keine Cloud, kein Tracking/Analytics-Dienst.
- **Spätere Option:** Veröffentlichung im Play Store ist denkbar (nicht jetzt).
  Konsequenzen, die deshalb beachtet werden: `applicationId` bleibt dauerhaft
  stabil, `targetSdk` aktuell halten. (Publishing kostet einmalig 25 USD und
  erfordert für neue Personen-Accounts geschlossenen Test + Identitätsprüfung.)

---

## Tech-Stack

- Sprache: **Kotlin**
- UI: **Jetpack Compose**
- Build: **Gradle (Kotlin DSL)**, Versionskatalog `libs.versions.toml`, Gradle-Wrapper im Repo
- `applicationId`: `com.mechanicel.tomsdarts`
- `minSdk`: 26
- Persistenz: **Room** (lokale Datenbank)
- Architektur: Spielmodi als **austauschbare Strategie** (gemeinsames Interface,
  jeder Modus implementiert seine Regeln) — umgesetzt als `GameMode<S>` im puren
  Domänen-Paket `com.mechanicel.tomsdarts.game` (Phase 2)

### Gradle-Befehle
- Build (Debug-APK): `./gradlew assembleDebug`
- Auf Gerät installieren: `./gradlew installDebug`
- Unit-Tests (JVM): `./gradlew test`
- Instrumented-Tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`
- Voller Check: `./gradlew build`

---

## Design-Entscheidungen (festgelegt)

### Profile
- Mehrere Menschen als Spielerprofile anlegbar (erstellen, auflisten, bearbeiten, löschen).

### Spielmodi
- Kern-Modus zuerst: **X01 mit 501 und Double-Out**.
  → **Umgesetzt (Phase 2 / „X01-Modus").** `X01Mode : GameMode<X01State>`
    (`key="X01"`); **startScore-agnostisch**, deckt damit **301/501/701** über
    `GameConfig.startScore` ab — siehe „Spielmodi-Domänenlogik" / „X01-Modus" unten.
- Geplantes Repertoire danach (Reihenfolge offen, anpassbar):
  301/701, Cricket, Around the Clock, Shanghai, Count Up / High Score, Killer.
  → **301/701 sind bereits durch den startScore-agnostischen `X01Mode` abgedeckt**
    (kein eigener Modus, nur andere `GameConfig.startScore`); die Konfig-Auswahl
    folgt in Phase 3 (Spiel-Setup-Screen).
- Jeder Modus wird über das gemeinsame Strategie-Interface umgesetzt, damit
  „neuer Modus" nicht „App umschreiben" bedeutet.
  → **Umgesetzt (Phase 2 / „Strategie-Interface").** Das gemeinsame Interface ist
    `GameMode<S>` im Paket `com.mechanicel.tomsdarts.game` (siehe Abschnitt
    „Spielmodi-Domänenlogik" unten).

### Eingabe (Ziffernblock)
- **Ziffernblock**, keine gezeichnete Dartscheibe.
- **Single** ist der stille Standardzustand.
- **Double / Triple** sind Modifikator-Tasten (Toggle). Nach **jedem** Dart
  springt der Modifikator automatisch zurück auf Single (Auto-Reset).
- Modifikator wird als Präfix **`D-`** / **`T-`** angezeigt (Großbuchstabe + Bindestrich),
  z. B. `D-20`, `T-20`. Sobald Double/Triple aktiv ist, zeigen die Zifferntasten
  das Präfix live (`D-1 … D-20`).
- **Layout:** Zahlen 1–20 links als 4-Spalten-Block. Rechts eine Spalte
  Sondertasten, von oben nach unten: **Undo, Out, Bull, Double, Triple**.
- **Out** = geworfener Dart mit **0 Punkten**, belegt einen der drei Wurf-Slots.
- **Bull** = 25. **Double-Bull** (`D-Bull`) = 50. Triple auf Bull existiert nicht
  (wirkungslos).
- **Erfassung Dart für Dart** (keine Rundensummen-Eingabe) — bewusst, weil die
  Einzel-Dart-Daten die Grundlage für Analytics und Spaß-Trigger sind.
- Drei Wurf-Slots pro Aufnahme. Auto-Verrechnung nach dem dritten Dart oder
  sofort beim Checkout (Rest auf 0).
- **Undo** entfernt den zuletzt eingegebenen Dart.

#### Umsetzung (festgelegt, Phase 2 / „Eingabe-Screen (Ziffernblock)")
- **Eingabe-Logik getrennt von der Compose-UI:** Die Wurf-Eingabe ist als pure,
  Compose-/Android-freie `DartInputState`-Logik (Paket
  `com.mechanicel.tomsdarts.ui.input`) gebaut und damit rein per JUnit (ohne
  Robolectric) testbar. Die Compose-Schicht (`DartKeypad.kt`) ist eine dünne,
  stateless/stateful getrennte Darstellung darüber — gleiches Muster wie bei der
  Profilverwaltung. Wiederverwendete Werte sind die Domänen-`Dart`s aus dem
  `game`-Paket (kein eigener Eingabe-Dart-Typ).
- **Rotationsfest über `rememberSaveable`-Saver:** Der `DartKeypad`-State (laufende
  Aufnahme inkl. aktivem Modifikator) übersteht Konfigurationsänderungen/Rotation.
- **Sperren statt Auto-Clear nach 3 Darts:** Ist die Aufnahme voll (`isComplete`),
  sind alle Eingabe-Transitionen No-ops; nur `undo` und `startNewTurn` bleiben
  wirksam. Das Leeren für die nächste Aufnahme (`startNewTurn()`) ist **bewusst
  Sache des Aufrufers** (spätere Spiel-Engine), nicht ein automatisches Clear —
  damit der Aufrufer die fertige Aufnahme zuerst verrechnen/speichern kann.
- **Aufnahme-Summe wird angezeigt** (`turnSum`, neben den drei Slot-Kacheln) als
  laufende Rückmeldung; die eigentliche Score-Verrechnung passiert erst beim
  Koppeln an die Spiel-Logik.
- **Bull-Semantik im Modifikator:** Bull ist im TRIPLE-Modus deaktiviert
  (`bullEnabled == false`, kein Triple-Bull); DOUBLE+Bull = Doppel-Bull (50),
  sonst Bull (25). Nach jeder Eingabe Auto-Reset auf SINGLE (auch nach `pressOut`).

### Datenhaltung (throw-level)
- **Jeder einzelne Dart wird gespeichert**: Segment (1–20, 25, 0), Multiplikator
  (1/2/3), resultierender Wert, Reihenfolge innerhalb der Aufnahme, Zuordnung zu
  Aufnahme/Leg/Match/Spieler. Pflicht für Analytics **und** Spaß-Trigger.

### Analytics
- Auswertbar: welche Felder ein Spieler wie oft trifft, in welcher Reihenfolge,
  erster Dart, Sequenzen, 3-Dart-Average, Checkout-Quote, First-9-Average,
  Trefferverteilung u. ä.

### Delight-Schicht (stumme Bildschirm-Animationen, wie beim Bowling)
- **Kein Ton.** Animation als Vollbild-Overlay, verschwindet automatisch.
- Trigger-System **datengetrieben und erweiterbar** (pro Trigger: Bedingung,
  Icon/Animation, Text), gespeist aus den throw-level-Daten.
- Bereits festgelegte Trigger:
  - **180** (Maximum) — Feier mit Konfetti.
  - **Waschmaschine** — die drei Darts liegen alle in {20, 5, 1}, aber nicht alle
    auf 20 (Streuen um die 20). Dreh-Animation.
  - **Rentnerdreieck** — die drei Darts liegen alle in {19, 7, 3}. Dreieck.
- Weitere Trigger später möglich (z. B. Madhaus / D1, Bull, Ton ab 100, …).

### Datenmodell (Room) — umgesetzt (Phase 1 / „Entities + DAOs")
> Das ursprünglich vorgeschlagene Modell ist umgesetzt. Feldnamen sind **englisch,
> camelCase** (konsistent zu `Player`). Zusätzlich zur ursprünglichen Skizze gibt es
> die Cross-Ref-Tabelle `MatchPlayer` (siehe Datenmodell-Entscheidungen unten).
- `Player` ("players"): id, name, createdAt, (optional Farbe/Avatar später)
- `Match` ("matches"): id, modusTyp + Konfiguration (Startpunkte, doubleOut, Legs, Sets),
  startedAt, finishedAt, winnerId (→Player, SET_NULL)
- `MatchPlayer` ("match_players"): id, matchId (→Match, CASCADE), playerId (→Player,
  RESTRICT), position — bildet Spielerliste/Reihenfolge relational ab
- `Leg` ("legs"): id, matchId (→Match, CASCADE), setNummer (optional), legNummer,
  winnerId (→Player, SET_NULL), startedAt, finishedAt
- `Turn` (Aufnahme, "turns"): id, legId (→Leg, CASCADE), playerId (→Player, RESTRICT),
  aufnahmeIndex, bustFlag, summeGeworfen
- `Throw` (Dart, "throws"): id, turnId (→Turn, CASCADE), dartIndex (0–2), segment
  (1–20/25/0), multiplier (1/2/3), value, timestamp (Epoch-Millis)

### Datenmodell-Entscheidungen (festgelegt)
- **`MatchPlayer`-Cross-Ref:** Die Match-Teilnahme (Spielerliste + Reihenfolge) wird
  über eine eigene Cross-Ref-Tabelle `match_players` (matchId/playerId/position)
  abgebildet statt als Liste im `Match` — sauber relational und über `position`
  geordnet abfragbar (`MatchPlayerDao.getByMatch` mit `ORDER BY position`).
- **FK-Löschstrategie (onDelete):**
  - **CASCADE** für die Besitz-Hierarchie: Leg→Match, Turn→Leg, Throw→Turn,
    MatchPlayer→Match. Löscht man ein Match/Leg/Turn, verschwinden die untergeordneten
    Daten mit.
  - **SET_NULL** für Gewinner-Referenzen `Match.winnerId` / `Leg.winnerId` (→Player):
    Spieler-Löschung entfernt nicht das Match/Leg, nur die Gewinner-Markierung.
  - **RESTRICT** für `Turn.playerId` und `MatchPlayer.playerId` (→Player): ein Spieler
    mit Spielhistorie kann nicht versehentlich gelöscht werden, solange er verknüpft ist.
  - Indizes liegen auf allen FK-Spalten.
- **Schema-Versionsstrategie (solange unveröffentlicht):** `@Database(version=1)`
  bleibt bei `1`; bei Modelländerungen wird das exportierte Schema-JSON
  (`app/schemas/.../1.json`) **regeneriert** statt eine Migration zu schreiben. Erst
  ab der ersten ausgelieferten Version werden Versionsschritte + Migrationen Pflicht.

### Persistenz-Tech (festgelegt)
- **Room `2.7.2`** als lokale Datenbank, eingebunden über KSP (`com.google.devtools.ksp`,
  `2.2.10-2.0.2`) als Annotation-Processor; alle Versionen im Versionskatalog
  `gradle/libs.versions.toml`.
- **Schema-Export ist an** (`room.schemaLocation`), die exportierten Schema-JSONs
  werden committet (`app/schemas/...`) — Grundlage für spätere Migrationstests.
- **AGP-9-Eigenheit:** AGP 9 bringt eingebautes Kotlin mit (kein separates
  `org.jetbrains.kotlin.android`-Plugin). Damit KSP funktioniert, ist
  `android.disallowKotlinSourceSets=false` in `gradle.properties` gesetzt.

### Test-Strategie Datenschicht (festgelegt)
- **Datenschicht-/DAO-Tests laufen host-seitig unter Robolectric** (`4.14.1`,
  `androidx.test:core 1.6.1`) gegen eine **In-Memory-Room-DB** und werden über
  `./gradlew test` ausgeführt — **nicht** als instrumented Tests, weil in der
  Bau-Umgebung **kein Gerät/Emulator** verfügbar ist. Die Testklassen liegen
  daher unter `app/src/test/...` (nicht `androidTest`).
- Pflicht-Konfiguration: `@Config(sdk = [34])` (Robolectric unterstützt
  compileSdk 36 noch nicht) und `testOptions { unitTests { isIncludeAndroidResources = true } }`.
- Dieses Muster (Robolectric + `@Config(sdk=[34])` + In-Memory-Room) ist der
  **Standard für künftige Datenschicht-Tests**.
- **Später möglich:** Wechsel auf reine instrumented Tests (`connectedAndroidTest`)
  sobald ein Gerät/Emulator bereitsteht, bzw. höhere `sdk`-Werte sobald Robolectric
  compileSdk 36 unterstützt.

### Repository-/DI-Schicht (festgelegt)
- **Manuelles DI über `AppContainer`** (`data/AppContainer.kt`) statt eines
  DI-Frameworks (kein Hilt/Dagger/Koin): bewusst minimal, hält die App schlank und
  offline. `AppContainer(context)` baut das DB-Singleton und stellt
  `playerRepository` / `matchRepository` (`by lazy`) bereit — der einzige
  Einstiegspunkt für die spätere UI-/ViewModel-Schicht.
- **DB-Singleton** `TomsDartsDatabase.getInstance(context)`: thread-sicheres,
  prozessweites Singleton (`@Volatile` + double-checked locking), file-basiert in
  `tomsdarts.db`, mit `fallbackToDestructiveMigration()` — konsistent zur
  Schema-Versionsstrategie „regenerieren statt migrieren" (solange unveröffentlicht).
- **Repository-Schicht ist regelfrei:** Repositories sind dünne Persistenz-Wrapper
  über den DAOs (reines Durchreichen). Spielregeln, Bust-/Checkout-/Score-Logik
  gehören **nicht** hierher, sondern in eine Domain-/ViewModel-Schicht in Phase 2.
- **Reaktives Lesen über `Flow`:** Listen, die die UI beobachten soll
  (`PlayerRepository.observePlayers` ↔ `PlayerDao.observeAll`, ORDER BY name),
  werden als `Flow` geliefert; punktuelle Reads/Writes bleiben `suspend`. Kein
  zusätzliches `withContext(Dispatchers.IO)` in den Repositories, da Rooms
  `suspend`-DAOs bereits auf dem eigenen Executor laufen.

### UI-/ViewModel-Schicht (festgelegt, ab Profilverwaltung)
- **Manuelles DI bis in die UI:** Kein DI-Framework auch in der UI-Schicht. Eine
  `TomsDartsApp : Application` hält den `AppContainer` (im Manifest als
  `android:name=".TomsDartsApp"`). ViewModels ziehen ihre Repositories über eine
  companion-`Factory` aus der `Application` — konsistent zum manuellen `AppContainer`-DI.
- **ViewModel-Anbindung an Compose:** über `androidx.lifecycle:lifecycle-viewmodel-compose`
  + `lifecycle-runtime-compose` (an lifecycle `2.6.1` gebunden); UI konsumiert State mit
  `viewModel(factory = …)` und `collectAsStateWithLifecycle()`.
- **UI-State-Muster:** pro Screen ein **sealed** UI-State (z. B. `ProfileUiState`:
  Loading/Empty/Content/Error) plus ein eigener sealed Dialog-State (z. B. `ProfileDialog`:
  None/Add/Edit/ConfirmDelete) als `StateFlow`. Listen-State entsteht reaktiv aus dem
  Repository-`Flow` (`observePlayers().map{…}.catch{Error}.stateIn(WhileSubscribed)`), ein
  `retryTrigger` erlaubt Neuversuch nach Fehler.
- **Stateless/Stateful-Trennung:** Jeder Screen wird in eine stateful Hülle
  (holt ViewModel) und eine stateless `…Content`-Composable (nimmt State + Callbacks)
  zerlegt — letztere ist Preview- und testbar (mehrere `@Preview` pro Screen-Zustand).
- **Bewusst KEINE Icon-Dependency** (`material-icons-*`): Symbole werden als Text-Glyphen
  bzw. Avatar-Initialen dargestellt, hält die App schlank.
- **Listeneintrag-Konvention:** Material3 `ListItem` + `HorizontalDivider`.
- **Test-Muster ViewModel:** ViewModel-/UI-State-Logik wird host-seitig getestet; der
  Coroutine-Test-Scheduler wird über eine gemeinsame `testing/MainDispatcherRule`
  (setzt `Dispatchers.Main` auf einen `UnconfinedTestDispatcher`) **an den Test-Scope
  gekoppelt**, damit `stateIn(WhileSubscribed)`-Flows deterministisch (nicht flaky) laufen.
  `kotlinx-coroutines-test` als `testImplementation`.

### Spielmodi-Domänenlogik (festgelegt, ab Phase 2)
- **Austauschbare Modi über generisches Strategie-Interface `GameMode<S : Any>`**
  (Paket `com.mechanicel.tomsdarts.game`): Jeder Modus implementiert seine Regeln
  mit einem eigenen, modus-spezifischen Spielerzustand `S`. So bedeutet „neuer Modus"
  nicht „App umschreiben".
- **Pure Domänenlogik, strikt entkoppelt von Persistenz und UI:** Das `game`-Paket
  hat **keine** Android-/Room-/Compose-Abhängigkeit und ist mit reinem JUnit (ohne
  Robolectric) testbar. Die Value-Objekte sind bewusst von den Room-Entities getrennt:
  `Dart` ≠ `data.entity.Throw` (keine IDs/Timestamps), `GameConfig` ≠ `data.entity.Match`.
- **Bust-/legWon-Semantik:** `applyDart` verarbeitet **genau einen** Dart. Die Strategie
  ist **zustandslos bzgl. Aufnahme-Grenzen** (3 Darts pro Aufnahme) — das **Bündeln zu
  Aufnahmen und das Bust-Zurücksetzen** (Verwerfen der bereits geworfenen Darts,
  Rückkehr zum Aufnahme-Startzustand) verantwortet die **aufrufende Engine**, nicht die
  Strategie. `applyDart` meldet nur `bust == true` und liefert einen `newState`, den die
  Engine bei Bust verwirft. Invariante: **`bust` XOR `legWon`** (nie beide gleichzeitig).
- **`GameConfig` vs. Persistenz — Modus-Identität über `key`:** `GameConfig` ist das
  Domänen-Konfig (Startpunkte, Double-Out, Legs/Sets); der Modus wird über `GameMode.key`
  (stabile, persistierbare Kennung) identifiziert — **kein `modeType`-Feld in `GameConfig`**.
  Die spätere Engine-/Mapping-Schicht führt `GameConfig` + `key` mit der `Match`-Entity
  (`modusTyp`/`modeType`) zusammen.
- **Erweiterbarkeit (YAGNI):** Modus-spezifische Konfigfelder (z.B. Cricket-Zahlen,
  Around-the-Clock-Optionen, Count-Up-Ziel) werden **bewusst NICHT vorab** in `GameConfig`
  aufgenommen, sondern bei Bedarf modusspezifisch ergänzt — statt `GameConfig` aufzublähen.
- **X01 als erste konkrete Strategie (Phase 2):** `X01Mode : GameMode<X01State>`
  (`key="X01"`) ist die erste echte Modus-Implementierung. Bewusst
  **startScore-agnostisch** — 301/501/701 sind **ein** Modus mit unterschiedlicher
  `GameConfig.startScore`, **kein** separater Modus pro Startwert. Double-Out wird
  über `GameConfig.doubleOut` gesteuert; Doppel-Bull (50) zählt beim Finish als
  Double (`Dart.isDouble`). Eigener Zustands-Typ `X01State(remaining)` statt nacktem
  `Int`, damit der Zustand lesbar und später erweiterbar bleibt (z.B. Statistik),
  ohne den `GameMode`-Vertrag zu brechen.
- **`X01Mode` vertraut auf valide Eingaben (keine Guards):** Eingabe-Robustheit ist
  bewusst **nicht** in `X01Mode` — Plausibilitätsprüfung von Darts/Config ist Sache
  der späteren Engine bzw. eine Produktentscheidung (vgl. `Dart.isValid` +
  Config-Validierung). Siehe Backlog „Engine validiert Darts/Config". Die Tests
  dokumentieren das IST-Verhalten (kein Guard gegen `startScore <= 0`, `isValid` wird
  in `applyDart` nicht konsultiert, negativer `dart.value` erhöht den Rest).

### Spiel-Engine & Eingabe-Kopplung (festgelegt, Phase 2 / „Eingabe an die Spiel-Logik koppeln")
- **Pure, modus-agnostische Engine `LegEngine<S>`** (`com.mechanicel.tomsdarts.game.engine`,
  weiterhin Android-/Room-/Compose-frei, rein JUnit-testbar): Sie verantwortet genau das,
  was die `GameMode`-Strategie bewusst NICHT tut (siehe „Spielmodi-Domänenlogik") — die
  **Aufnahme-Bündelung** (max. 3 Darts), das **Bust-Revert** (State zurück auf den
  Aufnahme-Startzustand, Verwerfen der Aufnahme-Darts) und den **Sofort-Checkout**
  (Aufnahme endet beim Leg-Sieg vor dem dritten Dart). Schnittstellen: `applyDart →
  DartResult`, `startNewTurn`, `snapshot` (`LegEngineSnapshot`) und `undoLastDart()`
  (In-Turn-Undo; No-op bei leerer oder bereits beendeter Aufnahme).
- **Engine-getriebene Kopplung im `GameViewModel`** (`ui/game/`): Das ViewModel hält den
  gehoisteten `DartInputState` (UI-Eingabe) **und** die `LegEngine` und hält beide
  synchron — es repliziert die Keypad-Transition und treibt die Engine Dart für Dart.
  Eingabe-Logik (Anzeige/Modifikator) bleibt damit von der Score-Logik (Engine) getrennt.
- **Throw-level-Persistenz pro Aufnahme:** Erst beim Aufnahme-Ende wird ein
  `Turn`(turnIndex, bust, totalScored) + alle zugehörigen `Throw`s geschrieben.
  **Bust-Darts werden mitgespeichert** (alle real geworfenen Darts der Aufnahme) —
  konsistent zur Design-Entscheidung „Datenhaltung (throw-level)". (Semantik von
  `dartsUsed` / „nur gewertete Darts" ist offen, siehe Backlog.)
- **`@Update` statt `@Insert(REPLACE)` für Match-/Leg-Abschluss:** Das Setzen von
  `endedAt`/`winnerId` läuft über neue `@Update`-DAO-Methoden
  (`MatchRepository.updateMatch`/`updateLeg`), **nicht** über ein REPLACE-Insert — sonst
  hätte `ON DELETE CASCADE` die bereits persistierten Kind-Datensätze (Legs/Turns/Throws)
  beim Ersetzen mitgelöscht. Reine DAO-Erweiterung, **kein Schema-Drift**.
- **`GameUiState` als sealed State** (Loading/Error/NoPlayer/Playing/Won) plus ein
  separater transienter **`bustEvents: StateFlow<Int>`** (zählender Tick) für das einmalige
  Bust-Banner — konsistent zum sealed-UI-State-Muster der Profilverwaltung.
- **Navigation vorerst als MainActivity-State-Switch:** Profil ⇄ Spiel über
  `rememberSaveable`-State (`SCREEN_PROFILE`/`SCREEN_GAME`), **bewusst ohne
  navigation-compose** — minimal gehalten, solange es nur zwei Screens gibt. Ein echter
  Navigationsgraph kann später nachgezogen werden (z.B. mit Spiel-Setup-Screen in Phase 3).
- **Spiel-Einstieg über Profil-Tap:** Tap auf den Spieler-Body in der Profilliste startet
  ein Spiel für diesen Spieler (`onPlayClick`/`onPlay`); Bearbeiten/Löschen wandern in das
  Overflow-Menü (vorher war der Zeilen-Tap = Bearbeiten).
- **`Match.modeType = "X01"`:** Die Persistenz-Modus-Kennung der `Match`-Entity wird mit
  `GameMode.key` (`"X01"`) befüllt — das in „Spielmodi-Domänenlogik" beschriebene
  Mapping `GameConfig`+`key` ↔ `Match` ist damit erstmals konkret umgesetzt.

---

## Status: Setup

- [x] GitHub-Repo angelegt (`Mechanicel/TomsDarts`)
- [x] Android Studio installiert (Standard-Setup)
- [x] Kotlin/Compose-Gerüst per Wizard erzeugt (Empty Activity / Compose, Kotlin DSL)
- [x] Gerüst ins geklonte Repo `Tom_Darts` migriert; `.gitignore` zusammengeführt;
      `CLAUDE.md`, `README`, `.claude/` unangetastet
- [x] Gradle-Sync + `Make Project` erfolgreich (BUILD SUCCESSFUL)
- [x] App läuft auf echtem Gerät (Samsung S25 / SM-S931B, Wireless Debugging)
- [x] Agent-Setup vom Altprojekt migriert und auf Android angepasst
      (implementer, tester, reviewer, fixer, dokumentar, designer + Orchestrator-Loop)

---

## Offene Setup-Aufgaben

- [x] Android-Gerüst committen (`chore/android-scaffold`) und nach `main` mergen
      (falls noch nicht geschehen — mit `git status` prüfen)
      → Verifiziert: Gerüst liegt via Commit `db1f618` („chore: scaffold
        Kotlin/Compose Android project") auf `main` (Commit ist Vorfahr von `main`).
        Working Tree sauber, keine untracked Dateien. Zentrale Gradle-/App-Dateien
        getrackt (`gradlew`, `gradlew.bat`, `settings.gradle.kts`, `build.gradle.kts`,
        `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.jar`,
        `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`,
        `app/src/main/java/com/mechanicel/tomsdarts/MainActivity.kt`).
        War bereits durch frühere Commits erledigt — kein eigener
        Implementierungsschritt nötig, nur als erledigt dokumentiert.
- [x] Echte Gradle-Befehle in `CLAUDE.md` und alle Agents eintragen
      (Platzhalter ersetzen; Befehle siehe Abschnitt „Tech-Stack")
      → Verifiziert: Befehle via Commit `f22cac7` („docs: Build-/Test-/Lint-Befehle
        und Tech-Stack in CLAUDE.md und Agents eintragen") eingetragen — vollständige
        `./gradlew`-Tabelle in `CLAUDE.md` (`assembleDebug`, `installDebug`, `test`,
        `connectedAndroidTest`, `lint`, `build`) und echte `./gradlew`-Befehle in
        `implementer`, `tester`, `fixer`, `reviewer`. `designer` (read-only, plant nur)
        und `dokumentar` (git-basiert) brauchen keine Gradle-Befehle. Keine Platzhalter
        mehr. War bereits durch früheren Commit erledigt — kein eigener
        Implementierungsschritt nötig, nur als erledigt dokumentiert.
- [x] In `CLAUDE.md` einen Abschnitt „Tech-Stack" ergänzen und den Hinweis
      „Tech-Stack noch nicht eingerichtet / kein Scaffolding" entfernen
      → Verifiziert: `CLAUDE.md` enthält via Commit `f22cac7` („docs: Build-/Test-/Lint-
        Befehle und Tech-Stack in CLAUDE.md und Agents eintragen") den Abschnitt
        `## Tech-Stack` (Kotlin, Jetpack Compose, Gradle Kotlin DSL, minSdk 26,
        `applicationId`). Der Hinweis „Tech-Stack noch nicht eingerichtet / kein
        Scaffolding" existiert nirgends mehr in `CLAUDE.md`. War bereits durch
        früheren Commit erledigt — kein eigener Implementierungsschritt nötig, nur
        als erledigt dokumentiert.
- [x] Diese `CHECKLISTE.md` ins Repo übernehmen und committen
      → liegt unter `docs/CHECKLISTE.md`; als zentrale Steuerung in `CLAUDE.md`
        (Orchestrator-Loop) und im `dokumentar`-Agent verankert.

---

## Bau-Roadmap

### Phase 1 — Fundament ✅ vollständig abgeschlossen
- [x] Room einrichten (Dependencies, Database-Klasse, Schema-Export)
      → Room `2.7.2` (`room-runtime`, `room-ktx`, `room-compiler`) + KSP-Plugin
        `2.2.10-2.0.2` über den Versionskatalog (`gradle/libs.versions.toml`)
        eingerichtet. Schema-Export aktiv (`room.schemaLocation` →
        `app/schemas/...TomsDartsDatabase/1.json`, committet; `app/schemas` als
        androidTest-Asset registriert). Minimale `@Database`-Klasse
        `TomsDartsDatabase` (version 1, `exportSchema=true`) mit `Player` als
        Seed-Entity + `PlayerDao`, damit die DB kompiliert/testbar ist.
        AGP-9-Workaround `android.disallowKotlinSourceSets=false` in
        `gradle.properties` (AGP 9 hat eingebautes Kotlin, KSP braucht das).
        Tests host-seitig unter Robolectric grün; `test`, `lint`, `assembleDebug`
        alle BUILD SUCCESSFUL.
- [x] Entities + DAOs: `Player`, `Match`, `Leg`, `Turn`, `Throw`
      → `Player` + `PlayerDao` waren bereits als Seed-Entity aus der Room-Einricht-
        Aufgabe vorhanden. Neu ergänzt: Entities `Match` ("matches"), `Leg` ("legs"),
        `Turn` ("turns"), `Throw` ("throws") sowie die Cross-Ref `MatchPlayer`
        ("match_players": matchId/playerId/position) — letztere als bewusste Ergänzung,
        um die in der Match-Config genannte „Spielerliste/Reihenfolge" relational sauber
        abzubilden. Englische camelCase-Feldnamen konsistent zu `Player`. Je ein DAO
        im Stil von `PlayerDao` (alle `suspend`): `MatchDao` (insert/getById/getAll/
        delete), `LegDao` (insert/getById/getByMatch), `TurnDao` (insert/getById/
        getByLeg), `ThrowDao` (insert/getById/getByTurn), `MatchPlayerDao`
        (insert/getById/getByMatch ORDER BY position). Alle in `TomsDartsDatabase`
        eingebunden (`@Database(entities=[Player, Match, Leg, Turn, MatchPlayer, Throw],
        version=1, exportSchema=true)`); **version bleibt 1** (App unveröffentlicht,
        keine Migration), Schema-JSON `app/schemas/.../1.json` neu erzeugt + committet.
        FK-/onDelete-Strategie: CASCADE für die Hierarchie (Leg→Match, Turn→Leg,
        Throw→Turn, MatchPlayer→Match), SET_NULL für `Match.winnerId`/`Leg.winnerId`
        (→Player), RESTRICT für `Turn.playerId` und `MatchPlayer.playerId` (→Player,
        damit Spieler mit Spielhistorie nicht versehentlich gelöscht werden). Indizes
        auf allen FK-Spalten. Tests host-seitig (Robolectric, `./gradlew test`):
        Smoke-Test + 43 Edge-/Beziehungs-/FK-Constraint-Tests, alle grün
        (CASCADE/SET_NULL/RESTRICT real verifiziert). `test`, `lint`, `assembleDebug`
        BUILD SUCCESSFUL.
- [x] Repository-Schicht über den DAOs
      → `PlayerRepository` (`data/repository/`, ctor `PlayerDao`):
        `observePlayers(): Flow<List<Player>>`, `getPlayer`, `addPlayer(name)`
        (setzt `createdAt`), `updatePlayer`, `deletePlayer`. `MatchRepository`
        (`data/repository/`, ctor MatchDao/LegDao/TurnDao/ThrowDao/MatchPlayerDao):
        dünne, **regelfreie** Persistenz-Ops — `createMatch`, `addPlayerToMatch`,
        `addLeg`, `addTurn`, `addThrow`; Reads `getMatches`/`getLegs`/`getTurns`/
        `getThrows`/`getMatchPlayers`; `deleteMatch`. Keine Spielregeln/Score-Logik
        (kommt in Phase 2). `PlayerDao` erweitert um `update`, `delete` und
        `observeAll(): Flow<List<Player>>` (ORDER BY name) — Flow-basierte
        Beobachtung als reaktiver Standard für die spätere UI. Manueller
        DI-Einstiegspunkt `AppContainer` (`data/AppContainer.kt`, ctor Context,
        kein Hilt) baut DB-Singleton + `playerRepository`/`matchRepository`
        (`by lazy`). DB-Singleton `TomsDartsDatabase.getInstance(context)`
        (thread-sicher, `@Volatile` double-checked, file-basiert `tomsdarts.db`,
        `fallbackToDestructiveMigration()`). Tests host-seitig (Robolectric,
        `./gradlew test`): PlayerRepository Smoke + 8 Edge-Cases, MatchRepository 9
        (Reads/Isolation/CASCADE/Sortierung), AppContainer 1 Smoke — 18+ grün, kein
        Schema-Drift. `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL.
- [x] Profilverwaltung: Spieler anlegen / auflisten / bearbeiten / löschen (UI + Persistenz)
      → Erste Compose-UI der App: `ui/profile/ProfileScreen.kt` (stateful + stateless
        `ProfileScreenContent`, TopAppBar „Spieler", ExtendedFAB „Neuer Spieler",
        Loading/Empty/Content/Error-Rendering, Liste auf `widthIn(max=600.dp)` zentriert,
        6 Previews), `ui/profile/PlayerListItem.kt` (Material3 `ListItem` +
        `HorizontalDivider`, Avatar-Initiale, „Erstellt am dd.MM.yyyy",
        Overflow-`DropdownMenu` Bearbeiten/Löschen, Zeilen-Tap → Bearbeiten),
        `ui/profile/ProfileDialogs.kt` (`PlayerEditDialog` für Add+Edit mit
        Autofokus/Trim/Validierung, `DeletePlayerDialog` mit Bestätigung). State/Logik:
        `ui/profile/ProfileUiState.kt` (sealed `ProfileUiState` Loading/Empty/Content/Error,
        sealed `ProfileDialog` None/Add/Edit/ConfirmDelete) + `ui/profile/ProfileViewModel.kt`
        (`uiState`/`dialog` als `StateFlow`, CRUD über `PlayerRepository` mit Namens-Trim +
        Ablehnung leerer Namen, `retry()`, companion `Factory`). App-Plumbing:
        `TomsDartsApp : Application` (hält `AppContainer`, im Manifest als
        `android:name=".TomsDartsApp"`), neue Compose-Lifecycle-Deps
        (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` an lifecycle 2.6.1) +
        `kotlinx-coroutines-test`. Alle UI-Strings in `res/values/strings.xml` (deutsch).
        `MainActivity` zeigt jetzt `ProfileScreen` (Greeting entfernt). **Bewusst keine
        Icon-Dependency** — Text-Glyphen/Avatar-Initiale. 19 ViewModel-Tests host-seitig
        (Robolectric, `./gradlew test`) grün; Test-Flakiness über gemeinsame
        `testing/MainDispatcherRule` deterministisch behoben. `test`, `lint`,
        `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
        **Verifikation offen:** Compose-UI wurde nur kompiliert + via Previews + ViewModel-
        Logik getestet, **nicht** auf echtem Gerät/Emulator (keiner in der Bau-Umgebung) —
        siehe Backlog/Phase-2-Zeile „Auf echtem Gerät (S25) testen".
      → **Damit ist Phase 1 — Fundament vollständig abgeschlossen.**

### Phase 2 — Kern-Gameplay (erste spielbare Scheibe)
- [x] Strategie-Interface für Spielmodi definieren
      → Pures Domänen-Paket `com.mechanicel.tomsdarts.game` (KEINE Android-/Room-/
        Compose-Abhängigkeit → reines JUnit, ohne Robolectric). Generisches
        Strategie-Interface `GameMode<S : Any>` mit modus-spezifischem Spielerzustand
        `S`: `key` (stabile, persistierbare Modus-Kennung, z.B. "X01"), `displayName`,
        `initialState(config): S`, `applyDart(state, dart, config): DartOutcome<S>`.
        Value-Objekte: `Dart(segment, multiplier)` (`value = segment*multiplier`,
        `isDouble`/`isTriple`, `isValid` mit Board-Regeln: segment∈{0,1..20,25},
        mult∈1..3, Miss nur Single, Bull nur Single/Double = kein Triple-Bull;
        Factories `single/double/triple/bull/doubleBull/miss`), `GameConfig`
        (`startScore=501`, `doubleOut=true`, `legsToWin=1`, `setsToWin=1` — pures
        Domänen-Konfig, entkoppelt von der `Match`-Entity; Modus-Identität über
        `GameMode.key`, kein `modeType`-Feld in `GameConfig`), `DartOutcome<S>`
        (`newState`, `bust`, `legWon`, `scored`). **Vertrag (KDoc):** `applyDart`
        verarbeitet GENAU EINEN Dart; die Strategie ist zustandslos bzgl.
        Aufnahme-Grenzen — das **Bust-Zurücksetzen** (Verwerfen der Aufnahme-Darts)
        obliegt der aufrufenden Engine; `bust` und `legWon` nie gleichzeitig
        (`bust` XOR `legWon`). Tests rein JUnit: `DartTest` (14) + `GameModeContractTest`
        (14, mit Count-Up- und X01-Fake nur als Testcode) — 28 grün. `test`, `lint`,
        `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- [x] X01-Modus (501, Double-Out) implementieren: Aufrechnen, Bust-Logik,
      Checkout-/Double-Out-Prüfung, Leg-Sieg
      → Erste konkrete `GameMode`-Strategie: `X01Mode : GameMode<X01State>`
        (`key="X01"`, `displayName="X01"`) im puren Domänen-Paket
        `com.mechanicel.tomsdarts.game`, `X01State(remaining: Int)` als
        Spielerzustand. `initialState(config) = X01State(config.startScore)`.
        Regeln in `applyDart`: `newRemaining = remaining - dart.value`; `< 0` → Bust.
        Mit Double-Out (`config.doubleOut == true`): `== 0 && dart.isDouble` → Leg
        gewonnen (Doppel-Bull zählt als Double); `== 0` ohne Double → Bust (Finish
        nur per Double); `== 1` → Bust (Rest 1 nicht per Double ausspielbar); sonst
        regulär. Ohne Double-Out: `== 0` mit beliebigem Wurf → Leg gewonnen. Bust
        gibt den **unveränderten Eingangszustand** zurück (`scored = 0`); Engine
        verwirft ohnehin. Invariante **`bust` XOR `legWon`** eingehalten.
        **startScore-agnostisch:** 301/501/701 unterscheiden sich nur über
        `GameConfig.startScore` → **kein separater Modus** für 301/701 nötig (deckt
        die Phase-3-„Startpunkte"-Auswahl mit ab). Tests rein JUnit: `X01ModeTest`
        (9) + `X01ModeEdgeCasesTest` (22) = **31 grün** — Aufrechnen/Sequenzen,
        Checkout-Varianten (D20/D1/Doppel-Bull/High-Checkout 170), Bust-Varianten,
        `doubleOut=false`, 301/701, Invariante `bust` XOR `legWon`, Eingabe-
        Robustheit (dokumentiert IST-Verhalten, siehe Backlog). `test`, `lint`,
        `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- [x] Eingabe-Screen (Ziffernblock) in Compose bauen, gemäß Design-Entscheidungen
      → Ziffernblock als eigenständige Komponente umgesetzt — **noch nicht** an die
        Spiel-Logik/Persistenz gekoppelt (das ist die nächste Roadmap-Zeile). Zwei
        Bausteine im neuen Paket `com.mechanicel.tomsdarts.ui.input`:
        (1) **Pure Eingabe-Logik** (Compose-/Android-frei, rein JUnit-testbar):
        `DartModifier{SINGLE,DOUBLE,TRIPLE}` (+ `multiplier`-Extension 1/2/3) und der
        immutable State-Holder `DartInputState(modifier, darts)` (max. 3 Darts) mit
        Properties `isComplete`/`turnSum`/`canUndo`/`bullEnabled`/`inputEnabled` und
        puren Transitionen `toggleDouble`/`toggleTriple` (exklusiv, erneut→SINGLE),
        `pressNumber(n)` (nur 1..20, Auto-Reset auf SINGLE), `pressBull` (No-op bei
        TRIPLE; DOUBLE→Doppel-Bull/50, sonst 25; Auto-Reset), `pressOut`
        (Miss/0 + Auto-Reset), `undo` (Modifier unverändert), `startNewTurn`. Nach
        drei Darts sind alle Eingabe-Transitionen No-ops; nur `undo`/`startNewTurn`
        bleiben wirksam. Pure Label-Funktionen (`DartLabel.kt`):
        `dartShortLabel`/`numberKeyLabel`/`bullKeyLabel` (Präfixe `D-`/`T-`,
        „Out"/„Bull"/„D-Bull"; bewusst nicht in strings.xml, da nicht-lokalisierte
        Glyphen). (2) **Compose-UI** (`DartKeypad.kt`): stateless `DartKeypadContent`
        + stateful `DartKeypad` (`rememberSaveable`-Saver → Aufnahme übersteht
        Rotation), Callback-Bündel `DartKeypadCallbacks` (inkl. `onDart`/
        `onTurnComplete` als Kopplungspunkte für die nächste Aufgabe). Layout: oben
        `TurnSlots` (3 Slot-Kacheln + Aufnahme-Summe), darunter 5×5-Raster
        (Zahlen 1–20 als 4-Spalten-Block links, Sondertasten rechts: Undo/Out/Bull/
        Double/Triple); Live-`D-`/`T-`-Präfixe auf den Zifferntasten, Double/Triple
        als `selected`-Toggle, bei voller Aufnahme nur Undo aktiv, Bull bei TRIPLE
        deaktiviert. Responsive (Portrait/Landscape via `BoxWithConstraints`,
        `widthIn(max≈600dp)`), A11y-contentDescriptions, Material3-Farbrollen,
        **keine** Icon-Dependency. Neue Strings im Block „Eingabe (Ziffernblock)"
        in `res/values/strings.xml`; 6 Previews. Tests rein JUnit:
        `DartInputStateTest` (49) + `DartLabelTest` (14) = **63 grün** (keine
        Compose-Instrumentationstests — kein Emulator). `test`, `lint`,
        `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- [x] Eingabe an die Spiel-Logik koppeln; throw-level speichern
      → Vollständige Kopplung **Ziffernblock → Spiel-Logik → Persistenz** für ein
        **Einzelspieler-X01-Leg** (Zwei Spieler / Legs / Sets folgen in der nächsten
        Zeile). Drei Bausteine:
        (1) **Pure Engine** `game.engine.LegEngine<S>` (modus-agnostisch, treibt
        `GameMode<S>`/`X01Mode` Dart für Dart): Aufnahme-Verwaltung (max. 3 Darts),
        **Bust-Revert** (State zurück auf den Aufnahme-Startzustand), **Sofort-Checkout**,
        `applyDart → DartResult`, `startNewTurn`, `snapshot`, **`undoLastDart()`**
        (In-Turn-Undo; No-op bei leerer/beendeter Aufnahme). + `DartResult` /
        `LegEngineSnapshot`. (2) **`GameViewModel`** (`ui/game/`): repliziert die
        gehoisteten `DartInputState`-Transitionen und treibt damit die Engine; legt
        bei Init Match (`modeType="X01"`, `GameConfig(501, doubleOut=true, legsToWin=1,
        setsToWin=1)`) + `MatchPlayer` + erstes Leg an; **persistiert throw-level**:
        pro Aufnahme-Ende ein `Turn`(turnIndex, bust, totalScored) + dessen `Throw`s
        (alle geworfenen Darts inkl. Bust-Darts); bei Checkout `endedAt`/`winnerId`
        auf Leg + Match. `onUndo`, `onNewLeg` (neues Leg im selben Match), Factory
        `provideFactory(playerId)`. Exponiert `uiState: StateFlow<GameUiState>`
        (Loading/Error/NoPlayer/Playing/Won) + `bustEvents: StateFlow<Int>`
        (transientes Bust-Signal). (3) **UI** `ui/game/GameScreen.kt`
        (stateful + stateless, 6 Previews): Scoreboard (Restpunkte groß, liveRegion),
        eingebetteter `DartKeypadContent`, transientes **Bust-Banner** (errorContainer,
        liveRegion=Assertive), **Won-Panel** („Geschafft!" + „Neues Leg"/„Zurück"),
        Loading/Error/NoPlayer; Strings im Block „Spiel" in `strings.xml`, Material3,
        keine Icon-Dependency. **Repository-Erweiterung:** `MatchDao`/`LegDao` um
        `@Update` + `MatchRepository.updateMatch`/`updateLeg` ergänzt (reine
        DAO-Methoden, **kein Schema-Drift**) — nötig, weil `@Insert(REPLACE)` über
        `ON DELETE CASCADE` die schon persistierten Kinder gelöscht hätte.
        **Einstieg:** `MainActivity` als einfacher State-Switch Profil ⇄ Spiel
        (kein navigation-compose, `rememberSaveable`); im Profil startet **Tap auf
        den Spieler-Body** das Spiel (Edit/Delete nur noch im Overflow-Menü),
        `ProfileScreen`/`PlayerListItem` um `onPlayClick`/`onPlay` erweitert. Tests
        rein JVM/Robolectric: `LegEngineTest` + `LegEngineEdgeCasesTest` (inkl. undo)
        sowie **15 GameViewModel-Tests** (`GameViewModelTest` 6 +
        `GameViewModelEdgeCasesTest` 9; Persistenz über `getTurns`/`getThrows` belegt:
        Turns/Throws/dartIndex, Bust-Turn, Checkout→endedAt/winnerId, onUndo, onNewLeg,
        NoPlayer, Toggle) — alle grün, deterministisch. `test`, `lint`, `assembleDebug`
        BUILD SUCCESSFUL, kein Schema-Drift.
- [ ] Zwei Spieler, Aufnahme-Wechsel, Legs/Sets
- [ ] Auf echtem Gerät (S25) testen

### Phase 3 — Konfigurierbarkeit
- [ ] Spiel-Setup-Screen: Startpunkte (301/501/701), Spieleranzahl,
      Double-Out an/aus, Anzahl Legs/Sets, „Best of X"
- [ ] Modus-Auswahl

### Phase 4 — Weitere Spielmodi
- [ ] Cricket
- [ ] Around the Clock
- [ ] Shanghai
- [ ] Count Up / High Score
- [ ] Killer

### Phase 5 — Analytics
- [ ] Auswertungs-Queries auf throw-level-Daten
- [ ] Kennzahlen: 3-Dart-Average, First-9-Average, Checkout-Quote, Trefferverteilung
- [ ] Sequenz-/Reihenfolge-Auswertungen
- [ ] Analytics-Screens (pro Spieler / pro Match)

### Phase 6 — Delight-Schicht
- [ ] Datengetriebenes Trigger-/Animations-System (Bedingung → Animation/Text)
- [ ] Trigger: 180, Waschmaschine, Rentnerdreieck
- [ ] Weitere Trigger (Madhaus, Bull, Ton, …) ergänzbar machen
- [ ] Stumme Vollbild-Animationen, Auto-Dismiss

---

## Backlog / spätere Ideen
- **Engine-/Mapping-Schicht für Spielmodi (Orientierung für X01- und Engine-Aufgaben):**
  Die spätere Spiel-Engine muss das pure Domänen-Konfig `GameConfig` + `GameMode.key`
  mit der Persistenz (`Match`-Entity inkl. `modusTyp`/`modeType`, `Turn`/`Throw`)
  zusammenführen — eine Mapping-Schicht (Domäne ↔ Room). Außerdem liegt die
  Aufnahme-Bündelung (3 Darts) und das **Bust-Revert** (Verwerfen der Aufnahme-Darts)
  in der Engine, nicht in der `GameMode`-Strategie (siehe Design-Entscheidung
  „Spielmodi-Domänenlogik"). Auch die in Phase 1 zurückgestellte Wert-Plausibilität
  der Wurfdaten (siehe Backlog-Punkt „Keine Wert-Plausibilität auf DB-Ebene") kann
  hier über `Dart.isValid` greifen.
- **Engine validiert Darts/Config (`X01Mode` vertraut auf valide Eingaben):**
  `X01Mode` enthält bewusst keine Eingabe-Guards (Validierung = Sache der späteren
  Engine / Produktentscheidung). Konkret nicht abgesichert (per Test als IST-Verhalten
  dokumentiert): (1) `initialState` hat keinen Guard gegen `startScore <= 0`;
  (2) `applyDart` konsultiert `Dart.isValid` nicht — physikalisch unmögliche Würfe
  (z.B. Triple-Bull, Segment > 20) werden über `dart.value` verrechnet; (3) negativer
  `dart.value` erhöht den Rest. Die aufrufende Engine sollte Darts/Config validieren
  (z.B. über `Dart.isValid` + Config-Validierung), bevor sie an die Strategie gehen.
  Hängt mit dem Backlog-Punkt „Engine-/Mapping-Schicht für Spielmodi" zusammen.
- **`Player.name` ohne Constraints:** Aktuell wird weder Leer-Name noch
  Eindeutigkeit erzwungen (kein `@NonNull`-Check über Kotlin hinaus, kein
  Unique-Index). Ob doppelte/leere Spielernamen erlaubt sein sollen, ist eine
  spätere Produktentscheidung (ggf. Unique-Index + Validierung in der UI).
- ~~**`PlayerDao` hat kein `delete()`:** Das Spieler-Löschen fehlt im DAO; SET_NULL/
  RESTRICT-Verhalten wurde in den Tests deshalb über direktes SQL ausgelöst.~~
  **(erledigt — Phase 1 / „Repository-Schicht")**: `PlayerDao` hat nun `delete`
  (und `update` + `observeAll`), `PlayerRepository.deletePlayer` reicht es durch.
  Hinweis: Die bestehenden FK-Constraint-Tests aus „Entities + DAOs" lösen das
  Lösch-Verhalten weiterhin über rohes SQL aus; eine Umstellung auf `PlayerDao.delete`
  kann bei der Profilverwaltung (Phase 1) nachgezogen werden.
- **`fallbackToDestructiveMigration()` (no-arg) ist deprecated:** In der genutzten
  Room-Version erzeugt der parameterlose Aufruf eine Deprecation-Warnung (kein
  Fehler). Später auf die parameterisierte Überladung umstellen.
- **`AppContainer`/DB-Singleton ist unter Robolectric nur eingeschränkt testbar:**
  Das file-basierte, prozessweite DB-Singleton führt bei mehreren Testmethoden zu
  „Illegal connection pointer". Für breitere Integrationstests später eine
  injizierbare/zurücksetzbare DB-Instanz vorsehen (Produktionscode-Änderung, kein
  Tester-Thema).
- **Keine Wert-Plausibilität auf DB-Ebene (keine CHECK-Constraints):** Die DB erzwingt
  keine fachliche Gültigkeit der Wurfdaten — z. B. `multiplier` außerhalb 1–3, `segment`
  außerhalb {0, 1–20, 25}, `value ≠ segment * multiplier` oder negative Scores werden
  **nicht** verhindert. Diese Plausibilität soll später in der Spiel-/Score-Logik
  (Phase 2) geprüft werden.
- **Undo-Snackbar nach Löschen (zurückgestellt):** Beim Löschen eines Spielers war eine
  Undo-Snackbar als Komfort-Funktion angedacht (Design-Entscheidung D), wurde aber bewusst
  auf den Backlog geschoben. Aktuell ist Löschen direkt + Bestätigungsdialog, kein Undo.
- **Hinweis/Behandlung doppelter Spielernamen (zurückgestellt):** Beim Anlegen/Bearbeiten
  wird derzeit kein Hinweis bei doppeltem Namen gezeigt (Design-Entscheidung E, zurückgestellt).
  Hängt mit dem bestehenden Backlog-Punkt „`Player.name` ohne Constraints" zusammen.
- **`PluralsCandidate`-Lint-Warnungen am Ziffernblock:** 4 Warnungen für
  contentDescription-Strings mit „%d Punkte" (Singular/Plural grammatikalisch
  sauberer über `<plurals>`). Rein kosmetisch, Build/Lint bleiben grün — bei
  Gelegenheit auf `<plurals>` umstellen.
- **Ziffernblock-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI des
  Eingabe-Ziffernblocks (`DartKeypad`) wurde nur kompiliert + über `@Preview`
  geprüft und die Logik (`DartInputState`/Labels) per JUnit getestet — keine
  Instrumentationstests (kein Emulator/Gerät in der Bau-Umgebung). Offen: Keypad
  auf dem echten Gerät (S25) sichten und/oder Compose-UI-Instrumentationstests
  (`connectedAndroidTest`) ergänzen — deckt sich mit der Phase-2-Zeile „Auf echtem
  Gerät (S25) testen".
- **Profil-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI der Profilverwaltung
  wurde nur kompiliert + über `@Preview` und ViewModel-Logik getestet — es gibt keinen
  Emulator/kein Gerät in der Bau-Umgebung, daher keine Instrumentationstests. Offen:
  Profil-UI auf dem echten Gerät (S25) sichten und/oder Compose-UI-Instrumentationstests
  (`connectedAndroidTest`) ergänzen, sobald ein Gerät/Emulator bereitsteht (deckt sich mit
  der Phase-2-Zeile „Auf echtem Gerät (S25) testen").
- **`dartsUsed` schließt Bust-Darts ein (IST):** Pro Aufnahme werden alle real
  geworfenen Darts persistiert — auch die einer Bust-Aufnahme. Ob für Statistik/Average
  „nur gewertete Darts" zählen sollen, ist eine **Produkt-Entscheidung** (ggf. eigene
  Kennzahl/Filter statt Änderung der Roh-Persistenz).
- **`GameUiState.Error` ohne Retry-Hook:** Das `GameViewModel` bietet aktuell keinen
  echten `retry()`; „Erneut versuchen" führt zurück zur Profilliste. Ein echter Retry
  (Init erneut versuchen, ohne den Screen zu verlassen) wäre später nachzuziehen —
  vgl. das `retryTrigger`-Muster im `ProfileViewModel`.
- **`onNewLeg` bei `legsToWin = 1`:** „Neues Leg" legt ein weiteres Leg im bereits
  abgeschlossenen Match an (öffnet das Match nicht wieder). Für einen echten Match-Flow
  (mehrere Legs/Sets, „Best of X") muss das mit der Roadmap-Zeile „Zwei Spieler,
  Aufnahme-Wechsel, Legs/Sets" geschärft werden.
- **`Throw.dartIndex`-Konvention inkonsistent (1..3 vs. 0..2):** Code/Persistenz
  (GameViewModel) und das Datenmodell der Checkliste nutzen `dartIndex` **1..3**, die
  KDoc der `Throw`-Entity nennt aber **0..2**. Muss angeglichen werden (Vorschlag:
  KDoc auf 1..3) — Hinweis für `reviewer`/`fixer`.
- **Game-Screen nicht auf echtem Gerät verifiziert:** Der Spiel-Bildschirm
  (`GameScreen`) wurde nur kompiliert + über `@Preview` (6) und die VM-/Engine-Logik
  per JUnit/Robolectric getestet — keine Instrumentationstests (kein Emulator/Gerät in
  der Bau-Umgebung). On-Device-Sichtung (S25) steht aus — deckt sich mit der
  Phase-2-Zeile „Auf echtem Gerät (S25) testen".

---

## Änderungslog
- _Erstanlage:_ Vision, Tech-Stack, Design-Entscheidungen (Eingabe + Delight),
  Setup-Status und Bau-Roadmap erfasst.
- _Workflow-Verankerung:_ `docs/CHECKLISTE.md` als zentrale Steuerung etabliert —
  Orchestrator-Loop in `CLAUDE.md` arbeitet die Liste top-down ab (genau eine
  Aufgabe pro Durchlauf, dann Stopp/„weiter"); `dokumentar`-Agent als Eigentümer
  der Datei festgelegt.
- _Setup-Aufgabe „Android-Gerüst committen/mergen" abgehakt:_ Verifiziert, dass
  das Gerüst via Commit `db1f618` bereits auf `main` liegt (Working Tree sauber,
  zentrale Gradle-/App-Dateien getrackt). Bereits durch frühere Commits erledigt,
  daher nur dokumentiert.
- _Setup-Aufgabe „Echte Gradle-Befehle eintragen" abgehakt:_ Verifiziert, dass die
  `./gradlew`-Befehle via Commit `f22cac7` in der `CLAUDE.md`-Tabelle und in den
  Agents `implementer`/`tester`/`fixer`/`reviewer` stehen; `designer`/`dokumentar`
  brauchen keine. Keine Platzhalter mehr — bereits durch früheren Commit erledigt,
  daher nur dokumentiert.
- _Setup-Aufgabe „Tech-Stack-Abschnitt in `CLAUDE.md`" abgehakt:_ Verifiziert, dass
  `CLAUDE.md` via Commit `f22cac7` den Abschnitt `## Tech-Stack` enthält und der
  Hinweis „Tech-Stack noch nicht eingerichtet / kein Scaffolding" entfernt ist.
  Bereits durch früheren Commit erledigt, daher nur dokumentiert. Damit sind **alle
  Setup-Aufgaben erledigt** — der Bau kann mit der Bau-Roadmap (Phase 1) fortfahren.
- _Phase 1 / „Room einrichten" abgehakt:_ Room `2.7.2` + KSP `2.2.10-2.0.2` über den
  Versionskatalog eingerichtet, Schema-Export aktiv (committetes Schema-JSON v1),
  minimale `@Database`-Klasse `TomsDartsDatabase`. `Player`-Entity + `PlayerDao`
  als Seed mit-implementiert, damit die DB kompiliert/testbar ist — die Roadmap-
  Zeile „Entities + DAOs" wurde entsprechend angepasst (`Player` erledigt, Rest
  offen). AGP-9-Workaround `android.disallowKotlinSourceSets=false` dokumentiert.
  Test-Strategie der Datenschicht als Design-Entscheidung festgehalten (Robolectric
  host-seitig, `@Config(sdk=[34])`, In-Memory-Room via `./gradlew test`, da kein
  Emulator). Backlog-Eintrag zu fehlenden `Player.name`-Constraints ergänzt.
  `test`, `lint`, `assembleDebug` grün.
- _Phase 1 / „Entities + DAOs" abgehakt:_ Entities `Match`, `Leg`, `Turn`, `Throw`
  + Cross-Ref `MatchPlayer` samt je einem DAO im `PlayerDao`-Stil ergänzt und in
  `TomsDartsDatabase` eingebunden (version bleibt 1, Schema-JSON v1 regeneriert +
  committet). FK-/onDelete-Strategie festgelegt und als Datenmodell-Entscheidung
  dokumentiert (CASCADE-Hierarchie, SET_NULL für winnerId, RESTRICT für playerId,
  Indizes auf allen FK-Spalten). Den Block „Vorgeschlagenes Datenmodell" auf
  „umgesetzt" umgestellt (englische camelCase-Feldnamen, `MatchPlayer` ergänzt) und
  neuen Abschnitt „Datenmodell-Entscheidungen" eingefügt (inkl. Schema-Versions-
  strategie: solange unveröffentlicht regenerieren statt migrieren). Zwei Backlog-
  Einträge aus den Tester-Hinweisen aufgenommen (`PlayerDao.delete` fehlt;
  keine CHECK-Constraints/Wert-Plausibilität auf DB-Ebene). 43+ Datenschicht-Tests
  grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL.
- _Phase 1 / „Repository-Schicht über den DAOs" abgehakt:_ `PlayerRepository` und
  `MatchRepository` (`data/repository/`) als dünne, regelfreie Wrapper über den DAOs
  ergänzt; `PlayerDao` um `update`/`delete`/`observeAll(): Flow` (ORDER BY name)
  erweitert; DB-Singleton `TomsDartsDatabase.getInstance` (thread-sicher,
  `fallbackToDestructiveMigration`) und manueller DI-Einstiegspunkt `AppContainer`
  (kein Hilt) hinzugefügt. Neuer Abschnitt „Repository-/DI-Schicht" unter
  Design-Entscheidungen (manuelles DI, DB-Singleton, regelfreie Repositories,
  Flow-Lesen). Backlog-Item „`PlayerDao` hat kein `delete()`" als erledigt markiert;
  zwei neue Backlog-Hinweise aufgenommen (`fallbackToDestructiveMigration` no-arg
  deprecated; `AppContainer`/DB-Singleton unter Robolectric eingeschränkt testbar).
  18+ Repository-/Container-Tests grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL.
- _Phase 1 / „Profilverwaltung" abgehakt — Phase 1 (Fundament) damit vollständig
  abgeschlossen:_ Erste Compose-UI der App umgesetzt (`ui/profile/`: `ProfileScreen`,
  `PlayerListItem`, `ProfileDialogs`, `ProfileUiState`, `ProfileViewModel`) mit voller
  Spieler-CRUD über `PlayerRepository` (anlegen/auflisten/bearbeiten/löschen). App-Plumbing
  `TomsDartsApp : Application` (hält `AppContainer`, im Manifest registriert), `MainActivity`
  zeigt jetzt `ProfileScreen` statt Greeting. Neuen Abschnitt „UI-/ViewModel-Schicht" unter
  Design-Entscheidungen ergänzt (manuelles DI bis in die UI via Application/`Factory`;
  sealed `ProfileUiState`/`ProfileDialog` + `StateFlow` + `collectAsStateWithLifecycle`;
  Stateless/Stateful-Trennung; bewusst keine Icon-Dependency; `ListItem`+`HorizontalDivider`;
  ViewModel-Test-Muster mit gekoppeltem Test-Scheduler `MainDispatcherRule`). Drei
  Backlog-Einträge aufgenommen (Undo-Snackbar nach Löschen [D]; Hinweis bei doppeltem Namen
  [E]; Profil-UI auf echtem Gerät / Compose-Instrumentationstests, da kein Emulator).
  19 ViewModel-Tests grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL, kein
  Schema-Drift. Verifikation der UI auf echtem Gerät (S25) bleibt offen (Backlog/Phase 2).
- _Phase 2 / „Strategie-Interface für Spielmodi definieren" abgehakt:_ Pures
  Domänen-Paket `com.mechanicel.tomsdarts.game` ohne Android-/Room-/Compose-Bezug
  angelegt: generisches Strategie-Interface `GameMode<S : Any>` (`key`, `displayName`,
  `initialState`, `applyDart`) + Value-Objekte `Dart`, `GameConfig`, `DartOutcome<S>`.
  Vertrag dokumentiert: `applyDart` verarbeitet genau einen Dart, Bust-Revert obliegt
  der Engine, `bust` XOR `legWon`. Neuen Abschnitt „Spielmodi-Domänenlogik" unter
  Design-Entscheidungen ergänzt (generisches `GameMode<S>`; pure Domäne entkoppelt von
  Room/UI; Bust-Revert in der Engine; Modus-Identität über `key`; `GameConfig`+`key`↔
  `Match`-Mapping in späterer Engine; bewusste Nicht-Aufnahme modusspezifischer
  Konfigfelder / YAGNI). Bestehende „Spielmodi"-Design-Entscheidung („gemeinsames
  Strategie-Interface") als jetzt umgesetzt markiert; im Tech-Stack die Zeile
  „austauschbare Strategie" entsprechend annotiert. Backlog-Eintrag „Engine-/
  Mapping-Schicht" als Orientierung für die X01-/Engine-Aufgaben aufgenommen.
  28 reine JUnit-Tests grün (`DartTest` 14, `GameModeContractTest` 14); `test`,
  `lint`, `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „X01-Modus (501, Double-Out) implementieren" abgehakt:_ Erste konkrete
  `GameMode`-Strategie `X01Mode : GameMode<X01State>` (`key="X01"`) + Zustands-Typ
  `X01State(remaining)` im puren Domänen-Paket `com.mechanicel.tomsdarts.game`.
  `applyDart`-Regeln: Aufrechnen (`remaining - dart.value`), Bust bei `< 0`; mit
  Double-Out Finish nur per Double (`== 0 && isDouble`, Doppel-Bull eingeschlossen),
  `== 0` ohne Double und `== 1` → Bust; ohne Double-Out gewinnt `== 0` mit beliebigem
  Wurf. Bust liefert unveränderten Eingangszustand (`scored = 0`), Invariante
  `bust` XOR `legWon`. **startScore-agnostisch** → 301/501/701 = ein Modus,
  verschiedene `GameConfig.startScore`. Design-Entscheidungen ergänzt (X01 als erste
  Strategie, startScore-agnostisch, `X01Mode` ohne Eingabe-Guards) und die
  „Spielmodi"-Entscheidung annotiert (Kern-Modus umgesetzt; 301/701 durch
  startScore-agnostischen `X01Mode` abgedeckt). Backlog-Eintrag „Engine validiert
  Darts/Config" mit den drei dokumentierten Robustheits-Lücken aufgenommen. 31 reine
  JUnit-Tests grün (`X01ModeTest` 9, `X01ModeEdgeCasesTest` 22); `test`, `lint`,
  `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „Eingabe-Screen (Ziffernblock) in Compose bauen" abgehakt:_ Ziffernblock
  als eigenständige Komponente im neuen Paket `com.mechanicel.tomsdarts.ui.input`
  umgesetzt — pure, Compose-/Android-freie Eingabe-Logik (`DartInputState` +
  `DartModifier` + Label-Funktionen, rein JUnit-testbar) getrennt von der Compose-UI
  (`DartKeypad`, stateless/stateful, `rememberSaveable`-Saver). 5×5-Layout (Zahlen
  1–20 + Sondertasten Undo/Out/Bull/Double/Triple), D-/T-Modifikator mit Auto-Reset,
  3 Slots + Aufnahme-Summe, Bull bei TRIPLE gesperrt, responsiv, keine Icon-Dependency.
  Unterabschnitt „Umsetzung" zur Design-Entscheidung „Eingabe (Ziffernblock)" ergänzt
  (Logik/UI-Trennung, Rotationsfestigkeit, bewusste Sperr-statt-Auto-Clear-Semantik
  via `startNewTurn()` durch den Aufrufer, angezeigte Aufnahme-Summe, Bull-Semantik).
  Zwei Backlog-Einträge aufgenommen (`PluralsCandidate`-Lint-Warnungen für „%d Punkte"-
  contentDescriptions; Ziffernblock-UI nicht on-device/instrumented verifiziert).
  **Bewusst NICHT** an Spiel-Logik/Persistenz gekoppelt — das ist die nächste Roadmap-
  Zeile „Eingabe an die Spiel-Logik koppeln; throw-level speichern". 63 reine JUnit-Tests
  grün (`DartInputStateTest` 49, `DartLabelTest` 14); `test`, `lint`, `assembleDebug`
  BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „Eingabe an die Spiel-Logik koppeln; throw-level speichern" abgehakt:_
  Vollständige Kopplung Ziffernblock → Spiel-Logik → Persistenz für ein
  **Einzelspieler-X01-Leg**. Neu: pure `game.engine.LegEngine<S>` (Aufnahme-Bündelung,
  Bust-Revert, Sofort-Checkout, `undoLastDart`, `snapshot`; + `DartResult`/
  `LegEngineSnapshot`), `GameViewModel` (`ui/game/`: koppelt `DartInputState` an die
  Engine, persistiert pro Aufnahme `Turn`+`Throw`s inkl. Bust-Darts, setzt bei Checkout
  `endedAt`/`winnerId`, `onUndo`/`onNewLeg`, `provideFactory`), `GameUiState`
  (Loading/Error/NoPlayer/Playing/Won) + transientes `bustEvents`, und `GameScreen`
  (Scoreboard, eingebetteter Keypad, Bust-Banner, Won-Panel, 6 Previews; Strings im
  Block „Spiel"). `MatchDao`/`LegDao` um `@Update` + `MatchRepository.updateMatch`/
  `updateLeg` ergänzt (CASCADE-Schutz statt REPLACE-Insert; kein Schema-Drift).
  Einstieg: `MainActivity` als State-Switch Profil ⇄ Spiel (kein navigation-compose);
  Profil-Tap auf den Spieler-Body startet das Spiel (Edit/Delete in den Overflow).
  Neuen Abschnitt „Spiel-Engine & Eingabe-Kopplung" unter Design-Entscheidungen ergänzt
  (Engine-getriebene Kopplung, throw-level pro Aufnahme inkl. Bust-Darts, `@Update` statt
  REPLACE, sealed `GameUiState`+`bustEvents`, State-Switch-Navigation, Profil-Tap-Einstieg,
  `Match.modeType="X01"`). Fünf Backlog-Einträge aufgenommen (dartsUsed-Semantik,
  fehlender Error-Retry-Hook, `onNewLeg` bei `legsToWin=1`, `Throw.dartIndex`-KDoc
  1..3 vs. 0..2, Game-Screen nicht on-device verifiziert). Tests rein JVM/Robolectric:
  `LegEngineTest`/`LegEngineEdgeCasesTest` + 15 GameViewModel-Tests grün; `test`, `lint`,
  `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
