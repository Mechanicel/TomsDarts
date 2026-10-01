# 0030 — Count Up als fünfter Katalog-Modus (zweiter Nutzer der legEnded-Infrastruktur)

**Status:** Akzeptiert

## Kontext

[ADR-0028](0028-leg-ende-ohne-werfer-sieg.md) hat die rundenbasierte Leg-Ende-Infrastruktur
(`legEnded`, `legScore`, Rangvergleich-Gewinner) etabliert. Shanghai ist der erste konkrete Modus, der
diese Infrastruktur nutzt. Count Up ist der **zweite rundenbasierte Modus** — und die **einfachste
Variante**: Ein reiner **Punktesammel-Modus ohne Zielzahlen, ohne Shanghai-Sieg und ohne Feature-Überraschungen**.

Mit der etablierten ADR-0028-Infrastruktur dockt Count Up rein additiv an. Das Pattern folgt
ADR-0022 (Modus-Infrastruktur) und dem Cricket-/Around-the-Clock-Bauplan, mit lokalisiertem Code
und lokalisierter UI (Shanghai-Verhalten bleibt unverändert).

## Entscheidung

### Count-Up-Regeln

Implementiert wurde die **Standard-Variante:**

1. **Runden und Darts:** 8 reguläre Runden mit je einer Aufnahme (3 Darts pro Aufnahme, insgesamt 24 Darts).
   Nach Runde 8: **Sudden Death** (Stechen) bei Gleichstand — Runde 9, 10, … laufen unbegrenzt
   weiter, bis ein eindeutiger Gewinner feststeht.

2. **Aufnahmen und Wertung:** Pro Runde wirft ein Spieler genau eine Aufnahme (3 Darts).
   Kein Ziel, kein Bust, kein Checkout, kein Sofort-Sieg.
   - **JEDER Dart punktet seinen eigenen Wert** (Segment × Multiplier): Single/Double/Triple zählen voll,
     Bull 25, Doppel-Bull 50, Fehlwurf 0 — der Dart zählt aber als geworfen (die Aufnahme läuft weiter).
   - Die Aufnahme endet nach 3 Darts automatisch; keine vorzeitige Beendigung.
   - `legScore = points` (akkumulierter Punktestand des Spielers).

3. **Rundenende und legEnded:** Nach dem 3. Dart einer Aufnahme, wenn alle Bedingungen erfüllt sind:
   - Der Werfer hat **mindestens 8 Runden** abgeschlossen (oder ist in Sudden Death).
   - **Alle Gegner** haben mindestens genauso viele Runden abgeschlossen (der Werfer ist der letzte oder gleichzeitig fertig).
   - Es existiert ein **eindeutiger Punkte-Führender** — ein Spieler hat die höchsten Punkte,
     alle anderen haben weniger (kein Gleichstand an der Spitze).
   → Dann meldet der Modus `legEnded = true`.

4. **Gleichstand → Sudden Death:** Wenn am Ende von Runde 8 oder später zwei oder mehr Spieler
   die gleichen Punkte haben (Gleichstand an der Spitze), meldet der Modus **kein** `legEnded`.
   Das Spiel geht in weitere Runden (Runde 9, 10, …) über — so lange, bis ein Spieler eindeutig
   führt und die Bedingung von (3) erfüllt ist.

5. **Keine speziellen Features:** Anders als Shanghai gibt es kein Shanghai-Sieg-Konzept, keine
   Trefferspur (`visitHits`), keine Zielzahl-Logik — reine Punkt-Akkumulation.

### State-Vertrag

`CountUpState` speichert:
- `dartsThrown: Int` — Anzahl der insgesamt geworfenen Darts (Quelle der Wahrheit für `round`).
- `points: Int` — aktueller Punktestand des Spielers.

Abgeleitete Properties (aus `dartsThrown`):
- `round: Int` — 1-basiert, berechnet aus `dartsThrown / 3 + 1`.
- `completedRounds: Int` — Anzahl vollständig abgeschlossener Runden, `dartsThrown / 3`.

**Invarianten:**
- Der Zustand beschreibt den **aktuellen Stand nach dem letzten Dart**, nicht den Stand der letzten
  abgeschlossenen Runde. Nach Aufnahme-Ende (`dartsThrown % 3 == 0`) zeigt der Zustand bereits die nächste
  Runde.
- Keine `visitHits`, keine Trefferspur — nur `dartsThrown` und `points`.

### UI-Entscheidungen

**Neue `PlayerBoardUi.CountUp`-Karte:**
- **Hero-Wert:** `points` (der Punktestand), dargestellt als große Zahl mit `displaySmall`-Größe (oder kleiner
  bei schmalen Karten, um 4-stellige Stände zu fassen).
  Dies ist das Unterscheidungsmerkmal zwischen Count Up und anderen Modi — der akkumulierte Score.
