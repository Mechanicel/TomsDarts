# 0008 — Datenmodell-Entscheidungen

**Status:** Akzeptiert

## Kontext
Zum Datenmodell (siehe [ADR-0007](0007-datenmodell-room.md)) gehören bewusste
Entscheidungen zu Beziehungen, Löschverhalten und Schema-Versionierung.

## Entscheidung
- **`MatchPlayer`-Cross-Ref:** Die Match-Teilnahme (Spielerliste + Reihenfolge) wird
  über eine eigene Cross-Ref-Tabelle `match_players` (matchId/playerId/position)
  abgebildet statt als Liste im `Match` — sauber relational und über `position`
  geordnet abfragbar (`MatchPlayerDao.getByMatch` mit `ORDER BY position`).
- **FK-Löschstrategie (onDelete):**
  - **CASCADE** für die Besitz-Hierarchie: Leg→Match, Turn→Leg, Throw→Turn,
    MatchPlayer→Match. Löscht man ein Match/Leg/Turn, verschwinden die untergeordneten
    Daten mit.
  - **SET_NULL** für Gewinner-Referenzen `Match.winnerId` / `Leg.winnerId` (→Player):
    Spieler-Löschung entfernt nicht das Match/Leg, nur die Gewinner-Markierung.
  - **RESTRICT** für `Turn.playerId` und `MatchPlayer.playerId` (→Player): ein Spieler
    mit Spielhistorie kann nicht versehentlich gelöscht werden, solange er verknüpft ist.
  - Indizes liegen auf allen FK-Spalten.
- **Schema-Versionsstrategie (solange unveröffentlicht):** `@Database(version=1)`
  bleibt bei `1`; bei Modelländerungen wird das exportierte Schema-JSON
  (`app/schemas/.../1.json`) **regeneriert** statt eine Migration zu schreiben. Erst
  ab der ersten ausgelieferten Version werden Versionsschritte + Migrationen Pflicht.

## Konsequenzen
- Kein Schema-Drift bei Modelländerungen vor Veröffentlichung.
- Spieler mit Historie sind gegen versehentliches Löschen geschützt.
- Ab erster Auslieferung sind Migrationen Pflicht (dann Versionsschritte statt
  Regenerieren).
