# 0018 — Setup-Screen: Startpunkt-Auswahl

**Status:** Akzeptiert

## Kontext
Festgelegt in Phase 3 / „Spiel-Setup-Screen: Startpunkte (301/501/701) wählbar".
Der Mehrspieler-Match-Flow ([ADR-0015](0015-mehrspieler-match-legs-sets.md)) läuft
bisher mit fester Konfig (`startScore=501`). Phase 3 beginnt mit der Konfigurierbarkeit
— der erste Schritt ist die **Startpunkt-Auswahl** (301 / 501 / 701). Der
Setup-Screen wird zwischen Profil-Auswahl und Spiel eingeschoben und kann später
um weitere Optionen (Double-Out, Legs/Sets, Spieleranzahl, Modus) erweitert werden.

## Entscheidung
- **Setup-Screen einschobene Navigation:** Neuer State `SCREEN_SETUP` in `MainActivity`
  — der Flow ändert sich von **Profil ⇄ Spiel** zu **Profil → Setup → Spiel**. Gesteuert
  über State-Variable + `rememberSaveable`, minimal gehalten (kein navigation-compose).
  `onCancel` im Setup führt zurück zur Profil-Auswahl, `onConfirm` zum Spiel.
- **Startpunkt-Auswahl via auswählbare Karten (statt SegmentedButton):**
  Auswählbare **Karten** (`Row` mit `weight(1f)`, Tap-Selektion aus zentraler List
  `START_SCORES = listOf(301, 501, 701)`): (1) **Prominenter** — größere Fläche für
  Bedienung am Board (Material-3 `SegmentedButton` ist für diese Anwendung zu klein);
  (2) **Erweiterbar** — weitere Optionen unter zukünftigen Sections hinzufügbar;
  (3) **Konsistent zu Darts-Domäne** — X01-Standard-Eröffnungs-Punkte.
  **Default:** 501 (`DEFAULT_START_SCORE`), **Persistence:** `rememberSaveable`.
  **Selektions-Semantik:** `selectableGroup` + `selectable(role=RadioButton)` für
  TalkBack; `contentDescription` je Karte; Touch-Target ≥ 48 dp.
- **Primäraktion: Full-Width-Button in bottomBar (nicht FAB):**
  Der Button zum Starten des Spiels sitzt in einer `bottomBar` und ist Full-Width
  (nicht FAB, nicht TopAppBar-Action) — das ist setup-typisch (Bestätigungsschritt
  nach Optionen-Auswahl) und bleibt scroll-fest. Label: `setup_start_match` String.
  `BackHandler` → `onCancel` (zurück).
- **Startpunkt-Labels aus Werteliste gerendert (kein String je Zahl):**
  Die 301/501/701-Labels stammen **nicht aus strings.xml** (kein Eintrag pro Zahl),
  sondern werden **direkt aus `START_SCORES`** in der UI gerendert. Für TalkBack wird
  ein Format-String `setup_start_score_cd = "%d Startpunkte"` eingetragen (+ Plurale
  können später auf `<plurals>` umgestellt werden). Das spart Hardcoding und macht
  die `START_SCORES`-List zur Single Source of Truth.
- **Screen als erweiterbare Section-Liste aufgebaut:**
  Der Setup-Screen wird nicht als Flach-Form mit einzelnen Feldern aufgebaut, sondern
  als **erweiterbare Section-Liste** (conceptual: Abschnitt „Startpunkte" jetzt, später
  „Double-Out", „Legs/Sets", etc. als separate Sections). Das ermöglicht **atomare,
  unabhängige Erweiterung** je Phase-3-Aufgabe (neue Section = neue Zeile in ROADMAP,
  eigene PR). Die aktuelle `SetupScreen`-Komponente ist damit das Container-Pattern,
  neue Sections werden als Child-Komponenten eingefügt.
