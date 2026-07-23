# 0022 — Modus-Infrastruktur (Gegner-Lesezugriff, Katalog, UI-Abstraktion)

**Status:** Akzeptiert

## Kontext

Phase 4 (Weitere Spielmodi) beginnt mit einer zweiteiligen Umsetzung: **PR A** (diese
Entscheidung) etabliert die generische Infrastruktur, damit weitere Modi rein additiv
andocken können; **PR B** implementiert dann den ersten zusätzlichen Modus (Cricket).
Cricket braucht — im Gegensatz zu X01 — Lesezugriff auf die Gegner-Zustände, um die
Punktevergabe zu berechnen. Damit entsteht Designraum: Wie können neue Modi strukturiert
werden, ohne X01 zu brechen?

**Design-Grundlage:** [ADR-0013](0013-spielmodi-domaenenlogik.md) beschreibt bereits
`GameMode<S : Any>` als generische Strategie mit modus-spezifischem Spielerzustand `S`.
Diese Entscheidung erklärt, wie die Infrastruktur ausgebaut wird, um von der reinen
X01-Implementierung zu einer wahrhaft modularen, multiplen-Modi-Architektur zu wechseln.

## Entscheidung

### Gegner-Lesezugriff statt Gegner-Kopie oder Sub-Interface

**Anforderung:** Cricket braucht die aktuellen Gegner-Zustände, um zu entscheiden,
ob ein Spieler gewonnen hat (wer zuerst alle Felder geschlossen + führt bei Punkte).

**Alternativprüfung:**

- **(a) Gegner als Kopie im State:** `CricketState(closed, points, **opponentStates: List<CricketState>**)`.
  **Ablehnung:** Erhöht die State-Redundanz, bricht die modusspezifische Zustandskapselung.
  Neue Modi müssten alle Gegner-States replizieren. Wartbar, aber speicherintensiv.

- **(b) Typecheck/Interface-Hierarchy:** Zur Laufzeit prüfen, ob der Gegner ein `CricketState`
  ist, per `instanceof` casten, Feld zugreifen. **Ablehnung:** Breaks type safety,
  erzeugt Fehleranfälligkeit bei neuen Modi. `GameMode.applyDart` müsste Gegner-Handling
  im Code duplizieren.

- **(c) Lesender Provider (gewählt):** `GameMode.applyDart(state, dart, config, **opponents:
  List<S> = emptyList()**)` — die Engine reicht aktuelle Gegner-Zustände durch. Modi, die
  sie brauchen (Cricket), nutzen sie; Modi, die sie nicht brauchen (X01), ignorieren sie.
  **Vorteil:** (1) Type-safe — `opponents: List<S>` hat die gleiche Typ wie der Spielerzustand.
  (2) Nicht invasiv — X01 braucht keinen Code zu ändern. (3) Erweiterbar —
  Cricket (Gegner-Zustände) kann genauso Gegner brauchen wie künftige Modi.

**Implementierung:**
- `LegEngine<S>` erhält einen Konstruktor-Parameter `opponents: () -> List<S>`
  (ein Zero-Arg-Lambda, das bei jedem Aufruf die aktuellen Zustände ALLER
  Mitspieler als Liste liefert; Default: leere Liste).
- Beim `applyDart`-Aufruf werden die aktuellen Gegner-Zustände dynamisch aus dem
  Provider gelesen — **nicht gecacht**, immer live.
- `MatchEngine<S>` speist an **allen drei Erzeugungsstellen** des `LegEngine`-Snapshots
  die Gegner-Provider ein (Init erstes Leg, `onNewLeg` nächstes Leg, Undo-Replay).
- **Cross-Turn-Undo-Korrektheit:** Der Replay nutzt den **Referenzmodell**-Ansatz:
  `MatchEngine` führt parallel die Engines aller Spieler nach und kann damit auf Anfrage
  deren live Zustände liefern. Tests belegen, dass Gegner-Provider auch im Undo-Replay
  korrekt arbeitet (Gegner-Zustände stimmen mit den erneut durchgespielen Engines überein).

### GameModeCatalog: Registry ohne typisierte Instanzen

