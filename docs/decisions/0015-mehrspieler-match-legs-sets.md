# 0015 — Mehrspieler-Match / Legs/Sets

**Status:** Akzeptiert

## Kontext
Festgelegt in Phase 2 / „Zwei Spieler, Aufnahme-Wechsel, Legs/Sets". Der
Einzelspieler-Flow ([ADR-0014](0014-spiel-engine-eingabe-kopplung.md)) wird auf
mehrere Spieler mit Aufnahme-Wechsel und Legs/Sets erweitert.

## Entscheidung
- **Pure, modus-agnostische Engine `MatchEngine<S>`** (`com.mechanicel.tomsdarts.game.engine`,
  weiterhin Android-/Room-/Compose-frei, rein JUnit-testbar): Sie sitzt **über** der
  `LegEngine` und verantwortet alles, was über ein einzelnes Leg hinausgeht — den
  **Werfer-Wechsel** nach jeder Aufnahme, das Erkennen von **Leg-/Set-/Match-Ende**
  (`GameConfig.legsToWin`/`setsToWin`, „Best of X") und die **Starter-Rotation** je Leg.
  Schnittstellen: `applyDart → MatchDartResult`, `startNewTurn`, `snapshot`
  (`MatchSnapshot`), `undoLastDart`. Die `LegEngine`/`GameMode`-Verträge (Bust-Revert,
  Sofort-Checkout, `bust` XOR `legWon`) bleiben unangetastet.
- **`MatchEngine`-Kopplung im `GameViewModel`:** Das ViewModel treibt jetzt
  `MatchEngine<X01State>` für `playerIds: List<Long>` (Factory `provideFactory(playerIds)`)
  statt einer einzelnen `LegEngine`. **Feste Konfig vorerst:**
  `GameConfig(startScore=501, doubleOut=true, legsToWin=2, setsToWin=1)` → **Best of 3
  Legs, 1 Set**. Die volle Spiel-Konfig (Startpunkte/Double-Out/Legs-/Sets-Anzahl) kommt
  erst mit dem **Phase-3-Setup-Screen**; bis dahin ist die Best-of-3-Legs-Konfig
  bewusst hartkodiert (Tom-Entscheidung).
- **Mehrspieler-Persistenz (`MatchPlayer` + Legs):** Beim Init wird das Match
  (`modeType="X01"`) + **alle** `MatchPlayer` (`position` = Eingabe-Index, bildet
  Spielerliste/Reihenfolge ab) + das erste Leg angelegt. **Throw-level pro Werfer-Aufnahme:**
  jede beendete Aufnahme schreibt einen `Turn`(`playerId` = Werfer) + dessen `Throw`s
  (inkl. Bust-Darts, konsistent zu [ADR-0004](0004-datenhaltung-throw-level.md)). Leg-Ende setzt
  `Leg.winnerId`/`endedAt` (`updateLeg`), Match-Ende `Match.winnerId`/`endedAt`
  (`updateMatch`); `onNewLeg` legt das (engine-intern bereits rotierte) nächste Leg an.
  **Kein Schema-Drift** — Entities/DAOs unverändert.
- **`GameUiState` für Mehrspieler:** Neuer Sub-State `PlayerScoreUi(playerId, name,
  remaining, legsWon, setsWon, isCurrent)` und die Zustände
  `Playing`(players, startScore, input, currentLegNumber, currentSetNumber, legsToWin,
  setsToWin) / `LegWon`(players, legWinnerName, nextStarterName, nextLegNumber, dartsUsed) /
  `MatchWon`(players, matchWinnerName, dartsUsed) ersetzen das frühere Einzelspieler-`Won`.
  `NoPlayer` = **weniger als 2 gültige Spieler**. (Das transiente `bustEvents`-Signal aus
  der Einzelspieler-Kopplung bleibt bestehen.)
- **Mehrspieler-UI:** Neue Datei `ui/game/MatchScoreboard.kt` (`MatchScoreboard` +
  `PlayerScoreCard`): Portrait-Row bzw. Landscape-Kompaktzeile via `BoxWithConstraints`.
  Der **aktive Werfer** wird ausschließlich über **Hervorhebung** gekennzeichnet
  (`▸`-Glyph + `primaryContainer`) plus `liveRegion=Polite` auf der aktiven Karte —
  **bewusst kein transientes Per-Aufnahme-Wechsel-Banner**. `GameScreen` um
  `LegWonContent`/`MatchWonContent` erweitert; `GameScreen`/`MainActivity` reichen die
  `playerIds: List<Long>` durch (`rememberSaveable` LongArray).
- **Profil-Auswahlmodus als Spiel-Einstieg (Tom-Entscheidung):** Der Spiel-Einstieg ist
  jetzt ein **Auswahlmodus in der Profilliste** statt des bisherigen Einzel-Tap-Starts.
  Separater `ProfileSelectionState(active, selectedIds)`-StateFlow + VM-Methoden
  `enterSelection`/`enterSelectionMenu`/`toggleSelect`/`exitSelection`/`startMatch`
  (validiert **≥ 2** Spieler). `ProfileScreen` zeigt im Auswahlmodus eine CAB
  (✕ + „N ausgewählt") und einen FAB „Match starten (N)" (disabled bei < 2);
  `PlayerListItem` hat einen zweiten Render-Pfad (Tap = toggle, Avatar zeigt die
  **Reihenfolge-Nummer** auf `primary`, kein Overflow-Menü, `toggleable`/`role=Checkbox`).
  Tap im Normalmodus startet den Auswahlmodus **und** selektiert die Zeile (wie Long-Press).
  Detail-Entscheidungen: Auswahlmodus aktivierbar per **Long-Press UND TopAppBar-Aktion
  „Match"**; Auswahl-Indikator = **Reihenfolge-Nummer im Avatar** (kein zusätzliches
  Checkbox-Element); eigener String `game_next_leg` („Nächstes Leg").
- **`dartsUsed`-Semantik (Mehrspieler):** `dartsUsed` zählt die Darts **des Gewinners im
  Gewinn-Leg** (per-Spieler-Zähler); **Bust-Darts des Gewinners zählen mit**. Das ist per
  Test als IST-Verhalten festgehalten — die finale Statistik-Definition bleibt eine
  **Produktentscheidung** (siehe Backlog).

## Konsequenzen
- Vollwertiger Match-Flow (Legs/Sets, „Best of X") über die `MatchEngine`.
- Volle Konfigurierbarkeit (Startpunkte/Double-Out/Legs/Sets/Spieleranzahl) erst mit
  dem Phase-3-Setup-Screen; bis dahin feste Best-of-3-Legs-Konfig.
- `dartsUsed` und Set-Übergänge bleiben offene Produkt-/Konfig-Themen (siehe Backlog).
