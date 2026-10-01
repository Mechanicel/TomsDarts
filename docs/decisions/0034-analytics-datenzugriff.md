# 0034 — Analytics-Datenzugriff: flacher StatsDao + pures Domänenmodell

**Status:** Akzeptiert

## Kontext

Phase 5 (Analytics, [ADR-0005](0005-analytics.md)) baut Kennzahlen (3-Dart-Average,
First-9-Average, Checkout-Quote, Trefferverteilung), Sequenz-Auswertungen und
Analytics-Screens auf den throw-level-Daten ([ADR-0004](0004-datenhaltung-throw-level.md)) auf.
Die bestehenden DAOs sind reines CRUD je Tabelle; eine Auswertung über
Match → Leg → Turn → Throw bräuchte damit N+1-Abfragen. Gleichzeitig sollen die
Kennzahlen **pur und JVM-testbar** sein (kein Room, kein Android), und zwei
Folgeaufgaben (Kennzahlen, Sequenzen) sollen parallel auf **einem stabilen
Datenmodell** aufsetzen können.

Randbedingungen aus der Persistenz (Ist-Stand, Room v2):
- Eine Aufnahme (`Turn`) wird erst nach ihrem Abschluss geschrieben; Bust-Aufnahmen
  persistieren alle real geworfenen Darts (`totalScored` wie von der Engine gewertet).
- `turnIndex` zählt fortlaufend **pro Leg über alle Spieler**; `dartIndex` ist 1..3.
- Checkout-/Restpunkte werden nicht gespeichert — per Replay aus `startScore` ableitbar.
- Gelöschte Spieler bleiben als `playerId = null` in der Historie (SET_NULL, [ADR-0020](0020-spieler-loeschen-set-null.md)).

## Entscheidung

### 1. Ein lesender `StatsDao` mit flachen Join-Zeilen

`data/dao/StatsDao.kt` liefert die Daten in **einer** Query als flache Zeilen
`StatsThrowRow` (eine Zeile je Wurf): `turns` INNER JOIN `legs` INNER JOIN `matches`,
`throws` per **LEFT JOIN** — eine Aufnahme ohne persistierte Würfe fällt so nicht still
heraus (Dart-Felder dann `null`). Die Zeilenklassen (`StatsThrowRow`, `StatsMatchRow`,
`data/dao/StatsRows.kt`) sind pure Kotlin-Data-Classes ohne Annotationen (Room mappt
per Spalten-Alias).

| Methode | Inhalt |
|---|---|
| `getThrowRowsForPlayer(playerId: Long, modeType: String? = null): List<StatsThrowRow>` | Nur die **eigenen** Aufnahmen des Spielers; `modeType = null` = alle Modi |
| `getThrowRowsForMatch(matchId: Long): List<StatsThrowRow>` | Alle Spieler des Matches (inkl. `playerId = null`) |
| `getMatchesForPlayer(playerId: Long): List<StatsMatchRow>` | Matches laut `match_players`, distinct, `startedAt DESC, id DESC` |

**Sortierung** der Zeilen-Queries: Match chronologisch (`startedAt`, `id`), dann
`setNumber` (NULL zuerst), `legNumber`, Leg-`id`, `turnIndex`, Turn-`id`, `dartIndex`,
Throw-`id`.

**Keine Schemaänderung**, keine DB-Versionserhöhung (bleibt v2). Der DAO ist in
`TomsDartsDatabase.statsDao()` registriert.

### 2. Pures Domänenmodell im Paket `com.mechanicel.tomsdarts.analytics`

Android-frei, stabile Namen (Vertrag für die Folgeaufgaben):