**Problem:** Wo/Wie werden die verfügbaren Modi registriert und typisiert? Die App
braucht (1) eine Liste verfügbarer Modi (für UI-Auswahl), (2) eine Weise, zur Laufzeit
eine `GameMode<S>`-Instanz zu erstellen (`S` ist erst bei `create()` bekannt).

**Entscheidung:**
- Neues `object GameModeCatalog` (in `com.mechanicel.tomsdarts.game`): eine
  **unveränderliche Registry von `GameModeInfo`-Einträgen**, nicht von `GameMode`-Instanzen.
- `GameModeInfo(key, usesStartScore, usesDoubleOut)` hält **Metadaten**, keine Instanzen.
  Das entkoppelt die Registry-Verwaltung von der konkreten Typisierung.
- **Typisierte Auflösung in `provideFactory`:** Die einzige Stelle, wo ein unbekannter
  `mode.key` zu einer `GameMode<S>`-Instanz wird, ist im `GameViewModel.provideFactory`-`when`.
  Dort wird der Mode gecased, `S` aus dem Fall-Arm bekannt, Instanz erzeugt.
  Unbekannter Key → `IllegalArgumentException` — klar signalisiert.
- **Rationale:**
  1. **Katalog bleibt klein und stabil:** Reine Metadaten, keine Code-Duplikation
     (Pro Mode einmal: `GameModeInfo`-Eintrag + Factory-`when`-Arm).
  2. **Type-Safety:** Die Typisierung (`GameMode<X01State>` vs. `GameMode<CricketState>`)
     bleibt lokal im `provideFactory`-`when`, nicht im Katalog selbst.
  3. **Erweiterbarkeit:** Neuer Mode = neuer `GameModeInfo`-Eintrag + neuer `when`-Arm.
     Die Katalog-Architektur ändert sich nicht.

**Konstruktion:**
```
object GameModeCatalog {
  val entries = listOf(
    GameModeInfo("X01", usesStartScore = true, usesDoubleOut = true),
    GameModeInfo("Cricket", usesStartScore = false, usesDoubleOut = false),
    // …
  )
}
```

### UI-Abstraktion: ModeUiAdapter und sealed PlayerBoardUi

**Problem:** Die UI zeigt den Spielerzustand an — `remaining` für X01, `closed`/`points`
für Cricket. Wie wird das modusspezifische UI generisch?

**Entscheidung:**
- Neues Interface `ModeUiAdapter<S>` (in `ui.game`-Paket) mit zwei Aufgaben:
  1. `board(state: S): PlayerBoardUi` — konvertiert Spielerzustand in generisches
     UI-Model.
  2. `checkout(state: S, config: GameConfig): List<Dart>?` — generiert den
     Checkout-Vorschlag als Dart-Kombination (X01 nutzt diese für „Double-Out",
     Cricket vielleicht für etwas anderes), oder `null` wenn der Modus keinen
     Vorschlag kennt bzw. der Zustand nicht auscheckbar ist.

- Sealed Klasse `PlayerBoardUi` mit Modi-spezifischen Subtypes:
  - `PlayerBoardUi.X01(remaining: Int)` — Restpunkte.
  - `PlayerBoardUi.Cricket(fields: List<CricketFieldUi>, points: Int)` — Feld-Marks-Liste (je `CricketFieldUi(target, marks)`) + Punkte.
  - Beliebig erweiterbar für neue Modi.

- `GameViewModel<S>` wird generisch: Constructor-Parameter `uiAdapter: ModeUiAdapter<S>`,
  nutzt ihn, um `uiState` zu füttern. Nicht länger `X01`-fest.

**Rationale:**
1. **Generisch, aber typsicher:** Kein `Any`-Casting, keine stringly-typen Checks.
2. **Keine UI-Duplikation:** Board-Rendering nutzt sealed `PlayerBoardUi.when`,
   nicht vier verschiedene Compose-Funktionen.
3. **Adapter-Pattern:** Der Adapter lebt bei der konkreten Strategie (z.B.
   `X01UiAdapter : ModeUiAdapter<X01State>`), nicht im generischen ViewModel.

**Konstruktion:**
```
val gameVm = GameViewModel(
  matchRepository, playerRepository, playerIds, config,
  mode = X01Mode(), uiAdapter = X01UiAdapter(),
)
```

