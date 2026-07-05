# 0019 — Setup-Screen: Teilnehmerverwaltung (Reihenfolge & Entfernen)

**Status:** Akzeptiert

## Kontext
Festgelegt in Phase 3 / „Spiel-Setup-Screen: Spieleranzahl". Der Setup-Screen
([ADR-0018](0018-setup-screen-startpunkt.md)) wird um die **erste Konfigurationssektion**
erweitert: eine **Teilnehmerverwaltung**, über die die im Profil-Auswahlmodus gewählten
Spieler editierbar werden — Reihenfolge (relevant für Starter-Rotation der
`MatchEngine`) ändern und Spieler entfernen. Damit die Untergrenze `MIN_MATCH_PLAYERS = 2`
gewahrt bleibt, gibt es eine Removes-Schutzlogik auf mehreren Ebenen (Reducer, UI).

## Entscheidung

### Teilnehmer-Section als erste Section des Setup-Screens
- **Position:** Die Teilnehmerverwaltung ist die **erste Section** des Setup-Screens
  (vor Startpunkten, Double-Out, Legs/Sets) — Reihenfolge ist Spielmechanik, nicht nur
  Display-Option. Das stellt sicher, dass die `playerIds`-Liste im Setup **vor** der
  Konfiguration der Spielregeln (Startpunkt etc.) editierbar ist.
- **Darstellung:** Geordnete Liste (Zeile je Teilnehmer): Positions-Nummer (1, 2, 3…),
  Avatar-Glyph (Anfangsbuchstaben), Spieler-Name.
- **Ausgangswerte:** Die ID-Liste kommt aus dem Profil-Auswahlmodus (verortet in
  `MainActivity`); das ViewModel (`SetupViewModel`) löst die IDs via `PlayerRepository`
  zu Spieler-Namen auf und gibt eine geordnete `List<SetupPlayer>` aus, die in der UI
  bearbeitet wird.

### Reihenfolge ändern: ↑/↓-Buttons (Nachbar-Swap statt Drag)
- **UI-Komponente:** Je Zeile zwei Icon-Buttons (`IconButton`) mit Icons
  `Icons.Default.KeyboardArrowUp` und `Icons.Default.KeyboardArrowDown` (neue Dependency
  `androidx.compose.material:icons-core`). Sie führen Nachbar-Swaps durch: ↑ tauscht
  mit dem Vorgänger, ↓ mit dem Nachfolger.
