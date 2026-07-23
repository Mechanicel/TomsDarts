# 0026 — Kontrollpause nach dem dritten Dart (Turn-Review)

**Status:** Akzeptiert

## Kontext

Nutzer-Feedback aus Geräte-Tests (Spielpraxis): Nach dem dritten Dart eines regulären
3-Dart-Aufnahmenendes wechselt das Spiel **sofort** zum nächsten Spieler. Der Werfer
hat **keine Zeit**, die gerade geworfenen Darts zu prüfen — besonders problematisch
bei ähnlichen Zahlbereichen (z.B. T20/T19 visuell leicht verwechselbar).

Bisheriges Modell (ADR-0014, ADR-0021): Die `GameEngine` hat stets sofort (nach jedem
Dart) auf Spielerwechsel gepruft; `onUndo()` spult über Leg-Grenzen zurück (aber nicht
über Leg-Ende).

**Produktentscheidung** (Orchestrator): Eine kurze, **pausierbare Kontrollphase** nach
regulärem 3-Dart-Ende — Werfer sieht seine Darts gross, kann weiter („Weiter"-Button)
oder korrigieren („Korrigieren"/Undo). Auto-Pause (~1,5 s) mit Skip-Option, nicht
erzwungenes Confirm.

## Entscheidung

### Kontrollpause — Grenzen & Trigger

**Tritt auf bei:**
- Regulärer 3-Dart-Abschluss (`turnEnd()` mit 3 Darts) ohne Bust, ohne Checkout,
  ohne Leg-/Match-Gewinn.

**Tritt NICHT auf bei:**
- Bust (behält aktuelles Bust-Banner, wechselt normal).
- Checkout / Double-Out-Gewinn (zeigt Checkout-Panel direkt).
- Leg-Gewinn (zeigt LegWon-Panel direkt).
- Match-Gewinn (zeigt MatchWon-Panel direkt).

### VM-seitige Umsetzung

**Post-Wechsel-Snapshot zurückhalten:**
Die Spiel-Engine (`MatchEngine`) berechnet im Normalfall nach jedem Dart den
nächsten Spieler. Das `GameViewModel` **fängt den Post-Wechsel-Snapshot ab** und
hält ihn in `pendingSnapshot` zurück — der Wechsel wird nicht sofort in den
UI-State übernommen.

**`TurnReviewUi` State:**
Statt sofort zu wechseln, setzt das ViewModel den UI-State um auf `Playing`
mit gesetztem `turnReview: TurnReviewUi?`:
- `throwerName` — Anzeigename des soeben werfenden Spielers.
- `darts` — die (bis zu 3) tatsächlich geworfenen Darts dieser Aufnahme.
- `turnSum` — gewertete Summe dieser Aufnahme.
- `nextPlayerName` — Anzeigename des Spielers, der nach „Weiter" am Zug ist.

Während die Pause aktiv ist (`turnReview != null`), bleiben alle Eingaben (**Zahl,
Bull, Double/Triple-Tasten**) **gesperrt** — der Ziffernblock reagiert nicht.

**Auto-Pause mit Timer:**
Das `GameViewModel` startet einen `viewModelScope`-Timer mit `TURN_REVIEW_MILLIS = 1500L`.
Nach ~1,5 Sekunden ruft der Timer automatisch `onContinue()` auf.

**`onContinue()` Callback (Weiter-Button & Timer-Ablauf):**
- `onContinue()` ist **idempotent** — Guard über `pendingSnapshot == null`.
- **Weiter-Button (UI)** ruft `onContinue()` sofort auf (Skip).
- **Timer-Ablauf** ruft `onContinue()` nach ~1,5 s automatisch auf.
- Falls der Nutzer den Weiter-Button **vor dem Timer-Ablauf** drückt, wird der Timer
  gecancelt, `onContinue()` läuft, der späte Timer-Ablauf ist dann no-op (Guard).
- Falls der Timer **vor Weiter-Tap** abläuft, wird der Spieler normal gewechselt,
  ein später Weiter-Tap findet kein `pendingSnapshot` vor und ist ebenfalls no-op.

Diese Reihenfolge-Unabhängigkeit (Tap-vs-Timer-Race) ist explizit gewollt und getestet.

**`onUndo()` erweitert (Korrigieren-Button):**
Wenn während der Kontrollpause `onUndo()` aufgerufen wird (Korrigieren-Button):
1. Der laufende Pausen-Timer wird gecancelt (`turnReviewJob?.cancel()`).
2. Der `pendingSnapshot` wird verworfen.
3. Die bestehende Undo-Logik (Cross-Turn-Replay, ADR-0021) wird aufgerufen —
   die soeben abgeschlossene Aufnahme wird rückgängig gemacht, der Werfer kann
   die Darts erneut werfen.
4. Nach erneutem 3-Dart-Ende **startet eine neue Kontrollpause** (Turn-Review mit
   frischem Timer, neu berechnetem Snapshot).

Undo-Grenzen (Leg-Grenze) bleiben gültig (ADR-0021); während einer laufenden
Kontrollpause ändert sich nichts daran.

### UI-Präsentation

**Block-Layout während der Pause:**
Während `turnReview != null`, ersetzt `TurnReviewContent` temporär den Keypad-Bereich:
- Titel: „Aufnahme beendet".
- Geworfene Darts: `ThrownDartsRow` (Reihe von `ThrownDartTile`-Kacheln);
  jede Kachel zeigt ein Kurzlabel (z.B. „T20", „D15", „Out").
- Prominente Summen-Anzeige (z.B. „Aufnahme: 47").
- Leiste: Dünne, stumme Progressbar (Visual Feedback für Timer-Ablauf, kein Percent-Label).
- Nächster Spieler: Unaufdringliche Ankündigung (z.B. „Nächster: Anna").
- Buttons: Zwei CTA-Buttons nebeneinander:
  - „Weiter" (primär) — ruft `onContinue()` auf.
  - „Korrigieren" (sekundär) — ruft `onUndo()` auf.

**Werfername-Identifikation:** Der Name des soeben werfenden Spielers wird **nicht als
sichtbarer Hero-Text angezeigt**, sondern über die Scoreboard-Hervorhebung (`isCurrent = true`)
kommuniziert. Der Name ist Teil der barrierefreien `contentDescription`.

**Scoreboard während der Pause:**
Der Scoreboard bleibt sichtbar — der aktuelle Werfer wird hervorgehoben (isCurrent = true).
Das Scoreboard rendert den **aktuellen Zustand** (der `pendingSnapshot` wird noch nicht
eingeflochten), wodurch es zu **keinem visuellen Sprung** beim Werfer-Wechsel kommt.

**Barrierefreiheit:**
- Neue, fokussierte `contentDescription`: Format-String
  `"[Werfername]: Aufnahme beendet, [Darts], Summe [Wert]. Weiter oder korrigieren."`
  (entspricht `game_turn_review_cd`), gelesen als Ganzes durch TalkBack.

### Persistenz bleibt sofort

Der Throw-Stack wird **sofort nach Dart 3 persistiert** (bestehende Behavior,
ADR-0004). Die Kontrollpause ist rein auf VM/UI-Ebene; es ist kein deferred
insert oder Rollback involviert. Undo nutzt wie bisher Replay (ADR-0021).

### Bewusste Wahl: Auto-Pause + Weiter-Skip vs. Alternativen

**Gewählter Ansatz: Auto-Pause + Skip-Button**
- Nutzer **muss aktiv handeln** (Weiter drücken), wenn er die Darts prüfen möchte.
- **Auto-Weiter nach ~1,5 s** — Spielfluss wird nicht unterbrochen, wenn Nutzer
  aktiv bleibt (Übervertrauen in die Eingabe, schnelle Spielweise).
- **Korrigieren bleibt jederzeit möglich** (schneller als zurück zum Setup).

**Alternative 1: Reiner Auto-Delay (kein Button)**
- Nachteil: Spieler **muss** warten, auch wenn sie schnell spielen wollen.
- Nachteil: Keine Möglichkeit, die Pause zu überspringen (Kompromiss für Casual-Spieler
  vs. Speed-Spieler).

**Alternative 2: Reines Confirm-Fenster (kein Auto, muss Weiter drücken)**
- Nachteil: Spielfluss wird unterbrochen, wenn Spieler `onContinue()` vergisst —
  Spiel wirkt „stehen gelassen".
- Nachteil: Schnellspieler müssen jede einzelne Turn ausdrücklich bestätigen (Friction).

**Begründung der Wahl:**
Der gewählte Ansatz (Auto-Pause + Skip) bietet die beste Balance zwischen:
1. **Sicherheit:** Werfer hat Zeit, die Darts zu prüfen (Nutzer-Feedback-Punkt).
2. **Fluss:** Auto-Ablauf verhindert Brechen des Spielrhythmus für Spieler, die
   vertrauen und schnell spielen.
3. **Flexibilität:** Skip-Button für Spieler, die sofort weitermachen wollen,
   Korrigieren-Button für Fehler-Recovery.

## Konsequenzen

- **GameViewModel-Erweiterung:**
  - Neuer `TURN_REVIEW_MILLIS: Long = 1500L`-Konstante.
  - Neue `pendingSnapshot`-Variable (MatchSnapshot Zurückhalten).
  - Neuer `turnReviewJob: Job?`-Variable (Timer).
  - Neue öffentliche `onContinue()`-Methode (idempotent).
  - `onUndo()` erweitert: Cancel des turnReviewJob, Verwerfen von pendingSnapshot,
    dann bestehende Undo-Logik.
  - Bestehende Turn-Abschluss-Logik forkiert auf zwei Pfade:
    (1) regulärer 3-Dart → `turnReview` + Timer setzen, `pendingSnapshot` halten.
    (2) Bust/Checkout/Gewinn → Normal-Wechsel (wie bisher).

- **UI-Integration:**
  - Neue private `TurnReviewContent`-Composable in `GameScreen.kt` (Pause-Block).
  - Neue öffentliche `ThrownDartTile`- und `ThrownDartsRow`-Composables in `ui/input/DartKeypad.kt`
    (Kachel + Reihe der geworfenen Darts mit Kurzlabel).
  - `GameScreen` bindet `TurnReviewContent` in den `Playing`-State ein (ersetzt Keypad).
  - `GameUiState.Playing.turnReview: TurnReviewUi?` (neues Feld).
  - `GameScreenCallbacks.onContinue()` (neuer Callback).
  - 5 neue Strings in `res/values/strings.xml`: `game_turn_review_title`,
    `game_turn_review_next`, `game_turn_review_continue`, `game_turn_review_correct`,
    `game_turn_review_cd`.

- **Test-Auswirkungen:**
  - **Semantikänderung in bestehenden Tests:** Viele VM-Tests, die einen regulären
    3-Dart-Abschluss erwarteten, müssen aktualisiert werden:
    früher: Dart 3 → sofort Spielerwechsel im State.
    jetzt: Dart 3 → `turnReview` ist gesetzt, Spielerwechsel steht aus.
    Tests müssen `onContinue()` nach Dart 3 aufrufen oder Timer ablaufen lassen
    (via `advanceTimeBy`), um den Wechsel zu prüfen.
  - **Neue Hardening-Tests:** 8 neue Tests in `GameViewModelTurnReviewHardeningTest.kt`:
    - Timer-Ablauf vs. Weiter-Tap (Reihenfolge-Unabhängigkeit).
    - Eingabe-Sperre während Pause.
    - Undo während Pause → Aufnahme wieder offen.
    - Erneute Pause nach Korrigieren + 3. Dart.
    - Pause tritt NICHT bei Bust/Checkout/Gewinn auf.
  - **Bestehende Tests grün:** 5 + 8 neue Tests, Gesamtsuite 575 grün.

- **Engine unverändert:** `MatchEngine`, `LegEngine`, `X01Mode` etc. sind agnostisch
  gegenüber der VM-Pause — sie berechnen weiterhin Post-Wechsel-Snapshots sofort.
  Nur das ViewModel hält sie zurück. ADR-0014 (Engine-Eingabe-Kopplung) und ADR-0021
  (Cross-Turn-Undo) bleiben gültig.

- **Modus-Agnostik:** Die Kontrollpause ist VM-seitig und arbeitet mit jedem Modus
  (X01, Cricket, Around the Clock). Sie nutzt nur `MatchSnapshot`, nicht Modi-spezifische
  Zustände. Cricket/ATC werden indirekt durch die allgemeinen VM-Tests getestet.

- **Offen / Zurückgestellt (Backlog):**
  1. **Pause auch bei Bust:** Aktuell kein Pause-Block bei Bust-Banner; könnte erweitert
     werden, ist aber nicht Teil dieser Entscheidung.
  2. **Cricket/ATC nur indirekt getestet:** Die Hardening-Tests nutzen X01; Cricket/ATC
     sind durch die bestehende Modus-Infrastruktur-Tests gesichert.

- **Verweise:**
  - ADR-0003 (Eingabe-Ziffernblock): Sperr-Logik ergänzt durch Pause-Sperre.
  - ADR-0014 (Engine-Eingabe-Kopplung): VM-Snapshot-Verzögerung ist rein UI-seitig.
  - ADR-0021 (Cross-Turn-Undo): `onUndo()` während Pause bricht Pause ab, nutzt Replay.
