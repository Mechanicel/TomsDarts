# 0024 — Standard-Cricket als erster Katalog-Modus

**Status:** Akzeptiert

## Kontext

[ADR-0022](0022-modus-infrastruktur.md) hat die generische Modus-Infrastruktur etabliert (PR A:
Gegner-Lesezugriff, `GameModeCatalog`, `ModeUiAdapter<S>`, UI-Setup-Integration) — mit der
Messlatte, dass X01 völlig unverändert bleibt. Jetzt folgt PR B (**diese Entscheidung**): der
erste echte konkrete Spielmodus neben X01 — **Standard-Cricket**, Roadmap Phase 4. Cricket
unterscheidet sich fundamental von X01:
- **X01:** Restpunkte-Tracking, Double-Out-Regel, Bust-Revert.
- **Cricket:** Feldweise Marks-Tracking (15–20, Bull 25), gegnerabhängige Punktevergabe, Feld-Schließungslogik.

Mit ADR-0022 vorbereiteter Infrastruktur muss Cricket **rein additiv** in den bestehenden Code
docken — ohne X01 anzutasten, ohne Infra nochmal zu überarbeiten.

## Entscheidung

### Standard-Cricket-Regeln

Implementiert wurde **Standard-Cricket** (nicht Cut-Throat — das bleibt zurückgestellt):

1. **In-Play-Felder:** 15, 16, 17, 18, 19, 20 und Bull (25). Alle anderen Segmente (1–14) und
   Miss (0) sind No-Ops (kein Zustandswechsel, `scored = 0`).

2. **Marks pro Treffer:** `dart.multiplier`-basiert:
   - Single = 1 Mark
   - Double = 2 Marks
   - Triple = 3 Marks
   - Bull Single = 1 Mark, Bull Double (Doppel-Bull) = 2 Marks

3. **Feld schließen:** Ein Feld gilt mit 3 Marks als geschlossen (auf 3 gekappt).

4. **Overflow (Schließen + Punkten in einem Dart):** Mit `m` = aktuelle Marks des Feldes:
   - `toClose = min(marks, 3 - m)` (wieviel braucht es zum Schließen)
   - `newMarks = m + toClose` (neue Marks des Feldes, auf 3 gekappt)
   - `overflow = marks - toClose` (überschüssige Marks)

5. **Punkte:** `overflow`-Marks bringen `overflow × Feldwert` Punkte (Bull = 25), **aber nur wenn**:
   - `overflow > 0` (es gibt überschüssige Marks) **UND**
   - (keine Gegner bekannt **ODER** mindestens ein Gegner hat das Feld noch offen) — die `scorable`-Regel.

   Dies ist die Gegner-abhängige Wertung, die Cricket braucht: Sobald alle Gegner ein Feld
   geschlossen haben, punktet das Feld nicht mehr für den aktuellen Spieler.

6. **Kein Bust, kein Checkout:** Cricket kennt diese X01-Konzepte nicht.

