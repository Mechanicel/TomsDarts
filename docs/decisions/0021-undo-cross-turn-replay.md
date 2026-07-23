# 0021 — Undo über Aufnahmen hinweg (Replay-Ansatz)

**Status:** Akzeptiert

**Update (2026-07-23):** `onUndo()` wurde erweitert, um eine laufende Kontrollpause abzubrechen und die abgeschlossene Aufnahme wieder zu öffnen ([ADR-0026](0026-turn-review-kontrollpause.md)); Undo-Grenzen (Leg-Grenze) bleiben gültig.

## Kontext

Festgelegt nach Geräte-Test Phase 2 (Samsung S25): Der Undo-Button war bisher auf die
**laufende, noch nicht abgeschlossene Aufnahme beschränkt** (In-Turn-Undo via
`LegEngine.undoLastDart()` ohne Ablöse). In der Praxis führte das zu Frustration:
Wenn ein Spieler Rest 141 wirft T20/T20/20 → Rest 1 (erkannt als Bust bei Double-Out),
ließ sich der Fehler **nicht mehr korrigieren** — die Aufnahme war bereits persistiert.
Nutzer mussten zur Profil-Auswahl zurück und das ganze Match neu starten.

**Produktentscheidung** (Orchestrator): Undo spult unbegrenzt innerhalb des laufenden
Legs zurück — über Aufnahme- und Spielerwechsel-Grenzen hinweg. **Leg-Grenze bleibt
Undo-Grenze** (Leg-Ende ist Abschluss, keine Korrektur nach Leg-Sieg). 1 Undo-Schritt
= 1 Dart (nicht pro Aufnahme). Auf `LegWon`/`MatchWon`-Panels kein Undo.

## Entscheidung

### Replay-Ansatz (gewählt)

**Kern-Idee:** `MatchEngine` hält eine **komplette Dart-Historie** des laufenden Legs
(`legDartHistory: List<Dart>`) — alle akzeptierten Darts in Reihenfolge. `undoLastDart()`
poppt den letzten Dart und **erzeugt alle `LegEngine`-Instanzen frisch**, setzt den
Spieler-Index zurück auf den Leg-Startwert und **spielt die verkürzten Darts deterministisch
neu durch** (replay-style).

**Vorteile:**
1. **Minimal-invasive Änderung:** Kein Snapshot-Speichern, kein Restore-Code. Die Engine
   bleibt datenlos; Zustand wird rein durch die Dart-Folge definiert.
2. **Determinis­tisch:** Die `MatchEngine` ist idempotent — die gleiche Dart-Folge
   erzeugt immer den gleichen Zustand. Aufnahme-Bündelung, Bust-Revert und
   Spielerwechsel reproduzieren sich **automatisch** (das ist die X01-Engine-Logik).
   Kein fehleranfälliger Inverse-Betrieb nötig.
3. **Komplexität vs. Korrektheit:** `LegEngine` und `MatchEngine` bleiben unverändert
   in ihren Invarianten; nur `applyDartCore` wird extrahiert, und die Engine nutzt
   einen deterministischen Replay-Loop.

**Alternativen gewertet:**

- **(a) Snapshot-Stack:** Je Dart einen Snapshot (`MatchSnapshot`) speichern, beim Undo
  den letzten restaurieren. **Ablehnung:** O(N Darts × Zustandsgröße) Speicher, bräuchte
  separate Restore-Konstruktoren für alle State-Klassen, fragile bei späterer Erweiterung
  (neue Felder vergessen?) — höhere Wartungslast.

