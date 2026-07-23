# 0020 — Spieler-Loeschen per SET_NULL

**Status:** Akzeptiert

## Kontext
Spieler mit Match-Historie waren nach [ADR-0008](0008-datenmodell-entscheidungen.md)
bislang **nicht löschbar** — `MatchPlayer.playerId` und `Turn.playerId` hatten
`onDelete = ForeignKey.RESTRICT`, um versehentliches Löschen von Spielern mit
Spielhistorie zu verhindern. In der Praxis (Device-Test Phase 2/3) zeigte sich,
dass die Fehlermeldung dem Nutzer still verwehrt bleibt und er die Lösch-Aktion als
„kaputt" wahrnimmt. Eine **Produktentscheidung** war erforderlich: CASCADE (würde
komplette Match-Historie des Spielers und aller Gegner vernichten — fatal für
künftige Statistik/Phase 5), RESTRICT + Fehlermeldung (Nutzer muss Matches kennen),
oder SET_NULL (Anonymisierung der gelöschten Spieler bei Erhalt der kompletten
Match-Historie).

## Entscheidung
- **SET_NULL für `Turn.playerId` und `MatchPlayer.playerId`:** Beim Löschen eines
  Spielers setzen sich die Foreign-Keys auf `NULL` (statt RESTRICT zu blockieren oder
  CASCADE zu löschen). Damit bleibt die komplette Match-Historie erhalten — alle
  Turns, Throws, Match-Daten sind nachvollziehbar, nur die Spieler-Identität ist
  gelöscht.
- **Anonymisierungs-Fallback (`player_deleted`):** Für die künftige History-/Statistik-
  Anzeige wird eine neue String-Ressource `player_deleted` („Gelöschter Spieler")
  vorbereitet, um `NULL`-playerId-Einträge lesbar zu machen (z.B. in Statistik-
  Anzeigen Phase 5).
- **Room-Schema-Migration v1→v2:** Da die App noch unveröffentlicht ist (Strategie
  aus [ADR-0008](0008-datenmodell-entscheidungen.md) = Regenerieren statt Migrieren),
  wird **erstmals eine echte Migration** geschrieben: `MIGRATION_1_2`
  (`data/Migrations.kt`); beide Tabellen (`turns`, `match_players`) müssen
  Spalten-Umständigkeiten durchlaufen (playerId wird nullable). Das Schema wird
  neu exportiert als `2.json`.
  - Indizes werden in der Migration neu angelegt (Neuaufbau der Tabellen ist sauberer
    als ALTER TABLE an mehreren Stellen).
  - `fallbackToDestructiveMigration()` wird **entfernt** (bislang als Notfalllösung
    eingebunden, siehe [ADR-0009](0009-persistenz-tech.md)). Migrationen, die scheitern,
    sollen **nicht** still durch Datenverlust maskiert werden — der Fehler wird sichtbar.
- **Bugfix: `ProfileViewModel.deletePlayer` crasht nicht mehr uncaught:**
  Der Aufruf wirft jetzt eine `SQLiteConstraintException` (bei RESTRICT; mit SET_NULL
  verschwindet das Problem). Um aber auch bei anderen SQLite-Fehlern abgefangen zu
  sein, wurde ein `try/catch` ergänzt (CancellationException wird rethrown, um
  Coroutine-Abbruch nicht zu maskieren). Ein neuer Fehlerdialog (`DeletePlayerErrorDialog`)
  zeigt dem Nutzer, dass das Löschen fehlgeschlagen ist.
- **`profile_delete_message` angepasst:** Die Bestätigung vor dem Löschen weist jetzt
  darauf hin, dass die Match-Historie des Spielers anonymisiert erhalten bleibt
  (statt „Alle Daten werden gelöscht").

## Konsequenzen
- **Spieler können jetzt gelöscht werden** — komplette Match-Historie bleibt erhalten.
- **Anonymisierung in der History:** Gelöschte Spieler zeigen sich als `NULL` playerId;
  der Fallback-String `player_deleted` wird später in Statistik-/History-Screens
  eingeführt (Phase 5).
- **Schema-Version 2 erfordert echte Migration:** Erste Migration (`MIGRATION_1_2`)
  geschrieben; `fallbackToDestructiveMigration()` entfernt — Migrationsfehler sind
  jetzt sichtbar statt stiller Datenverlust.
- **Fehlerdialog bei Delete-Fehler:** `ProfileViewModel` zeigt jetzt UI-Feedback bei
  Lösch-Fehlern statt stiller Abbruch.
- **TEST-Unterstützung:** `room-testing` als `testImplementation` (war nur
  `androidTestImplementation`), MigrationTestHelper lädt under Robolectric Schemas
  aus `mergeDebugAssets` (debug BuildType registriert Schema-Assets).
- **ADR-0008 Update:** Die RESTRICT-Strategie wird durch diese Entscheidung abgelöst
  — Verweis in [ADR-0008](0008-datenmodell-entscheidungen.md) ergänzt.
