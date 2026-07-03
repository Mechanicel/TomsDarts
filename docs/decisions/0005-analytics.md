# 0005 — Analytics

**Status:** Akzeptiert

## Kontext
Aus den throw-level-Daten (siehe [ADR-0004](0004-datenhaltung-throw-level.md))
sollen aussagekräftige Kennzahlen und Auswertungen entstehen.

## Entscheidung
Auswertbar: welche Felder ein Spieler wie oft trifft, in welcher Reihenfolge,
erster Dart, Sequenzen, 3-Dart-Average, Checkout-Quote, First-9-Average,
Trefferverteilung u. ä.

## Konsequenzen
- Die throw-level-Persistenz ist Voraussetzung (Phase 1 umgesetzt).
- Die konkreten Queries/Kennzahlen/Screens folgen in Phase 5 (Analytics).
