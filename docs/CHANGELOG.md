# TomsDarts — Changelog

> Chronologisches Änderungslog des Baus. Hier stehen (1) die **ausführlichen
> Umsetzungsnotizen** je erledigtem Roadmap-Punkt (früher die `→`-Prosa in der
> CHECKLISTE) und (2) das kompakte **Änderungslog**. Die Roadmap
> ([ROADMAP.md](ROADMAP.md)) selbst hält je Punkt nur einen Einzeiler + Link.
> Bewusste Entscheidungen liegen in den [ADRs](decisions/README.md).

---

## Ausführliche Umsetzungsnotizen

### Setup

**Android-Gerüst committen + nach `main` mergen** — Verifiziert: Gerüst liegt via
Commit `db1f618` („chore: scaffold Kotlin/Compose Android project") auf `main`
(Commit ist Vorfahr von `main`). Working Tree sauber, keine untracked Dateien.
Zentrale Gradle-/App-Dateien getrackt (`gradlew`, `gradlew.bat`,
`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`,
`gradle/wrapper/gradle-wrapper.jar`, `gradle/libs.versions.toml`,
`app/src/main/AndroidManifest.xml`,
`app/src/main/java/com/mechanicel/tomsdarts/MainActivity.kt`). War bereits durch
frühere Commits erledigt — kein eigener Implementierungsschritt nötig, nur als
erledigt dokumentiert.

**Echte Gradle-Befehle in `CLAUDE.md` + Agents eintragen** — Verifiziert: Befehle
via Commit `f22cac7` eingetragen — vollständige `./gradlew`-Tabelle in `CLAUDE.md`
(`assembleDebug`, `installDebug`, `test`, `connectedAndroidTest`, `lint`, `build`)
und echte `./gradlew`-Befehle in `implementer`, `tester`, `fixer`, `reviewer`.
`designer` (read-only, plant nur) und `dokumentar` (git-basiert) brauchen keine
Gradle-Befehle. Keine Platzhalter mehr.

**Tech-Stack-Abschnitt in `CLAUDE.md` ergänzen** — Verifiziert: `CLAUDE.md` enthält
via Commit `f22cac7` den Abschnitt `## Tech-Stack` (Kotlin, Jetpack Compose,
Gradle Kotlin DSL, minSdk 26, `applicationId`). Der Hinweis „Tech-Stack noch nicht
eingerichtet / kein Scaffolding" existiert nirgends mehr in `CLAUDE.md`.

**Projekt-Checkliste ins Repo übernehmen** — Lag unter `docs/CHECKLISTE.md`; als
zentrale Steuerung in `CLAUDE.md` (Orchestrator-Loop) und im `dokumentar`-Agent
verankert. (Später abgelöst durch die Doku-Struktur-Aufteilung, siehe
[ADR-0016](decisions/0016-doku-struktur-aufteilung.md).)

### Phase 1 — Fundament

**Room einrichten (Dependencies, Database-Klasse, Schema-Export)** — Room `2.7.2`
(`room-runtime`, `room-ktx`, `room-compiler`) + KSP-Plugin `2.2.10-2.0.2` über den
Versionskatalog (`gradle/libs.versions.toml`) eingerichtet. Schema-Export aktiv
(`room.schemaLocation` → `app/schemas/...TomsDartsDatabase/1.json`, committet;
`app/schemas` als androidTest-Asset registriert). Minimale `@Database`-Klasse
`TomsDartsDatabase` (version 1, `exportSchema=true`) mit `Player` als Seed-Entity +
`PlayerDao`, damit die DB kompiliert/testbar ist. AGP-9-Workaround
`android.disallowKotlinSourceSets=false` in `gradle.properties` (AGP 9 hat
eingebautes Kotlin, KSP braucht das). Tests host-seitig unter Robolectric grün;
`test`, `lint`, `assembleDebug` alle BUILD SUCCESSFUL.

**Entities + DAOs: `Player`, `Match`, `Leg`, `Turn`, `Throw`** — `Player` +
`PlayerDao` waren bereits als Seed-Entity aus der Room-Einricht-Aufgabe vorhanden.
Neu ergänzt: Entities `Match` ("matches"), `Leg` ("legs"), `Turn` ("turns"),
`Throw` ("throws") sowie die Cross-Ref `MatchPlayer` ("match_players":
matchId/playerId/position) — letztere als bewusste Ergänzung, um die in der
Match-Config genannte „Spielerliste/Reihenfolge" relational sauber abzubilden.
Englische camelCase-Feldnamen konsistent zu `Player`. Je ein DAO im Stil von
`PlayerDao` (alle `suspend`): `MatchDao` (insert/getById/getAll/delete), `LegDao`
(insert/getById/getByMatch), `TurnDao` (insert/getById/getByLeg), `ThrowDao`
(insert/getById/getByTurn), `MatchPlayerDao` (insert/getById/getByMatch ORDER BY
position). Alle in `TomsDartsDatabase` eingebunden
(`@Database(entities=[Player, Match, Leg, Turn, MatchPlayer, Throw], version=1,
exportSchema=true)`); **version bleibt 1** (App unveröffentlicht, keine Migration),
Schema-JSON `app/schemas/.../1.json` neu erzeugt + committet. FK-/onDelete-Strategie:
CASCADE für die Hierarchie (Leg→Match, Turn→Leg, Throw→Turn, MatchPlayer→Match),
SET_NULL für `Match.winnerId`/`Leg.winnerId` (→Player), RESTRICT für `Turn.playerId`
und `MatchPlayer.playerId` (→Player, damit Spieler mit Spielhistorie nicht
versehentlich gelöscht werden). Indizes auf allen FK-Spalten. Tests host-seitig
(Robolectric, `./gradlew test`): Smoke-Test + 43 Edge-/Beziehungs-/FK-Constraint-
Tests, alle grün (CASCADE/SET_NULL/RESTRICT real verifiziert). `test`, `lint`,
`assembleDebug` BUILD SUCCESSFUL.

**Repository-Schicht über den DAOs** — `PlayerRepository` (`data/repository/`, ctor
`PlayerDao`): `observePlayers(): Flow<List<Player>>`, `getPlayer`, `addPlayer(name)`
(setzt `createdAt`), `updatePlayer`, `deletePlayer`. `MatchRepository`
(`data/repository/`, ctor MatchDao/LegDao/TurnDao/ThrowDao/MatchPlayerDao): dünne,
**regelfreie** Persistenz-Ops — `createMatch`, `addPlayerToMatch`, `addLeg`,
`addTurn`, `addThrow`; Reads `getMatches`/`getLegs`/`getTurns`/`getThrows`/
`getMatchPlayers`; `deleteMatch`. Keine Spielregeln/Score-Logik (kommt in Phase 2).
`PlayerDao` erweitert um `update`, `delete` und `observeAll(): Flow<List<Player>>`
(ORDER BY name) — Flow-basierte Beobachtung als reaktiver Standard für die spätere
UI. Manueller DI-Einstiegspunkt `AppContainer` (`data/AppContainer.kt`, ctor Context,
kein Hilt) baut DB-Singleton + `playerRepository`/`matchRepository` (`by lazy`).
DB-Singleton `TomsDartsDatabase.getInstance(context)` (thread-sicher, `@Volatile`
double-checked, file-basiert `tomsdarts.db`, `fallbackToDestructiveMigration()`).
Tests host-seitig (Robolectric, `./gradlew test`): PlayerRepository Smoke + 8
Edge-Cases, MatchRepository 9 (Reads/Isolation/CASCADE/Sortierung), AppContainer 1
Smoke — 18+ grün, kein Schema-Drift. `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL.

**Profilverwaltung: Spieler anlegen / auflisten / bearbeiten / löschen (UI +
Persistenz)** — Erste Compose-UI der App: `ui/profile/ProfileScreen.kt` (stateful +
stateless `ProfileScreenContent`, TopAppBar „Spieler", ExtendedFAB „Neuer Spieler",
Loading/Empty/Content/Error-Rendering, Liste auf `widthIn(max=600.dp)` zentriert, 6
Previews), `ui/profile/PlayerListItem.kt` (Material3 `ListItem` + `HorizontalDivider`,
Avatar-Initiale, „Erstellt am dd.MM.yyyy", Overflow-`DropdownMenu` Bearbeiten/Löschen,
Zeilen-Tap → Bearbeiten), `ui/profile/ProfileDialogs.kt` (`PlayerEditDialog` für
Add+Edit mit Autofokus/Trim/Validierung, `DeletePlayerDialog` mit Bestätigung).
State/Logik: `ui/profile/ProfileUiState.kt` (sealed `ProfileUiState`
Loading/Empty/Content/Error, sealed `ProfileDialog` None/Add/Edit/ConfirmDelete) +
`ui/profile/ProfileViewModel.kt` (`uiState`/`dialog` als `StateFlow`, CRUD über
`PlayerRepository` mit Namens-Trim + Ablehnung leerer Namen, `retry()`, companion
`Factory`). App-Plumbing: `TomsDartsApp : Application` (hält `AppContainer`, im
Manifest als `android:name=".TomsDartsApp"`), neue Compose-Lifecycle-Deps
(`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` an lifecycle 2.6.1) +
`kotlinx-coroutines-test`. Alle UI-Strings in `res/values/strings.xml` (deutsch).
`MainActivity` zeigt jetzt `ProfileScreen` (Greeting entfernt). **Bewusst keine
Icon-Dependency** — Text-Glyphen/Avatar-Initiale. 19 ViewModel-Tests host-seitig
(Robolectric, `./gradlew test`) grün; Test-Flakiness über gemeinsame
`testing/MainDispatcherRule` deterministisch behoben. `test`, `lint`, `assembleDebug`
BUILD SUCCESSFUL, kein Schema-Drift. **Verifikation offen:** Compose-UI wurde nur
kompiliert + via Previews + ViewModel-Logik getestet, **nicht** auf echtem
Gerät/Emulator (keiner in der Bau-Umgebung) — siehe Roadmap-Zeile „Auf echtem Gerät
(S25) testen". **Damit ist Phase 1 — Fundament vollständig abgeschlossen.**

### Phase 2 — Kern-Gameplay

**Strategie-Interface für Spielmodi definieren** — Pures Domänen-Paket
`com.mechanicel.tomsdarts.game` (KEINE Android-/Room-/Compose-Abhängigkeit → reines
JUnit, ohne Robolectric). Generisches Strategie-Interface `GameMode<S : Any>` mit
modus-spezifischem Spielerzustand `S`: `key` (stabile, persistierbare Modus-Kennung,
z.B. "X01"), `displayName`, `initialState(config): S`,
`applyDart(state, dart, config): DartOutcome<S>`. Value-Objekte:
`Dart(segment, multiplier)` (`value = segment*multiplier`, `isDouble`/`isTriple`,
`isValid` mit Board-Regeln: segment∈{0,1..20,25}, mult∈1..3, Miss nur Single, Bull
nur Single/Double = kein Triple-Bull; Factories
`single/double/triple/bull/doubleBull/miss`), `GameConfig` (`startScore=501`,
`doubleOut=true`, `legsToWin=1`, `setsToWin=1` — pures Domänen-Konfig, entkoppelt von
der `Match`-Entity; Modus-Identität über `GameMode.key`, kein `modeType`-Feld in
`GameConfig`), `DartOutcome<S>` (`newState`, `bust`, `legWon`, `scored`). **Vertrag
(KDoc):** `applyDart` verarbeitet GENAU EINEN Dart; die Strategie ist zustandslos
bzgl. Aufnahme-Grenzen — das **Bust-Zurücksetzen** (Verwerfen der Aufnahme-Darts)
obliegt der aufrufenden Engine; `bust` und `legWon` nie gleichzeitig (`bust` XOR
`legWon`). Tests rein JUnit: `DartTest` (14) + `GameModeContractTest` (14, mit
Count-Up- und X01-Fake nur als Testcode) — 28 grün. `test`, `lint`, `assembleDebug`
BUILD SUCCESSFUL, kein Schema-Drift.

**X01-Modus (501, Double-Out) implementieren: Aufrechnen, Bust-Logik,
Checkout-/Double-Out-Prüfung, Leg-Sieg** — Erste konkrete `GameMode`-Strategie:
`X01Mode : GameMode<X01State>` (`key="X01"`, `displayName="X01"`) im puren
Domänen-Paket `com.mechanicel.tomsdarts.game`, `X01State(remaining: Int)` als
Spielerzustand. `initialState(config) = X01State(config.startScore)`. Regeln in
`applyDart`: `newRemaining = remaining - dart.value`; `< 0` → Bust. Mit Double-Out
(`config.doubleOut == true`): `== 0 && dart.isDouble` → Leg gewonnen (Doppel-Bull
zählt als Double); `== 0` ohne Double → Bust (Finish nur per Double); `== 1` → Bust
(Rest 1 nicht per Double ausspielbar); sonst regulär. Ohne Double-Out: `== 0` mit
beliebigem Wurf → Leg gewonnen. Bust gibt den **unveränderten Eingangszustand**
zurück (`scored = 0`); Engine verwirft ohnehin. Invariante **`bust` XOR `legWon`**
eingehalten. **startScore-agnostisch:** 301/501/701 unterscheiden sich nur über
`GameConfig.startScore` → **kein separater Modus** für 301/701 nötig (deckt die
Phase-3-„Startpunkte"-Auswahl mit ab). Tests rein JUnit: `X01ModeTest` (9) +
`X01ModeEdgeCasesTest` (22) = **31 grün** — Aufrechnen/Sequenzen, Checkout-Varianten
(D20/D1/Doppel-Bull/High-Checkout 170), Bust-Varianten, `doubleOut=false`, 301/701,
Invariante `bust` XOR `legWon`, Eingabe-Robustheit (dokumentiert IST-Verhalten, siehe
Backlog). `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.

**Eingabe-Screen (Ziffernblock) in Compose bauen** — Ziffernblock als eigenständige
Komponente umgesetzt — **noch nicht** an die Spiel-Logik/Persistenz gekoppelt (das
ist die nächste Roadmap-Zeile). Zwei Bausteine im neuen Paket
`com.mechanicel.tomsdarts.ui.input`: (1) **Pure Eingabe-Logik** (Compose-/Android-frei,
rein JUnit-testbar): `DartModifier{SINGLE,DOUBLE,TRIPLE}` (+ `multiplier`-Extension
1/2/3) und der immutable State-Holder `DartInputState(modifier, darts)` (max. 3 Darts)
mit Properties `isComplete`/`turnSum`/`canUndo`/`bullEnabled`/`inputEnabled` und puren
Transitionen `toggleDouble`/`toggleTriple` (exklusiv, erneut→SINGLE), `pressNumber(n)`
(nur 1..20, Auto-Reset auf SINGLE), `pressBull` (No-op bei TRIPLE; DOUBLE→Doppel-Bull/50,
sonst 25; Auto-Reset), `pressOut` (Miss/0 + Auto-Reset), `undo` (Modifier unverändert),
`startNewTurn`. Nach drei Darts sind alle Eingabe-Transitionen No-ops; nur
`undo`/`startNewTurn` bleiben wirksam. Pure Label-Funktionen (`DartLabel.kt`):
`dartShortLabel`/`numberKeyLabel`/`bullKeyLabel` (Präfixe `D-`/`T-`,
„Out"/„Bull"/„D-Bull"; bewusst nicht in strings.xml, da nicht-lokalisierte Glyphen).
(2) **Compose-UI** (`DartKeypad.kt`): stateless `DartKeypadContent` + stateful
`DartKeypad` (`rememberSaveable`-Saver → Aufnahme übersteht Rotation), Callback-Bündel
`DartKeypadCallbacks` (inkl. `onDart`/`onTurnComplete` als Kopplungspunkte für die
nächste Aufgabe). Layout: oben `TurnSlots` (3 Slot-Kacheln + Aufnahme-Summe), darunter
5×5-Raster (Zahlen 1–20 als 4-Spalten-Block links, Sondertasten rechts:
Undo/Out/Bull/Double/Triple); Live-`D-`/`T-`-Präfixe auf den Zifferntasten,
Double/Triple als `selected`-Toggle, bei voller Aufnahme nur Undo aktiv, Bull bei
TRIPLE deaktiviert. Responsive (Portrait/Landscape via `BoxWithConstraints`,
`widthIn(max≈600dp)`), A11y-contentDescriptions, Material3-Farbrollen, **keine**
Icon-Dependency. Neue Strings im Block „Eingabe (Ziffernblock)" in
`res/values/strings.xml`; 6 Previews. Tests rein JUnit: `DartInputStateTest` (49) +
`DartLabelTest` (14) = **63 grün** (keine Compose-Instrumentationstests — kein
Emulator). `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.

**Eingabe an die Spiel-Logik koppeln; throw-level speichern** — Vollständige Kopplung
**Ziffernblock → Spiel-Logik → Persistenz** für ein **Einzelspieler-X01-Leg** (Zwei
Spieler / Legs / Sets folgen in der nächsten Zeile). Drei Bausteine: (1) **Pure Engine**
`game.engine.LegEngine<S>` (modus-agnostisch, treibt `GameMode<S>`/`X01Mode` Dart für
Dart): Aufnahme-Verwaltung (max. 3 Darts), **Bust-Revert** (State zurück auf den
Aufnahme-Startzustand), **Sofort-Checkout**, `applyDart → DartResult`, `startNewTurn`,
`snapshot`, **`undoLastDart()`** (In-Turn-Undo; No-op bei leerer/beendeter Aufnahme).
+ `DartResult` / `LegEngineSnapshot`. (2) **`GameViewModel`** (`ui/game/`): repliziert
die gehoisteten `DartInputState`-Transitionen und treibt damit die Engine; legt bei
Init Match (`modeType="X01"`, `GameConfig(501, doubleOut=true, legsToWin=1,
setsToWin=1)`) + `MatchPlayer` + erstes Leg an; **persistiert throw-level**: pro
Aufnahme-Ende ein `Turn`(turnIndex, bust, totalScored) + dessen `Throw`s (alle
geworfenen Darts inkl. Bust-Darts); bei Checkout `endedAt`/`winnerId` auf Leg + Match.
`onUndo`, `onNewLeg` (neues Leg im selben Match), Factory `provideFactory(playerId)`.
Exponiert `uiState: StateFlow<GameUiState>` (Loading/Error/NoPlayer/Playing/Won) +
`bustEvents: StateFlow<Int>` (transientes Bust-Signal). (3) **UI**
`ui/game/GameScreen.kt` (stateful + stateless, 6 Previews): Scoreboard (Restpunkte
groß, liveRegion), eingebetteter `DartKeypadContent`, transientes **Bust-Banner**
(errorContainer, liveRegion=Assertive), **Won-Panel** („Geschafft!" + „Neues
Leg"/„Zurück"), Loading/Error/NoPlayer; Strings im Block „Spiel" in `strings.xml`,
Material3, keine Icon-Dependency. **Repository-Erweiterung:** `MatchDao`/`LegDao` um
`@Update` + `MatchRepository.updateMatch`/`updateLeg` ergänzt (reine DAO-Methoden,
**kein Schema-Drift**) — nötig, weil `@Insert(REPLACE)` über `ON DELETE CASCADE` die
schon persistierten Kinder gelöscht hätte. **Einstieg:** `MainActivity` als einfacher
State-Switch Profil ⇄ Spiel (kein navigation-compose, `rememberSaveable`); im Profil
startet **Tap auf den Spieler-Body** das Spiel (Edit/Delete nur noch im Overflow-Menü),
`ProfileScreen`/`PlayerListItem` um `onPlayClick`/`onPlay` erweitert. Tests rein
JVM/Robolectric: `LegEngineTest` + `LegEngineEdgeCasesTest` (inkl. undo) sowie **15
GameViewModel-Tests** (`GameViewModelTest` 6 + `GameViewModelEdgeCasesTest` 9;
Persistenz über `getTurns`/`getThrows` belegt: Turns/Throws/dartIndex, Bust-Turn,
Checkout→endedAt/winnerId, onUndo, onNewLeg, NoPlayer, Toggle) — alle grün,
deterministisch. `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.

**Zwei Spieler, Aufnahme-Wechsel, Legs/Sets** — Der Einzelspieler-Spielfluss ist auf
**Mehrspieler mit Aufnahme-Wechsel und Legs/Sets** erweitert, getragen von der neuen
puren Engine `game.engine.MatchEngine<S>` (modus-agnostisch, Android-/Room-/Compose-frei,
rein JUnit-testbar; in 3 Commits auf diesem Branch entstanden: pure Engine + Happy-Path
+ Edge-Cases). Sie kapselt über der `LegEngine` den **Werfer-Wechsel** nach jeder
Aufnahme, das **Leg-/Set-/Match-Ende** (`legsToWin`/`setsToWin`, „Best of X") und die
**Starter-Rotation** je Leg; Schnittstellen `applyDart → MatchDartResult`,
`startNewTurn`, `snapshot` (`MatchSnapshot`), `undoLastDart`. (1) **`GameViewModel`**
treibt jetzt `MatchEngine<X01State>` für `playerIds: List<Long>` (Factory
`provideFactory(playerIds)`) mit fester Konfig `GameConfig(startScore=501,
doubleOut=true, legsToWin=2, setsToWin=1)` → **Best of 3 Legs, 1 Set**. Init legt Match
(`modeType="X01"`) + **alle** `MatchPlayer` (position = Eingabe-Index) + erstes Leg an;
persistiert **pro Werfer-Aufnahme** `Turn`(playerId = Werfer)+`Throw`s (inkl.
Bust-Darts); schließt Leg via `updateLeg(winnerId/endedAt)` und Match via
`updateMatch(winnerId/endedAt)`; `onNewLeg` legt das (engine-intern bereits rotierte)
nächste Leg an. (2) **`GameUiState`** um `PlayerScoreUi(playerId, name, remaining,
legsWon, setsWon, isCurrent)` und die Zustände `Playing`(players, startScore, input,
currentLegNumber, currentSetNumber, legsToWin, setsToWin) / `LegWon`(players,
legWinnerName, nextStarterName, nextLegNumber, dartsUsed) / `MatchWon`(players,
matchWinnerName, dartsUsed) erweitert; `NoPlayer` = < 2 gültige Spieler. (3) **UI:**
neue Datei `ui/game/MatchScoreboard.kt` (`MatchScoreboard` + `PlayerScoreCard`,
Portrait-Row/Landscape-Kompaktzeile via `BoxWithConstraints`, aktiver Spieler per
`▸`-Glyph + `primaryContainer` hervorgehoben, `liveRegion=Polite`); `GameScreen` um
`LegWonContent`/`MatchWonContent` erweitert; `GameScreen`/`MainActivity` reichen
`playerIds: List<Long>` durch (`rememberSaveable` LongArray). (4) **Profil-Auswahlmodus:**
separater `ProfileSelectionState(active, selectedIds)`-StateFlow + VM-Methoden
`enterSelection`/`enterSelectionMenu`/`toggleSelect`/`exitSelection`/`startMatch`
(validiert ≥ 2); `ProfileScreen` mit CAB (✕ + „N ausgewählt") und FAB „Match starten
(N)" (disabled < 2); `PlayerListItem` zweiter Render-Pfad (Tap = toggle, Avatar =
Reihenfolge-Nr. auf `primary`, kein Overflow, `toggleable`/`role=Checkbox`); Tap im
Normalmodus startet den Auswahlmodus und selektiert die Zeile (wie Long-Press). Neue
deutsche Strings (Block „Spiel"/„Profil") in `res/values/strings.xml`.
**Schlüssel-Entscheidungen:** kein transientes Per-Aufnahme-Wechsel-Banner (nur
Hervorhebung + `liveRegion=Polite`); Auswahlmodus per Long-Press **und**
TopAppBar-Aktion „Match"; Auswahl-Indikator = Reihenfolge-Nummer im Avatar (kein
Checkbox); eigener String `game_next_leg`; `dartsUsed` (Mehrspieler) = Darts des
Gewinners im Gewinn-Leg (Bust-Darts des Gewinners zählen mit) — per Test als
IST-Verhalten dokumentiert, finale Statistik-Definition bleibt Produktentscheidung;
volle Spiel-Konfig (Startpunkte/Double-Out/Legs/Sets-Anzahl) kommt erst mit dem
Phase-3-Setup-Screen, bis dahin feste Best-of-3-Legs-Konfig. Siehe
[ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md). Tests host-seitig
(Robolectric + In-Memory-Room + `MainDispatcherRule`): pure `MatchEngine` separat
gehärtet (`MatchEngineTest` + `MatchEngineEdgeCasesTest`), angepasste
`GameViewModelTest`/`GameViewModelEdgeCasesTest`, neue `GameViewModelHardeningTest`,
`ProfileViewModelSelectionTest`, `ProfileViewModelSelectionHardeningTest` — alle grün.
`test`, `lint`, `assembleDebug` BUILD SUCCESSFUL, **kein Schema-Drift** (Entities/DAOs
unverändert).

### Phase 3 — Spiel-Setup-Screen: Startpunkt-Auswahl (301/501/701)

**Spiel-Setup-Screen mit Startpunkt-Karten eingebaut** — Der **Spiel-Setup-Screen**
(`ui/setup/SetupScreen.kt`, stateful + stateless `SetupScreenContent` + `SetupScreenCallbacks`
analog `GameScreen`-Muster) wird **zwischen Profil-Auswahl und Spiel** eingeschoben:
Profil → Setup → Spiel (neuer `SCREEN_SETUP`-State in `MainActivity`, gesteuert über
`rememberSaveable`). **Startpunkt-Auswahl** via auswählbare **Karten** (Row, `weight(1f)`,
Tap-Selektion) aus zentraler List `START_SCORES = listOf(301, 501, 701)`, Default 501
(`DEFAULT_START_SCORE`), persisted über `rememberSaveable`. Radio-Semantik für TalkBack
(`selectableGroup` + `selectable(role=RadioButton)`); `contentDescription` je Karte;
Touch-Target ≥ 48 dp. Full-Width-Button in der `bottomBar` („Match starten",
`setup_start_match` String); `BackHandler` → `onCancel` (zurück zur Profil-Auswahl).

**Flow-Änderung:** `GameScreen(playerIds, startScore, onExit)`, `GameViewModel.provideFactory`
erhält `startScore` + setzt es in die `GameConfig` (übrige Konfig weiterhin hartkodiert).
`MainActivity` reicht `startScore` via State-Flow durch. Persistenz: Der `startScore` läuft
automatisch bis in den `Match` (Domäne validiert nicht gegen `START_SCORES` — das ist
UI-only-Validierung).

**Strings:** Neue Block „Setup-Screen" in `res/values/strings.xml` (`setup_title`
„Spiel einrichten", `setup_start_score_label` „Startpunkte", `setup_start_match`
„Match starten", `setup_start_score_cd` Format-String „%d Startpunkte" für TalkBack);
`game_back` wiederverwendet.

**Tests:** Suite grün — **314 Tests**. Neue Tests: `GameViewModelStartScoreWiringTest`
(variiert nur `startScore`, Rest fix; dokumentiert IST-Verhalten: `startScore` außerhalb
der drei Karten läuft ungeprüft durch — Validierung lebt nur in der Setup-UI),
`SetupScreenConstantsTest` (`START_SCORES`/Default). Logik-Tests bestehende
`GameViewModelTest` (Wiring 301/501/701 via Factory) ergänzt.

**Bewusste Designentscheidungen (festgehalten in ADR-0018):** (1) Auswahl-Komponente
= **auswählbare Karten** statt Material-3 SegmentedButton — prominenter/größere Flächen
für die Bedienung am Board; (2) Primäraktion = **Full-Width-Button in der bottomBar**
(nicht FAB) — setup-typischer Bestätigungsschritt, scroll-fest; (3) Startpunkt-Labels
= **direkt aus zentraler Werteliste** gerendert (kein String je Zahl) + Format-String
für TalkBack; (4) Screen als erweiterbare **Section-Liste** aufgebaut (spätere Optionen
Double-Out/Legs-Sets/Spieleranzahl/Modus kommen als weitere Sections), ermöglicht atomare
Erweiterbarkeit. Dynamischer Spieltitel je Score bewusst **out of scope**.

Damit ist **Phase 3, atomarer Task 1** vollständig umgesetzt — Setup-Flow etabliert,
startScore-Durchreichung getestet, UI-only-Validierung dokumentiert.

**Spiel-Setup-Screen: Double-Out an/aus (Phase 3, Task 2)** — Der Setup-Screen wird um
eine zweite Konfigurationssektion erweitert: **Double-Out-Schalter** (Auschecken mit
Doppel an/aus). Neue `DoubleOutSection` unter der `StartScoreSection` mit Abschnitts-Label
„Auschecken", Titel „Double-Out", Erklärtext „Das letzte Feld muss ein Doppel sein.",
Material-3 `Switch`. **Toggle-Semantik:** Ganze Zeile toggelbar (`Modifier.toggleable`,
`Role.Switch`, ≥48dp), `Switch` mit `onCheckedChange=null` (semantische Toggle-Node,
nur eine für TalkBack). Konstante `DEFAULT_DOUBLE_OUT = true`. Der Wert läuft durch:
`SetupScreen` (`rememberSaveable`) → `onConfirm(playerIds, startScore, doubleOut)` →
`MainActivity` → `GameScreen(..., doubleOut)` → `GameViewModel.provideFactory(playerIds,
startScore, doubleOut)` → `GameConfig.doubleOut` → persistierter `Match.doubleOut`
(vorher hartkodiert `true`). Neue Strings im Block „Setup-Screen (Auschecken / Double-Out)"
in `res/values/strings.xml`. Previews: zwei neue (`Double-Out an` / `Double-Out aus`),
bestehende Previews aktualisiert. **Tests:** `GameViewModelDoubleOutWiringTest` (Wert-Durchreichung
für beide Werte bis in `Match`, Kombinationen mit `startScore` aus `START_SCORES`,
plus end-to-end fachlicher Effekt: `doubleOut=false` → Single-Checkout gewinnt das Leg;
`doubleOut=true` → dieselbe Dart-Folge = Bust). `SetupScreenConstantsTest` um
`DEFAULT_DOUBLE_OUT` erweitert. Suite grün — **324 Tests** (172 neue, + bestehende,
davon 245 in `GameViewModelDoubleOutWiringTest`). Bestehende `GameViewModelTest` und
`GameViewModelStartScoreWiringTest` für `doubleOut`-Variationen ergänzt. **Bewusste Punkte:**
(1) Wortlaut „Auschecken" (Section-Label) vs. „Double-Out" (Titel) — etablierte Darts-
Begriffe; (2) Erklärtext für Laien; (3) `GameUiState.Playing` trägt bewusst kein
`doubleOut`-Feld — Observable nur über `Match`-Entity (architektonische Konsistenz,
da die Engine über das Match die Regeln konsultiert). Section-Pattern (erweiterbar wie
schon mit Startpunkte) bestätigt.

### Geräte-Test Phase 2 (S25)

**Auf echtem Gerät (Samsung S25) testen** — Verifiziert: Phase 2 (Kern-Gameplay) wurde
auf echtem Gerät (Samsung S25, Android 14) getestet. Spielerauswahl (Profil-Screen,
CAB + FAB „Match starten") und Punkt-/Dart-Eingabe (Ziffernblock) laufen stabil und
ohne Sichtungsprobleme. Drei Findings ermittelt:

1. **Letzte Aufnahme fehlende Anzeige (neues Feature):** `GameUiState.Playing` kennt
   nur die laufende Eingabe (`DartInputState.input`), aber keine Historie der zuletzt
   geworfenen Pfeile der letzten abgeschlossenen Aufnahme. Das erschwert es, schnell
   nachzuvollziehen, warum der aktuelle Rest übrig ist. Konkretes Beispiel: Rest 141,
   Werfer wirft T20/T20/20 → Rest 1 → Bust bei Double-Out. Ein Checkout-Vorschlag
   hätte gewarnt. Zwei separate, PR-große Arbeiten erforderlich: (a) Letzte Aufnahme
   im UI zeigen; (b) Checkout-Vorschlag generieren. **Einsortiert als Phase 3.5
   (X01-Feinschliff)**, zwei atomare Einträge in ROADMAP ergänzt.

2. **Rematch/Neues Match nach Match-Ende fehlt (bekanntes Backlog-Item):** Nach
   Match-Ende zeigt `MatchWonContent` nur „Zurück"-Aktion (onExit). Beim Wieder-
   Einstieg mit denselben Spielern wird das bereits beendete Match erneut geladen und
   sofort wieder „Match gewonnen" angezeigt statt einen Reset zu bieten. Bekannt vom
   Backlog (siehe [BACKLOG](BACKLOG.md)), eingeplant für Phase 3
   (Spiel-Setup-Screen, wo die Full-Konfig konfigurierbar wird). **Status: per
   Geräte-Test bestätigt.**

3. **Spieler mit Match-Historie nicht löschbar (bekannte ADR-Entscheidung):** Spieler,
   die an irgendeinem Match teilnahmen, können nicht gelöscht werden. `MatchPlayer.
   playerId` hat `onDelete = ForeignKey.RESTRICT` (Entscheidung ADR-0008). Beim Tap
   auf „Löschen" wird die Operation still in der Coroutine zurückgewiesen. Ist eine
   bewusste Designentscheidung (RESTRICT-Strategie statt CASCADE/SET_NULL), deren
   Produktkonsequenzen (Fehlermeldung für den Nutzer) erst klarer werden, wenn dieser
   Produktslot dran ist. **Status: per Geräte-Test bestätigt, Produktentscheidung
   ausstehend** (siehe [BACKLOG](BACKLOG.md) + [ADR-0008](decisions/0008-datenmodell-entscheidungen.md)).

Ableitung: Finding ① erzeugt zwei neue Roadmap-Einträge (Phase 3.5); Finding ② + ③
waren bereits erfasst und werden hiermit als geräte-getestet gekennzeichnet.

---

## Änderungslog (kompakt, chronologisch)

- _Erstanlage:_ Vision, Tech-Stack, Design-Entscheidungen (Eingabe + Delight),
  Setup-Status und Bau-Roadmap erfasst.
- _Workflow-Verankerung:_ `docs/CHECKLISTE.md` als zentrale Steuerung etabliert —
  Orchestrator-Loop in `CLAUDE.md` arbeitet die Liste top-down ab (genau eine
  Aufgabe pro Durchlauf, dann Stopp/„weiter"); `dokumentar`-Agent als Eigentümer
  der Datei festgelegt.
- _Setup-Aufgabe „Android-Gerüst committen/mergen" abgehakt:_ Verifiziert, dass
  das Gerüst via Commit `db1f618` bereits auf `main` liegt (Working Tree sauber,
  zentrale Gradle-/App-Dateien getrackt). Bereits durch frühere Commits erledigt,
  daher nur dokumentiert.
- _Setup-Aufgabe „Echte Gradle-Befehle eintragen" abgehakt:_ Verifiziert, dass die
  `./gradlew`-Befehle via Commit `f22cac7` in der `CLAUDE.md`-Tabelle und in den
  Agents `implementer`/`tester`/`fixer`/`reviewer` stehen; `designer`/`dokumentar`
  brauchen keine. Keine Platzhalter mehr — bereits durch früheren Commit erledigt,
  daher nur dokumentiert.
- _Setup-Aufgabe „Tech-Stack-Abschnitt in `CLAUDE.md`" abgehakt:_ Verifiziert, dass
  `CLAUDE.md` via Commit `f22cac7` den Abschnitt `## Tech-Stack` enthält und der
  Hinweis „Tech-Stack noch nicht eingerichtet / kein Scaffolding" entfernt ist.
  Bereits durch früheren Commit erledigt, daher nur dokumentiert. Damit sind **alle
  Setup-Aufgaben erledigt** — der Bau kann mit der Bau-Roadmap (Phase 1) fortfahren.
- _Phase 1 / „Room einrichten" abgehakt:_ Room `2.7.2` + KSP `2.2.10-2.0.2` über den
  Versionskatalog eingerichtet, Schema-Export aktiv (committetes Schema-JSON v1),
  minimale `@Database`-Klasse `TomsDartsDatabase`. `Player`-Entity + `PlayerDao`
  als Seed mit-implementiert, damit die DB kompiliert/testbar ist — die Roadmap-
  Zeile „Entities + DAOs" wurde entsprechend angepasst (`Player` erledigt, Rest
  offen). AGP-9-Workaround `android.disallowKotlinSourceSets=false` dokumentiert.
  Test-Strategie der Datenschicht als Design-Entscheidung festgehalten (Robolectric
  host-seitig, `@Config(sdk=[34])`, In-Memory-Room via `./gradlew test`, da kein
  Emulator). Backlog-Eintrag zu fehlenden `Player.name`-Constraints ergänzt.
  `test`, `lint`, `assembleDebug` grün.
- _Phase 1 / „Entities + DAOs" abgehakt:_ Entities `Match`, `Leg`, `Turn`, `Throw`
  + Cross-Ref `MatchPlayer` samt je einem DAO im `PlayerDao`-Stil ergänzt und in
  `TomsDartsDatabase` eingebunden (version bleibt 1, Schema-JSON v1 regeneriert +
  committet). FK-/onDelete-Strategie festgelegt und als Datenmodell-Entscheidung
  dokumentiert (CASCADE-Hierarchie, SET_NULL für winnerId, RESTRICT für playerId,
  Indizes auf allen FK-Spalten). Den Block „Vorgeschlagenes Datenmodell" auf
  „umgesetzt" umgestellt (englische camelCase-Feldnamen, `MatchPlayer` ergänzt) und
  neuen Abschnitt „Datenmodell-Entscheidungen" eingefügt (inkl. Schema-Versions-
  strategie: solange unveröffentlicht regenerieren statt migrieren). Zwei Backlog-
  Einträge aus den Tester-Hinweisen aufgenommen (`PlayerDao.delete` fehlt;
  keine CHECK-Constraints/Wert-Plausibilität auf DB-Ebene). 43+ Datenschicht-Tests
  grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL.
- _Phase 1 / „Repository-Schicht über den DAOs" abgehakt:_ `PlayerRepository` und
  `MatchRepository` (`data/repository/`) als dünne, regelfreie Wrapper über den DAOs
  ergänzt; `PlayerDao` um `update`/`delete`/`observeAll(): Flow` (ORDER BY name)
  erweitert; DB-Singleton `TomsDartsDatabase.getInstance` (thread-sicher,
  `fallbackToDestructiveMigration`) und manueller DI-Einstiegspunkt `AppContainer`
  (kein Hilt) hinzugefügt. Neuer Abschnitt „Repository-/DI-Schicht" unter
  Design-Entscheidungen (manuelles DI, DB-Singleton, regelfreie Repositories,
  Flow-Lesen). Backlog-Item „`PlayerDao` hat kein `delete()`" als erledigt markiert;
  zwei neue Backlog-Hinweise aufgenommen (`fallbackToDestructiveMigration` no-arg
  deprecated; `AppContainer`/DB-Singleton unter Robolectric eingeschränkt testbar).
  18+ Repository-/Container-Tests grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL.
- _Phase 1 / „Profilverwaltung" abgehakt — Phase 1 (Fundament) damit vollständig
  abgeschlossen:_ Erste Compose-UI der App umgesetzt (`ui/profile/`: `ProfileScreen`,
  `PlayerListItem`, `ProfileDialogs`, `ProfileUiState`, `ProfileViewModel`) mit voller
  Spieler-CRUD über `PlayerRepository` (anlegen/auflisten/bearbeiten/löschen). App-Plumbing
  `TomsDartsApp : Application` (hält `AppContainer`, im Manifest registriert), `MainActivity`
  zeigt jetzt `ProfileScreen` statt Greeting. Neuen Abschnitt „UI-/ViewModel-Schicht" unter
  Design-Entscheidungen ergänzt (manuelles DI bis in die UI via Application/`Factory`;
  sealed `ProfileUiState`/`ProfileDialog` + `StateFlow` + `collectAsStateWithLifecycle`;
  Stateless/Stateful-Trennung; bewusst keine Icon-Dependency; `ListItem`+`HorizontalDivider`;
  ViewModel-Test-Muster mit gekoppeltem Test-Scheduler `MainDispatcherRule`). Drei
  Backlog-Einträge aufgenommen (Undo-Snackbar nach Löschen [D]; Hinweis bei doppeltem Namen
  [E]; Profil-UI auf echtem Gerät / Compose-Instrumentationstests, da kein Emulator).
  19 ViewModel-Tests grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL, kein
  Schema-Drift. Verifikation der UI auf echtem Gerät (S25) bleibt offen (Backlog/Phase 2).
- _Phase 2 / „Strategie-Interface für Spielmodi definieren" abgehakt:_ Pures
  Domänen-Paket `com.mechanicel.tomsdarts.game` ohne Android-/Room-/Compose-Bezug
  angelegt: generisches Strategie-Interface `GameMode<S : Any>` (`key`, `displayName`,
  `initialState`, `applyDart`) + Value-Objekte `Dart`, `GameConfig`, `DartOutcome<S>`.
  Vertrag dokumentiert: `applyDart` verarbeitet genau einen Dart, Bust-Revert obliegt
  der Engine, `bust` XOR `legWon`. Neuen Abschnitt „Spielmodi-Domänenlogik" unter
  Design-Entscheidungen ergänzt (generisches `GameMode<S>`; pure Domäne entkoppelt von
  Room/UI; Bust-Revert in der Engine; Modus-Identität über `key`; `GameConfig`+`key`↔
  `Match`-Mapping in späterer Engine; bewusste Nicht-Aufnahme modusspezifischer
  Konfigfelder / YAGNI). Bestehende „Spielmodi"-Design-Entscheidung („gemeinsames
  Strategie-Interface") als jetzt umgesetzt markiert; im Tech-Stack die Zeile
  „austauschbare Strategie" entsprechend annotiert. Backlog-Eintrag „Engine-/
  Mapping-Schicht" als Orientierung für die X01-/Engine-Aufgaben aufgenommen.
  28 reine JUnit-Tests grün (`DartTest` 14, `GameModeContractTest` 14); `test`,
  `lint`, `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „X01-Modus (501, Double-Out) implementieren" abgehakt:_ Erste konkrete
  `GameMode`-Strategie `X01Mode : GameMode<X01State>` (`key="X01"`) + Zustands-Typ
  `X01State(remaining)` im puren Domänen-Paket `com.mechanicel.tomsdarts.game`.
  `applyDart`-Regeln: Aufrechnen (`remaining - dart.value`), Bust bei `< 0`; mit
  Double-Out Finish nur per Double (`== 0 && isDouble`, Doppel-Bull eingeschlossen),
  `== 0` ohne Double und `== 1` → Bust; ohne Double-Out gewinnt `== 0` mit beliebigem
  Wurf. Bust liefert unveränderten Eingangszustand (`scored = 0`), Invariante
  `bust` XOR `legWon`. **startScore-agnostisch** → 301/501/701 = ein Modus,
  verschiedene `GameConfig.startScore`. Design-Entscheidungen ergänzt (X01 als erste
  Strategie, startScore-agnostisch, `X01Mode` ohne Eingabe-Guards) und die
  „Spielmodi"-Entscheidung annotiert (Kern-Modus umgesetzt; 301/701 durch
  startScore-agnostischen `X01Mode` abgedeckt). Backlog-Eintrag „Engine validiert
  Darts/Config" mit den drei dokumentierten Robustheits-Lücken aufgenommen. 31 reine
  JUnit-Tests grün (`X01ModeTest` 9, `X01ModeEdgeCasesTest` 22); `test`, `lint`,
  `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „Eingabe-Screen (Ziffernblock) in Compose bauen" abgehakt:_ Ziffernblock
  als eigenständige Komponente im neuen Paket `com.mechanicel.tomsdarts.ui.input`
  umgesetzt — pure, Compose-/Android-freie Eingabe-Logik (`DartInputState` +
  `DartModifier` + Label-Funktionen, rein JUnit-testbar) getrennt von der Compose-UI
  (`DartKeypad`, stateless/stateful, `rememberSaveable`-Saver). 5×5-Layout (Zahlen
  1–20 + Sondertasten Undo/Out/Bull/Double/Triple), D-/T-Modifikator mit Auto-Reset,
  3 Slots + Aufnahme-Summe, Bull bei TRIPLE gesperrt, responsiv, keine Icon-Dependency.
  Unterabschnitt „Umsetzung" zur Design-Entscheidung „Eingabe (Ziffernblock)" ergänzt
  (Logik/UI-Trennung, Rotationsfestigkeit, bewusste Sperr-statt-Auto-Clear-Semantik
  via `startNewTurn()` durch den Aufrufer, angezeigte Aufnahme-Summe, Bull-Semantik).
  Zwei Backlog-Einträge aufgenommen (`PluralsCandidate`-Lint-Warnungen für „%d Punkte"-
  contentDescriptions; Ziffernblock-UI nicht on-device/instrumented verifiziert).
  **Bewusst NICHT** an Spiel-Logik/Persistenz gekoppelt — das ist die nächste Roadmap-
  Zeile „Eingabe an die Spiel-Logik koppeln; throw-level speichern". 63 reine JUnit-Tests
  grün (`DartInputStateTest` 49, `DartLabelTest` 14); `test`, `lint`, `assembleDebug`
  BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „Eingabe an die Spiel-Logik koppeln; throw-level speichern" abgehakt:_
  Vollständige Kopplung Ziffernblock → Spiel-Logik → Persistenz für ein
  **Einzelspieler-X01-Leg**. Neu: pure `game.engine.LegEngine<S>` (Aufnahme-Bündelung,
  Bust-Revert, Sofort-Checkout, `undoLastDart`, `snapshot`; + `DartResult`/
  `LegEngineSnapshot`), `GameViewModel` (`ui/game/`: koppelt `DartInputState` an die
  Engine, persistiert pro Aufnahme `Turn`+`Throw`s inkl. Bust-Darts, setzt bei Checkout
  `endedAt`/`winnerId`, `onUndo`/`onNewLeg`, `provideFactory`), `GameUiState`
  (Loading/Error/NoPlayer/Playing/Won) + transientes `bustEvents`, und `GameScreen`
  (Scoreboard, eingebetteter Keypad, Bust-Banner, Won-Panel, 6 Previews; Strings im
  Block „Spiel"). `MatchDao`/`LegDao` um `@Update` + `MatchRepository.updateMatch`/
  `updateLeg` ergänzt (CASCADE-Schutz statt REPLACE-Insert; kein Schema-Drift).
  Einstieg: `MainActivity` als State-Switch Profil ⇄ Spiel (kein navigation-compose);
  Profil-Tap auf den Spieler-Body startet das Spiel (Edit/Delete in den Overflow).
  Neuen Abschnitt „Spiel-Engine & Eingabe-Kopplung" unter Design-Entscheidungen ergänzt
  (Engine-getriebene Kopplung, throw-level pro Aufnahme inkl. Bust-Darts, `@Update` statt
  REPLACE, sealed `GameUiState`+`bustEvents`, State-Switch-Navigation, Profil-Tap-Einstieg,
  `Match.modeType="X01"`). Fünf Backlog-Einträge aufgenommen (dartsUsed-Semantik,
  fehlender Error-Retry-Hook, `onNewLeg` bei `legsToWin=1`, `Throw.dartIndex`-KDoc
  1..3 vs. 0..2, Game-Screen nicht on-device verifiziert). Tests rein JVM/Robolectric:
  `LegEngineTest`/`LegEngineEdgeCasesTest` + 15 GameViewModel-Tests grün; `test`, `lint`,
  `assembleDebug` BUILD SUCCESSFUL, kein Schema-Drift.
- _Phase 2 / „Zwei Spieler, Aufnahme-Wechsel, Legs/Sets" abgehakt:_ Mehrspieler-Match
  mit Aufnahme-Wechsel und Legs/Sets über die neue pure `MatchEngine<S>` umgesetzt
  (Werfer-Wechsel, Leg-/Set-/Match-Ende via `legsToWin`/`setsToWin`, Starter-Rotation);
  `GameViewModel` treibt `MatchEngine<X01State>` für `playerIds` (feste Konfig Best of 3
  Legs, 1 Set), Mehrspieler-Persistenz (`MatchPlayer` + Legs, throw-level pro
  Werfer-Aufnahme); `GameUiState` um `PlayerScoreUi`/`Playing`/`LegWon`/`MatchWon`
  erweitert; neue UI `MatchScoreboard`/`PlayerScoreCard`; Profil-Auswahlmodus als
  Spiel-Einstieg (CAB + FAB, ≥ 2 Spieler). Design-Entscheidung „Mehrspieler-Match /
  Legs/Sets" ergänzt. Alle Tests grün; `test`, `lint`, `assembleDebug` BUILD SUCCESSFUL,
  kein Schema-Drift.
- _Geräte-Test Phase 2 (S25) — Findings erfasst:_ App wurde auf echtem Gerät
  (Samsung S25) getestet. Spielerauswahl + Punkt-Eingabe sauber. Drei Findings:
  (1) Letzte Aufnahme/Checkout-Vorschlag fehlende UI-Ergänzungen → zwei neue
  Roadmap-Einträge Phase 3.5 (atomare PR-Größe je) hinzugefügt. (2) Rematch nach
  Match-Ende fehlt → bekanntes Backlog-Item, Status auf „geräte-getestet" gesetzt.
  (3) Spieler mit Match-Historie nicht löschbar (RESTRICT) → bekannte ADR-0008-
  Entscheidung, Status auf „geräte-getestet" gesetzt, Produktentscheidung ausstehend.
- _Doku-Struktur-Aufteilung:_ `docs/CHECKLISTE.md` (~957 Zeilen) nach Belang aufgeteilt:
  atomare [ROADMAP.md](ROADMAP.md), [BACKLOG.md](BACKLOG.md), dieses CHANGELOG und
  ein ADR je Design-Entscheidung unter [decisions/](decisions/README.md). CHECKLISTE
  wird zum Pointer-Stub. Roadmap-Punkte rückwirkend atomisiert; ausführliche
  Umsetzungsnotizen hierher verschoben. `CLAUDE.md` + `dokumentar`-Agent auf
  `docs/ROADMAP.md` als Taktgeber umgestellt. Kehrt den „CHECKLISTE-only"-Ansatz aus
  Commit `a19ee18` bewusst um — siehe
  [ADR-0016](decisions/0016-doku-struktur-aufteilung.md).
- _Phase 3 / „Spiel-Setup-Screen: Startpunkt-Auswahl (301/501/701)" abgehakt:_
  Spiel-Setup-Screen (`SetupScreen`, auswählbare Karten) eingeschoben zwischen
  Profil-Auswahl und Spiel (neuer `SCREEN_SETUP`-State). Startpunkt-Auswahl via
  zentrale List (301/501/701, Default 501); startScore läuft durchgängig in
  GameConfig + Match; UI-only-Validierung (Domäne validiert nicht); Screen als
  erweiterbare Section-Liste aufgebaut (spätere Optionen als neue Sections);
  Full-Width-Button in bottomBar, Radio-Semantik für TalkBack. 314 Tests grün,
  neue Wiring-Tests für startScore-Variationen. Designentscheidungen in
  [ADR-0018](decisions/0018-setup-screen-startpunkt.md) festgehalten. Test-Lücken
  (Setup-UI only @Preview, kein Gerät) ins BACKLOG eingefügt.
- _Phase 3 / „Spiel-Setup-Screen: Double-Out an/aus" abgehakt:_ Setup-Screen um
  `DoubleOutSection` (Switch-Toggle: an/aus) erweitert; Abschnitts-Label „Auschecken",
  Titel „Double-Out", Erklärtext für Laien; ganze Zeile toggelbar (`Role.Switch`,
  ≥48dp); Wert läuft durch `SetupScreen` → `MainActivity` → `GameScreen` →
  `GameViewModel.provideFactory` → `GameConfig` → `Match.doubleOut`. Default `true`.
  Neue Strings + zwei Previews (an/aus); bestehende Previews aktualisiert. 324 Tests
  grün (`GameViewModelDoubleOutWiringTest` + Konstanten-Test): Wert-Durchreichung für
  beide Werte, Kombinationen mit `START_SCORES`, plus fachlicher Effekt (Single-Checkout
  vs. Bust nach Regel). Bewusste Design: Section-Pattern bestätigt; `GameUiState.Playing`
  trägt kein `doubleOut` (Observable über `Match`). Siehe [CHANGELOG](CHANGELOG.md#spiel-setup-screen-double-out-anaus-phase-3-task-2) (ausführlich).
