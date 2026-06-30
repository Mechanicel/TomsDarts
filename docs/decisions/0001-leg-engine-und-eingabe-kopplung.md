# 0001 — Leg-Engine, Eingabe-Kopplung und Abschluss-Persistenz für ein X01-Leg

- Status: akzeptiert
- Datum: 2026-06-30
- Phase: 2 — Kern-Gameplay
- Kontext-Aufgabe: „Eingabe an die Spiel-Logik koppeln; throw-level speichern"

## Kontext

Der Ziffernblock (`DartInputState` / `DartKeypad`) und die reine X01-Modus-Strategie
(`X01Mode : GameMode<X01State>`) existierten bereits, waren aber nicht verbunden und es
wurde nichts gespeichert. Für die erste spielbare Scheibe musste die Eingabe an die
Spiel-Logik gekoppelt und jeder Dart throw-level persistiert werden — vorerst für ein
**Einzelspieler-X01-Leg**.

Die `GameMode`-Strategie ist bewusst zustandslos bzgl. Aufnahme-Grenzen (verarbeitet
genau einen Dart, meldet nur `bust`/`legWon`); das Bündeln zu Aufnahmen und das
Bust-Revert sollte laut bestehender Design-Entscheidung „Spielmodi-Domänenlogik" in einer
aufrufenden Engine liegen.

## Entscheidung

1. **Pure, modus-agnostische `LegEngine<S>`** (`com.mechanicel.tomsdarts.game.engine`,
   weiterhin Android-/Room-/Compose-frei): übernimmt Aufnahme-Bündelung (max. 3 Darts),
   Bust-Revert (State zurück auf den Aufnahme-Startzustand), Sofort-Checkout sowie
   In-Turn-Undo (`undoLastDart()`). Sie ist rein per JUnit testbar und bleibt von
   Persistenz und UI getrennt.

2. **Engine-getriebene Kopplung im `GameViewModel`:** Das ViewModel hält den gehoisteten
   `DartInputState` (UI-Anzeige/Modifikator) und die `LegEngine` (Score-Logik) und hält
   beide synchron. So bleibt die Eingabe-Logik von der Score-Logik getrennt.

3. **Throw-level-Persistenz pro Aufnahme:** Erst am Aufnahme-Ende werden ein `Turn` und
   alle zugehörigen `Throw`s geschrieben — inklusive der Darts einer Bust-Aufnahme.

4. **`@Update` statt `@Insert(REPLACE)` für den Match-/Leg-Abschluss:** `endedAt`/
   `winnerId` werden über neue `@Update`-DAO-Methoden gesetzt.

5. **Navigation als MainActivity-State-Switch** (Profil ⇄ Spiel via `rememberSaveable`),
   bewusst ohne navigation-compose; Spiel-Einstieg über Tap auf den Spieler-Body.

## Begründung / Trade-offs

- **Engine getrennt von der Strategie:** hält `GameMode` simpel und wiederverwendbar für
  künftige Modi; die Aufnahme-/Bust-Mechanik liegt an genau einer Stelle.
- **`@Update` statt REPLACE:** Ein REPLACE-Insert auf Match/Leg hätte über
  `ON DELETE CASCADE` die bereits gespeicherten Kinder (Legs/Turns/Throws) gelöscht. Die
  reine DAO-Erweiterung vermeidet das **ohne Schema-Drift** — abgelehnte Alternative wäre
  ein Insert mit `OnConflictStrategy.REPLACE` gewesen.
- **Bust-Darts werden mitgespeichert:** Roh-Persistenz bleibt vollständig; ob für
  Statistik „nur gewertete Darts" zählen, ist eine spätere Produkt-Entscheidung (eigene
  Kennzahl/Filter statt Verlust der Rohdaten).
- **State-Switch statt navigation-compose:** minimal gehalten bei nur zwei Screens; ein
  echter Navigationsgraph kann mit dem Spiel-Setup-Screen (Phase 3) nachgezogen werden.

## Konsequenzen / offene Punkte

- Nur ein **Einzelspieler-Leg**; Zwei Spieler / Aufnahme-Wechsel / Legs/Sets folgen.
- `GameUiState.Error` hat noch keinen echten `retry()` (führt zurück zur Profilliste).
- `onNewLeg` bei `legsToWin=1` legt ein weiteres Leg im bereits abgeschlossenen Match an.
- `Throw.dartIndex`-Konvention im Code/Datenmodell ist 1..3, die Entity-KDoc nennt 0..2 —
  anzugleichen.

Details und Folge-Aufgaben siehe `docs/CHECKLISTE.md` (Abschnitt „Spiel-Engine &
Eingabe-Kopplung" und Backlog).