7. **Leg-Sieg:** Ein Spieler gewinnt das Leg, wenn:
   - **ALLE Felder geschlossen** sind (15, 16, 17, 18, 19, 20, Bull) **UND**
   - Seine Punkte ≥ allen Gegnern (bei leerer Gegnerliste genügt „alle geschlossen").

### Code-Struktur: rein additiv

Cricket dockt über drei neue Dateien an:

1. **`CricketState`** (pure Domäne) — Value-Object mit `marks: Map<Int, Int>` (0–3 pro Feld) und `points: Int`.
   - `marksOf(target)`, `isClosed(target)`, `allClosed()` Helper.
   - Konstanten `FIELDS`, `BULL`, `CLOSED_MARKS`.

2. **`CricketMode : GameMode<CricketState>`** (pure Domäne) — implementiert die Regeln.
   - `initialState()` → alle Felder auf 0 Marks, 0 Punkte.
   - `applyDart(state, dart, config, opponents)` → neue `CricketState` + `DartOutcome` mit `legWon`.
   - Nutzt den `opponents: List<CricketState>`-Parameter für die `scorable`-Regel.

3. **`CricketUiAdapter : ModeUiAdapter<CricketState>`** (UI-Abstraktion) — konvertiert State → UI.
   - `board(state)` → `PlayerBoardUi.Cricket(fields: List<CricketFieldUi>, points: Int)`.
   - `checkout(state, config)` → `null` (Cricket kennt Checkout nicht).

4. **Katalog-Eintrag** in `GameModeCatalog`:
   ```
   GameModeInfo(key = CRICKET, usesStartScore = false, usesDoubleOut = false)
   ```
   Cricket kennt diese X01-Optionen nicht → Flags auf `false` → Setup blendet die Sections aus.

5. **UI-Integration:**
   - `GameUiState.kt` erhält `PlayerBoardUi.Cricket` (neuer sealed subtype) mit `fields: List<CricketFieldUi>, points: Int`.
   - `GameViewModel.provideFactory` wächst um einen `CRICKET`-Branch (erzeugt `GameViewModel<CricketState>`
     mit `CricketUiAdapter`).
   - `MatchScoreboard.kt` (bereits generisch aus ADR-0022) rendert Cricket-Karten über
     `PlayerBoardUi.Cricket`-when-Zweig: 7 Felder in Reihenfolge 20→15→Bull, Marks als
     Canvas-basierte Symbole (Schrägstrich/Kreuz/eingekreistes Kreuz, Strichfarbe = `contentColor`
     → Kontrast-sicher), Punkte als Hero-Wert.
   - Barrierefreiheit durch zusammengesetzte `contentDescription` pro Karte.

6. **Tests:** 37 Cricket-Tests über drei Dateien:
   - `CricketModeTest.kt` (12 Tests) — Happy Path (Felder schließen, Overflow, Gegner-abhängige Wertung, Leg-Sieg).
   - `CricketModeEdgeCasesTest.kt` (22 Tests) — Randfälle (No-Op bei Nicht-In-Play, leere Gegnerliste Solo,
     mehrspieliges Punkte-Vergleich, grenzwertige Marks).
   - `CricketMatchIntegrationTest.kt` (3 Tests) — Engine-Verdrahtung (Gegner-Provider-Live-Read,
     Spielerwechsel nach exakt 3 Darts, No-Bust).

**Gesamte Test-Suite:** 532 grün (bestehende 495 X01-/Infra-Tests unverändert + 37 neue Cricket-Tests).

### Bewusst zurückgestellt (Backlog)

Die folgenden Aspekte sind **nicht Teil dieser Entscheidung**, sondern bewusst auf den Backlog:

1. **Cut-Throat-Cricket-Variante:** Alternative Regel, bei der Punkte von jedem Spieler minus den
   Punkten der anderen gerechnet werden — eine andere Gegner-Abhängigkeit. Aufgabe für später.

2. **„Totes Feld"-Dimming:** Visual-Feedback, dass ein Feld für keinen Spieler mehr offen ist
   (alle haben es geschlossen) — nur UI-Verschönerung, später nachzuziehen.

3. **1–14-Tasten im Cricket ausblenden/deaktivieren:** Der Keypad zeigt derzeit alle 20 Tasten
   (1–20, Bull, Miss). Cricket könnte die sinnlosen 1–14-Tasten deaktivieren oder ausblenden —
   Später, keine höhere Priorität.

4. **Dedizierte Cricket-State-Persistenz nach Neustart:** Aktuell wird der Modus-State wie bei
   X01 über die `LegEngine` geführt (Engine-Replay beim App-Neustart stellt den State wieder her).
   Eine explizite Cricket-Persistenz (z. B. Cricket-State-Snapshot in der DB) war nicht Teil
   dieser Aufgabe — kann bei späteren Performance-Optimierungen nachgezogen werden.

## Konsequenzen

- **Cricket ist live:** Standard-Cricket läuft offline, konfigurierbar über `GameModeCatalog`.
  Mit Cricket im Katalog wird die `ModeSection` im Setup automatisch sichtbar (`entries.size > 1`).

- **Gegner-Provider funktioniert für Cricket:** Die Architektur aus ADR-0022 (Gegner-Provider
  als Zero-Arg-Lambda, live abgerufen) trägt vollständig die gegnerabhängige Cricket-Wertung.
  Cross-Turn-Undo bleibt korrekt (Gegner-Zustände im Replay stimmen).

- **Nächster Modus wird schneller:** Mit dieser zweiteiligen Infrastruktur
  (ADR-0022 + Cricket) ist das Pattern etabliert — weitere Modi docken analog an.

- **ADR-0013 Ergänzung:** `GameMode.applyDart` **kann optional Gegner lesen** — Strategie
  entscheidet, ob sie nötig sind. Cricket braucht sie, X01 ignoriert sie, künftige Modi
  können frei wählen.

- **X01 bleibt unverändert — Regressionssicherheit:** Die komplette Test-Suite läuft grün.
  493 X01-/Infra-Tests grün wie zuvor, 39 neue Cricket-Tests grün.
