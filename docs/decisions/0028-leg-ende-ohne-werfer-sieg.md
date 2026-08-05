# 0028 — Leg-Ende ohne Werfer-Sieg (legEnded/legScore) — Vertragserweiterung für rundenbasierte Modi

**Status:** Akzeptiert

**Revidiert:** Keine vorangegangene Entscheidung; etabliert neuen Vertrag neben dem klassischen Werfer-Sieg-Modell.

## Kontext

Phase 4 (Weitere Spielmodi) beginnt mit Shanghai — ein **rundenbasierter Modus**, der nach
einer festen Anzahl Runden endet und den Gewinner dann per **Punktvergleich** ermittelt,
nicht per direktem Checkout/Abschluss. Das heißt: der Gewinner ist **nicht zwingend der
letzte Werfer**.

Der bisherige `GameMode`-Vertrag kennt nur `legWon: Boolean` in `DartOutcome` — das
signalisiert: „Der Werfer hat das Leg gewonnen." Für Shanghai und ähnliche Modi reicht das
nicht; wir brauchen ein zweites Signal: „Das Leg ist entschieden, aber der Gewinner wird
per Rangvergleich ermittelt."

**Alternativprüfung (bewusst verworfen):**

- **(a) Phantom-Dart-Trick:** Ein Modus hängt einen unsichtbaren Extra-Dart an die
  Aufnahme, um `legWon` zu signalisieren. **Ablehnung:** Bricht ADR-0004 (throw-level-Persistenz
  — jeder geworfene Dart ist real); verschmutzt die Datenbank mit fiktiven Throws.

- **(b) VM-getriebenes `endLeg()`:** Die UI/ViewModel ruft eine `endLeg()`-Methode auf der
  Engine auf, um das Leg zu beenden. **Ablehnung:** Verstößt gegen ADR-0013 (Domänenlogik
  in `GameMode`, nicht in der UI-Schicht); Regel-Entscheidungen gehören zum Modus, nicht
  zur View.

- **(c) Erweiterter Vertrag (gewählt):** Ein drittes Flag `legEnded: Boolean` in `DartOutcome`,
  das unabhängig vom Werfer-Sieg ist. Die Engine konsultiert dann `GameMode.legScore` über
  alle Spieler und ermittelt deterministisch, wer gewinnt. **Vorteil:** (1) Reine Domänenlogik,
  (2) additive Erweiterung (bestehende Modi unberührt), (3) Gewinner-Ermittlung ist Engine-Sache,
  nicht UI-Sache.

## Entscheidung

### Neue Felder in `DartOutcome<S>`

```text
@param legEnded Boolean = false — „Leg entschieden, Gewinner per Rangvergleich".
  Invariante: höchstens eines der drei Flags (bust, legWon, legEnded) ist
  true — paarweise exklusiv.
```

- `legEnded: Boolean = false` (Defaultwert, damit bestehende Modi unverändert bleiben).
- **Invariante:** `bust`, `legWon` und `legEnded` sind gegenseitig exklusiv:
  - Bei `bust` sind beide Leg-Ende-Flags immer `false`.
  - Bei `legWon` sind `bust` und `legEnded` immer `false`.
  - Bei `legEnded` sind `bust` und `legWon` immer `false`.

### Neues Interface-Feld in `GameMode<S>`

```kotlin
fun legScore(state: S): Int = 0
```

- **Semantik:** Rangwert eines Spielerzustands für den Gewinner-Vergleich bei Leg-Ende ohne
  Werfer-Sieg. Die Engine konsultiert diese Methode NUR bei `legEnded == true`.
- **Vergleich:** Höherer Wert = besserer Platz. Die Engine erklärt den höchsten Rangwert zum
  Leg-Gewinner (argmax).
- **Gleichstand-Konvention:** Sollte mehrere Spieler den gleichen Höchstwert haben, gewinnt
  der **Spieler mit dem kleinsten Index** (= erste in der Spielerliste) — deterministische
  Regel, dokumentiert, keine Willkür.
- **Default:** `0` für alle Modi mit klassischem Werfer-Sieg (X01, Cricket, Around the Clock),
  da diese `legEnded` nie melden.
- **Modi ohne Override:** Ein Modus könnte `legEnded` melden, ohne `legScore` zu überridden.
  Dann hätten alle Spieler Rangwert `0` (Gleichstand), und die Gleichstand-Konvention würde
  greifen (erster Spieler gewinnt). Dies ist zulässig und wird durch Tests
  (`RoundLimitFakeMode` mit `reportLegScore=false`) validiert.

### Neue Engine-Mechanik in `LegEngine<S>`

**Private Flags:**
- `legEnded: Boolean = false` — lokal registriert, wenn `DartOutcome.legEnded` meldet.

**Neue Properties:**
- `isLegEnded: Boolean` — öffentlich lesbar, signalisiert der UI: „Leg ist entschieden (aber
  nicht zwingend durch diesen Spieler)."
