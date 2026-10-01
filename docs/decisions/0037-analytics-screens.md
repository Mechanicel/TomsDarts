# 0037 — Analytics-Screens: Einstieg, Balkenliste, Kachelraster, Abschnitts-Modell, Modus-Filter, Wurfmuster, Match-Statistik

**Status:** Akzeptiert

## Kontext

Datenzugriff ([ADR-0034](0034-analytics-datenzugriff.md)), Kennzahlen
([ADR-0035](0035-analytics-kennzahlen-definitionen.md)) und Sequenzen
([ADR-0036](0036-analytics-sequenz-auswertungen.md)) liegen als pure Funktionen vor; es fehlte
die Oberfläche ([ADR-0005](0005-analytics.md)). Offen waren: wo der Einstieg liegt (Tap und
Long-Press auf eine Spielerzeile sind bereits durch den Auswahlmodus belegt), wie die
Trefferverteilung dargestellt wird, wie Kachelraster auf schmalen Geräten und mit großer
Schrift umbrechen, wie der Screen um weitere Abschnitte wächst und welche Modi der Filter
anbietet. Die Screens werden in zwei Lieferungen gebaut (1: Spieler-Statistik, 2: Match-Statistik,
Match-Liste, Button im Sieg-Panel).

## Entscheidung

1. **Einstieg über das Overflow-Menü** der Spielerliste: neuer erster Eintrag „Statistik" vor
   Bearbeiten/Löschen. Im Auswahlmodus ist das Menü wie bisher ausgeblendet. Navigation bleibt
   der `rememberSaveable`-State-Switch in `MainActivity` (`SCREEN_PLAYER_STATS`, `statsPlayerId`),
   kein navigation-compose.
2. **Trefferverteilung als Balkenliste statt Dartboard-Heatmap:** Ring-Anteile
   (Single/Double/Triple/Out) als Kacheln, darunter eine Zeile je getroffenem Segment (absteigend
   nach Treffern, bei Gleichstand nach Segment absteigend) mit Balken relativ zum häufigsten
   Segment und Ring-Aufteilung „S · D · T". Top 10 sichtbar, Rest aufklappbar. Begründung:
   barrierefrei (eine TalkBack-Ansage je Zeile), lesbar auf 320 dp und mit 200 % Schrift, ohne
   eigene Zeichenlogik und eigene Farben. Die Heatmap ist für eine spätere Delight-Phase im
   BACKLOG.
3. **Kachelraster mit `statGridColumns(maxWidth, fontScale)`:** effektive Breite =
   `maxWidth / fontScale`; ≥ 480 dp → 3 Spalten, ≥ 240 dp → 2, sonst 1. Reihen nach dem Muster
   aus [ADR-0033](0033-modus-auswahl-raster-setup.md) (`chunked`, `IntrinsicSize.Min`,
   Spacer-Füller). Große Schrift bricht dadurch früher auf weniger Spalten um, statt Werte
   abzuschneiden.
4. **Abschnitts-Modell:** `PlayerStatsUiState.Content.sections: List<StatsSectionUi>` (sealed:
   `Overview`, `X01`, `X01Empty`, `Distribution`, `Sequences`, `Matches`). Reihenfolge und
   Auswahl bestimmt das ViewModel; der Screen rendert nur. Die Match-Liste (Lieferung 2) ist als
   Variante `Matches` angedockt (ihre Zeilen sind eigene Lazy-Items).
5. **Modus-Filter nur über gespielte Modi:** Chips „Alle" + die `modeType`s aus den Matches des
   Spielers in `GameModeCatalog`-Reihenfolge, unbekannte Kennungen roh am Ende. Bei genau einem
   gespielten Modus wird der Filter ausgeblendet. Die Daten werden **einmal** geladen
   (`legsForPlayer(playerId, null)`, `matchesForPlayer`) und je Filterwechsel im Speicher
   gefiltert und auf `Dispatchers.Default` neu berechnet. Der X01-Abschnitt erscheint nur bei
   „Alle" oder X01 (ohne X01-Darts als Hinweis `X01Empty`); bei anderen Modi entfällt er samt
   Überschrift. Unbeendete Matches zählen in der Übersicht mit.
