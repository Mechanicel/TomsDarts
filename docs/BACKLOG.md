# TomsDarts — Backlog / spätere Ideen

> Zurückgestellte Ideen, offene Produktentscheidungen und bewusst nicht jetzt
> umgesetzte Punkte. Die aktive Bau-Reihenfolge steht in [ROADMAP.md](ROADMAP.md),
> bewusste Entscheidungen in den [ADRs](decisions/README.md).

- **Engine-/Mapping-Schicht für Spielmodi (Orientierung für X01- und Engine-Aufgaben):**
  Die spätere Spiel-Engine muss das pure Domänen-Konfig `GameConfig` + `GameMode.key`
  mit der Persistenz (`Match`-Entity inkl. `modusTyp`/`modeType`, `Turn`/`Throw`)
  zusammenführen — eine Mapping-Schicht (Domäne ↔ Room). Außerdem liegt die
  Aufnahme-Bündelung (3 Darts) und das **Bust-Revert** (Verwerfen der Aufnahme-Darts)
  in der Engine, nicht in der `GameMode`-Strategie (siehe
  [ADR-0013](decisions/0013-spielmodi-domaenenlogik.md)). Auch die in Phase 1
  zurückgestellte Wert-Plausibilität der Wurfdaten (siehe Backlog-Punkt „Keine
  Wert-Plausibilität auf DB-Ebene") kann hier über `Dart.isValid` greifen.
- **Engine validiert Darts/Config (`X01Mode` vertraut auf valide Eingaben):**
  `X01Mode` enthält bewusst keine Eingabe-Guards (Validierung = Sache der späteren
  Engine / Produktentscheidung). Konkret nicht abgesichert (per Test als IST-Verhalten
  dokumentiert): (1) `initialState` hat keinen Guard gegen `startScore <= 0`;
  (2) `applyDart` konsultiert `Dart.isValid` nicht — physikalisch unmögliche Würfe
  (z.B. Triple-Bull, Segment > 20) werden über `dart.value` verrechnet; (3) negativer
  `dart.value` erhöht den Rest. Die aufrufende Engine sollte Darts/Config validieren
  (z.B. über `Dart.isValid` + Config-Validierung), bevor sie an die Strategie gehen.
  Hängt mit dem Backlog-Punkt „Engine-/Mapping-Schicht für Spielmodi" zusammen.
- **`Player.name` ohne Constraints:** Aktuell wird weder Leer-Name noch
  Eindeutigkeit erzwungen (kein `@NonNull`-Check über Kotlin hinaus, kein
  Unique-Index). Ob doppelte/leere Spielernamen erlaubt sein sollen, ist eine
  spätere Produktentscheidung (ggf. Unique-Index + Validierung in der UI).
- **Spieler mit Match-Historie nicht löschbar (RESTRICT-FK):** Spieler können nicht
  gelöscht werden, solange sie an irgendeinem Match teilgenommen haben.
  `MatchPlayer.playerId` hat `onDelete = ForeignKey.RESTRICT` (siehe
  [ADR-0008](decisions/0008-datenmodell-entscheidungen.md)). `ProfileViewModel.deletePlayer`
  scheitert dadurch still in der Coroutine. Die Produktentscheidung (CASCADE vs. SET_NULL
  vs. RESTRICT + Fehlermeldung an den Nutzer) fällt erst, wenn dieser Slot im Roadmap
  dran ist. **per Geräte-Test (S25) erneut bestätigt; jetzt als ROADMAP-Aufgabe im Abschnitt
  ‚Bugfixes / Robustheit' geführt** (siehe [ROADMAP](ROADMAP.md#bugfixes--robustheit) und [CHANGELOG](CHANGELOG.md#geräte-test-phase-3-s25)).
- ~~**`PlayerDao` hat kein `delete()`:** Das Spieler-Löschen fehlt im DAO; SET_NULL/
  RESTRICT-Verhalten wurde in den Tests deshalb über direktes SQL ausgelöst.~~
  **(erledigt — Phase 1 / „Repository-Schicht")**: `PlayerDao` hat nun `delete`
  (und `update` + `observeAll`), `PlayerRepository.deletePlayer` reicht es durch.
  Hinweis: Die bestehenden FK-Constraint-Tests aus „Entities + DAOs" lösen das
  Lösch-Verhalten weiterhin über rohes SQL aus; eine Umstellung auf `PlayerDao.delete`
  kann bei der Profilverwaltung (Phase 1) nachgezogen werden.
- **`fallbackToDestructiveMigration()` (no-arg) ist deprecated:** In der genutzten
  Room-Version erzeugt der parameterlose Aufruf eine Deprecation-Warnung (kein
  Fehler). Später auf die parameterisierte Überladung umstellen.
- **`AppContainer`/DB-Singleton ist unter Robolectric nur eingeschränkt testbar:**
  Das file-basierte, prozessweite DB-Singleton führt bei mehreren Testmethoden zu
  „Illegal connection pointer". Für breitere Integrationstests später eine
  injizierbare/zurücksetzbare DB-Instanz vorsehen (Produktionscode-Änderung, kein
  Tester-Thema).
- **Keine Wert-Plausibilität auf DB-Ebene (keine CHECK-Constraints):** Die DB erzwingt
  keine fachliche Gültigkeit der Wurfdaten — z. B. `multiplier` außerhalb 1–3, `segment`
  außerhalb {0, 1–20, 25}, `value ≠ segment * multiplier` oder negative Scores werden
  **nicht** verhindert. Diese Plausibilität soll später in der Spiel-/Score-Logik
  (Phase 2) geprüft werden.
- **Undo-Snackbar nach Löschen (zurückgestellt):** Beim Löschen eines Spielers war eine
  Undo-Snackbar als Komfort-Funktion angedacht (Design-Entscheidung D), wurde aber bewusst
  auf den Backlog geschoben. Aktuell ist Löschen direkt + Bestätigungsdialog, kein Undo.
- **Hinweis/Behandlung doppelter Spielernamen (zurückgestellt):** Beim Anlegen/Bearbeiten
  wird derzeit kein Hinweis bei doppeltem Namen gezeigt (Design-Entscheidung E, zurückgestellt).
  Hängt mit dem bestehenden Backlog-Punkt „`Player.name` ohne Constraints" zusammen.
- **`PluralsCandidate`-Lint-Warnungen am Ziffernblock:** 4 Warnungen für
  contentDescription-Strings mit „%d Punkte" (Singular/Plural grammatikalisch
  sauberer über `<plurals>`). Rein kosmetisch, Build/Lint bleiben grün — bei
  Gelegenheit auf `<plurals>` umstellen.
- **Ziffernblock-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI des
  Eingabe-Ziffernblocks (`DartKeypad`) wurde nur kompiliert + über `@Preview`
  geprüft und die Logik (`DartInputState`/Labels) per JUnit getestet — keine
  Instrumentationstests (kein Emulator/Gerät in der Bau-Umgebung). Offen: Keypad
  auf dem echten Gerät (S25) sichten und/oder Compose-UI-Instrumentationstests
  (`connectedAndroidTest`) ergänzen — deckt sich mit der Roadmap-Zeile „Auf echtem
  Gerät (S25) testen".
- **Profil-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI der Profilverwaltung
  wurde nur kompiliert + über `@Preview` und ViewModel-Logik getestet — es gibt keinen
  Emulator/kein Gerät in der Bau-Umgebung, daher keine Instrumentationstests. Offen:
  Profil-UI auf dem echten Gerät (S25) sichten und/oder Compose-UI-Instrumentationstests
  (`connectedAndroidTest`) ergänzen, sobald ein Gerät/Emulator bereitsteht (deckt sich mit
  der Roadmap-Zeile „Auf echtem Gerät (S25) testen").
- **`dartsUsed` schließt Bust-Darts ein (IST):** Pro Aufnahme werden alle real
  geworfenen Darts persistiert — auch die einer Bust-Aufnahme. Ob für Statistik/Average
  „nur gewertete Darts" zählen sollen, ist eine **Produkt-Entscheidung** (ggf. eigene
  Kennzahl/Filter statt Änderung der Roh-Persistenz).
- **`GameUiState.Error` ohne Retry-Hook:** Das `GameViewModel` bietet aktuell keinen
  echten `retry()`; „Erneut versuchen" führt zurück zur Profilliste. Ein echter Retry
  (Init erneut versuchen, ohne den Screen zu verlassen) wäre später nachzuziehen —
  vgl. das `retryTrigger`-Muster im `ProfileViewModel`.
- ~~**`onNewLeg` bei `legsToWin = 1`:** „Neues Leg" legt ein weiteres Leg im bereits
  abgeschlossenen Match an (öffnet das Match nicht wieder). Für einen echten Match-Flow
  (mehrere Legs/Sets, „Best of X") muss das mit der Roadmap-Zeile „Zwei Spieler,
  Aufnahme-Wechsel, Legs/Sets" geschärft werden.~~
  **(überholt — Phase 2 / „Zwei Spieler, Aufnahme-Wechsel, Legs/Sets")**: Der echte
  Match-Flow ist umgesetzt — `MatchEngine` treibt Legs/Sets über `legsToWin`/`setsToWin`
  („Best of X"), `onNewLeg` legt das engine-intern rotierte nächste Leg an, Leg-/Match-Ende
  werden korrekt erkannt (`LegWon`/`MatchWon`).
- ~~**Rematch / „Neues Match" nach Match-Ende:**~~ **(erledigt — Phase 3 / Setup-Flow)**:
  Nach Match-Ende kann man über „Zurück" zur Profil-Auswahl zurückkehren, erneut Spieler
  selektieren und durch den neuen Setup-Screen (Profil → Setup → frisches Match) ein neues
  Match starten. **per Geräte-Test (S25) bestätigt** (siehe [CHANGELOG](CHANGELOG.md#geräte-test-phase-3-s25)).
- **Game-Screen nicht auf echtem Gerät verifiziert:** Der Spiel-Bildschirm
  (`GameScreen`) wurde nur kompiliert + über `@Preview` (6) und die VM-/Engine-Logik
  per JUnit/Robolectric getestet — keine Instrumentationstests (kein Emulator/Gerät in
  der Bau-Umgebung). On-Device-Sichtung (S25) steht aus — deckt sich mit der
  Roadmap-Zeile „Auf echtem Gerät (S25) testen".
- **`dartsUsed`-Mehrspieler-Semantik (offene Produktentscheidung):** Im Mehrspieler-Flow
  zählt `dartsUsed` (in `LegWon`/`MatchWon`) die Darts **des Gewinners im Gewinn-Leg**
  (per-Spieler-Zähler, **inkl. Bust-Darts des Gewinners**) — per Test als IST-Verhalten
  dokumentiert. Ob die finale Statistik/Average „nur gewertete Darts" bzw. eine andere
  Bezugsgröße nutzen soll, ist eine **Produktentscheidung** (vgl. den verwandten Backlog-
  Punkt „`dartsUsed` schließt Bust-Darts ein").
- **Set-Übergänge (`setsToWin > 1`) im App-Flow nicht erreichbar:** Die `MatchEngine`
  unterstützt Sets, aber die App läuft mit fester Konfig `setsToWin=1` (Best of 3 Legs,
  1 Set). Echte Set-Übergänge sind über die UI erst erreichbar, sobald der
  **Phase-3-Setup-Screen** die Legs-/Sets-Anzahl konfigurierbar macht. Engine-seitig sind
  Set-Übergänge per `MatchEngine`-Tests abgedeckt.
- **`GameUiState.Error` ohne fehlerinjizierendes Repo nicht testbar:** Der `Error`-Zweig
  des `GameViewModel` lässt sich mit dem realen (In-Memory-)`MatchRepository` nicht gezielt
  auslösen; für eine Abdeckung bräuchte es ein fehlerinjizierendes Fake-/Stub-Repository
  (zusammen mit dem fehlenden echten Retry-Hook, siehe „`GameUiState.Error` ohne
  Retry-Hook").
- **Mehrspieler-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI des Mehrspieler-
  Spiels (`GameScreen`, `MatchScoreboard`/`PlayerScoreCard`, `LegWon`/`MatchWon`-Panels)
  und der **Profil-Auswahlmodus** (CAB, FAB „Match starten", Auswahl-`PlayerListItem`)
  wurden nur kompiliert + über `@Preview` und VM-/Engine-Logik (JUnit/Robolectric)
  getestet — keine Instrumentationstests (kein Emulator/Gerät in der Bau-Umgebung).
  On-Device-Sichtung (S25) steht aus — deckt sich mit der Roadmap-Zeile „Auf echtem
  Gerät (S25) testen".
- **Setup-Screen-UI nicht auf echtem Gerät verifiziert:** Die Compose-UI des
  Setup-Screens (`SetupScreen`, auswählbare Startpunkt-Karten + Double-Out-Switch,
  Tap-Selektion / Toggle-Interaktion, Selected-State, `BackHandler`,
  `rememberSaveable`-Persistence) wurde nur kompiliert + via `@Preview` getestet;
  die Logik (Konstanten `START_SCORES`/`DEFAULT_DOUBLE_OUT`, Wiring über Factory
  für beide Werte) per JUnit/Robolectric (`GameViewModelDoubleOutWiringTest` +
  `SetupScreenConstantsTest`); keine Instrumentationstests (kein Gerät/Emulator in
  der Bau-Umgebung). Konkret: Double-Out-Switch/Toggle-Row-UI nur kompiliert +
  Previews (an/aus) + Wiring-Tests, keine UI-Interaktionstests (Tap, Toggle,
  State-Übergänge). On-Device-Sichtung (S25) steht aus — deckt sich mit der
  Roadmap-Zeile „Auf echtem Gerät (S25) testen". Einsortiert als Test-Lücke zu
  Phase 3 Validation.
