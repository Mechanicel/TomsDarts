# 0031 — Modus-Infrastruktur für Killer – Spieler-Identität, Eliminierung, Gegner-Sicht

**Status:** Akzeptiert

## Kontext

**Killer** (Phase 4, v1-Produkt) ist der letzte Klassiker-Modus mit neuen Vertragsanforderungen an
die Modus-Infrastruktur (ADR-0022), die X01, Cricket, Around the Clock, Shanghai und Count Up nicht haben:

1. **Spieler-Identität**: Jeder Spieler bekommt beim Match-Start eine Zielzahl
   (`playerIndex`-abhängig), die fest über das komplette Match läuft und unabhängig bei Undo/Leg-Wechsel
   bewahrt bleiben muss. `GameMode.initialState(config)` (bisher 1-Param) braucht den Sitzplatz des
   Spielers, um per-Spieler-Zustand zu erzeugen.

2. **Eliminierung**: Spieler können alle Leben verlieren und aus dem aktiven Spielfluss fallen
   (kein Werfen mehr, aber Gegner-Effekte wirken weiter). Die Engine muss sie bei der Aufnahme-Rotation
   überspringen (`nextIndex` → `nextActiveIndex` im `turnEnded`-Zweig).

3. **Gegner-Abhängige Anzeige**: Die verbleibenden Leben eines Spielers ergeben sich aus den
   Treffern der Gegner (Inversions-Trick, hier neu eingeführt — baut auf dem Gegner-Lesezugriff aus
   ADR-0022 auf, ADR-0021-konform bzgl. Determinismus). Die UI-Adapter brauchen beim Rendern
   der Spieler-Karte Zugriff auf Gegner-Zustaende (`board(state)` → `board(state, opponents)`).

Diese drei Anforderungen sind **additive Vertragserweiterungen** (Overloads/Defaults), die keinen
bestehenden Modus anfassen: Der Bestandscode der fünf bisherigen Modi und ihre 733 Tests bleiben
unverändert, nur die Schnittstellen werden ergänzt.

### Verworfene Alternativen

- **applyDart-Fremdmutation:** Gegner-Effekte (Leben reduzieren) direkt in der `applyDart` des
  Werfers als Seiteneffekt auf Gegner-Zustaende. **Problem:** Bricht den Read-only-Vertrag
  der Gegner-Liste + zerstört Determinismus beim Replay (Reihenfolge der Seiteneffekte variabel).

- **`opponentEffects` im `DartOutcome`:** Extra-Feld für Fremdwirkungen auf Gegner im `DartOutcome`,
  die Engine wendet sie separaten an. **Problem:** Gleiche Risiken wie obiger Punkt — Zustand
  bleibt nicht reine Funktion der Dart-Historie (kritisch für Undo/Replay).

- **No-Op-Würfe für Eliminierte:** Eliminierte Spieler werfen „Phantom-Darts" (0-Wert),
  die persistiert werden. **Problem:** DB-Verschmutzung (Würfe, die spielfachlich nie passierten),
  wie die verworfene Shanghai-Variante mit Phantom-Darts — unnötige historische Komplexität.

