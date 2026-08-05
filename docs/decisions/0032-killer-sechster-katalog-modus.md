# 0032 — Killer als sechster Katalog-Modus

**Status:** Akzeptiert

## Kontext

**Killer** ist der letzte Klassiker-Modus (Phase 4, v1-Produkt) und nutzt erstmals produktiv alle drei
Infrastruktur-Erweiterungen aus ADR-0031:

1. **Spieler-Identität:** Jeder Spieler erhält eine Zielzahl (1–20), die über das Match konstant ist.
2. **Eliminierung:** Spieler können alle Leben verlieren und aus dem aktiven Spielfluss ausscheiden.
3. **Gegner-Abhängige Anzeige:** Leben werden invers aus den Treffern der Gegner abgeleitet (Inversions-Muster).

Damit wird Killer das **sechste Modus-Katalog-Element** neben X01, Cricket, Around the Clock, Shanghai
und Count Up — der Katalog ist damit vollständig (Phase 4 abgeschlossen).

## Entscheidung

### 1. Regeln v1

- **Zielzahlen:** Eindeutige Zahlen 1–20 pro Spieler, deterministisch aus `GameConfig.killerSeed`
  (Default 0L; bei 0 → `Random.nextLong()`) via `(1..20).shuffled(Random(seed))[playerIndex]`.
- **Spielablauf (zwei Phasen):**
  - **Phase 1 — Killer-Werdung:** Der Spieler wirft auf sein Double. Treffer → Status wird „Killer",
    Zählweichenmeter `scored=1`.
  - **Phase 2 — Lebensabzug:** Der Spieler wirft auf die Doubles der **lebenden Gegner**.
    Treffer auf einen Gegner → `hitsOn` des Gegners erhöht sich um 1, `scored=1` für den Werfer.
    Doubles/Treffer auf Eliminierte wirkungslos.
- **Leben:** Jeder Spieler startet mit 3 Leben (hartcodiert als `DEFAULT_LIVES=3`).
  Abgeleitet als `3 − Σ(gegnerische Treffer)`. Bei ≤ 0 → eliminiert.
- **Sieg:** Spieler mit dem letzten aktiven Leben bleibt übrig → **legWon** beim Werfer.
- **Keine Bust/Leg-Beendigung:** Killer hat kein Bust-Konzept; ein Leg endet nur durch Eliminierung aller Gegner.

### 2. Seed-Handling & Determinismus (Vertrag per ADR-0021)

- **Seed-Einfrierung in `provideFactory`:** Der `killerSeed` wird in `GameConfig` nicht veränderbar gelagert.
  Ein privater Extension `withKillerSeed(seed)` friert ihn EINMAL ein (als Prod-Nutzung von PR B).
- **Replay-Vertrag:** Der Seed wird **nicht persistiert** (nicht in der `Match`-Entity).
  Für künftiges Match-Resume braucht es eine separate Persistierungs-Entscheidung (→ BACKLOG).
- **Zahlen-Stabilität:** Zahlen bleiben über Undo/Leg-Wechsel stabil, weil `LegEngine(playerIndex)`
  die Zahl deterministisch **einmalig** bei Leg-Start berechnet (in `KillerState.numberFor`).

### 3. Inversions-Muster (Formalisierung + Konsistenz)

- **Eine Formel-Quelle:** `KillerState.livesOf(number, opponents)` berechnet Leben REIN aus den
  Gegner-Zuständen: `3 − Σ Gegner-hitsOn`. Diese **zentrale Formel** wird überall benutzt:
  - UI-Adapter zum Rendern der Karte,
  - Engine zum Prüfen auf Eliminierung,
  - Tests zum Verifizieren des Zustands.
  **Keine Redundanz,** kein lokaler `lives`-Feld.
- **Gegner-Sicht über Snapshot:** Die Engine & Adapter erhalten die Gegner-Liste als Read-only-Snapshot
  (Konvention aus ADR-0031), damit die Berechnung rein funktional bleibt.

### 4. `scored`-Präzisierung gegenüber ADR-0031

Im ADR-0031-Entwurf stand pauschal „`scored=0` für jeden Killer-Dart"; dies wurde bei der Umsetzung präzisiert zu:

- **`scored=1` je wirksamem Dart** — analog zu ATC, Shanghai, Count Up (Katalog-Konsistenz).
- **Wirksamkeit-Definition:** Dart zählt, wenn er game-logisch Wirkung entfaltet:
  - Killer-Werdung des Werfers (Double auf der eigenen Zahl) → `scored=1`,
  - Lebensabzug eines lebenden Gegners (Double auf dessen Zahl) → `scored=1`,
  - Doppel/Treffer auf Eliminierte, fehlende Treffer → `scored=0`.
