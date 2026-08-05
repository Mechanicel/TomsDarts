# 0029 — Shanghai als vierter Katalog-Modus (erster Nutzer der legEnded-Infrastruktur)

**Status:** Akzeptiert

## Kontext

[ADR-0028](0028-leg-ende-ohne-werfer-sieg.md) hat die rundenbasierte Leg-Ende-Infrastruktur
(`legEnded`, `legScore`, Rangvergleich-Gewinner) etabliert. Shanghai ist der **erste konkrete Modus**,
der diese Infrastruktur nutzt — der erste **rundenbasierte Modus**, bei dem das Leg nicht durch
einen Werfer-Sieg endet, sondern nach einer festen Anzahl Runden per Punktvergleich.

Mit der etablierten ADR-0028-Infrastruktur dockt Shanghai rein additiv an. Das Pattern folgt
ADR-0022 (Modus-Infrastruktur) und dem Cricket-/Around-the-Clock-Bauplan.

## Entscheidung

### Shanghai-Regeln

Implementiert wurde die **Standard-Variante:**

1. **Runden und Zielzahlen:** 7 reguläre Runden mit Zielzahlen 1, 2, 3, …, 7 der Reihe nach.
   Nach Runde 7: **Sudden Death** (Stechen) bei Gleichstand — Runde 8 und weitere laufen zyklisch
   weiter (Runde 8 → Ziel 1, Runde 9 → Ziel 2, etc.), bis ein eindeutiger Gewinner feststeht.

2. **Aufnahmen:** Pro Runde wirft ein Spieler genau **eine Aufnahme (3 Darts)**. Kein Bust,
   kein Checkout. Nach 3 Darts endet die Aufnahme automatisch; eine neue Runde beginnt.

3. **Treffer und Punkte:** Nur Treffer auf die **aktuelle Zielzahl** punkten: `target * dart.multiplier`.
   - Zielzahl mit Single (1×) → `1 × Zielzahl` Punkte.
   - Zielzahl mit Double (2×) → `2 × Zielzahl` Punkte.
   - Zielzahl mit Triple (3×) → `3 × Zielzahl` Punkte.
   - Jede andere Zahl, Bull und Miss → 0 Punkte (aber der Dart zählt als geworfen).

4. **Shanghai (sofort Sieg):** Wenn ein Spieler in **einer Aufnahme** die **Single, Double und
   Triple** der aktuellen Zielzahl trifft (in beliebiger Reihenfolge, auf beliebige Positionen
   verteilt in den 3 Darts) — das ist ein **Shanghai** — dieser Spieler gewinnt das Leg sofort
   (`legWon = true`). Shanghai hat Vorrang vor dem Rundenende. Ferner schließt sich nach Shanghai
   die Aufnahme sofort (kein 4. Dart); der `visitHits` wird geleert (State-Vertrag).

5. **Rundenende und legEnded:** Nach dem 3. Dart einer Aufnahme, wenn folgende Bedingungen alle zutreffen:
   - Der Werfer hat **mindestens 7 Runden** abgeschlossen (oder ist in Sudden Death).
   - **Alle Gegner** haben mindestens genauso viele Runden abgeschlossen (der Werfer ist der
     letzte oder gleichzeitig fertig — Vergleich: `completedRounds >= allOthers.completedRounds`).
   - Es existiert ein **eindeutiger Punkte-Führender** — ein Spieler hat die höchsten Punkte,
     **alle anderen haben weniger** (kein Gleichstand an der Spitze).
   → Dann meldet der Modus `legEnded = true`.

6. **Gleichstand → Sudden Death:** Wenn am Ende von Runde 7 oder später zwei oder mehr Spieler
   die gleichen Punkte haben (Gleichstand an der Spitze), meldet der Modus **kein** `legEnded`.
   Das Spiel geht in weitere Runden (Runde 8, 9, …) über — so lange, bis ein Spieler eindeutig
   führt und die Bedingung von (5) erfüllt ist.

7. **Kein Bust, kein Checkout:** Shanghai kennt diese Konzepte nicht; jede Aufnahme läuft zu Ende
   (3 Darts) oder endet früher nur durch Shanghai-Sieg oder Match-Ende.

### State-Vertrag