6. **Abschnitt „Wurfmuster"** (Sequenzen aus [ADR-0036](0036-analytics-sequenz-auswertungen.md)),
   nach der Trefferverteilung, auf derselben gefilterten Leg-Liste:
   - **Gezeigt** werden nur vier Blöcke: *Erster Dart* (häufigster erster Treffer **ohne Miss**,
     bei Gleichstand absteigend nach Feld-Ordnung, max. zwei Felder sichtbar + „+N"; daneben der
     Anteil „erster Dart daneben"), *Nach Dart-Position* (Ø Punkte je Dart 1/2/3, nur wenn
     mindestens eine Position X01-Darts hat — sonst entfällt der Block), *Häufigste Aufnahmen*
     (nur **ungeordnete Kombinationen**, kein Umschalter auf geordnete Sequenzen) und *Häufigste
     Folgen* (Übergänge). Nicht gezeigt: Übergangsmatrix, geordnete Sequenzen, volle
     Erster-Dart-Verteilung, Positions-Felder, unvollständige Aufnahmen.
   - **Rauschfilter:** Muster und Folgen erst ab **Anzahl ≥ 2** (aus den Top 10 von
     `computeSequenceStats`); sichtbar 5, Rest aufklappbar. Bleibt nichts übrig, steht „Noch zu
     wenige Aufnahmen für Muster.".
   - **Feld-Notation** wie im Eingabe-Ziffernblock (`dartShortLabel`: „T-20", „D-Bull", „Out"),
     TalkBack über `dartSpokenLabel` („Triple 20", „Doppel-Bull", „Daneben"); Kombinationen
     werden absteigend angezeigt („T-20 · 20 · 5"), die Daten bleiben kanonisch.
   - **Positions-Raster** mit eigener Spaltenregel `positionGridColumns`: effektive Breite
     (`maxWidth / fontScale`) ≥ 264 dp → 3 Spalten, sonst 1 (keine 2+1-Aufteilung).
     `StatTileGrid` nimmt dafür optional eine eigene Spaltenregel entgegen.
   - `SequenceSection` ist zustandslos (bis auf den Aufklapp-Zustand je Liste, Reset bei
     Filterwechsel) und wird im Match-Screen (Lieferung 2) wiederverwendet.
7. **Match-Statistik (Lieferung 2/2)** mit **zwei Einstiegen**:
   - **A — Matches-Liste** als letzter Abschnitt des Spieler-Screens (`StatsSectionUi.Matches`,
     Modus-Filter beachtet, neueste zuerst): Modus, Datum/Uhrzeit, Ergebnis aus Sicht des Spielers
     „Sieg"/„Niederlage"/„Nicht beendet" + Glyphe „›"; die Zeile ist eine Schaltfläche (ein
     TalkBack-Stopp). „Zurück" führt dann zum Spieler-Screen.
   - **B — Button „Match-Statistik"** im Sieg-Panel (`MatchWonContent`, primär, nach dem
     Endstand); `GameUiState.MatchWon` trägt dafür die `matchId`. „Zurück" führt dann zur
     Profilliste (das Match ist beendet).
   - Navigation weiter als State-Switch: `SCREEN_MATCH_STATS`, `statsMatchId` und
     `matchStatsBackTo` (Ziel von „Zurück") in `MainActivity`.
   - **Aufbau:** Kopf-Panel (Modus, Datum, „Sieger: …"/„Kein Sieger"/„Nicht beendet", „Legs
     gespielt"), ein Abschnitt **je Teilnehmer in Sitzreihenfolge** (X01: dieselben sechs Kacheln
     wie im Spieler-Screen; andere Modi: „Legs gewonnen" + „Darts geworfen" und einmal der Hinweis
     „Average und Checkout gibt es nur für X01."), Sieger mit Zusatz „· Sieger", dann
     Trefferverteilung und Wurfmuster des per **einer gemeinsamen Spieler-Chip-Reihe** gewählten
     Teilnehmers (Default: erster) und zuletzt die Legs-Liste („Set S · Leg N" nur bei
     `setsToWin > 1`, sonst „Leg N").
   - **Gelöschte Spieler** (`playerId = null`) werden zu **einem** Abschnitt „Gelöschter Spieler"
     zusammengefasst (Schlüssel `DELETED_PARTICIPANT_KEY = -1`, an der Position des ersten
     gelöschten Sitzes). Da Legs/Matches beim Abschluss immer mit Sieger gespeichert werden, gilt
     ein abgeschlossenes Leg/Match ohne bekannten Sieger als vom gelöschten Spieler gewonnen,
     sofern einer teilnahm — sonst „Kein Sieger".
   - **Zustände:** Loading, Empty (Match ohne Aufnahmen; Kopf bleibt sichtbar), Content, Error mit
     Retry, NotFound mit Zurück. Ein Spieler, der nur an Matches ohne eigene Aufnahme teilnahm,
     sieht im Spieler-Screen Inhalt (Übersicht + Match-Liste) statt „Noch keine Spiele".

## Konsequenzen

- Keine neue Library, keine DAO-/Schemaänderung; Offline-Kern unberührt.
- Formatierung (`StatsFormat.kt`) ist pure und JVM-getestet; Zahlen immer in deutscher Locale.
- Weitere Abschnitte sind additive `StatsSectionUi`-Varianten ohne Umbau bestehender.
- Die Wurfmuster-Aufbereitung (`SequenceFormat.kt`) ist pure und JVM-getestet; die Balken der
  Felder-Liste und der Wurfmuster teilen sich `StatBar` (Mindestbreite 2 dp bei Anzahl > 0).
- Beim Zurückkehren nach dem `WhileSubscribed`-Timeout lädt der Screen neu, zeigt aber kein
  Loading mehr, solange ein Ergebnis gehalten wird (Loading nur beim ersten Laden und nach Retry).
- Spieler- und Match-Screen teilen sich die Kachel-Bausteine (`x01MetricTiles`,
  `statValueTile`, `legsWonTile`, `dartsThrownTile`) und den Filter-Chip (`StatsFilterChip`) aus
  `StatsComponents`; „Darts geworfen" zeigt ohne Aufnahme „–".
- Die Match-Statistik braucht `MatchRepository.getMatch(id)` (dünnes Durchreichen von
  `MatchDao.getById`); sonst keine DAO-/Schemaänderung.
- Der Filter zeigt nie leere Modi; ein Filter auf einen nicht (mehr) gespielten Modus fällt
  auf „Alle" zurück.
- Zurückgestellt (BACKLOG): Dartboard-Heatmap, Schalter „Nur beendete", Vergleichstabelle der
  Spieler im Match, geordnete Sequenzen/Übergangsmatrix im Wurfmuster-Abschnitt.
