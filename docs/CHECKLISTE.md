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

### Phase 1 — Fundament
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
- [ ] Repository-Schicht über den DAOs
- [ ] Profilverwaltung: Spieler anlegen / auflisten / bearbeiten / löschen (UI + Persistenz)

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
- **`PlayerDao` hat kein `delete()`:** Das Spieler-Löschen fehlt im DAO; SET_NULL/
  RESTRICT-Verhalten wurde in den Tests deshalb über direktes SQL ausgelöst. Sobald
  Spieler-Löschung ein Produktfeature wird (siehe Profilverwaltung, Phase 1), braucht
  `PlayerDao` ein `delete` — und die FK-Constraint-Tests sollten darauf umgestellt
  werden statt auf rohem SQL.
- **Keine Wert-Plausibilität auf DB-Ebene (keine CHECK-Constraints):** Die DB erzwingt
  keine fachliche Gültigkeit der Wurfdaten — z. B. `multiplier` außerhalb 1–3, `segment`
  außerhalb {0, 1–20, 25}, `value ≠ segment * multiplier` oder negative Scores werden
  **nicht** verhindert. Diese Plausibilität soll später in der Spiel-/Score-Logik
  (Phase 2) geprüft werden.

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
