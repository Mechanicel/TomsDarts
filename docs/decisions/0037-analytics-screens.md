# 0037 — Analytics-Screens: Einstieg, Balkenliste, Kachelraster, Abschnitts-Modell, Modus-Filter

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
   `Overview`, `X01`, `X01Empty`, `Distribution`). Reihenfolge und Auswahl bestimmt das
   ViewModel; der Screen rendert nur. Match-Liste (Lieferung 2) und Sequenzen (ADR-0036) docken
   als weitere Varianten an.
5. **Modus-Filter nur über gespielte Modi:** Chips „Alle" + die `modeType`s aus den Matches des
   Spielers in `GameModeCatalog`-Reihenfolge, unbekannte Kennungen roh am Ende. Bei genau einem
   gespielten Modus wird der Filter ausgeblendet. Die Daten werden **einmal** geladen
   (`legsForPlayer(playerId, null)`, `matchesForPlayer`) und je Filterwechsel im Speicher
   gefiltert und auf `Dispatchers.Default` neu berechnet. Der X01-Abschnitt erscheint nur bei
   „Alle" oder X01 (ohne X01-Darts als Hinweis `X01Empty`); bei anderen Modi entfällt er samt
   Überschrift. Unbeendete Matches zählen in der Übersicht mit.

## Konsequenzen

- Keine neue Library, keine DAO-/Schemaänderung; Offline-Kern unberührt.
- Formatierung (`StatsFormat.kt`) ist pure und JVM-getestet; Zahlen immer in deutscher Locale.
- Weitere Abschnitte sind additive `StatsSectionUi`-Varianten ohne Umbau bestehender.
- Der Filter zeigt nie leere Modi; ein Filter auf einen nicht (mehr) gespielten Modus fällt
  auf „Alle" zurück.
- Zurückgestellt (BACKLOG): Dartboard-Heatmap, Schalter „Nur beendete", Vergleichstabelle der
  Spieler im Match.