**Gewählte Lösung:** **Inversions-Trick** (hier neu eingeführtes Muster, siehe „Verweise") — jeder
Spieler trackt nur **seine eigenen Treffer** (`hitsOn`); die Leben der Gegner werden **rein
abgeleitet** aus den Treffer-Countern der anderen. Determinismus bleibt garantiert (reine Funktion
der Dart-Historie), Zustand hat keine Redundanz (kein Duplikat wie „mein Leben" und
„Leben = 3 - genommene Treffer").

## Entscheidung

### 1. Spieler-Identität: `GameMode.initialState` mit optionalem `playerIndex`

```kotlin
interface GameMode<S : Any> {
    fun initialState(config: GameConfig): S
    
    fun initialState(config: GameConfig, playerIndex: Int): S = initialState(config)
}
```

- **Default-Overload:** Delegiert an die 1-Param-Version, sodass Modi ohne Identität (X01, Cricket,
  Around the Clock, Shanghai, Count Up) unverändert bleiben (Quellcode-Kompatibilität).
- **Killer-Impl (geplant für PR B, noch nicht vorhanden):** Nutzt `playerIndex`, um die Zielzahl
  deterministisch zu bestimmen (z. B. `KILLER_NUMBERS[playerIndex]` aus dem `GameConfig`-Seed, der
  vorab eingefroren ist).
- **KDoc-Hardliner:** Kein Zufall in `initialState` — die Engine erzeugt `LegEngine<S>` bei
  Undo-Replay und Leg-Wechsel neu; ein hier gewürfelter Wert würde nicht deterministisch sein.
  Zufall muss vorab in `GameConfig` eingefroren sein.

### 2. Eliminierung: `GameMode.isEliminated` mit optionalem `opponents`-Snapshot

```kotlin
interface GameMode<S : Any> {
    fun isEliminated(state: S, opponents: List<S>): Boolean = false
}
```

- **Reine Funktion:** Nur aus Zustaenden (kein Zufall, keine Seiteneffekte), damit Undo-Replay
  denselben Wert ausliest (deterministisch).
- **Gegner-Snapshot:** Die Liste enthält nur die MITSPIELER (ohne den Spieler selbst), in Snapshot-Reihenfolge,
  identisch zum Vertrag von `applyDart.opponents`.
- **Default `false`:** Modi ohne Eliminierung (alle bestehenden) bleiben unverändert.
- **Skip-Semantik in MatchEngine:** Nur im `turnEnded`-Zweig der Rotation (`nextActiveIndex`-Schleife),
  **nicht** in Sieg-Pfaden (`deferLegTransition`): Ein neues Leg startet mit frischen `LegEngine`s,
  in denen per Definition niemand eliminiert ist — Skip wäre wirkungslos und würde auf veralteten
  Zustaenden des gerade beendeten Legs rechnen.
- **Ein Skip greift erst zum Aufnahme-Ende:** Ein mitten in der eigenen Aufnahme eliminierter Werfer
  wirft trotzdem seine restlichen Darts zu Ende — `DartOutcome` kennt kein eigenes Aufnahme-Ende-Signal,
  das entscheidet erst die `LegEngine` anhand der Dart-Anzahl (Annahme, die PR B kennen muss).

### 3. Gegner-Abhängige Anzeige: `ModeUiAdapter.board` mit optionalem `opponents`-Snapshot

```kotlin
interface ModeUiAdapter<S : Any> {
    fun board(state: S): PlayerBoardUi
    
    fun board(state: S, opponents: List<S>): PlayerBoardUi = board(state)
}
```

- **Default-Overload:** Delegiert an 1-Param-Version, sodass bestehende Adapter (X01, Cricket,
  Around the Clock, Shanghai, Count Up) quellcode-kompatibel bleiben.
- **Gegner-Snapshot:** Identisch zur Konvention in `applyDart` + `isEliminated`, nur lesend.
- **VM-Wirung:** `GameViewModel.buildPlayers` berechnet pro Spieler die `opponents`-Liste aus allen
  anderen Spielern des Snapshots und reicht sie an `uiAdapter.board(ps.state, opponents)` durch.

### 4. Einheitliche Verdrahtung: Ein Ort für alle `playerIndex`-Durchreichungen

`MatchEngine.createLegEngine(playerIndex: Int)` ist die **einzige Stelle**, über die alle drei
Erzeugungspfade laufen:
- Konstruktor-Init: erste `LegEngine`s
- Undo-Voll-Replay (`undoLastDart`): Engines frisch ersetzt
- Leg-Wechsel (`commitLegTransition`): nächstes Leg mit neuen Engines

Damit bleibt die `playerIndex`-Identität eines Spielers stabil über Undo und Leg-Grenzen
hinweg — vorausgesetzt, der Modus leitet sie deterministisch ab (siehe KDoc in `initialState`).

## Konsequenzen

### Bestandsmodi unverändert (733 Tests grün)
Alle fünf Katalog-Modi (X01, Cricket, Around the Clock, Shanghai, Count Up) implementieren
die neuen Methoden nicht — nutzen die Defaults. Ihre 733 bestehenden Tests laufen grün ohne Änderung.

### PR A (Infrastruktur) + PR B (Killer v1-Produkt)
- **PR A (diese PR):** Die drei Vertragserweiterungen im `GameMode`/`ModeUiAdapter`-Interface,
  `LegEngine(playerIndex)`, `MatchEngine.nextActiveIndex`/`isEliminated`, Tests der
  Infrastruktur selbst (Vertrag-Beweise via `EliminationFakeMode`, Integrationstests Skip-Logik,
  Gegner-Sicht bei bestehenden Adaptern). **39 neue Tests** (MatchEngineEliminationTest 14 +
  MatchEngineEliminationHardeningTest 7 + GameViewModelOpponentBoardTest 3 + GameViewModelEliminationTest 3 +
  ModeUiAdapterOpponentBoardRegressionTest 6 + GameModeContractTest +6).

- **PR B (Phase 4, später, noch nicht umgesetzt):** Killer-Implementierung (`KillerState`/`KillerMode`)
  mit v1-Produktzuschnitt:
  - **Zufalls-Zahlen via `GameConfig`-Seed (geplant, noch nicht vorhanden):** In `GameConfig` können
    5 Zielzahlen (je Spieler) vorab per Seed-RNG gemischt werden; Match konstant. Keine dynamische
    Zahl pro Match/Spieler.
  - **3 feste Leben pro Spieler.**
  - **Ab 2 Spielern spielbar** (übliches Minimum der App), fachlich ab 3 Spielern interessanter.
  - **`scored=0` für jeden Killer-Dart** (Killer hat keine eigene Punktwertung, anders als X01 & Co.).
  - **Keine Selbst-Treffer-Variante:** Würde eine vierte Erweiterung `legScore(state, opponents)`
    erfordern. Bewusst vermieden, um PR B atomar zu halten.

### Bewusst zurückgestellt (BACKLOG)
- **Setup-Zahlwahl pro Teilnehmer:** Statt des (in PR B geplanten, noch nicht vorhandenen) Seeds in
  `GameConfig` können Spieler die fünf Zielzahlen vor Match-Start individuell auswählen →
  `setupChoice: List<Int>` in Setup-Screen, fließt wie der Seed vorab über `GameConfig` ein.
- **Konfigurierbare Leben:** `gameConfig.killerLives: Int` (default 3, geplant, noch nicht vorhanden)
  statt hartcodiert.
- **Selbst-Treffer-Variante:** Würde `legScore(state, opponents)` erfordern — Killer mit
  Score-Ranking statt Leben-Ranking (Gegner-Effekte kompensiert). Später.

### Verweise
- [ADR-0013](0013-spielmodi-domaenenlogik.md) — Strategie-Interface `GameMode<S>`
- [ADR-0021](0021-undo-cross-turn-replay.md) — Replay-Determinismus (Grund, warum die Ableitung des Inversions-Tricks rein sein muss)
- [ADR-0022](0022-modus-infrastruktur.md) — Gegner-Lesezugriff, Katalog, UI-Abstraktion
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause (profitiert automatisch vom Skip)
- [ADR-0027](0027-undo-im-gewonnen-zustand.md) — Undo im Gewonnen-Zustand (v1-Killer nicht betroffen)
- [ADR-0028](0028-leg-ende-ohne-werfer-sieg.md) — `legEnded`/`legScore`-Vertragserweiterung (nicht Killer)