- `legClosed: Boolean` (privat) = `legWon || legEnded` — gemeinsamer „Leg ist zu Ende"-Check,
  sperrt weitere Darts und neue Aufnahmen.

**Dart-Verarbeitung:**
- Wenn `outcome.legEnded` meldet: Modus-Zustand aktualisieren (wie regulär), Aufnahme sofort
  beenden (`turnEnded = true`), `legEnded`-Flag setzen, Leg für weitere Darts sperren.
- Alle drei Aufnahme-Ende-Szenarien (Bust, legWon, legEnded) beenden die Aufnahme sofort,
  auch bei `< 3` Darts.

**Snapshot:**
`LegEngineSnapshot` wird NICHT erweitert — es enthält weiterhin nur `isLegWon`, nicht
`isLegEnded`. Die Snapshot-KDoc wird präzisiert: „Bei `legEnded` bleibt `isLegWon` false;
wer gewonnen hat, meldet allein die MatchEngine."

### Neue Engine-Mechanik in `MatchEngine<S>`

**Neue Gewinner-Ermittlung:**
- `resolveLegScoreWinner(): Int` (private Hilfsmethode) — nimmt die `legScore` aller Spieler
  (`mode.legScore(legEngines[i].state)` über alle Spieler-Indizes), ermittelt argmax,
  handhabt Gleichstand per kleinstem Index.

**Konsolidierte Sieg-Buchführung:**
- Private Hilfsmethode `awardLeg(winnerIndex: Int): LegAward` — **für beide Sieg-Pfade**
  (`legWon` des Werfers UND `legEnded` via Rangvergleich):
  - Hochzählen `legsWonInSet[winnerIndex]++`.
  - Prüfen: Set gewonnen?
  - Prüfen: Match gewonnen?
  - Rückgabe: `LegAward(setWon, matchWon)`.
  - **Zweck:** Kein Code-Duplikat zwischen legWon- und legEnded-Pfaden.

**Zwei Leg-Ende-Szenarien in `applyDart`:**

1. **`dartResult.legWon == true`** (klassischer Werfer-Sieg):
   - Werfer selbst gewinnt das Leg.
   - `legWinnerId = throwerId` (Spieler-ID des Werfers).
   - `awardLeg(throwerIndex)` aufrufen.

2. **`dartResult.legEnded == true`** (Rangvergleich-Ende):
   - Leg ist entschieden, aber Gewinner ist offen.
   - `winnerIndex = resolveLegScoreWinner()` — argmax-Logik.
   - `legWinnerId = playerIds[winnerIndex]` — Gewinner-ID.
   - `awardLeg(winnerIndex)` aufrufen (identische Buchführung).

**`MatchDartResult`-Feld:**
- `legWinnerId: Long? = null` — **gesetzt bei jedem Leg-Ende**, egal ob `legWon` oder
  `legEnded`:
  - Bei `legWon`: Werfer-ID.
  - Bei `legEnded`: Per Rangvergleich ermittelte ID.
  - Bleibt `null`, wenn das Leg noch läuft.
  - **Signal für UI/Persistenz:** `legWinnerId != null` ⟹ „Leg ist entschieden."

**Aufschub-/Undo-Mechanik (ADR-0027):**
- `pendingLegTransition` und das Aufschub-Modell bleiben unverändert, gelten für beide
  Sieg-Pfade. Die Gewinner-Ermittlung (Werfer vs. Rangvergleich) ist orthogonal zur
  Aufschub-Frage.

**Replay-Konsistenz (ADR-0021):**
- Der Voll-Replay in `MatchEngine.undoLastDart()` ist modus-agnostisch; der leg-beendende
  Dart (legWon wie legEnded) ist per Invariante stets der letzte Historien-Eintrag und wird
  entfernt, bevor die Restdarts neu durchgespielt werden. `LegEngine.undoLastDart()` bleibt
  bei geschlossenem Leg bewusst No-op.

### GameViewModel-Seite

**Gewinner-Auflösung:**
```kotlin
val legWinnerId = result.legWinnerId
val winnerId = legWinnerId ?: throwerId  // legWinnerId hat Vorrang; sonst Werfer (bei laufendem Leg)
```

- Die VM liest `legWinnerId` aus `MatchDartResult`.
- Ist es gesetzt (legWon oder legEnded), ist das die Gewinner-ID.
- Ist es `null`, läuft das Leg weiter; der Werfer ist der „aktive" Spieler (für Anzeigezwecke).

**UI-Bedingung für LegWon-Panel:**
- Alte Bedingung: `if (result.legWon) { ... GameUiState.LegWon ... }`.
- Neue Bedingung: `if (legWinnerId != null) { ... GameUiState.LegWon ... }`.
  (Beides in `GameViewModel.onDart`; die Composable `LegWonContent` in `GameScreen.kt`
  bleibt unverändert.)
- **Grund:** Beide Sieg-Pfade (legWon des Werfers, legEnded via Rangvergleich) zeigen das
  gleiche Panel, aber mit unterschiedlichem Gewinner-Namen.

