# 0038 — Delight-Trigger-System: pures Regelwerk, Auslösezeitpunkt, Event-Muster, gehaltene Kontrollpause

**Status:** Akzeptiert

## Kontext

[ADR-0006](0006-delight-schicht.md) legt fest, dass besondere Aufnahmen mit einer stummen
Vollbild-Animation gefeiert werden und das Trigger-System **datengetrieben und erweiterbar** ist
(pro Trigger: Bedingung, Animation, Text). Offen waren: wo das Regelwerk lebt, welche Daten eine
Bedingung sieht, wann im Spielablauf ausgewertet wird (die Kontrollpause nach dem dritten Dart,
[ADR-0026](0026-turn-review-kontrollpause.md), verzögert den Spielerwechsel), wie das Ergebnis die
UI erreicht und wie Feier und Pausen-Timer zusammenspielen. Diese Entscheidung deckt nur System und
Anbindung ab; die konkreten Produkt-Trigger (180, Waschmaschine, Rentnerdreieck, …) und die
Animationen sind eigene Roadmap-Punkte.

## Entscheidung

1. **Pures Paket `com.mechanicel.tomsdarts.delight`** (kein Android/Compose/Room):
   - `DelightVisit` — genau eine abgeschlossene Aufnahme: `darts: List<Dart>` (vorhandener
     Domänentyp `game.Dart`), `bust`, `modeKey`, `scored` (vom Modus gewertet, bei Bust 0),
     `checkout` (Werfer hat das Leg selbst beendet, = `MatchDartResult.legWon`), `legEnded` (Leg
     mit dieser Aufnahme entschieden, egal für wen), `playerId: Long?`; abgeleitet `dartSum`
     (rohe Punktsumme). Damit sind alle in ADR-0006 genannten und angedachten Trigger ohne
     Modelländerung formulierbar (180 = `dartSum == 180`, Waschmaschine/Rentnerdreieck über die
     Segmente, Madhaus = `checkout` und letzter Dart `D1`, Bull, Ton ab 100).
   - `DelightTrigger(id, priority, condition: (DelightVisit) -> Boolean, presentation)` und
     `DelightPresentation(animation: DelightAnimation, textKey: String)`; `DelightAnimation` ist
     ein Enum (`CONFETTI`, `SPIN`, `TRIANGLE`, `GENERIC`). `textKey` ist ein stabiler Schlüssel;
     das Mapping auf `R.string` passiert erst in der UI.
   - `evaluateDelight(visit, triggers, id)` / `DelightRegistry.evaluate(visit, id)` liefern
     **höchstens ein** `DelightEvent(id, triggerId, presentation, visit)` (+ `playerId`):
     höchste Priorität gewinnt, bei Gleichstand die Registrierungsreihenfolge (stabile
     Sortierung), deterministisch. Doppelte Trigger-IDs lehnt die Registry ab. Die Registry
     sortiert ihre Trigger einmal bei der Konstruktion vor, nicht bei jeder Aufnahme.
   - **Erweiterung:** Ein neuer Trigger ist genau ein Eintrag in `ProductDelightTriggers.ALL`;
     `DelightRegistry.DEFAULT` baut darauf auf. Die Liste ist vorerst leer.
2. **Auslösezeitpunkt: beim Abschluss jeder Aufnahme, also schon beim dritten Dart.** Im
   `GameViewModel` gilt eine Aufnahme mit `turnEnded` als abgeschlossen: Persistenz und
   Undo-Stapel laufen sofort, „Weiter" wendet nur noch den zurückgehaltenen Spielerwechsel an.
   Ausgewertet wird daher in `onDart` direkt nach `turnEnded`, für alle Ausgänge (regulär, Bust,
   Leg-/Match-Gewinn) und alle Modi. Fachlich wirkt die Feier im Moment des Wurfs; erst nach
   „Weiter" zu feiern käme zu spät und würde bei einer Feier mit dem nächsten Spieler kollidieren.
3. **Bust:** Das System wertet auch Bust-Aufnahmen aus; ob gefeiert wird, entscheidet die
   Bedingung über `bust`. **Produkt-Trigger sollen Bust ausschließen** (`!visit.bust`), weil ein
   Bust keine Leistung ist, die man feiert (eine 180 als Bust ist keine 180).
4. **Event-Muster: Zustand statt Fire-and-forget.** `GameViewModel.delightEvents:
   StateFlow<DelightEvent?>` folgt dem vorhandenen `bustEvents`-Muster (`StateFlow`, keine
   `SharedFlow`/`Channel`): `null`, solange keine Feier ansteht, sonst das zuletzt ausgelöste
   Event. `DelightEvent.id` steigt je VM streng monoton (ab 1) und dient der UI als
   Dedupe-Schlüssel und Animations-Seed. Die UI quittiert mit `onDelightDismissed(id)`, danach ist
   der Wert wieder `null`, also kein erneutes Abspielen nach einer Konfigurationsänderung.
   Veraltete oder doppelte IDs sind wirkungslos. Gegenüber einer `SharedFlow` ohne Replay geht
   so kein Event verloren, wenn gerade niemand sammelt. **Ausnahme Kontrollpause:** Dort
   verwirft das Sicherheitsnetz (Punkt 6) ein nicht quittiertes Event nach
   `DELIGHT_MAX_HOLD_MILLIS`. Testseitig ist der Wert synchron lesbar (keine nachlaufenden
   Collector-Coroutines).
