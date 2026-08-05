# 0027 — Undo im Gewonnen-Zustand (Leg-/Match-Gewinn)

**Status:** Akzeptiert

**Revidiert:** [ADR-0021](0021-undo-cross-turn-replay.md) (Undo-Grenzen; „Leg-Grenze bleibt Undo-Grenze / kein Undo auf LegWon/MatchWon" ist aufgehoben).

## Kontext

ADR-0021 legte fest: **Leg-Grenze bleibt Undo-Grenze** — keine Korrektur nach Leg-Sieg,
kein Undo von `LegWon`/`MatchWon`-Panels. Rationale: Leg-Ende als Abschluss-Punkt,
Spiellogik infrage stellen vermeiden.

**Praxis-Feedback (Nutzer-Test):** Ein Spieler wirft eine falsche letzte Aufnahme, die
fälschlicherweise zum Leg-/Match-Sieg führt. Der Fehler war **nicht korrigierbar** —
auf dem Sieg-Panel gab es kein Undo. Der Spieler war „gefesselt": nur zurück zur
Profil-Auswahl, komplettes Match neu starten. Das ist eine **unzumutbare UX**.

**Produktentscheidung** (Orchestrator): Undo **soll** vom Sieg-Zustand (`LegWon`/`MatchWon`)
aus möglich sein — aber nur bis zum **Commit-Zeitpunkt**, nicht unbegrenzt rückwärts
(Bestandteil der Entscheidung: wann ist ein Sieg final?).

## Entscheidung

### Aufschub-Modell: Zähler sofort, Reset aufgeschoben

**Kernidee:** Der Sieg wird erkannt und sein **Zustand sofort in den Engine-Snapshots
festgestellt** (Match-/Leg-Zähler, Gewinner, Nummern erhöhen sofort), aber der eigentliche
**Leg-Reset ist aufgeschoben**:
- `MatchEngine` hält einen Zustand `pendingLegTransition` (NEXT_LEG/NEXT_SET/MATCH_END),
  der den **kommenden** Wechsel markiert.
- Private `LegBaseline` speichert die **vor dem Sieg gültigen Zähler/Nummern/legStartIndex**
  (Felder: `legsWonInSet: List<Int>`, `setsWon: List<Int>`, `setNumber`, `legNumber`, `startIndex`).
- `undoLastDart()` wird erweitert: Befindet sich ein Leg-Wechsel in Pending, wird die
  `LegBaseline` restauriert, die Engine-Flags (`isMatchWon`, `matchWinnerId`) auf ihren
  Vorsieg-Zustand gesetzt, und anschließend der normale Replay-Block (ADR-0021) ausgeführt.
  **Dadurch wird der Sieg-Dart zurückgenommen** und der spielende Zustand wieder geöffnet.

**Neue öffentliche Engine-Methode:** `commitLegTransition(): Boolean` führt den
aufgeschobenen Leg-Reset durch. Gerufen vom `GameViewModel` in `onNewLeg()`.

**Lazy-Commit-Sicherungsnetz:** Sollte der Spieler **weitermachen ohne `onNewLeg()`
zu rufen** (z.B. blind ein Dart werfen), wird der aufgeschobene Wechsel automatisch
vorab committed (`applyDart` vor der nächsten Dart-Verarbeitung). Damit bleibt die
Engine-Konsistenz gewahrt — der Sieg wird dann endgültig.

### Neue Undo-Grenze = Commit-Zeitpunkt

Die **Undo-Grenze verschiebt sich** vom Sieg-Dart selbst auf den Commit-Zeitpunkt
(`onNewLeg()` oder nächster geworfener Dart):
- **Auf `LegWon`/`MatchWon`-Panels:** Undo ist möglich, solange `pendingLegTransition != null`
  (= Sieg noch nicht committed).
- **Nach `onNewLeg()` oder implizitem Lazy-Commit:** Der Sieg ist endgültig, Undo nicht mehr möglich.

### GameViewModel-Seite: Win-Turn-Verwaltung & onUndoWin

**Sieg-Turn-Persistenz (neue `pushWinTurn`-Methode):**
Ein Sieg führt zu einer finalen Aufnahme (Turn mit Throws des siegreich abschließenden
Spielers). Das ViewModel:
1. Bucht diese Aufnahme via `pushWinTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)`, 
   die eine `Deferred<Long>?` zum asynchronen Turn-Insert zurückgibt.
2. Setzt `lastTurnByPlayer` **bewusst nicht** (der Turn wird separat als Sieg-Turn gebucht,
   Anzeigen wie „Letzte Aufnahme" sollten ihn ignorieren).
3. `winFinalizeJob: Job?`: Ein Job (nullable), der die asynchronen Insert-Operationen des Sieg-Turns
   (Turn + alle Throws) puffert.

**Neue `onUndoWin()`-Methode (nur von `LegWon`/`MatchWon` aus):**
1. **Re-Entrancy-Schutz:** `private var undoWinInProgress = false` wird SYNCHRON (vor dem ersten
   Suspendierungspunkt) auf `true` gesetzt, im `finally` der Coroutine zurückgesetzt. Ein zweiter Tap
   während eines laufenden Undo-Vorgangs wird zu No-op. Zusätzlich `winFinalizeJob.join()` +
   Zustands-Re-Check (um auf Persistenz-Abschluss zu warten).
2. **Engine-Undo:** `matchEngine.undoLastDart()` (Sieg-Dart zurück, Baseline restauriert,
   `isMatchWon=false`, `matchWinnerId=null`).
3. **Persistenz-Rückbau:**
   - `updateLeg(endedAt=null, winnerId=null)` (Leg wird wieder geöffnet).
   - Bei `MatchWon` zusätzlich `updateMatch(...)` (Match wird wieder geöffnet).
   - `deleteTurn` (Sieg-Turn + alle Throws via CASCADE gelöscht).
4. **Zähler:** `legDartsByPlayer--` (Dart-Zahl korrigiert).
5. **Rückkehr in `Playing`:** State mit wieder geöffnetem Ziffernblock.
6. **Keine Kontrollpause dabei:** Das Replay braucht keinen neuen Review-Timer.

**Kein Schema-Drift:** Die `Leg`- und `Match`-Entities hatten bereits nullable
`endedAt`/`winnerId`-Felder (ADR-0008, Kompatibilität), kein neues DAO nötig.

### UI-Seite: Win-Undo-Button

**Neue Buttons auf `LegWonContent` + `MatchWonContent`:**
- Button-Label: `game_won_undo` / `game_won_undo_cd` (neue Strings).
- Position auf `LegWonContent`: Zwischen „Nächstes Leg" und „Zurück", mit 24 dp
  oben-Abstands-Block gegen Fehltipp.
- Position auf `MatchWonContent`: vor dem „Zurück"-Button.
- **Kein Bestätigungsdialog:** Undo ist selbst reversibel (bei Fehltipp → erneut Undo
  wählen). Ein Dialog würde statt für Undo `onNewLeg()` eine größere Friction erzeugen.
- **Scroll-Fix:** Beide Sieg-Panels mit `verticalScroll` erweitert (volle Seite kann
  wischen, besonders auf kleineren Screens).
- **Endstand-Daten für künftige UI-Erweiterungen:** Die `players[].board` (Restpunktzahl bei X01,
  Marks bei Cricket) im `LegWon`-State zeigt den Endstand des gewonnenen Legs nach dem Sieg-Dart.
  Heute wird dies von `LegWonContent` nicht angezeigt (nur Name + L/S), aber die Daten liegen vor
  als Falle/Hinweis für künftige Panel-Erweiterungen, z.B. zur Anzeige der Finalzahl.

### Design-Entscheidung: Gegen Bestätigungsdialog

**Warum kein Dialog?**
- **Asymmetrie-Argument:** Das versehentliche „Nächstes Leg"-Drücken hätte kein Undo.
  Ein Dialog-Schutz wäre dann auch für den „Weiter"-Button nötig — führt zu Dialog-Überflutung.
- **Reversibilität:** Undo selbst lässt sich rückgängig machen (Spieler wirft Darts,
  Sieg wird erneut erkannt). Ein Dialog würde nicht den Fehler selbst verhindern, sondern nur Fehltipps.
- **Praxis-Feedback:** Spieler möchten schnell korrigieren, nicht bestätigen.

## Konsequenzen

- **ADR-0021 bleibt tragend:** Das Replay-Modell und der Determinismus-Ansatz bleiben
  unverändert. Nur die Undo-Grenze verschiebt sich.
- **Engine-Tests bewusst umgeschrieben:** 7 Tests über drei Dateien (`MatchEngineTest`, 
  `MatchEngineEdgeCasesTest`, `MatchEngineUndoHardeningTest`), die „Undo nach Sieg = No-Op" 
  festschrieben, wurden auf das **neue Verhalten umgestellt**.
- **Neue Test-Suiten:**
  - `MatchEngineWinUndoHardeningTest.kt`: Win-Undo-Grundlagen, Pending-State-Kohärenz,
    Lazy-Commit-Trigger, tieferer Undo mit anderem Gewinner, Rundreise-Invarianzen über mehrere Runden,
    Set-/Match-Grenzen, ATC-Smoke-Test (modus-agnostisch).
  - `GameViewModelWinUndoHardeningTest.kt`: VM-Seite Persistenz-Rückbau (Leg/Match/Turn),
    Re-Entrancy-Schutz (sequenzieller No-op bei Doppel-Tap, getestet via `onUndoWin` zweimal hintereinander),
    Kontrollpause-Wechselwirkung, onUndoWin-Konsistenz, Rundreise-Invarianzen über 3 Undo-Zyklen.
- **Gesamte Test-Suite:** 597 grün (bestehende 575 + 22 neue Tests für Win-Undo-Härtung).
- **Sicherheitsguard:** `undoLastDart` hat einen expliziten Guard
  (`isMatchWon && pendingLegTransition==null`) — dies ist ein **bewusst unerreichbares Sicherheitsnetz**:
  Da `isMatchWon` stets mit `pendingLegTransition == MATCH_END` einhergeht (nie `null`),
  würde diese Bedingung niemals erfüllt. Der Guard schützt vor künftigen Fehlern bei Refactorings.
- **Persistenz-Integrität:** Die Repository-Methoden laufen bereits auf Rooms eigenem Executor;
  `winFinalizeJob.join()` + `Deferred.await()` auf den asynchronen Turn-Insert synchronisieren
  den Undo-Rückbau (updateLeg/updateMatch/deleteTurn), damit Sieg-Turn-Insert und -Delete nicht überlaufen.
- **Statistik-Reopen als Zukunftsnotiz:** Falls eine künftige Statistik-Schicht auf
  abgeschlossene Legs reagiert (z.B. `endedAt != null` als Trigger), ist `onUndoWin`
  ein Reopen-Fall — heute existiert keine solche Schicht, aber hier ist eine Stelle für
  künftige Erweiterungen markiert.

### Verweise

- [ADR-0021](0021-undo-cross-turn-replay.md) — Replay-Modell (unverändert, nur Grenzen revidiert).
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause bleibt orthogonal
  (Pause tritt nicht während Win-Undo auf).
- [ADR-0004](0004-datenhaltung-throw-level.md) — Throw-level-Persistenz (Sieg-Turns
  folgen dem gleichen Persistenz-Muster).
- [ADR-0008](0008-datenmodell-entscheidungen.md) — Datenmodell (nullable `endedAt`/`winnerId`
  bereits vorhanden, kein Schema-Drift).