```kotlin
data class AnalyticsLeg(
    val legId: Long, val matchId: Long, val modeType: String, val startScore: Int,
    val doubleOut: Boolean, val matchStartedAt: Long, val setNumber: Int?, val legNumber: Int,
    val winnerId: Long?, val finished: Boolean, val visits: List<AnalyticsVisit>,
)
data class AnalyticsVisit(
    val turnId: Long, val turnIndex: Int, val playerId: Long?, val bust: Boolean,
    val totalScored: Int, val darts: List<AnalyticsDart>,
)
data class AnalyticsDart(val dartIndex: Int, val segment: Int, val multiplier: Int, val value: Int)
data class AnalyticsMatchSummary(
    val matchId: Long, val modeType: String, val startedAt: Long, val endedAt: Long?, val winnerId: Long?,
)

fun List<StatsThrowRow>.toAnalyticsLegs(): List<AnalyticsLeg>
fun StatsMatchRow.toMatchSummary(): AnalyticsMatchSummary
```

- `finished` = Leg hat `endedAt`; `winnerId` = Leg-Gewinner (null bei offenem Leg,
  Leg-Ende ohne Werfer-Sieg oder gelöschtem Spieler).
- **Reihenfolge-Vertrag des Mappings:** Legs in Reihenfolge ihres ersten Auftretens
  (die Queries liefern chronologisch); Visits je Leg nach (`turnIndex`, `turnId`);
  Darts je Visit nach `dartIndex`. Aufnahme ohne Würfe → Visit mit leerer `darts`-Liste;
  unvollständige Dart-Zeilen werden ignoriert.
- Bei der Spieler-Query enthält jedes `AnalyticsLeg` **nur die Visits dieses Spielers**;
  Legs ohne eigene Aufnahme fehlen.

### 3. Dünnes `StatsRepository` ([ADR-0011](0011-repository-di-schicht.md))

`data/repository/StatsRepository.kt`, bereitgestellt als `AppContainer.statsRepository`:

```kotlin
class StatsRepository(private val statsDao: StatsDao) {
    suspend fun legsForPlayer(playerId: Long, modeType: String? = null): List<AnalyticsLeg>
    suspend fun legsForMatch(matchId: Long): List<AnalyticsLeg>
    suspend fun matchesForPlayer(playerId: Long): List<AnalyticsMatchSummary>
}
```

Reines Durchreichen + Mapping, **keine Kennzahlen**.

### 4. Kennzahlen als pure Funktionen auf dem Domänenmodell

Kennzahlen und Sequenzen entstehen in Folgeaufgaben als **pure Funktionen** auf
`List<AnalyticsLeg>` im Paket `analytics` (JVM-Tests ohne Robolectric). X01-spezifische
Kennzahlen (Average, First-9, Checkout-Quote) filtern per `modeType` (X01-Keys aus dem
`GameModeCatalog`) bzw. fragen gezielt `legsForPlayer(playerId, modeType)` ab;
modusübergreifende Auswertungen (Trefferverteilung) nutzen `modeType = null`.
Ob Bust-Darts in Averages zählen, bleibt Produktentscheidung (siehe BACKLOG
„`dartsUsed` schließt Bust-Darts ein") — die Daten enthalten sie, eine Kennzahl kann
per `bust` filtern.

## Konsequenzen

- Eine Abfrage statt N+1; Gruppierung und Kennzahlen sind pur und schnell testbar.
- Die Zeilenmenge wächst linear mit den Würfen eines Spielers; für die erwartete lokale
  Datenmenge unkritisch. Paging/Aggregation in SQL ist bei Bedarf nachrüstbar.
- Nicht beendete/abgebrochene Matches und Legs sind enthalten (`finished = false`,
  `AnalyticsMatchSummary.endedAt = null`) — Kennzahlen entscheiden selbst, ob sie filtern.
- Per Undo zurückgenommene Aufnahmen sind physisch gelöscht und tauchen nicht auf.
- **Index-Notiz (bewusst nicht umgesetzt, keine Schemaänderung):** Vorhanden sind
  `turns(legId)`, `turns(playerId)`, `legs(matchId)`, `throws(turnId)`,
  `match_players(playerId)`. Falls die Spieler-Query bei großen Historien langsam wird,
  wären Kandidaten ein zusammengesetzter Index `turns(playerId, legId)` sowie
  `matches(modeType)` / `matches(startedAt)` — erfordert Migration v2 → v3.
