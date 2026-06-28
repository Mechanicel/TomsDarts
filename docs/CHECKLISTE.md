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
  jeder Modus implementiert seine Regeln)

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
- Geplantes Repertoire danach (Reihenfolge offen, anpassbar):
  301/701, Cricket, Around the Clock, Shanghai, Count Up / High Score, Killer.
- Jeder Modus wird über das gemeinsame Strategie-Interface umgesetzt, damit
  „neuer Modus" nicht „App umschreiben" bedeutet.

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
- [ ] Strategie-Interface für Spielmodi definieren
- [ ] X01-Modus (501, Double-Out) implementieren: Aufrechnen, Bust-Logik,
      Checkout-/Double-Out-Prüfung, Leg-Sieg
- [ ] Eingabe-Screen (Ziffernblock) in Compose bauen, gemäß Design-Entscheidungen
- [ ] Eingabe an die Spiel-Logik koppeln; throw-level speichern
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
- **Profil-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI der Profilverwaltung
  wurde nur kompiliert + über `@Preview` und ViewModel-Logik getestet — es gibt keinen
  Emulator/kein Gerät in der Bau-Umgebung, daher keine Instrumentationstests. Offen:
  Profil-UI auf dem echten Gerät (S25) sichten und/oder Compose-UI-Instrumentationstests
  (`connectedAndroidTest`) ergänzen, sobald ein Gerät/Emulator bereitsteht (deckt sich mit
  der Phase-2-Zeile „Auf echtem Gerät (S25) testen").

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