`ShanghaiState` speichert:
- `dartsThrown: Int` — Anzahl der insgesamt geworfenen Darts (Quelle der Wahrheit für `round`).
- `points: Int` — aktueller Punktestand des Spielers.
- `visitHits: Set<Int>` — Multiplikatoren der **laufenden Aufnahme** (1=Single, 2=Double, 3=Triple).
  **Invariante:** Nach einer vollen Aufnahme (3 Darts) oder bei Shanghai-Sieg wird `visitHits`
  geleert → `emptySet()`. Dies geschieht **immer**, auch bei `legWon`, um Undo/Replay-Konsistenz
  (ADR-0021) zu gewährleisten. Nach dem Reset beschreibt der Zustand bereits die nächste Runde.

Abgeleitete Properties (aus `dartsThrown`):
- `round: Int` — 1-basiert, berechnet aus `dartsThrown / 3 + 1`.
- `target: Int` — Zielzahl der Runde, `(round - 1) % 7 + 1`.
- `completedRounds: Int` — Anzahl vollständig abgeschlossener Runden, `dartsThrown / 3`.

### UI-Entscheidungen

**Neue `PlayerBoardUi.Shanghai`-Karte:**
- **Hero-Wert:** `points` (der Punktestand), dargestellt als große Zahl mit `displaySmall`-Größe.
  Dies ist das Unterscheidungsmerkmal zwischen Shanghai und anderen Modi — nicht die Zielzahl,
  sondern der akkumulierte Score zählt.
- **Runde und Ziel:** Zeile „Runde n / Ziel z" im Card-Header.
  - Runden 1–7: „Runde n / 7".
  - Runden 8+ (Sudden Death): „Runde n" (ohne die „/ 7", da die reguläre Grenze überschritten ist).
  - **Wort „Stechen" / „Sudden Death"** wird **nicht** auf der Karte eingeblendet. Stattdessen
    wird im Scoreboard-Kopf ein visueller **Stechen-Chip** angezeigt, sobald **alle Spieler**
    in Runde > 7 sind (das ist der Punkt, ab dem eine Entscheidung im Stechen fällt). Dies ist
    verlässlicher und zentraler als dezentrale Markierungen pro Karte.
- **Visit-Zellen:** S/D/T-Treffer der laufenden Aufnahme werden in den Zellen sichtbar gemacht
  (wie Cricket). Die Zellen sind immer sichtbar (stabile Kartenhöhe).
  - **Kontrastierung:** S/D/T-Zellen nutzen Invertierung der `contentColor` und `containerColor`
    (nicht neue Farben), um mit bestehenden Cricket/ATC-Zellen konsistent zu bleiben.
