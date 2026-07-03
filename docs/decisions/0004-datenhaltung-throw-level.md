# 0004 — Datenhaltung (throw-level)

**Status:** Akzeptiert

## Kontext
Analytics und die Delight-Schicht (Spaß-Trigger) brauchen die feinste
Granularität der Wurfdaten — Rundensummen genügen nicht.

## Entscheidung
**Jeder einzelne Dart wird gespeichert**: Segment (1–20, 25, 0), Multiplikator
(1/2/3), resultierender Wert, Reihenfolge innerhalb der Aufnahme, Zuordnung zu
Aufnahme/Leg/Match/Spieler. Pflicht für Analytics **und** Spaß-Trigger.

## Konsequenzen
- Das Datenmodell hat eine `Throw`-Entity auf Dart-Ebene
  (siehe [ADR-0007](0007-datenmodell-room.md)).
- Bust-Darts werden mitgespeichert (siehe [ADR-0014](0014-spiel-engine-eingabe-kopplung.md)).
- Die Eingabe erfolgt Dart für Dart, keine Rundensummen-Eingabe
  (siehe [ADR-0003](0003-eingabe-ziffernblock.md)).
