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

### Vorgeschlagenes Datenmodell (Room) — als Umsetzungs-Leitplanke
- `Player`: id, name, erstelltAm, (optional Farbe/Avatar)
- `Match`: id, modusTyp, Konfiguration (Startpunkte, doubleOut, Legs, Sets,
  Spielerliste/Reihenfolge), gestartetAm, beendetAm, gewinnerId
- `Leg`: id, matchId, setNummer (optional), legNummer, gewinnerId, gestartetAm, beendetAm
- `Turn` (Aufnahme): id, legId, playerId, aufnahmeIndex, bustFlag, summeGeworfen
- `Throw` (Dart): id, turnId, dartIndex (1–3), segment (1–20/25/0),
  multiplikator (1/2/3), wert, zeitstempel

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
- [ ] Echte Gradle-Befehle in `CLAUDE.md` und alle Agents eintragen
      (Platzhalter ersetzen; Befehle siehe Abschnitt „Tech-Stack")
- [ ] In `CLAUDE.md` einen Abschnitt „Tech-Stack" ergänzen und den Hinweis
      „Tech-Stack noch nicht eingerichtet / kein Scaffolding" entfernen
- [x] Diese `CHECKLISTE.md` ins Repo übernehmen und committen
      → liegt unter `docs/CHECKLISTE.md`; als zentrale Steuerung in `CLAUDE.md`
        (Orchestrator-Loop) und im `dokumentar`-Agent verankert.

---

## Bau-Roadmap

### Phase 1 — Fundament
- [ ] Room einrichten (Dependencies, Database-Klasse, Schema-Export)
- [ ] Entities + DAOs: `Player`, `Match`, `Leg`, `Turn`, `Throw`
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
- (frei für neue Einfälle — Dokumentar trägt hier nach)

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