### Setup-Durchreichung: modeKey statt MODE_TYPE-Konstante

**Problem:** Bisher war der Modus hartcodiert (`MODE_TYPE="X01"`). Cricket-Phase
braucht einen flexiblen, zur Laufzeit wählbaren Mode.

**Entscheidung:**
- Entfernen Sie die Konstante `MODE_TYPE = "X01"`.
- Stattdessen läuft ein `modeKey: String` durch die Setup-Kette:
  1. `SetupScreen.onConfirm(playerIds, **modeKey**, startScore, doubleOut, legs, sets)`
     — `modeKey` als zweiter Parameter, direkt nach `playerIds`.
  2. `MainActivity` speichert `rememberSaveable("modeKey", modeKey)`
  3. `GameScreen` empfängt `modeKey` als Parameter
  4. `GameViewModel.provideFactory(**modeKey**, playerIds, startScore, doubleOut,
     legsToWin, setsToWin)` — `modeKey` als ERSTER Parameter, nutzt den Key im
     `when`, um die Mode-Instanz zu erzeugen.
- `Match.modeType` wird weiterhin persistiert, lädt aber jetzt den Runtime-`modeKey`
  statt fest `"X01"`.

### ModeSection im Setup: sichtbar, wenn > 1 Katalog-Eintrag

**Entscheidung:**
- Neue `ModeSection` im Setup-Screen (über oder neben `StartScoreSection`).
- **Sichtbarkeit:** Nur gezeigt, wenn `GameModeCatalog.entries.size > 1` (sonst kosmetisch).
- Heute: nur X01 im Katalog → `ModeSection` unsichtbar, Setup bleibt wie zuvor.
- Sobald Cricket im Katalog: `ModeSection` erscheint, Nutzer kann wählen.
- **StartScore- und DoubleOut-Sections an Katalog-Flags gebunden:**
  `usesStartScore` / `usesDoubleOut` aus `GameModeInfo` steuern, ob die Sections
  gezeigt werden. Cricket hat `usesStartScore=false` → keine StartScore-Karten.

### 2-PR-Schnitt: Infrastruktur vor Konkrete Modi

**Entscheidung:**
- **PR A (diese):** Aufbau Infrastruktur — `GameMode.applyDart(…, opponents)`,
  `GameModeCatalog`, `ModeUiAdapter<S>`, `GameViewModel<S>`, `ModeSection` im Setup.
  **X01 bleibt völlig unverändert** — Messlatte: alle bestehenden Tests grün.
  492 Tests grün, darunter 14 neue Härtungstests für Gegner-Provider,
  Cross-Turn-Undo im Replay, Factory-Randfälle.

- **PR B:** Cricket-Strategie + `CricketUiAdapter` — anbinden an die
  Infrastruktur, Cricket-Tests.

**Rationale:** Infrastruktur isoliert testen, Risiko für X01-Regression
minimieren.

## Konsequenzen

- **Multiple Spielmodi sind jetzt möglich:** Neue `GameMode<S>`-Strategien können
  rein additiv hinzukommen (neue `GameModeInfo`-Eintrag, neuer `ModeUiAdapter`,
  neuer `when`-Arm in `provideFactory`).
- **Gegner-Provider ist live und sicher:** Zur Laufzeit aktualisiert, Cross-Turn-Undo
  korrekt, keine State-Redundanz.
- **Katalog = Single Source of Truth für Modi:** `GameModeCatalog` ist die
  zentrale Registry; UI und Setup konsultieren nur diesen Katalog.
- **X01 unverändert — Regressionssicherheit:** 492 Tests grün
  (313 alt + 14 neue Härtungstests + 165 alt spezialisierten).
- **Nächste Modus-Implementierung ist schneller:** Mit dieser Infrastruktur kann
  eine neue Strategie (`CricketMode : GameMode<CricketState>`) in einer PR
  entwickelt werden, ohne die generische Architektur nochmal zu überarbeiten.
- **ADR-0013 Update:** Die Aussage zu `GameMode<S>` und Gegner-Ignoranz wird
  erweitert — `applyDart` kann optional Gegner lesen, Modi-Design entscheidet,
  ob sie genutzt werden.
