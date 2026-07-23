# 0003 — Eingabe (Ziffernblock)

**Status:** Akzeptiert

**Update (2026-07-23):** Nach dem dritten Dart greift eine Kontrollpause ein ([ADR-0026](0026-turn-review-kontrollpause.md)), die das Keypad zusätzlich sperrt; die Sperr-Entscheidung (Sperren statt Auto-Clear) bleibt gültig und wird ergänzt.

## Kontext
Die Wurf-Eingabe braucht eine schnelle, fehlerarme Bedienung. Eine gezeichnete
Dartscheibe wäre auf dem Handy fummelig; ein Ziffernblock ist präziser und
schneller.

## Entscheidung
- **Ziffernblock**, keine gezeichnete Dartscheibe.
- **Single** ist der stille Standardzustand.
- **Double / Triple** sind Modifikator-Tasten (Toggle). Nach **jedem** Dart
  springt der Modifikator automatisch zurück auf Single (Auto-Reset).
- Modifikator wird als Präfix **`D-`** / **`T-`** angezeigt (Großbuchstabe + Bindestrich),
  z. B. `D-20`, `T-20`. Sobald Double/Triple aktiv ist, zeigen die Zifferntasten
  das Präfix live (`D-1 … D-20`).
- **Layout:** Zahlen 1–20 links als 4-Spalten-Block. Rechts eine Spalte
  Sondertasten, von oben nach unten: **Undo, Out, Bull, Double, Triple**.
- **Out** = geworfener Dart mit **0 Punkten**, belegt einen der drei Wurf-Slots.
- **Bull** = 25. **Double-Bull** (`D-Bull`) = 50. Triple auf Bull existiert nicht
  (wirkungslos).
- **Erfassung Dart für Dart** (keine Rundensummen-Eingabe) — bewusst, weil die
  Einzel-Dart-Daten die Grundlage für Analytics und Spaß-Trigger sind.
- Drei Wurf-Slots pro Aufnahme. Auto-Verrechnung nach dem dritten Dart oder
  sofort beim Checkout (Rest auf 0).
- **Undo** entfernt den zuletzt eingegebenen Dart.

## Umsetzung (festgelegt, Phase 2 / „Eingabe-Screen (Ziffernblock)")
- **Eingabe-Logik getrennt von der Compose-UI:** Die Wurf-Eingabe ist als pure,
  Compose-/Android-freie `DartInputState`-Logik (Paket
  `com.mechanicel.tomsdarts.ui.input`) gebaut und damit rein per JUnit (ohne
  Robolectric) testbar. Die Compose-Schicht (`DartKeypad.kt`) ist eine dünne,
  stateless/stateful getrennte Darstellung darüber — gleiches Muster wie bei der
  Profilverwaltung. Wiederverwendete Werte sind die Domänen-`Dart`s aus dem
  `game`-Paket (kein eigener Eingabe-Dart-Typ).
- **Rotationsfest über `rememberSaveable`-Saver:** Der `DartKeypad`-State (laufende
  Aufnahme inkl. aktivem Modifikator) übersteht Konfigurationsänderungen/Rotation.
- **Sperren statt Auto-Clear nach 3 Darts:** Ist die Aufnahme voll (`isComplete`),
  sind alle Eingabe-Transitionen No-ops; nur `undo` und `startNewTurn` bleiben
  wirksam. Das Leeren für die nächste Aufnahme (`startNewTurn()`) ist **bewusst
  Sache des Aufrufers** (spätere Spiel-Engine), nicht ein automatisches Clear —
  damit der Aufrufer die fertige Aufnahme zuerst verrechnen/speichern kann.
- **Aufnahme-Summe wird angezeigt** (`turnSum`, neben den drei Slot-Kacheln) als
  laufende Rückmeldung; die eigentliche Score-Verrechnung passiert erst beim
  Koppeln an die Spiel-Logik.
- **Bull-Semantik im Modifikator:** Bull ist im TRIPLE-Modus deaktiviert
  (`bullEnabled == false`, kein Triple-Bull); DOUBLE+Bull = Doppel-Bull (50),
  sonst Bull (25). Nach jeder Eingabe Auto-Reset auf SINGLE (auch nach `pressOut`).

## Konsequenzen
- Feine throw-level-Daten für Analytics/Delight (siehe
  [ADR-0004](0004-datenhaltung-throw-level.md)).
- Die Score-Verrechnung obliegt der Spiel-Logik/Engine (siehe
  [ADR-0014](0014-spiel-engine-eingabe-kopplung.md)), nicht dem Keypad.