**Kontrollpause (ADR-0026):**
- Die Kontrollpause nach dem 3. Dart (Aufnahme-Review) wird bei `legEnded` übersprungen.
- **Grund:** Ein rundenbasiertes Leg-Ende bedeutet oft Aufnahme-Ende mitten in der Aufnahme
  (< 3 Darts). Eine Kontrollpause dort wäre verwirrend (nicht viel zu reviewen, sofort
  Gewinner-Panel). Das Gewinner-Panel selbst ist ausreichend markant.

**Persistenz:**
- `finishLeg(winnerId)`, `finishLegAndMatch(winnerId)` arbeiten modus-agnostisch über `winnerId`.
- Beide Sieg-Pfade nutzen die gleichen Persistenz-Methoden.

## Konsequenzen

- **Additive Erweiterung:** Bestehende Modi (X01, Cricket, Around the Clock) sind völlig
  unberührt — sie melden nie `legEnded`, also wird `legScore` nie konsultiert. Ihre Tests
  bleiben grün.

- **Neue Testfixture:** `RoundLimitFakeMode` (in `testing/`) als Vertrags-Beweis:
  - Meldet immer `legEnded` (nie `legWon`).
  - Implementiert `legScore` (höchster Punktestand gewinnt).
  - Nicht Teil des Produktionskatalogs, nur für Tests.
  - Erlaubt Test-Modi mit konfigurierbarem Dart-Limit (auch nicht-Vielfache von 3),
    um Werfer und Gewinner sauber zu unterscheiden.

- **Engine-Tests:** Neue Testsuiten (insgesamt 39 neue Tests):
  - `MatchEngineLegEndedTest.kt` (10 Tests): Basis-Szenarien (rundenbasiertes Leg-Ende,
    Rangvergleich-Gewinner, Gleichstand, Set-/Match-Grenzen).
  - `MatchEngineLegEndedHardeningTest.kt` (7 Tests): Edge-Cases (Cross-Turn-Undo-Tiefe nach
    einem `legEnded`-Sieg, Set-/Match-Grenzen, argmax über 3+ Spieler, Degenerat-Fall
    legScore=0 für alle).
  - `LegEngineLegEndedTest.kt` (8 Tests, neu): Einzelspieler-Sicht der LegEngine auf
    `legEnded` (Sofort-Ende beim 1./2./3. Dart, permanente No-ops von `applyDart`/
    `startNewTurn`/`undoLastDart`).
  - `GameViewModelLegEndedTest.kt` (5 Tests): VM-Seite (LegWon-Panel-Bedingung, Gewinner-Name,
    Persistenz).
  - `GameViewModelLegEndedHardeningTest.kt` (4 Tests): VM-Härtung (Kontrollpause-Übersprung,
    Modus-Agnostik).
  - `GameModeContractTest.kt` (+5 Tests, bestehend): Vertrags-Validierung für alle Modi
    (`legEnded` XOR-Invariante, `legScore` Rückgabewert).

- **Gesamte Test-Suite:** 636 grün (597 bestehende + 39 neue Leg-Ende-Tests).

- **Regressionssicherheit:** Alle bestehenden Tests der Modi (X01, Cricket, ATC) bleiben grün.
  Keine Code-Änderungen in `X01Mode`, `CricketMode`, `AroundTheClockMode`.

- **Nachfolgende Modi:** Shanghai (PR B) kann jetzt:
  1. Rundenbasierte Endbedingung auf `legEnded` signalisieren (wenn Rundenlimit erreicht).
  2. `legScore` implementieren (höchster Punktestand gewinnt).
  3. Die Engine kümmert sich um Rangvergleich, Gleichstand, Gewinner-Auflösung.
  4. Keine zusätzliche Infra nötig — PR B ist rein Modus-Implementation.

- **Sudden Death Option:** Ein Modus, der bei Gleichstand nicht enden will (z.B. Sudden Death),
  meldet einfach kein `legEnded` — der Modus-Designer entscheidet. Die Engine erzwingt nichts.

### Verweise

- [ADR-0013](0013-spielmodi-domaenenlogik.md) — `GameMode<S>` und Strategie-Pattern
  (unverändert; `legScore` ist orthogonale Erweiterung).
- [ADR-0021](0021-undo-cross-turn-replay.md) — Replay-Mechanik für Undo (unverändert,
  legEnded-Darts sind regulär).
- [ADR-0022](0022-modus-infrastruktur.md) — Modus-Katalog und generische Architektur
  (diese Entscheidung baut darauf auf).
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause nach dem 3. Dart
  (wird bei legEnded übersprungen).
- [ADR-0027](0027-undo-im-gewonnen-zustand.md) — Undo im Gewonnen-Zustand (gelten für beide
  Sieg-Pfade).
- [ADR-0004](0004-datenhaltung-throw-level.md) — throw-level-Persistenz (legEnded erzeugt
  keine Phantom-Darts, nur echte Darts werden persistiert).
