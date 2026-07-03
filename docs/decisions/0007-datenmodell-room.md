# 0007 — Datenmodell (Room)

**Status:** Akzeptiert

## Kontext
Die App braucht ein lokales, relationales Modell für Spieler, Matches, Legs,
Aufnahmen und einzelne Würfe (throw-level, siehe
[ADR-0004](0004-datenhaltung-throw-level.md)).

## Entscheidung
Das ursprünglich vorgeschlagene Modell ist umgesetzt (Phase 1 / „Entities + DAOs").
Feldnamen sind **englisch, camelCase** (konsistent zu `Player`). Zusätzlich zur
ursprünglichen Skizze gibt es die Cross-Ref-Tabelle `MatchPlayer` (siehe
[ADR-0008](0008-datenmodell-entscheidungen.md)).

- `Player` ("players"): id, name, createdAt, (optional Farbe/Avatar später)
- `Match` ("matches"): id, modusTyp + Konfiguration (Startpunkte, doubleOut, Legs, Sets),
  startedAt, finishedAt, winnerId (→Player, SET_NULL)
- `MatchPlayer` ("match_players"): id, matchId (→Match, CASCADE), playerId (→Player,
  RESTRICT), position — bildet Spielerliste/Reihenfolge relational ab
- `Leg` ("legs"): id, matchId (→Match, CASCADE), setNummer (optional), legNummer,
  winnerId (→Player, SET_NULL), startedAt, finishedAt
- `Turn` (Aufnahme, "turns"): id, legId (→Leg, CASCADE), playerId (→Player, RESTRICT),
  aufnahmeIndex, bustFlag, summeGeworfen
- `Throw` (Dart, "throws"): id, turnId (→Turn, CASCADE), dartIndex (1–3), segment
  (1–20/25/0), multiplier (1/2/3), value, timestamp (Epoch-Millis)

Je ein DAO im Stil von `PlayerDao` (alle `suspend`): `MatchDao`, `LegDao`,
`TurnDao`, `ThrowDao`, `MatchPlayerDao`.

## Konsequenzen
- FK-/onDelete-Strategie, `MatchPlayer`-Cross-Ref und Schema-Versionierung: siehe
  [ADR-0008](0008-datenmodell-entscheidungen.md).
- Alle Entities sind in `TomsDartsDatabase` (version 1) eingebunden.