- **Nutzen:** Die Kontrollpause zeigt nach jeder Aufnahme sinnvolle Summen — Killer-Aktionen sind
  auf einen Blick erkennbar (vs. „alle Darts zählen als 0").

**Update-Hinweis in ADR-0031:** Die dortige Aussage wurde durch ADR-0032 präzisiert.

### 5. UI-Umsetzung

- **Spieler-Karte (Killer-Erweiterung):**
  - `PlayerBoardUi.Killer(number, isKiller, lives, maxLives=3)` + `eliminated`-Property.
  - `KillerUiAdapter` (einziger Adapter, der `board(state, opponents)` nutzt) rendert die Karte.
  - Weiß nur über `playerIndex` Bescheid (nicht Spielername) — Identität = Zielzahl.

- **Mehrspieler-Scoreboard:**
  - `KillerBoard` / `KillerNumberHero` / `KillerNumberPill` (Anzeige der Zielzahl, Pile-Style gefüllt=Killer).
  - `KillerStatusLine` (Text: „Killer", „Ausgeschieden", oder „–" im Neutral-Zustand; bei Ausgeschiedenen
    Error-Rotton + Bold für Hervorhebung).
  - `KillerLivesRow` + `KillerLifeCell` (Canvas-Punkte, Leben verlieren „von rechts", 0.38f-Dimming
    für Eliminierte um Nicht-Spielbarkeit signalisieren).
  - `killerCardCd` für die Karte (ContentDescription).

- **LiveRegion-Erweiterung:** `PlayerScoreCard` zeigt jetzt auch die „Killer"-/„Ausgeschiedenen"-Info
  via TalkBack, weil Killer der einzige Modus mit **Fremdwirkung** ist — Lebensabzug eines Gegners wäre
  sonst stumm. Bedingung: `isCurrent || board is Killer` (einziger Spezialfall).

- **11 neue Strings** im Block `game_killer_*` in `res/values/strings.xml`; **6 Previews** für die UI-Komponenten.

### 6. IST-Verhalten (dokumentiert, nicht gefixt)

- **Zyklische Zahlen-Kollision ab 20 Teilnehmern:** `playerIndex ≥ 20` → `index % 20` (zyklisch,
  z.B. Index 20 erhält Zahl 1, Index 21 erhält Zahl 2). Statt Crash ist das ein dokumentiertes
  Verhalten, **produktiv aber unerreichbar** (App hat heute keinen Teilnehmer-Cap, Setup aber auch
  keine UI zum Hinzufügen von 20+ Spielern). → BACKLOG: Entweder Teilnehmer-Cap einführen oder
  ausführliche Warnung im Setup.
- **Seed nicht persistiert:** Match-Resume würde den Seed separat laden müssen. Heute nicht umgesetzt.
  → BACKLOG: Entity-Update für künftiges Resume-Feature.

## Konsequenzen

### Infrastruktur vollständig genutzt (Phase 4 abgeschlossen)

Die drei Erweiterungen aus ADR-0031 (`initialState(playerIndex)`, `isEliminated`, `board(opponents)`)
sind jetzt **erstmals im produktiven Einsatz**. Alle nachfolgenden Katalog-Modi sind einfacher: Sie brauchen
keine Spieler-Identität und keine Eliminierung.

### Katalog zählt 6 Modi

| # | Modus | ADR | Live |
|---|---|---|---|
| 1 | X01 | [0013](0013-spielmodi-domaenenlogik.md) | ✓ |
| 2 | Cricket | [0024](0024-standard-cricket-katalog-modus.md) | ✓ |
| 3 | Around the Clock | [0025](0025-around-the-clock-katalog-modus.md) | ✓ |
| 4 | Shanghai | [0029](0029-shanghai-katalog-modus.md) | ✓ |
| 5 | Count Up | [0030](0030-count-up-katalog-modus.md) | ✓ |
| 6 | **Killer** | **0032** (diese ADR) | ✓ |

### Setup-Label-Backlog-Item wird dringlicher

Der Setup-Screen zeigt heute 5 Modi-Karten; mit Killer sind es 6. Das UI-Layout (Spalten, Gridding)
wird enger. Das bestehende BACKLOG-Item „Setup-Screen-Label-Verbesserung für Modus-Katalog" wird
aktualisiert: Label-Duplikation aufgelöst (z.B. „X01 (501 Punkte)" und „X01 (301 Punkte)" → eine
Karte „X01" + Startpunkt-Wahl) → reduziert die Karten auf ~4–5 sichtbar auf einmal. Details später.

### Bewusst zurückgestellt (BACKLOG)

- **`killerSeed`-Persistenz:** Für Match-Resume muss der Seed in die `Match`-Entity und über Leg-Wechsel
  beharrlich sein. Später.
- **Teilnehmer-Cap / >20-Warnung:** Zyklische Zahlen-Kollision ab 20 Spielern sollte entweder durch Cap
  (z.B. `MAX_KILLER_PLAYERS=20`) oder Hinweis-Dialog gelöst werden. Später.
- **Letzte Aufnahme in Kontrollpause:** Die Kontrollpause zeigt heute die Darts der aktuellen Aufnahme
  (ADR-0026). Bei Killer besonders wertvoll (wer wurde getroffen?). Kann aber ohne Killer-spezifische
  Logik über ADR-0026-Extension realisiert werden → bleibt auf dem Backlog.
- **Konfigurierbare Leben:** `gameConfig.killerLives` (default 3) im Setup wählbar. Später.
- **Selbst-Treffer-Variante:** Killer mit Score-Ranking statt Leben-Ranking. Braucht `GameMode.legScore`
  (ADR-0031 nicht implementiert). Später.

## Verweise

- [ADR-0031](0031-modus-infrastruktur-killer-spieler-identitaet-eliminierung-gegner-sicht.md) — Basis-Infrastruktur (playerIndex-Vertrag, Eliminierung, Gegner-Sicht)
- [ADR-0022](0022-modus-infrastruktur.md) — Katalog-Architektur, Gegner-Lesezugriff
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause (Gegner-Effekte sichtbar)
- [ADR-0027](0027-undo-im-gewonnen-zustand.md) — Undo im Gewonnen-Zustand (Killer-Win-Undo getestet)
- [ADR-0021](0021-undo-cross-turn-replay.md) — Replay-Determinismus (Grund für Seed-Einfrierung)
