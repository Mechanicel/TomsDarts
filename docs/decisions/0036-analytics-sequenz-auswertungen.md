# 0036 — Analytics-Sequenz-Auswertungen: erster Dart, Dart-Positionen, Übergänge, Aufnahme-Muster

**Status:** Akzeptiert

## Kontext

[ADR-0005](0005-analytics.md) nennt neben den Kern-Kennzahlen auch Reihenfolge-Auswertungen:
„welche Felder in welcher Reihenfolge, der erste Dart, Sequenzen". Datenfundament
([ADR-0034](0034-analytics-datenzugriff.md)) und Kennzahlen-Konventionen
([ADR-0035](0035-analytics-kennzahlen-definitionen.md)) stehen. Offen war, *welche*
Sequenz-Sichten es gibt, wie Aufnahmen mit weniger als 3 Darts behandelt werden, wie Bust-Darts
Punkte je Dart bekommen und wie Top-Listen bei Gleichstand stabil sortiert werden — eine
spätere Screen-Aufgabe rendert das Ergebnis und braucht eine stabile API.

## Entscheidung

Eine pure Funktion im Paket `com.mechanicel.tomsdarts.analytics` (`analytics/Sequences.kt`),
kein Android, kein Room, keine DAO-/Schemaänderung:

```kotlin
fun computeSequenceStats(
    legs: List<AnalyticsLeg>,
    playerId: Long,
    topN: Int = DEFAULT_TOP_N, // = 10
): SequenceStats

data class SequenceStats(
    val visitsCounted: Int,
    val firstDart: Map<HitField, Int>,
    val mostFrequentFirstDart: HitField?,
    val mostFrequentFirstDartShare: Double?,
    val byPosition: List<PositionStats>,
    val transitions: List<Transition>,
    val transitionMatrix: Map<HitField, Map<HitField, Int>>,
    val topOrderedVisits: List<VisitPattern>,
    val topCombinations: List<VisitPattern>,
    val completeVisits: Int,
    val incompleteVisits: Int,
)
data class PositionStats(
    val position: Int, val darts: Int, val byField: Map<HitField, Int>,
    val x01Darts: Int, val x01Points: Int, val averagePoints: Double?,
)
data class Transition(val from: HitField, val to: HitField, val count: Int)
data class VisitPattern(val fields: List<HitField>, val count: Int)
```

`HitField(segment, multiplier)` aus `HitDistribution.kt` wird wiederverwendet (Miss =
`HitField(0, 1)`, Bull = `HitField(25, 1)`, Doppel-Bull = `HitField(25, 2)`).

**Gemeinsame Regeln (wie ADR-0035):**
- **Alle Modi** werden ausgewertet (physische Treffer). Ein Modus-Filter ist Sache des
  Aufrufers (Legs vorher filtern). Nur die Positions-Punkte sind auf X01 beschränkt.
- Nur Aufnahmen mit `playerId == playerId` (Match-Ansicht mit allen Spielern funktioniert);
  Aufnahmen ohne Darts werden übersprungen. Bust-Darts zählen als geworfen.
- Darts einer Aufnahme werden nach `dartIndex` sortiert ausgewertet; **Position** = 1-basierter
  Platz in dieser Reihenfolge (bei konsistenten Daten = `dartIndex`).
- **Feld-Ordnung** = Segment (0 = Miss, 1..20, 25 = Bull), dann Multiplier — dieselbe Ordnung
  wie `HitDistribution.byField`. Alle Maps sind danach sortiert.
- Leere Eingabe → leere Maps/Listen, Zähler 0, Anteile/Averages `null`. `topN` muss ≥ 0 sein
  (sonst `IllegalArgumentException`); `topN = 0` liefert leere Top-Listen, die Lookup-Map bleibt
  vollständig.

### Erster Dart
- `firstDart` = Anzahl je Feld für den ersten Dart jeder ausgewerteten Aufnahme.
- `mostFrequentFirstDart` = meistgetroffenes Feld; **bei Gleichstand das in Feld-Ordnung
  kleinere Feld**. `mostFrequentFirstDartShare` = dessen Anteil an allen ersten Darts
  (= `visitsCounted`).

### Dart-Positionen (`byPosition`)
- Immer genau drei Einträge (Position 1, 2, 3), auch bei 0 Darts. Darts jenseits Position 3
  (inkonsistente Daten) werden für die Positionen ignoriert.
- `darts` und `byField` über alle Modi.
- **Punkte nur in X01** (`modeType == GameModeCatalog.X01`): `x01Darts`, `x01Points`,
  `averagePoints = x01Points / x01Darts` (Punkte **je Dart**, nicht 3-Dart-Average), `null`
  ohne X01-Darts. In Nicht-X01-Modi sind Dart-Punkte nicht sinnvoll definiert.
- **Wertung je Dart:** Nicht-Bust-Aufnahme → `value` des Darts. **Bust-Aufnahme → jeder Dart 0
  Punkte, der Dart-`value` wird ignoriert** — konsistent mit der Aufnahmen-Wertung aus ADR-0035
  (Bust-Aufnahme = 0 Punkte, Darts zählen als geworfen). Die Summe der Positions-Punkte einer
  X01-Auswertung entspricht damit den gewerteten Punkten aus `X01Metrics`.

### Übergänge
- Gezählt wird jedes Paar Dart n → Dart n+1 **innerhalb derselben Aufnahme**, nie über
  Aufnahmen hinweg (eine 3-Dart-Aufnahme liefert 2 Übergänge, eine 2-Dart-Aufnahme einen).
- `transitionMatrix` = vollständiger Lookup `von → (nach → Anzahl)` („nach T20 folgt 20 (12×)").
- `transitions` = Top-N, sortiert nach Anzahl absteigend, dann von-Feld, dann nach-Feld
  (Feld-Ordnung).

### Häufigste Aufnahmen
- Nur **vollständige 3-Dart-Aufnahmen** (`completeVisits`). Aufnahmen mit weniger Darts
  (Checkout, Bust-Abbruch, Leg-Ende) werden in `incompleteVisits` gezählt — sie gehen in
  erster Dart, Positionen und Übergänge ein, verzerren aber nicht die 3-Dart-Muster.
- `topOrderedVisits` = geordnete Sequenzen (Felder in Wurf-Reihenfolge).
- `topCombinations` = ungeordnete Kombination (Multiset), Felder kanonisch nach Feld-Ordnung
  sortiert: T20-20-T20 und 20-T20-T20 sind verschiedene Sequenzen, aber dieselbe Kombination
  `[20, T20, T20]`.
- Sortierung: Anzahl absteigend, bei Gleichstand lexikografisch nach Feld-Ordnung.

## Konsequenzen

- Eine Funktion liefert alle Sequenz-Sichten in einem Durchlauf; der Analytics-Screen kann
  direkt rendern, ohne eigene Zähl-/Sortierlogik.
- Stabile, deterministische Sortierung macht Top-Listen testbar und bei gleichen Daten
  reproduzierbar.
- Positions-Averages sind Punkte je Dart; für einen 3-Dart-Vergleich ×3 rechnen.
- Längere Sequenzen über Aufnahmen hinweg (z.B. „Aufnahme nach einer 180") sind bewusst nicht
  Teil dieser Entscheidung und additiv ergänzbar.
