# 0025 — Around the Clock als zweiter Katalog-Modus (Sequenz-Modus über die Modus-Infrastruktur)

**Status:** Akzeptiert

## Kontext

[ADR-0022](0022-modus-infrastruktur.md) hat die generische Modus-Infrastruktur etabliert,
[ADR-0024](0024-standard-cricket-katalog-modus.md) brachte Cricket als ersten echten Katalog-Modus.
Jetzt folgt der zweite konkrete Modus: **Around the Clock**, Roadmap Phase 4. Around the Clock ist
ein gegner-unabhängiger **Sequenz-Modus** (wie X01), bei dem Ziele der Reihe nach von 1 bis 20
getroffen werden müssen — gegensätzlich zu Cricket, das gegner-abhängig ist.

Mit der etablierten ADR-0022-Infrastruktur dockt Around the Clock rein additiv an.

## Entscheidung

### Around-the-Clock-Regeln

Implementiert wurde die **Standard-Variante:**

1. **Ziele:** Zahlen 1, 2, 3, …, 20 der Reihe nach. Start bei 1, Ende bei 20.

2. **Treffer und Vorrücken:** Das aktuell anzuvisierende Ziel mit **beliebigem Multiplier** treffen
   (Single, Double oder Triple der Zielzahl zählen gleich — der Multiplier ist irrelevant) →
   das Ziel rückt um 1 vor.

3. **No-Op (Kein Vorrücken):**
   - Zahl != aktuelles Ziel → keine Änderung, `scored = 0`.
   - Bull (25) → No-Op.
   - Miss (0) → No-Op.

4. **`scored`-Wert:** 1 pro erfolgreichem Vorrücken, 0 sonst. Damit ist die Aufnahme-Summe in
   der UI gleich der Anzahl der Vorrückungen in dieser Aufnahme.

5. **Leg-Sieg:** Nach erfolgreichem Treffer der 20 rückt das Ziel auf 21 vor. Ein Ziel > 20
   markiert den Leg-Sieg.

6. **Kein Bust, kein Checkout:** Around the Clock kennt diese X01-Konzepte nicht.

7. **Gegner-Unabhängigkeit:** Wie X01 ist Around the Clock gegner-unabhängig; die `opponents`-Liste
   wird bewusst ignoriert.

### Code-Struktur: rein additiv

Around the Clock dockt über drei neue Dateien an (Cricket-Muster):

1. **`AroundTheClockState`** (pure Domäne) — Value-Object mit `target: Int`.
   - Konstanten `FIRST_TARGET = 1`, `TOTAL = 20`.
   - Helper `initial()` → Startzustand (target = 1).

2. **`AroundTheClockMode : GameMode<AroundTheClockState>`** (pure Domäne) — implementiert die Regeln.
   - `initialState()` → `AroundTheClockState.initial()` (target = 1).
   - `applyDart(state, dart, config, opponents)` → neue `AroundTheClockState` + `DartOutcome`.
   - Vergleicht `dart.segment` mit `state.target`; ignoriert Multiplier und `opponents`.

3. **`AroundTheClockUiAdapter : ModeUiAdapter<AroundTheClockState>`** (UI-Abstraktion).
   - `board(state)` → `PlayerBoardUi.AroundTheClock(target, completed)`.
   - `completed = (state.target - 1).coerceIn(0, TOTAL)` — defensiv gekappt für Post-Sieg-Kante (target > 20).
   - `checkout(state, config)` → `null` (Around the Clock kennt Checkout nicht).

4. **Katalog-Eintrag** in `GameModeCatalog`:
   ```
   GameModeInfo(key = AROUND_THE_CLOCK, usesStartScore = false, usesDoubleOut = false)
   ```
   Around the Clock kennt diese X01-Optionen nicht → Flags auf `false` → Setup blendet die Sections aus.

