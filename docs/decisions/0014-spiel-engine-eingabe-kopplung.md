# 0014 — Spiel-Engine & Eingabe-Kopplung

**Status:** Akzeptiert

**Update (2026-07-23):** Der ViewModel-seitige Turn-Abschluss wurde um eine Kontrollpause erweitert ([ADR-0026](0026-turn-review-kontrollpause.md)), die den Post-Wechsel-Snapshot verzögert; die Engine bleibt unverändert.

## Kontext
Festgelegt in Phase 2 / „Eingabe an die Spiel-Logik koppeln". Der Ziffernblock
([ADR-0003](0003-eingabe-ziffernblock.md)) und die Modus-Strategie
([ADR-0013](0013-spielmodi-domaenenlogik.md)) werden zu einem spielbaren
Einzelspieler-X01-Leg mit throw-level-Persistenz verbunden.

## Entscheidung
- **Pure, modus-agnostische Engine `LegEngine<S>`** (`com.mechanicel.tomsdarts.game.engine`,
  weiterhin Android-/Room-/Compose-frei, rein JUnit-testbar): Sie verantwortet genau das,
  was die `GameMode`-Strategie bewusst NICHT tut — die **Aufnahme-Bündelung** (max. 3
  Darts), das **Bust-Revert** (State zurück auf den Aufnahme-Startzustand, Verwerfen der
  Aufnahme-Darts) und den **Sofort-Checkout** (Aufnahme endet beim Leg-Sieg vor dem
  dritten Dart). Schnittstellen: `applyDart → DartResult`, `startNewTurn`, `snapshot`
  (`LegEngineSnapshot`) und `undoLastDart()` (In-Turn-Undo; No-op bei leerer oder bereits
  beendeter Aufnahme).
- **Engine-getriebene Kopplung im `GameViewModel`** (`ui/game/`): Das ViewModel hält den
  gehoisteten `DartInputState` (UI-Eingabe) **und** die `LegEngine` und hält beide
  synchron — es repliziert die Keypad-Transition und treibt die Engine Dart für Dart.
  Eingabe-Logik (Anzeige/Modifikator) bleibt damit von der Score-Logik (Engine) getrennt.
- **Throw-level-Persistenz pro Aufnahme:** Erst beim Aufnahme-Ende wird ein
  `Turn`(turnIndex, bust, totalScored) + alle zugehörigen `Throw`s geschrieben.
  **Bust-Darts werden mitgespeichert** (alle real geworfenen Darts der Aufnahme) —
  konsistent zu [ADR-0004](0004-datenhaltung-throw-level.md). (Semantik von
  `dartsUsed` / „nur gewertete Darts" ist offen, siehe Backlog.)
  
  > **Update (ADR-0021):** Die Einschränkung auf In-Turn-Undo (Undo nur innerhalb der
  > laufenden Aufnahme) wurde durch [ADR-0021](0021-undo-cross-turn-replay.md) abgelöst —
  > Undo funktioniert jetzt über Aufnahmen und Spielerwechsel hinweg (Replay-Ansatz,
  > Leg-Grenze bleibt Undo-Grenze).
- **`@Update` statt `@Insert(REPLACE)` für Match-/Leg-Abschluss:** Das Setzen von
  `endedAt`/`winnerId` läuft über neue `@Update`-DAO-Methoden
  (`MatchRepository.updateMatch`/`updateLeg`), **nicht** über ein REPLACE-Insert — sonst
  hätte `ON DELETE CASCADE` die bereits persistierten Kind-Datensätze (Legs/Turns/Throws)
  beim Ersetzen mitgelöscht. Reine DAO-Erweiterung, **kein Schema-Drift**.
- **`GameUiState` als sealed State** (Loading/Error/NoPlayer/Playing/Won) plus ein
  separater transienter **`bustEvents: StateFlow<Int>`** (zählender Tick) für das einmalige
  Bust-Banner — konsistent zum sealed-UI-State-Muster der Profilverwaltung
  ([ADR-0012](0012-ui-viewmodel-schicht.md)).
- **Navigation vorerst als MainActivity-State-Switch:** Profil ⇄ Spiel über
  `rememberSaveable`-State (`SCREEN_PROFILE`/`SCREEN_GAME`), **bewusst ohne
  navigation-compose** — minimal gehalten, solange es nur zwei Screens gibt. Ein echter
  Navigationsgraph kann später nachgezogen werden (z.B. mit Spiel-Setup-Screen in Phase 3).
- **Spiel-Einstieg über Profil-Tap:** Tap auf den Spieler-Body in der Profilliste startet
  ein Spiel für diesen Spieler (`onPlayClick`/`onPlay`); Bearbeiten/Löschen wandern in das
  Overflow-Menü (vorher war der Zeilen-Tap = Bearbeiten). *(Später abgelöst durch den
  Auswahlmodus, siehe [ADR-0015](0015-mehrspieler-match-legs-sets.md).)*
- **`Match.modeType = "X01"`:** Die Persistenz-Modus-Kennung der `Match`-Entity wird mit
  `GameMode.key` (`"X01"`) befüllt — das in [ADR-0013](0013-spielmodi-domaenenlogik.md)
  beschriebene Mapping `GameConfig`+`key` ↔ `Match` ist damit erstmals konkret umgesetzt.

## Konsequenzen
- Klare Verantwortungsteilung: Strategie (Regeln) ↔ Engine (Aufnahme/Bust/Checkout) ↔
  ViewModel (Kopplung/Persistenz).
- Kein Schema-Drift trotz Match-/Leg-Abschluss.
- Erweiterung auf Mehrspieler/Legs/Sets folgt in
  [ADR-0015](0015-mehrspieler-match-legs-sets.md).
