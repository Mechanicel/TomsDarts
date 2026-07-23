# 0001 — Profile

**Status:** Akzeptiert

**Update (ADR-0023):** Das Prinzip „kein Login/Backend" wird präzisiert: Der
lokale Offline-Kern bleibt garantiert. Firebase Login und Online-Features sind
reine, strikt opt-in-Zusatzschicht; ohne Konto funktioniert die App exakt wie
heute. Lokale Profile bleiben Basis und Single Source of Truth →
[ADR-0023](0023-firebase-optionale-online-schicht.md).

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
