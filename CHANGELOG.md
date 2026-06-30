# Changelog

Alle nennenswerten Änderungen an TomsDarts werden hier festgehalten.
Format angelehnt an Conventional Commits; neueste Einträge oben.

## [Unreleased]

### Phase 2 — Eingabe an die Spiel-Logik koppeln; throw-level speichern

- **feat(engine):** pure `game.engine.LegEngine<S>` für ein Einzelspieler-Leg —
  modus-agnostisch (treibt `GameMode<S>`/`X01Mode` Dart für Dart), verantwortet
  Aufnahme-Bündelung (max. 3 Darts), Bust-Revert (State zurück auf Aufnahme-Start)
  und Sofort-Checkout; `applyDart → DartResult`, `startNewTurn`, `snapshot`
  (`LegEngineSnapshot`) und `undoLastDart()` (In-Turn-Undo, No-op bei leerer/beendeter
  Aufnahme). Weiterhin Android-/Room-/Compose-frei.
- **feat(game):** `GameViewModel` (`ui/game/`) koppelt die Ziffernblock-Eingabe
  (`DartInputState`) an die Engine und **persistiert throw-level** — pro Aufnahme ein
  `Turn`(turnIndex, bust, totalScored) + alle `Throw`s (inkl. Bust-Darts). Legt bei Init
  Match (`modeType="X01"`, `GameConfig(501, doubleOut=true, legsToWin=1, setsToWin=1)`)
  + `MatchPlayer` + erstes Leg an; setzt bei Checkout `endedAt`/`winnerId` auf Leg+Match.
  `onUndo`, `onNewLeg`, Factory `provideFactory(playerId)`. Exponiert
  `uiState: StateFlow<GameUiState>` (Loading/Error/NoPlayer/Playing/Won) +
  `bustEvents: StateFlow<Int>` (transientes Bust-Signal).
- **feat(ui):** `GameScreen` (stateful + stateless, 6 Previews) — Scoreboard
  (Restpunkte groß, liveRegion), eingebetteter `DartKeypadContent`, transientes
  Bust-Banner (errorContainer, liveRegion=Assertive), Won-Panel („Geschafft!" +
  „Neues Leg"/„Zurück"), Loading/Error/NoPlayer. Neue Strings im Block „Spiel";
  Material3, keine Icon-Dependency.
- **feat(data):** `MatchDao`/`LegDao` um `@Update` ergänzt + `MatchRepository.updateMatch`/
  `updateLeg`. Notwendig, weil `@Insert(REPLACE)` über `ON DELETE CASCADE` die bereits
  persistierten Kind-Datensätze gelöscht hätte. Reine DAO-Erweiterung, **kein Schema-Drift**.
- **feat(ui):** Spiel-Einstieg verdrahtet — `MainActivity` als einfacher State-Switch
  Profil ⇄ Spiel (kein navigation-compose, `rememberSaveable`); Tap auf den Spieler-Body
  in der Profilliste startet das Spiel (`onPlayClick`/`onPlay`), Bearbeiten/Löschen nur
  noch im Overflow-Menü.
- **test:** `LegEngineTest` + `LegEngineEdgeCasesTest` (inkl. undo) und 15
  GameViewModel-Tests (`GameViewModelTest` 6 + `GameViewModelEdgeCasesTest` 9) —
  Persistenz über `getTurns`/`getThrows` belegt (Turns/Throws/dartIndex, Bust-Turn,
  Checkout→endedAt/winnerId, onUndo, onNewLeg, NoPlayer, Toggle). Rein JVM/Robolectric,
  alle grün und deterministisch.

> Umfang: ein **Einzelspieler-X01-Leg**. Zwei Spieler, Aufnahme-Wechsel und Legs/Sets
> folgen als nächste Aufgabe.