- **Runde und Fortschritt:** Zeile „Runde n / 8" — im Portrait unterhalb des Punkte-Heros,
  im Compact-/Querformat einzeilig neben Name und Punkten (das einzeilige Compact-Layout ist das
  bewusste Delta zu Shanghai — Count Up hat keine Aufnahme-Zellen, die eine zweite Zeile bräuchten).
  - Runden 1–8: „Runde 4 / 8".
  - Runden 9+ (Sudden Death): „Runde n" (ohne die „/ 8", da die reguläre Grenze überschritten ist).
  - Im schmalen Portrait (Kartenbreite < 120 dp, typischerweise 3+ Spieler bei 360 dp Screenbreite)
    wechselt die Zeile auf die Kurzform („R n/8"), um Umbruch zu vermeiden.
  - **Wort „Stechen" / „Sudden Death"** wird **nicht** auf der Karte eingeblendet. Stattdessen
    wird im Scoreboard-Kopf ein visueller **Stechen-Chip** angezeigt, sobald **alle Spieler**
    in Runde > 8 sind. Dies ist verlässlicher und zentraler als dezentrale Markierungen pro Karte
    (identisch zu Shanghai, siehe ADR-0029).
- **Keine Zellen:** Anders als Cricket/Shanghai zeigt Count Up keine S/D/T-Trefferzellen.
  Der Hero und die Rundenzeile sind ausreichend.
- **Zustand nach legEnded:** State-Vertrag: der Zustand wird sofort nach legEnded aktualisiert
  — für Undo/Replay-Konsistenz (ADR-0021), nicht für die Anzeige. Das **LegWon-Panel** selbst
  rendert kein Board — es zeigt nur Name und Stand (`StandingsBlock`, „L x · S y"); die Kontrollpause
  wird beim legEnded-Sieg direkt übersprungen (ADR-0026/-0027).

**Generalisierter UI-Helfer (Refactoring in diesem PR):**

Punkte-Hero wurde verallgemeinert, um beide Modi zu unterstützen:
- **Alt:** `ShanghaiPointsHero(points: Int)` — nur Shanghai, hardcodierte Typo-Größe `displaySmall`.
- **Neu:** `PointsHero(points: Int, label: String, style: TextStyle)` — generisch, Aufrufer bestimmt Größe.
  - Shanghai: `label = stringResource(R.string.game_shanghai_points_label)`, `style = displaySmall`.
  - Count Up: `label = stringResource(R.string.game_countup_points_label)`, `style = displaySmall` (oder kleiner
    auf schmalen Karten < 120 dp).
  - **Nebeneffekt:** Der Generalisierung liegt auch eine Verallgemeinerung der
    Sudden-Death-Erkennung zugrunde:
    - Alt: `isShanghaiSuddenDeath(players)` — hardcodiert Shanghai-Logik.
    - Neu: `isSuddenDeath(players)` — konsultiert `isOvertime(board)` für Shanghai und Count Up,
      `null` für andere Modi.
    - `isOvertime(board): Boolean?` — dreiwertig: `true`/`false` für rundenbasierte Modi (Shanghai,
      Count Up — je nachdem ob `round > ROUNDS`), `null` für alle anderen (X01, Cricket, Around the
      Clock).

  **Erweiterbarkeit:** Ein künftiger rundenbasierter Modus ergänzt genau einen Zweig in `isOvertime`;
  `isSuddenDeath` bleibt unverändert.

  **Visuelles Delta (dokumentiert):** `softWrap=false` in `PointsHero.Text()` gilt jetzt für Shanghai
  wie Count Up. Durch Analyse als No-op eingestuft (reine Ziffernfolge, keine Umbruchstelle bei
  maxLines=1); nicht durch UI-Tests abgedeckt (es gibt keine Compose-UI-Tests im Repo).

- **`SHANGHAI_ROUND_TARGET_COMPACT_BREAKPOINT` → `ROUND_LINE_COMPACT_BREAKPOINT`:** Der
  Kartenbreiten-Schwellenwert (120 dp) für die Kurzform der Rundenzeile ist jetzt generisch benannt,
  da Shanghai und Count Up ihn gemeinsam nutzen.

### Code-Struktur: rein additiv

Count Up dockt über vier neue Dateien an (Muster wie Cricket/ATC/Shanghai):

1. **`CountUpState`** (pure Domäne) — Value-Object mit `dartsThrown`, `points`.
   - Companion-Konstanten: `ROUNDS = 8`, `DARTS_PER_ROUND = 3`.
   - Helper-Funktionen: `roundOf(dartsThrown)`, `initial()`.

2. **`CountUpMode : GameMode<CountUpState>`** (pure Domäne) — implementiert die Regeln.
   - `key = "COUNT_UP"`, `displayName = "Count Up"`.
   - `initialState()` → `CountUpState.initial()`.
   - `applyDart(state, dart, config, opponents)` → neue `CountUpState` + `DartOutcome`.
   - Prüft Rundenende (legEnded) via eindeutiger Punkte-Führender.
   - Ignoriert die `opponents`-Liste **außer** für die Rundenende-Prüfung (>=-Vergleich, wie Shanghai).
   - `legScore()` → `state.points`.

3. **`CountUpUiAdapter : ModeUiAdapter<CountUpState>`** (UI-Abstraktion).
   - `board(state)` → `PlayerBoardUi.CountUp(round, points)`.
   - `checkout()` → `null` (Count Up kennt Checkout nicht).

4. **Katalog-Eintrag** in `GameModeCatalog`:
   ```
   GameModeInfo(key = COUNT_UP, usesStartScore = false, usesDoubleOut = false)
   ```
   → **5. Katalog-Eintrag** (X01, Cricket, Around the Clock, Shanghai, Count Up).

5. **UI-Integration:**
   - `GameUiState.kt` erhält `PlayerBoardUi.CountUp` (neuer sealed subtype) mit
     `round: Int, points: Int, companion ROUNDS = 8`.
   - `GameViewModel.provideFactory` wächst um einen `COUNT_UP`-Branch (erzeugt
     `GameViewModel<CountUpState>` mit `CountUpMode()` + `CountUpUiAdapter()`).
   - `MatchScoreboard.kt` erhält:
     - `CountUpBoard` — Rendert die Karten für Count Up (delegiert an Komponenten).
     - Generalisierte `PointsHero` (siehe oben).
     - Generalisierte `isSuddenDeath` + `isOvertime` (siehe oben).
     - **Stechen-Chip im Scoreboard-Kopf:** Sichtbar, wenn **alle** Spieler in `round > 8`
       (nicht pro-Spieler-Chip, sondern zentral, identisch zu Shanghai).

6. **Strings in `res/values/strings.xml`:**
   - 9 neue Einträge im Block „game_countup_*" (points_label, round, round_extra, round_short, round_extra_short,
     player_card_cd, current_player_cd, round_cd, round_extra_cd).
   - 2 generalisierte Einträge „game_sudden_death*" (von Shanghai-Naming abstrahiert).

7. **Tests:** 40 neue Count-Up-Tests über fünf neue plus zwei erweiterte Dateien:
   - `CountUpModeTest.kt` (15 Tests) — Happy Path (Runden, Punkte, legEnded nach R8,
     Gleichstand-Behavior, Sudden Death).
   - `CountUpModeEdgeCasesTest.kt` (8 Tests) — Randfälle: gemischte Wurfarten in einer Aufnahme,
     180er-Aufnahme ohne legWon, handgerechnete Akkumulation über 8 unterschiedliche Runden,
     Gleichstand-Kette über zwei Verlängerungsrunden, 3-Spieler-Szenario mit einem Führenden
     ungleich dem Werfer, Gleichstand nur unter nicht-führenden Gegnern, riesiger Vorsprung vor
     Runde 8 ohne legEnded, Flag-Invariante (`bust`/`legWon`) über 15 Runden.
   - `CountUpMatchIntegrationTest.kt` (6 Tests) — Engine-Verdrahtung über LegEngine/MatchEngine,
     Mehrspieler-Korrektheit, Rotation.
   - `CountUpUiAdapterTest.kt` (5 Tests) — UI-Adapter-Logik (round-Berechnung, Kartensynthese).
   - `CountUpViewModelTurnReviewTest.kt` (2 Tests) — Kontrollpause-Verhalten bei legEnded.
   - `GameModeCatalogTest.kt` (+2 Tests) — Count-Up-Eintrag im Katalog.
   - `GameModeInfrastructureTest.kt` (+2 Tests) — Count-Up-Branch in `GameViewModel.provideFactory`.

**Gesamte Test-Suite:** 733 grün (693 Bestand + 40 neue Count-Up-Tests).
Verifizierte Testzahlen: CountUpModeTest=15, CountUpModeEdgeCasesTest=8,
CountUpMatchIntegrationTest=6, CountUpUiAdapterTest=5, CountUpViewModelTurnReviewTest=2,
GameModeCatalogTest=+2, GameModeInfrastructureTest=+2 (15+8+6+5+2+2+2 = 40).

### IST-Verhalten (dokumentiert)

- **>=-Vergleich in Rundenende:** Bei der Prüfung „Werfer ist der letzte der Runde" wird geprüft,
  ob `opponents.all { it.completedRounds >= newState.completedRounds }`. Ein voreilender Gegner
  (der bereits eine weitere Runde angefangen hat) blockiert das Rundenende nicht — identisch zu Shanghai.

- **Solo-Spiel (ohne opponents):** Ein Spieler allein erfüllt die Rundenende-Bedingung trivial
  nach Runde 8 (alle Gegner haben >= Runden; es gibt einen eindeutigen Führenden — den Spieler selbst).
  Das Leg endet nach Runde 8 mit der letzten leeren opponents-Liste. **Diese Konstellation ist über die
  MatchEngine nicht erreichbar** (ein Match benötigt mindestens 2 Spieler); Count Up wird immer im
  Mehrspieler-Kontext geworfen.

- **LastTurnLine-Besonderheit:** Bei Count Up ist die rohe Dart-Summe (Segment × Multiplier) ausnahmsweise
  exakt die gewertete Aufnahme-Summe (jeder Dart zählt). Das ist ein dokumentiertes Delta zu Cricket/ATC
  (wo `LastTurnLine` die rohe Summe zeigt, aber die echte Wertung ein Scoring-Ziel berücksichtigt).

## Konsequenzen

- **Count Up ist live:** Der Modus läuft offline, integriert in `GameModeCatalog` als 5. Eintrag.
  Mit Count Up im Katalog zählt die `ModeSection` im Setup jetzt 5 Modi (X01, Cricket, ATC, Shanghai, Count Up).
  → **Dringende Backlog-Folge:** Das bisherige Backlog-Item „lokalisierte Modus-Labels + umbruchfähige
  Modus-Auswahl im Setup" wird DRINGENDER. Bei 5 Modi ist die Auswahl auf 360 dp Breite eng; Umbruch
  entsteht bereits bei 3-4 Modi. **Modus-Labels im Setup nicht mehr optional, sondern stark empfohlen.**
  **Update: erledigt** — siehe [ADR-0033](0033-modus-auswahl-raster-setup.md).

- **Zweiter Nutzer von ADR-0028:** Count Up ist der zweite konkrete Modus, der die legEnded-Infrastruktur
  nutzt. Shanghai + Count Up zusammen validieren, dass die Infrastruktur generalisierbar ist.

- **Verallgemeinerter Punkte-Hero:** `PointsHero` wird von beiden Modi genutzt und ist offen für
  künftige rundenbasierte Modi mit Punkte-Fokus.

- **Rein additiv:** X01, Cricket, Around the Clock und Shanghai bleiben unverändert; alle bestehenden
  Tests bleiben grün. Shanghai-Tests werden durch die Generalisierung von `isSuddenDeath` leicht
  berührt (kapseln die Bord-Typ-Prüfung), aber Verhalten ist identisch.

- **Nächster Modus wird schneller:** Mit Count Up ist ein zweites Rundenende-Pattern etabliert
  (legEnded ohne Zielzahl-Logik). Weitere Varianten (z.B. Killer, Round the Board) folgen schneller.

### Bewusst zurückgestellt (Backlog)

Die folgenden Aspekte sind **nicht Teil dieser Entscheidung**, sondern bewusst auf den Backlog:

1. **Count-Up-Varianten:**
   - **Rundenzahl konfigurierbar:** Count Up mit 5, 10, oder 12 Runden statt fest 8. Später nachzuziehen.
   - **Bonus-Regeln:** Größter Treffer der Runde, Doppel-Start, etc. — Sonderwünsche, später.

2. **Modus-Labels im Setup:** Siehe oben unter Konsequenzen.

### Verweise

- [ADR-0013](0013-spielmodi-domaenenlogik.md) — `GameMode<S>` und Strategie-Pattern.
- [ADR-0021](0021-undo-cross-turn-replay.md) — Replay-Mechanik für Undo (State-Reset ist konsistent).
- [ADR-0022](0022-modus-infrastruktur.md) — Modus-Infrastruktur und Gegner-Lesezugriff.
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause nach dem 3. Dart
  (wird bei legEnded übersprungen, identisch zu Shanghai).
- [ADR-0027](0027-undo-im-gewonnen-zustand.md) — Undo im Gewonnen-Zustand (gelten für legEnded
  wie legWon).
- [ADR-0028](0028-leg-ende-ohne-werfer-sieg.md) — Infrastruktur legEnded/legScore und Rangvergleich
  (Count Up ist der zweite Produktionsnutzer).
- [ADR-0029](0029-shanghai-katalog-modus.md) — Shanghai als erster legEnded-Nutzer; Count Up folgt
  dem gleichen Muster mit vereinfachtem Regelwerk.
