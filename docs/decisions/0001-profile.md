# 0001 — Profile

**Status:** Akzeptiert

## Kontext
TomsDarts wird von mehreren Menschen an einem Gerät genutzt. Es gibt kein Login
und kein Backend — Spielerprofile müssen lokal verwaltet werden.

## Entscheidung
Mehrere Menschen sind als Spielerprofile anlegbar: **erstellen, auflisten,
bearbeiten, löschen**.

## Konsequenzen
- Die App braucht eine lokale Profil-CRUD-Schicht (umgesetzt in Phase 1,
  siehe [ADR-0012](0012-ui-viewmodel-schicht.md)).
- Profile sind die Grundlage für die Zuordnung von Matches/Aufnahmen/Würfen
  und spätere Analytics.