- **Rationale:** (1) **Aufwand/Robustheit** — Nachbar-Swap (Two-Step-Reorder statt
  beliebiger Drag-Drop) ist einfacher zu implementieren und zu testen (pure Funktionen,
  host-testbar). (2) **Accessibility** — ↑/↓-Buttons mit klaren `contentDescription`
  sind screenreader-freundlich (TalkBack kann „Spieler 1 nach oben" vorlesen), Drag ist
  schlecht für Accessibility. (3) **Robustheit am Touchscreen** — Drag-Logik ist auf
  Mobilgeräten anfällig für Missklicks und Scroll-Konflikte. Nachbar-Swap ist schnell
  und zuverlässig.
- **Implementierung:** Reine Reducer-Funktionen `movePlayerUp`/`movePlayerDown` auf
  `List<SetupPlayer>` — defensiv, No-op bei ungültigem Index oder Grenzenüberschreitung
  (erste Zeile kann nicht nach oben, letzte nicht nach unten). Keine Exceptions.

### Spieler entfernen: ✕-Buttons mit Min-2-Schutz
- **UI-Komponente:** Je Zeile ein ✕-Button (`IconButton` mit `Icons.Default.Close`)
  zum Entfernen des Spielers aus dem Match. Ist auf `enabled = canRemove` gekoppelt.
- **Min-2-Schutz auf Reducer-Ebene:** Die reine Reducer-Funktion `removePlayerAt(index)`
  ist **defensiv**: Sinkt die Teilnehmerzahl durch das Entfernen unter `MIN_MATCH_PLAYERS` (= 2),
  bleibt die Liste unverändert (No-op). Keine Exception.
- **Min-2-Schutz auf UI-Ebene:** Die ✕-Buttons sind **deaktiviert** (`enabled = false`),
  wenn `participants.size == MIN_MATCH_PLAYERS` (genau 2 Teilnehmer). Zusätzlich wird
  ein Hinweis-Text unterhalb der Liste eingeblendet: „Mindestens 2 Teilnehmer erforderlich"
  (String `setup_players_min_hint`).
- **Match-Start-Schutz auf Setup-UI-Ebene:** Der „Match starten"-Button ist an
  `participants.size >= MIN_MATCH_PLAYERS` gekoppelt — liegt die Teilnehmerzahl unter
  dem Minimum, ist der Button disabled. Das ist die Verteidigungslinie auf der
  Setup-Screen-Ebene (mit `SetupScreenContent` als Einstiegspunkt).
- **Rationale:** Mehrschichtiger Schutz (Reducer + Button-Deaktivierung + Hinweis + Match-Button)
  verhindert versehentliche Leerzustände und ist benutzerfreundlich — der Nutzer sieht
  sofort warum er eine Aktion nicht durchführen kann.

### Neue `material-icons-core`-Dependency
- **Entscheidung:** Erstmals wird eine Icon-Dependency eingeführt. Vorher war die
  Konvention „bewusst KEINE Icon-Dependency" (siehe [ADR-0012](0012-ui-viewmodel-schicht.md)).
  Diese Entscheidung wird **aufgehoben** für Accessibility + Klarheit: ↑/↓/✕-Icons sind
  etabliert und selbsterklärend (statt Text „Hoch" / „Runter" / „Entfernen").
- **Dependency:** `androidx.compose.material:icons-core` (AndroidX Material3-Familie,
  enthalten in der Material-BOM, offline, kein Netzwerk/Tracking, garantierte
  Sicherheit + Wartung).
- **Einbindung:** In `app/build.gradle.kts` über die BOM; in `gradle/libs.versions.toml`
  wird die BOM-Version zentral geführt. App bleibt schlank — nur Icons, keine zusätzlichen
  Abhängigkeiten.
- **Update der ADR-0012:** Die Aussage „Bewusst KEINE Icon-Dependency" wird entsprechend
  angepasst (Entscheidung revidiert für bessere Accessibility + Klarheit).

### SetupViewModel + Namensauflösung
- **Neue Datei `ui/setup/SetupViewModel.kt`:** Das ViewModel wird nach dem
  `GameViewModel`-Muster gebaut (analog [ADR-0012](0012-ui-viewmodel-schicht.md)):
  - Constructor: `SetupViewModel(repository: PlayerRepository, playerIds: List<Long>)`
  - State: `participants: StateFlow<List<SetupPlayer>>` (reaktiv, die bearbeitete Liste)
  - Actions: `movePlayerUp(index)`, `movePlayerDown(index)`, `removePlayer(index)`
    (delegieren an die Reducer-Funktionen)
  - Output: `orderedPlayerIds(): List<Long>` (die final geordnete/reduzierte ID-Liste für
    den Match-Start)
  - Init-Block: Nennt `playerIds` zu `SetupPlayer`-Objekten auf via `PlayerRepository.getPlayer(id)`
    (unbekannte IDs fallen stillschweigend heraus) und initialisiert `participants` in der
    Eingangsreihenfolge.
  - Factory: `provideFactory(playerIds: List<Long>): ViewModelProvider.Factory` bezieht das
    `PlayerRepository` aus der `TomsDartsApp.container` (manuelles DI, wie in ADR-0012).
- **Rationale:** Entkopplung von der UI-Ebene (Compose), Reusability, Testbarkeit.
  Die Namensauflösung ist async, aber über `init { viewModelScope.launch { … } }` ist
  sie transparent für die UI (State wird reaktiv aktualisiert).

### Geteilte Konstante `MIN_MATCH_PLAYERS`
- **Entscheidung:** Die Konstante `MIN_MATCH_PLAYERS = 2` wird zentral in
  `ui/setup/SetupParticipants.kt` definiert (nicht inline, nicht in jedem Screen einzeln).
- **Nutzung:** `ProfileScreen` (Validierung der Auswahl, Spielmodus), `SetupScreen`
  (Min-Schutz für Button), Setup-Reducer (`removePlayerAt`).
- **Rationale:** Single Source of Truth. Wenn sich die Regel ändert (z. B. Min. 3 für neue
  Modi), ist eine Stelle zu ändern.

### Durchreichung der geordneten ID-Liste
- **Flow:** Profil-Auswahlmodus → `SetupScreen(playerIds)` → (Reihenfolge-Änderung im
  Setup) → `onConfirm(editedIds, ...)` → `MainActivity` aktualisiert State
  (`playerIds = editedIds.toLongArray()`) → `GameScreen` erhält die neue Reihenfolge →
  `GameViewModel.provideFactory(playerIds)` → `MatchEngine` rotet Starter nach der neuen
  Reihenfolge.
- **`MainActivity` Änderung:** Der `onConfirm`-Callback in `SetupScreen` erhält jetzt als
  **ersten Parameter** `editedIds: List<Long>` statt bisher implizit. Kommentar:
  „Die im Setup final sortierte/reduzierte Teilnehmerliste geht ins Match (Reihenfolge ist relevant)."
- **Kein Breaking Change:** Die Reihenfolge war immer bereits relevant (Engine-Detail);
  vorher kam sie aus dem Profil-Auswahlmodus. Jetzt ist sie editierbar — die Semantik
  ändert sich nicht, nur die Kontrolle.

## Konsequenzen
- **Teilnehmerverwaltung im Setup:** Der Setup-Screen wird von einer reinen
  Konfigurations-UI (Punkte/Regeln) zu einer echten **Match-Vorbereitung** erweitert
  (Spieler bearbeiten, Reihenfolge, Regeln).
- **Icon-Dependency aktiviert:** ADR-0012 wird angepasst; Icon-Einsatz ist jetzt bewusst
  genehmigt für bessere Accessibility (↑/↓/✕-Icons).
- **ViewModel-Pattern erprobt:** Das `SetupViewModel`-Pattern wird zu einer Vorlage für
  andere Setup-Screens (Falls später weitere Modi oder Konfigurationen kommen).
- **Min-2-Regel zentral:** `MIN_MATCH_PLAYERS` ist als Konstante verankert und validiert
  an mehreren Stellen (Reducer, UI, Button, Durchreichung).
- **Next Steps:** Phase 3 kann nun weitere Setup-Optionen atomaren Sections hinzufügen
  (neue Modi, Custom-Regeln) — das ViewModel-/Reducer-Pattern ist erprobt.