5. **UI-Integration:**
   - `GameUiState.kt` erhält `PlayerBoardUi.AroundTheClock` (neuer sealed subtype) mit
     `target: Int, completed: Int`.
   - `GameViewModel.provideFactory` wächst um einen `AROUND_THE_CLOCK`-Branch (erzeugt
     `GameViewModel<AroundTheClockState>` mit `AroundTheClockUiAdapter`).
   - `MatchScoreboard.kt` (bereits generisch aus ADR-0022) rendert ATC-Karten über
     `PlayerBoardUi.AroundTheClock`-when-Zweig: Hero-Zahl (aktuelles Ziel `target`),
     Fortschritts-Label „n / 20", defensive Verdrahtung für Post-Sieg-Zustand
     (target > 20 → Label bleibt bei „20 / 20"), Semantik im `contentDescription`.
   - Barrierefreiheit: zusammengesetzte `contentDescription` mit Ziel und Fortschritt.

6. **Strings in `res/values/strings.xml`:**
   - 6 neue Einträge im Block „game_atc_*" für Label, Hero-Text, Fortschritt.

7. **Tests:** 27 Around-the-Clock-Tests über drei Dateien:
   - `AroundTheClockModeTest.kt` (11 Tests) — Happy Path (Vorrücken, Multiplier-Ignoranz, Sieg bei target=21).
   - `AroundTheClockModeEdgeCasesTest.kt` (12 Tests) — Randfälle (No-Op bei falscher Zahl, Bull, Miss,
     Solo-Zustand ohne Gegner, post-Sieg-Dauerhaft-No-Op bei target > 20).
   - `AroundTheClockMatchIntegrationTest.kt` (4 Tests) — Engine-Verdrahtung über LegEngine/MatchEngine,
     Mehrspieler-Korrektheit.

**Gesamte Test-Suite:** 562 grün (bestehende 535 X01-/Cricket-/Infra-Tests unverändert + 27 neue ATC-Tests).

### Post-Sieg-Verhalten (beabsichtigt)

Nach `target = 21` (Sieg erkannt) werden weitere Würfe zu No-Ops — es existiert kein Dart-Segment 21.
Dies ist beabsichtigt und dokumentiert. Der UI-Adapter kappt `completed` defensiv auf den gültigen
Bereich, um Anzeige-Fehler zu vermeiden.

### Bewusst zurückgestellt (Backlog)

Die folgenden Aspekte sind **nicht Teil dieser Entscheidung**, sondern bewusst auf den Backlog:

1. **Bull-Finish-Variante (1 → 20 → Bull):** Alternative Regelwerk, bei dem nach der 20 noch der
   Bull als Abschlussfeld zu treffen ist. Trifft ein Spieler nur die 1–20, dann kann der nächste Spieler
   die Bull noch treffen und gewinnt. Später nachzuziehen.

2. **Advance-by-Multiplier-Variante:** Double/Triple der Zielzahl rücken um 2/3 Schritte vor statt immer 1.
   Ändert die taktische Komplexität, später nachzuziehen.

## Konsequenzen

- **Around the Clock ist live:** Der Modus läuft offline, integriert in `GameModeCatalog`.
  Mit ATC im Katalog zählt die `ModeSection` im Setup jetzt 3 Modi (X01, Cricket, ATC),
  wird aber weiterhin korrekt angezeigt.

- **Gegner-Unabhängigkeit wie X01:** Around the Clock braucht keine Gegner-Zustände,
  ignoriert die `opponents`-Liste bewusst (wie X01). Die ADR-0022-Infrastruktur trägt beide Muster.

- **Rein additiv:** X01 und Cricket bleiben unverändert; alle bestehenden Tests bleiben grün.

- **Nächster Modus wird schneller:** Mit dieser dreiteiligen Infrastruktur (ADR-0022 + Cricket + ATC)
  ist das Pattern vollständig etabliert — weitere Modi (Shanghai, Count Up, Killer) folgen dem
  gleichen Bauplan.

- **Setup-Screen:** Mit 3 Modi im Katalog ist die `ModeSection` sichtbar und alle drei Optionen wählbar.
  Start Score- und Double-Out-Sections bleiben an die ATC-Flags `usesStartScore=false/usesDoubleOut=false`
  gebunden (ausgeblendet für ATC).