5. **Undo:** Ein ausgelöstes Event wird durch Undo weder zurückgenommen noch erneut ausgelöst
   (Undo wertet nicht aus). Wird die Aufnahme nach Undo erneut abgeschlossen, wird neu ausgewertet
   und ein neues Event mit neuer ID kann entstehen. Das gilt für „Korrigieren", Cross-Turn-Undo
   und „Sieg zurücknehmen" gleichermaßen.
6. **Kontrollpause wartet auf die Feier.** Führt eine Aufnahme mit Feier in die Kontrollpause,
   startet der Pausen-Timer (`TURN_REVIEW_MILLIS`, 1500 ms) erst nach `onDelightDismissed(id)`
   mit voller Dauer, damit die Kontrollanzeige nicht unter der Feier abläuft.
   `TurnReviewUi.heldForDelight` zeigt der UI an, dass der Timer gerade gehalten wird.
   **Sicherheitsnetz:** Kommt kein Dismiss, etwa weil der Screen nicht sammelt oder eine Feier
   später per Schalter deaktiviert ist, wird die Feier nach `DELIGHT_MAX_HOLD_MILLIS = 6000 ms`
   wie quittiert behandelt. 6 s liegen deutlich über jeder geplanten Feier-Animation (wenige
   Sekunden), damit der Normalfall nie abgeschnitten wird, und sind kurz genug, dass ein
   hängendes Spiel nicht auffällt. „Weiter" bleibt während der Feier wirksam; „Korrigieren"
   verwirft den gehaltenen Timer samt Sicherheitsnetz (beide laufen als `turnReviewJob`).
   Ohne Feier läuft der Timer unverändert.
7. **Sieg-Aufnahmen und Bust** emittieren genauso, ohne Warten im ViewModel: Dort gibt es keine
   Kontrollpause. Die UI legt die Feier über das Sieg-Panel bzw. das Bust-Banner.
8. **Robustheit:** Wirft eine Trigger-Bedingung, fängt das `GameViewModel` die Ausnahme ab
   (`runCatching`) und behandelt die Aufnahme wie „kein Treffer" (kein Event, keine ID
   verbraucht). Eine fehlerhafte Feier darf Persistenz, Spielerwechsel und Kontrollpause nie
   brechen.
9. **Injektion:** `GameViewModel(…, delightRegistry: DelightRegistry = DelightRegistry.DEFAULT)`.
   Die Factory nutzt den Default, Tests reichen eigene Registries herein.

## Konsequenzen

- Produkt-Trigger (Roadmap Phase 6) sind reine Einträge in `ProductDelightTriggers.ALL` ohne
  Änderung an ViewModel oder Modell.
- Die UI-Folgeaufgabe sammelt `delightEvents`, spielt je neuer `id` einmal ab, ruft danach
  `onDelightDismissed(id)` auf und startet die Fortschrittsanzeige der Kontrollpause erst bei
  `heldForDelight == false`. Bis dahin ändert sich am sichtbaren Verhalten nichts, da die
  Produkt-Registry leer ist.
- **Hinweise für den UI-Konsumenten:**
  - Überspringt die UI ein nach einer Rotation erneut geliefertes Event (z.B. per
    gespeicherter `lastShownId`), muss sie trotzdem `onDelightDismissed(id)` rufen, sonst bleibt
    eine gehaltene Kontrollpause bis zum Sicherheitsnetz stehen.
  - Die `id` beginnt je `GameViewModel`-Instanz wieder bei 1. Deduplizieren daher per
    Gleichheit (`id == lastShownId`), nicht per Vergleich (`id > lastShownId`), da ein neues
    Spiel sonst keine Feier mehr zeigen würde.
- **Reihenfolge der Folgeaufgaben:** Produkt-Trigger dürfen erst in `ProductDelightTriggers.ALL`,
  wenn der UI-Konsument steht (`delightEvents` sammeln, `onDelightDismissed` rufen,
  Fortschrittsanzeige an `heldForDelight` koppeln). Sonst hält jede Feier die Kontrollpause
  6 s (Sicherheitsnetz) plus 1,5 s, ohne dass etwas zu sehen ist. Deshalb steht in der Roadmap
  der Punkt „Stumme Vollbild-Animationen, Auto-Dismiss" vor den Produkt-Triggern.
- Mit einer leeren Registry ist das Verhalten identisch zum bisherigen Stand (Timer läuft sofort).
- Offline-Kern unberührt: rein lokal, keine neue Abhängigkeit, keine Schemaänderung.

## Verweise

- [ADR-0006](0006-delight-schicht.md) — Delight-Schicht (Grundsatz, Trigger-Liste)
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause nach dem dritten Dart
- [ADR-0027](0027-undo-im-gewonnen-zustand.md) — Undo im Gewonnen-Zustand