- **`startScore`-Validierung: UI-only, nicht in der Domäne:**
  Die Domäne (Spielmodi-Engine, Persistenz) validiert **nicht** gegen `START_SCORES`.
  Das IST-Verhalten ist: wenn `startScore` außerhalb der drei Karten liegt
  (z. B. programmatisch 999 aus einer Debug-UI), läuft es ungeprüft durch die Engine
  und wird im Match persistiert. Die **Validierung lebt ausschließlich in der Setup-UI**
  (Auswahl aus Karten) — eine bewusste Trennung zwischen UI-Constraint (konfigurierbare
  Karten) und Domänen-Robustheit (keine Annahmen über gültige Werte). Spätere
  Produktentscheidungen (z. B. Custom-Punkte erlauben) können so aufgebaut werden,
  ohne die Engine zu ändern.
- **`startScore` läuft durchgängig bis in `GameConfig` + `Match`:**
  `MainActivity` hält den ausgewählten `startScore` in `rememberSaveable` (State),
  reicht ihn an `GameScreen` + `GameViewModel.provideFactory(playerIds, startScore)`.
  Das ViewModel setzt ihn in die `GameConfig(startScore, ...)` und persistiert ihn
  automatisch im `Match`. Damit ist `startScore` **nicht** eine Eingabe-Session-Variable,
  sondern eine vollwertige **Konfiguration** des Matches — sichtbar in der Datenbank,
  nutzbar für Statistiken / Rematch.
- **Dynamischer Spieltitel per startScore: out of scope.**
  Nicht umgesetzt: Der UI-Titel / die Match-Anzeige nicht nach startScore dynamisch
  aktualisiert (z. B. „501er Spiel"). Bleibt offen für später (wenn überhaupt sinnvoll).

## Konsequenzen
- Phase 3 beginnt mit dem konfigurierbaren Spiel-Setup; erste Konfiguration = Startpunkte.
- Der Screen ist erweiterbar (neue Sections = neue Phase-3-Aufgaben, atomare PRs).
- Startpunkt-Auswahl ist UI-zentriert; Domäne bleibt validierungsfrei (YAGNI).
- `startScore` ist nun eine vollwertige Match-Konfiguration, nicht Eingabe-Zufall.
- Nachfolgende Phase-3-Aufgaben: Double-Out, Legs/Sets, Spieleranzahl, Modus — alle
  über neue Sections im gleichen Screen-Pattern erweiterbar.

## Anwendung (Phase 3 / Task 2): Double-Out an/aus

Das etablierte Section-Pattern wird für die **Double-Out-Konfiguration** (Task 2)
ohne Abweichungen fortgesetzt:
- **Neue `DoubleOutSection`** unter der `StartScoreSection`: Label „Auschecken"
  (etablierter Darts-Begriff für Checkout), Titel „Double-Out", Erklärtext für Laien
  „Das letzte Feld muss ein Doppel sein."
- **UI-Komponente:** Material-3 `Switch` + `Modifier.toggleable` (ganze Zeile Toggle-Node,
  `Role.Switch`, ≥48dp), Switch selbst mit `onCheckedChange=null` (semantisch dekorativ,
  Interaktion nur auf der Row).
- **Persistenz:** Gleicher Flow wie `startScore` — Durchreichung über
  `SetupScreen` → `MainActivity` → `GameScreen` → `GameViewModel.provideFactory` →
  `GameConfig` → `Match.doubleOut`.
- **Default:** `DEFAULT_DOUBLE_OUT = true` (Double-Out aktiv).
- **Validierung:** Keine Domänen-Validierung; UI bietet nur an/aus.
- **Tests:** Wert-Durchreichung für beide Werte (an/aus) in Kombination mit allen
  `START_SCORES`; fachlicher End-to-End-Effekt (Checkout ohne Double gewinnt vs.
  führt zu Bust) verifiziert per `GameViewModelDoubleOutWiringTest`.

Das Pattern bestätigt sich: Boolean-Konfigurationen passen als Toggle-Rows in das
Section-Framework; die atomare Task-Größe (Section pro Task) bleibt stabil.