- **(c) Inverse Operationen:** Je Dart die Umkehrung berechnen (z.B. Rest + Value = alter
  Rest). **Ablehnung:** Nicht invertierbar — Bust-Revert ist nicht einfach umzukehren
  („welche Darts der Aufnahme führten zum Bust?"), Spielerwechsel-Inverses ist trivial,
  aber die Varianz ist hoch. Replay ist robuster.

### Leg-Grenze = Undo-Grenze

- **Entscheidung:** `undoLastDart()` wird **keine-op**, wenn die `legDartHistory` leer ist
  (Leg-Start). Das Leg selbst ist durch die `LegWon`/`MatchWon`-Panel-States geschützt
  — von dort aus kein Undo.
- **Rationale:** Leg-Ende ist ein Abschluss-Punkt (Gewinnung/Niederlage fachlich
  festgestellt). Undo-Möglichkeit auf Leg-Ebene zu geben würde die fachliche
  Spiellogik (Leg-Gewinn) infrage stellen. Ausreichend: Innerhalb des laufenden Legs
  beliebig weit zurück (auch über alte Aufnahmen), aber nicht über Leg-Grenzen.

### Persistenz: Deferred-Turn-Completion, Stack-Management, Race-Freiheit

**Kernproblem:** Cross-Turn-Undo bedeutet, dass eine bereits persistierte Aufnahme
gelöscht werden kann (wenn der Spieler über einen Turn zurückspult). **Lösung:**

1. **`GameViewModel.persistTurn` liefert `Deferred<Long>`:** Der `Turn` wird angefordert
   (scheduled), aber der Insert-Aufruf ist **nicht blocking**. Die ID wird in einen
   `Deferred` verpackt. Erst beim Bedarf (z.B. nächster Turn) wird auf die ID gewartet
   (`await()`).

2. **`completedTurns: MutableMap<Long, Deferred<Long>>`-Stack je Leg:** Das ViewModel
   hält einen Stack der gepufferten `Turn`-IDs, indexiert nach `turnIndex`. Beim
   Undo wird die zuletzt gepufferte ID aus dem Stack gepopped.

3. **Turn-Löschung race-frei:** Vor dem Löschen wird auf die `Deferred` gewartet
   (`await()`). Damit ist sichergestellt, dass der DB-Insert abgeschlossen ist,
   bevor der Delete startet. `TurnDao.deleteById` + `MatchRepository.deleteTurn` nutzen
   das existierende CASCADE-Verhalten (Throws werden mit gelöscht).

4. **`turnIndex` lückenlos:** Nach einem Cross-Turn-Undo wird der nächste Turn mit
   `turnIndex + 1` eingetragen (nicht neu durchnummeriert). Die Lücke durch den
   gelöschten Turn ist akzeptabel — Lücken in der `turnIndex`-Sequenz sind möglich.

5. **`lastTurnByPlayer`/`legDartsByPlayer` nachgeführt:** Nach Undo-Replay werden
   diese Caches aktualisiert (für UI-Anzeigen wie „Letzte Aufnahme").

**Konsequenz:** Kein Schema-Drift — `Turn`/`Throw`-Entities unverändert, nur
DAO-Methoden erweitert (`TurnDao.deleteById`, `MatchRepository.deleteTurn`).

### UI-Änderung: `canUndo` in `GameUiState.Playing`

- **Neues Feld:** `GameUiState.Playing.canUndo: Boolean = dartsThrownInCurrentLeg > 0`
- **Auswirkung:** Der Undo-Button ist aktiv, **sobald mindestens ein Dart im Leg
  geworfen wurde** — auch direkt nach einem Spielerwechsel (vorher war er da deaktiviert).
- **Bewusste Verhaltensänderung:** Zwei alte Tests, die „kein Undo nach Spielerwechsel"
  festschrieben, wurden aktualisiert (die neue Regel: Undo bis Leg-Start).

### Engine-interne Refaktorierung

- **`applyDartCore` extrahiert:** Die Kernlogik von `applyDart` (Strategie-Anwendung,
  Aufnahme-Verwaltung, Bust-Handling) wird in eine gemeinsame Funktion ausgezogen,
  die sowohl `applyDart` als auch der Replay-Loop nutzt — kein Code-Duplizierung.
- **`LegEngine.undoLastDart()` bleibt öffentlich:** Das intra-turn-Undo der
  `LegEngine` ist weiterhin getestete, öffentliche API. `MatchEngine` nutzt es nicht
  mehr, aber die Funktion bleibt (backward-compatible).

## Konsequenzen

- **Undo funktioniert jetzt über Aufnahmen und Spielerwechsel hinweg** — unbegrenzt
  bis Leg-Start.
- **Leg-Grenze bleibt Undo-Grenze:** Kein Undo von `LegWon`/`MatchWon`.
- **Replay garantiert Korrektheit:** Deterministische Engine erzeugt gleichen Zustand
  aus gleicher Dart-Folge. Keine separaten Restore-Konstruktoren nötig.
- **Persistenz-Konsistenz via Deferred + Stack:** Turn-Delete läuft race-frei ab,
  kein Schema-Drift (DAO-Erweiterung).
- **Test-Absicherung:** 470 Tests gesamt (Implementer-Basis + 12 Härtungstests für
  kompletten Leg-Rewind, Determinismus, Mehrspieler-Szenarien, Cross-Turn-Undo mit
  DB-Löschung, dartsUsed-Korrektheit nach Undo+Neuwurf, Randfall-Szenarien).
- **ADR-0014 Update:** Die Aussage zu In-Turn-Undo (`undoLastDart` No-op bei leerer
  Aufnahme) wird abgelöst durch diese Entscheidung — Verweis hinzugefügt.
