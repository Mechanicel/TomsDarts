# 0035 — Analytics-Kennzahlen: Definitionen (Average, First-9, Checkout-Quote, Trefferverteilung)

**Status:** Akzeptiert

## Kontext

Phase 5 ([ADR-0005](0005-analytics.md)) braucht verbindliche Definitionen der Kern-Kennzahlen.
Das Datenfundament steht ([ADR-0034](0034-analytics-datenzugriff.md)): `StatsRepository`
liefert `List<AnalyticsLeg>` mit Aufnahmen und Würfen, Bust-Aufnahmen enthalten alle real
geworfenen Darts, Restpunkte werden nicht gespeichert. Offen war (BACKLOG „`dartsUsed`
schließt Bust-Darts ein"), ob Bust-Darts in Averages zählen. Für die Checkout-Quote gibt es
in der Darts-Welt zwei Lesarten (aufnahme- vs. dartbasiert); ohne Festlegung wären die
Kennzahlen nicht vergleichbar.

## Entscheidung

Alle Kennzahlen sind **pure Funktionen** im Paket `com.mechanicel.tomsdarts.analytics`
(kein Android, kein Room), JVM-testbar:

```kotlin
fun computeX01Metrics(legs: List<AnalyticsLeg>, playerId: Long): X01Metrics
fun computeHitDistribution(legs: List<AnalyticsLeg>, playerId: Long): HitDistribution
fun isOneDartCheckout(remaining: Int, doubleOut: Boolean): Boolean
```

**Gemeinsame Regeln:**
- Es zählen nur Aufnahmen mit `playerId == playerId`. Damit funktionieren beide Quellen
  (`legsForPlayer` bereits gefiltert, `legsForMatch` mit allen Spielern für die Match-Ansicht).
- Leere Eingabe → Zähler 0, Quoten/Averages `null` (keine Division durch 0).

### X01-Kennzahlen (`X01Metrics`)

- **Gültigkeit:** nur Legs mit `modeType == GameModeCatalog.X01`; Legs anderer Modi werden
  ignoriert (Average/Checkout sind dort nicht sinnvoll definiert).
- **Bust-Darts zählen als geworfene Darts mit 0 Punkten** (PDC-/DartConnect-Konvention).
  Das persistierte `totalScored` einer Bust-Aufnahme ist bereits 0 (`LegEngine`); die Funktion
  liest es bei `bust == true` trotzdem nicht, damit nichts doppelt abgezogen oder gewertet wird.
- **3-Dart-Average** = Σ gewertete Punkte / Σ geworfene Darts × 3. Die gewinnende Aufnahme
  zählt mit ihrer tatsächlichen Dart-Zahl (1–3). Aufnahmen **ohne** persistierte Darts werden
  komplett übersprungen (weder Darts noch Punkte).
- **First-9-Average** = 3-Dart-Average über die ersten bis zu 3 eigenen Aufnahmen je Leg
  (sortiert nach `turnIndex`, dann `turnId`; Aufnahmen ohne Darts belegen keinen Platz).
- **Checkout-Quote (dartbasiert):** Der Rest wird per Replay ab `startScore` nachgerechnet,
  innerhalb einer Aufnahme Dart für Dart; nach einer Bust-Aufnahme wird er auf den Stand vor
  der Aufnahme zurückgesetzt.
  - **Versuch** = Dart, geworfen bei einem Rest, der mit genau einem Dart beendbar ist
    (`isOneDartCheckout`): mit Double-Out gerade 2..40 oder 50 (über die 1-Dart-Routen von
    `checkoutSuggestion` aus `game/Checkout.kt`), ohne Double-Out jeder mit einem gültigen
    Treffer erreichbare Wert (1..20, Doppel, Triple, 25, 50).
  - **Erfolg** = Dart, der den Rest auf 0 bringt, in einer Nicht-Bust-Aufnahme eines
    abgeschlossenen Legs mit `winnerId == playerId` (Kontrolle gegen inkonsistente Daten).
  - **Quote** = Erfolge / Versuche, `null` ohne Versuche.
  - **Höchster Checkout** = maximaler Rest vor der gewinnenden Aufnahme.
- **Unbeendete Legs** zählen für Averages und Checkout-**Versuche**, nicht für `legsPlayed`
  und Checkout-Erfolge. `legsPlayed` = abgeschlossene X01-Legs mit mindestens einer eigenen
  Aufnahme, `legsWon` = davon mit `winnerId == playerId`.

### Trefferverteilung (`HitDistribution`)

- **Über alle Modi**, physische Treffer: alle Darts der eigenen Aufnahmen inkl. Bust-Darts.
- `byField` je (`segment`, `multiplier`) inkl. Miss (`HitField(0, 1)`) und Bull (25/1, 25/2),
  `bySegment` je Segment, `byMultiplier` je 1/2/3 **ohne** Misses (die stehen in `misses`).
- Anteile `singleShare`/`doubleShare`/`tripleShare`/`missShare` beziehen sich auf
  `totalDarts` und summieren sich zu 1 (`null` ohne Darts).

## Konsequenzen

- Averages sind mit gängigen Darts-Apps vergleichbar; Busts drücken den Average realistisch.
- Die dartbasierte Checkout-Quote ist strenger als eine aufnahmebasierte („Darts at Double");
  eine aufnahmebasierte Variante wäre bei Bedarf additiv ergänzbar.
- Ohne Double-Out ist fast jeder Rest ≤ 60 ein Versuch — die Quote ist dort entsprechend
  niedriger und nur innerhalb derselben Regelvariante vergleichbar.
- Die Funktionen sind rein lesend auf dem Domänenmodell; keine DAO-/Schemaänderung.
  Sequenz-Auswertungen folgen separat.