- **Keine L/S-Anzeige:** Anders als Cricket zeigt Shanghai die laufende Punkt-Summe (Hero), nicht L/S.
- **Zustand nach legWon:** State-Vertrag: der Zustand wird sofort nach Shanghai-Sieg aktualisiert
  (visitHits geleert, nächste Runde aktiv) — für Undo/Replay-Konsistenz (ADR-0021), nicht für die
  Anzeige. Das **LegWon-Panel** selbst rendert kein Board — es zeigt nur Name und Stand
  (`StandingsBlock`, „L x · S y"); die Kontrollpause wird beim Shanghai-Sieg direkt übersprungen
  (ADR-0026/-0027), es gibt also keinen Zwischenschritt, der die bereits aktualisierte „nächste
  Runde" zeigt. Auf der **Spieler-Karte im Playing-Scoreboard** sichtbar wird der bereits
  fortgeschrittene Zustand nur bei regulären (nicht leg-beendenden) Aufnahmen, während der
  Kontrollpause danach.

### Code-Struktur: rein additiv

Shanghai dockt über vier neue Dateien an (Muster wie Cricket/ATC):

1. **`ShanghaiState`** (pure Domäne) — Value-Object mit `dartsThrown`, `points`, `visitHits`.
   - Companion-Konstanten: `ROUNDS = 7`, `DARTS_PER_ROUND = 3`.
   - Helper-Funktionen: `roundOf(dartsThrown)`, `targetOf(round)`, `initial()`.

2. **`ShanghaiMode : GameMode<ShanghaiState>`** (pure Domäne) — implementiert die Regeln.
   - `key = "SHANGHAI"`, `displayName = "Shanghai"`.
   - `initialState()` → `ShanghaiState.initial()` (0 Darts, 0 Punkte, leere visitHits).
   - `applyDart(state, dart, config, opponents)` → neue `ShanghaiState` + `DartOutcome`.
   - Prüft Shanghai (S+D+T) → `legWon = true` (Invariante: geschieht nur beim 3. Dart einer Aufnahme).
   - Prüft Rundenende (legEnded) via `hasUniqueLeader()` (privat).
   - Ignoriert die `opponents`-Liste **außer** für die Rundenende-Prüfung (wer hat seine Runde
     abgeschlossen, gibt es einen eindeutigen Führenden). IST-Verhalten: >= Vergleich bei
     `completedRounds` (voreilende Gegner blockieren nicht).
   - `legScore()` → `state.points` (Rangwert für Gewinner-Vergleich in der Engine).

3. **`ShanghaiUiAdapter : ModeUiAdapter<ShanghaiState>`** (UI-Abstraktion).
   - `board(state)` → `PlayerBoardUi.Shanghai(round, target, points, visitHits)`.
   - `checkout()` → `null` (Shanghai kennt Checkout nicht).

4. **Katalog-Eintrag** in `GameModeCatalog`:
   ```
   GameModeInfo(key = SHANGHAI, usesStartScore = false, usesDoubleOut = false)
   ```

5. **UI-Integration:**
   - `GameUiState.kt` erhält `PlayerBoardUi.Shanghai` (neuer sealed subtype) mit
     `round: Int, target: Int, points: Int, visitHits: Set<Int>, companion ROUNDS = 7`.
   - `GameViewModel.provideFactory` wächst um einen `SHANGHAI`-Branch (erzeugt
     `GameViewModel<ShanghaiState>` mit `ShanghaiMode()` + `ShanghaiUiAdapter()`).
   - `MatchScoreboard.kt` erhält:
     - `ShanghaiBoard` — Rendert die Karten für Shanghai (delegiert an Komponenten).
     - `ShanghaiPointsHero` — Hero mit großer Punktezahl.
     - `ShanghaiVisitRow` — Zeile mit S/D/T-Zellen.
     - `ShanghaiVisitCell` — Einzelne Zelle (mit Kontrastierung).
     - `shanghaiCardCd` — Hilfsfunktion für `contentDescription`.
     - **Stechen-Chip im Scoreboard-Kopf:** Sichtbar, wenn **alle** Spieler in `round > 7`.
       (nicht pro-Spieler-Chip, sondern zentral, weil die Bedingung „alle über Runde 7"
       nur global feststellbar ist).

6. **Strings in `res/values/strings.xml`:**
   - 17 neue Einträge im Block „game_shanghai_*" für Label, Hero-Text, Visit-Zellen, Stechen-Chip.

7. **Tests:** 57 Shanghai-Tests über sieben Dateien:
   - `ShanghaiModeTest.kt` (19 Tests) — Happy Path (Runden, Treffer, Punkte, Shanghai-Sieg,
     Rundenende nach R7 mit eindeutigem Führenden).
   - `ShanghaiModeEdgeCasesTest.kt` (18 Tests) — Randfälle (Sudden Death, Shanghai-Reihenfolge,
     visitHits-Reset, Gleichstand-Verhalten, Undo-Konsistenz, Sieger-Karte mit nächster Runde).
   - `ShanghaiMatchIntegrationTest.kt` (7 Tests) — Engine-Verdrahtung über LegEngine/MatchEngine,
     Mehrspieler-Korrektheit, legEnded-Persistenz.
   - `ShanghaiUiAdapterTest.kt` (7 Tests) — UI-Adapter-Logik (round/target-Berechnung,
     visitHits-Mapping).
   - `ShanghaiViewModelTurnReviewTest.kt` (2 Tests) — Kontrollpause-Verhalten bei legEnded
     (wird übersprungen).
   - `GameModeCatalogTest.kt` (+2 Tests, 8 → 10) — Shanghai-Eintrag im Katalog.
   - `GameModeInfrastructureTest.kt` (+2 Tests, 9 → 11) — Shanghai-Branch in
     `GameViewModel.provideFactory` / UI-Adapter-Verdrahtung.

**Gesamte Test-Suite:** 693 grün (636 Bestand + 57 neue Shanghai-Tests). Verifizierte Testzahlen:
ShanghaiModeTest=19, ShanghaiModeEdgeCasesTest=18, ShanghaiMatchIntegrationTest=7,
ShanghaiUiAdapterTest=7, ShanghaiViewModelTurnReviewTest=2, GameModeCatalogTest=+2,
GameModeInfrastructureTest=+2 (19+18+7+7+2+2+2 = 57).

### IST-Verhalten (dokumentiert)

- **>=-Vergleich in Rundenende:** Bei der Prüfung „Werfer ist der letzte der Runde" wird geprüft,
  ob `opponents.all { it.completedRounds >= newState.completedRounds }`. Ein voreilender Gegner
  (der bereits eine weitere Runde angefangen hat) blockiert das Rundenende nicht.

- **Solo-Spiel:** Ein Spieler allein (keine Gegner) erfüllt die Rundenende-Bedingung trivial
  nach Runde 7 (alle Gegner haben >= Runden; es gibt einen eindeutigen Führenden — den
  Spieler selbst). Das Leg endet nach Runde 7.

- **Zustand beschreibt nach Shanghai-Sieg bereits die nächste Runde:** `visitHits` wird geleert
  und der Zustand zeigt die nächste Runde (State-Vertrag, für Undo/Replay-Konsistenz). Das
  LegWon-Panel selbst rendert **kein** Board und zeigt diesen Zustand daher nicht — sichtbar wäre
  er nur auf der Spieler-Karte im Playing-Scoreboard, falls das Match weiterginge. Dies ist
  beabsichtigt.

## Konsequenzen

- **Shanghai ist live:** Der Modus läuft offline, integriert in `GameModeCatalog` als 4. Eintrag.
  Mit Shanghai im Katalog zählt die `ModeSection` im Setup jetzt 4 Modi (X01, Cricket, ATC, Shanghai).

- **Erster Nutzer von ADR-0028:** Shanghai ist der erste konkrete Modus, der die legEnded-Infrastruktur
  nutzt. Die MatchEngine und GameViewModel-Integrationen (legEnded-Branch, Rangvergleich-Gewinner,
  Kontrollanuse-Übersprung) sind damit bewährt.

- **Rein additiv:** X01, Cricket und Around the Clock bleiben unverändert; alle bestehenden Tests
  bleiben grün.

- **Nächster Modus wird schneller:** Mit Shanghai ist ein rundenbasiertes Modus-Pattern etabliert.
  Weitere Varianten oder neue Modi folgen dem gleichen Bauplan.

- **Setup-Screen:** Mit 4 Modi im Katalog ist die `ModeSection` gut sichtbar. Mode-Labels werden
  als rohes `mode.key` angezeigt („X01", „CRICKET", „AROUND_THE_CLOCK", „SHANGHAI") — bei
  4 Karten (360 dp) bricht „AROUND_THE_CLOCK" mehrzeilig um. **Zurückgestellt:** Lokalisierte
  Modus-Labels + umbruchfähige Modus-Auswahl im Setup (siehe Backlog).

### Bewusst zurückgestellt (Backlog)

Die folgenden Aspekte sind **nicht Teil dieser Entscheidung**, sondern bewusst auf den Backlog:

1. **Shanghai-Varianten:**
   - **20-Runden-Variante:** Shanghai über 20 Ziele (statt 7) spielen. Ändert die Spieldauer,
     später nachzuziehen.
   - **Shanghai-Sieg-Anzeige auf der Sieger-Karte:** Nach Shanghai-Sieg könnte die Karte explizit
     das Wort „Shanghai!" zeigen, statt zur nächsten Runde zu springen. Design-Feinschliff, später.

2. **Gewertete Aufnahme-Summe je Modus in `LastTurnLine`:** Heute zeigt `LastTurnLine` die rohe
   Dart-Summe (Segment × Multiplier ohne Modus-Logik). Für Shanghai könnte sie die Punkt-Summe
   dieser Aufnahme zeigen (unter Beachtung der Zielzahl). Betrifft auch Cricket und ATC, ist ein
   generisches Refactoring; zurückgestellt.

3. **Lokalisierte Modus-Labels + umbruchfähige Modus-Auswahl im Setup:** Mit 4 Modi bricht die
   rohe `mode.key`-Anzeige um. Später: i18n-Keys für Modus-Namen, responsive Auswahl (Scroll/
   Pagination/Flex-Layout).

### Verweise

- [ADR-0013](0013-spielmodi-domaenenlogik.md) — `GameMode<S>` und Strategie-Pattern.
- [ADR-0021](0021-undo-cross-turn-replay.md) — Replay-Mechanik für Undo (visitHits-Reset ist
  konsistent).
- [ADR-0022](0022-modus-infrastruktur.md) — Modus-Infrastruktur und Gegner-Lesezugriff.
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause nach dem 3. Dart
  (wird bei legEnded übersprungen).
- [ADR-0027](0027-undo-im-gewonnen-zustand.md) — Undo im Gewonnen-Zustand (gelten für legWon
  wie legEnded).
- [ADR-0028](0028-leg-ende-ohne-werfer-sieg.md) — Infrastruktur legEnded/legScore und Rangvergleich
  (Shanghai ist der erste Produktionsnutzer).
