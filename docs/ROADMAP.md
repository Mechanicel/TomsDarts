# TomsDarts — Roadmap (Bau-Checkliste)

> Diese Datei ist der **Taktgeber** für den Bau von TomsDarts. Der Orchestrator
> arbeitet sie strikt von oben nach unten ab — genau eine offene (`[ ]`) Aufgabe
> pro Durchlauf, danach Stopp und Warten auf Toms „weiter" (siehe Orchestrator-Loop
> in `../CLAUDE.md`).
>
> **Atomaritäts-Konvention:** Ein Task = eine PR-große, unabhängig mergebare
> Änderung. Je Zeile **max. ein Einzeiler + ein Link** — keine mehrzeilige Prosa.
> Ausführliche Umsetzungsnotizen stehen im [CHANGELOG](CHANGELOG.md), bewusste
> Entscheidungen in den [ADRs](decisions/README.md), zurückgestellte Ideen im
> [BACKLOG](BACKLOG.md).

---

## Status: Setup

- [x] GitHub-Repo angelegt (`Mechanicel/TomsDarts`)
- [x] Android Studio installiert (Standard-Setup)
- [x] Kotlin/Compose-Gerüst per Wizard erzeugt (Empty Activity / Compose, Kotlin DSL)
- [x] Gerüst ins geklonte Repo `Tom_Darts` migriert; `.gitignore` zusammengeführt
- [x] Gradle-Sync + `Make Project` erfolgreich (BUILD SUCCESSFUL)
- [x] App läuft auf echtem Gerät (Samsung S25 / SM-S931B, Wireless Debugging)
- [x] Agent-Setup migriert und auf Android angepasst (Orchestrator-Loop + 6 Rollen)
- [x] Android-Gerüst committen + nach `main` mergen → [CHANGELOG](CHANGELOG.md#setup)
- [x] Echte Gradle-Befehle in `CLAUDE.md` + Agents eintragen → [CHANGELOG](CHANGELOG.md#setup)
- [x] Tech-Stack-Abschnitt in `CLAUDE.md` ergänzen → [CHANGELOG](CHANGELOG.md#setup)
- [x] Projekt-Checkliste ins Repo übernehmen → [CHANGELOG](CHANGELOG.md#setup)

---

## Phase 1 — Fundament ✅ vollständig abgeschlossen

- [x] Room-Dependencies + KSP über Versionskatalog einrichten → [ADR-0009](decisions/0009-persistenz-tech.md)
- [x] Schema-Export aktivieren + Schema-JSON v1 committen → [ADR-0009](decisions/0009-persistenz-tech.md)
- [x] `@Database`-Klasse `TomsDartsDatabase` (version 1) anlegen → [ADR-0008](decisions/0008-datenmodell-entscheidungen.md)
- [x] Entities `Match`/`Leg`/`Turn`/`Throw` → [ADR-0007](decisions/0007-datenmodell-room.md)
- [x] Cross-Ref-Entity `MatchPlayer` (matchId/playerId/position) → [ADR-0008](decisions/0008-datenmodell-entscheidungen.md)
- [x] DAOs im `PlayerDao`-Stil (Match/Leg/Turn/Throw/MatchPlayer) → [ADR-0007](decisions/0007-datenmodell-room.md)
- [x] FK-/onDelete-Strategie (CASCADE/SET_NULL/RESTRICT) + Indizes → [ADR-0008](decisions/0008-datenmodell-entscheidungen.md)
- [x] `PlayerRepository` + `MatchRepository` (regelfreie DAO-Wrapper) → [ADR-0011](decisions/0011-repository-di-schicht.md)
- [x] `PlayerDao` um `update`/`delete`/`observeAll(): Flow` erweitern → [ADR-0011](decisions/0011-repository-di-schicht.md)
- [x] DB-Singleton + manuelles DI `AppContainer` (kein Hilt) → [ADR-0011](decisions/0011-repository-di-schicht.md)
- [x] Datenschicht-Tests host-seitig (Robolectric + In-Memory-Room) → [ADR-0010](decisions/0010-test-strategie-datenschicht.md)
- [x] `ProfileViewModel` + sealed `ProfileUiState`/`ProfileDialog` → [ADR-0012](decisions/0012-ui-viewmodel-schicht.md)
- [x] `ProfileScreen`/`PlayerListItem`/`ProfileDialogs` (Spieler-CRUD-UI) → [ADR-0012](decisions/0012-ui-viewmodel-schicht.md)
- [x] App-Plumbing `TomsDartsApp` + `MainActivity` zeigt Profil-Screen → [ADR-0012](decisions/0012-ui-viewmodel-schicht.md)

---

## Phase 2 — Kern-Gameplay (erste spielbare Scheibe)

- [x] Strategie-Interface `GameMode<S>` + Value-Objekte (`Dart`/`GameConfig`/`DartOutcome`) → [ADR-0013](decisions/0013-spielmodi-domaenenlogik.md)
- [x] `X01Mode` (startScore-agnostisch, Double-Out, Bust/Checkout, Leg-Sieg) → [ADR-0013](decisions/0013-spielmodi-domaenenlogik.md)
- [x] Pure Eingabe-Logik `DartInputState` + `DartModifier` + Label-Funktionen → [ADR-0003](decisions/0003-eingabe-ziffernblock.md)
- [x] Compose-`DartKeypad` (stateless/stateful, `rememberSaveable`-Saver) → [ADR-0003](decisions/0003-eingabe-ziffernblock.md)
- [x] Pure `LegEngine<S>` (Aufnahme-Bündelung, Bust-Revert, Sofort-Checkout, `undoLastDart`) → [ADR-0014](decisions/0014-spiel-engine-eingabe-kopplung.md)
- [x] `GameViewModel` koppelt Keypad ↔ Engine, throw-level-Persistenz → [ADR-0014](decisions/0014-spiel-engine-eingabe-kopplung.md)
- [x] `MatchDao`/`LegDao` `@Update` statt `@Insert(REPLACE)` (CASCADE-Schutz) → [ADR-0014](decisions/0014-spiel-engine-eingabe-kopplung.md)
- [x] `GameScreen` (Scoreboard, Bust-Banner, Won-Panel) + MainActivity-State-Switch → [ADR-0014](decisions/0014-spiel-engine-eingabe-kopplung.md)
- [x] Pure `MatchEngine<S>` (Werfer-Wechsel, Leg-/Set-/Match-Ende, Starter-Rotation) → [ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md)
- [x] `GameViewModel` treibt `MatchEngine`, Mehrspieler-Persistenz (`MatchPlayer` + Legs) → [ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md)
- [x] `GameUiState` Mehrspieler (`PlayerScoreUi`, `Playing`/`LegWon`/`MatchWon`) → [ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md)
- [x] Mehrspieler-Scoreboard-UI `MatchScoreboard`/`PlayerScoreCard` → [ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md)
- [x] Profil-Auswahlmodus (CAB + FAB „Match starten", ≥ 2 Spieler) → [ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md)
- [x] Auf echtem Gerät (S25) testen → [CHANGELOG](CHANGELOG.md#geräte-test-phase-2-s25)

---

## Phase 3 — Konfigurierbarkeit

- [x] Spiel-Setup-Screen: Startpunkte (301/501/701) wählbar → [ADR-0018](decisions/0018-setup-screen-startpunkt.md)
- [x] Spiel-Setup-Screen: Double-Out an/aus → [ADR-0018](decisions/0018-setup-screen-startpunkt.md)
- [x] Spiel-Setup-Screen: Anzahl Legs/Sets („Best of X") → [ADR-0018](decisions/0018-setup-screen-startpunkt.md)
- [x] Spiel-Setup-Screen: Spieleranzahl → [ADR-0019](decisions/0019-setup-teilnehmerverwaltung.md)

---

## Phase 3.5 — X01-Feinschliff ✅ vollständig abgeschlossen

- [x] Letzte Aufnahme (geworfene Pfeile des letzten Zugs) im Spiel-Screen anzeigen → [CHANGELOG](CHANGELOG.md#phase-35--x01-feinschliff)
- [x] Checkout-Vorschlag für Rest ≤ 170 (Double-Out) im Spiel-Screen anzeigen → [CHANGELOG](CHANGELOG.md#phase-35--x01-feinschliff)

---

## Bugfixes / Robustheit ✅ vollständig abgeschlossen

- [x] Setup-Teilnehmer: Reorder-Button-Feedback landet nach Swap auf deaktiviertem Button (Ripple/Highlight) → [ADR-0019](decisions/0019-setup-teilnehmerverwaltung.md#verfeinerung-rekompositions-strategie-positions-stabil)
- [x] Spieler mit Match-Historie löschbar machen (Lösch-Strategie festlegen: CASCADE / SET_NULL / RESTRICT+Hinweis) → [ADR-0020](decisions/0020-spieler-loeschen-set-null.md)
- [x] Undo über abgeschlossene Aufnahmen/Spielerwechsel ermöglichen → [ADR-0021](decisions/0021-undo-cross-turn-replay.md)

---

## Phase 4 — Weitere Spielmodi

- [ ] Cricket
- [ ] Around the Clock
- [ ] Shanghai
- [ ] Count Up / High Score
- [ ] Killer

---

## Phase 5 — Analytics

- [ ] Auswertungs-Queries auf throw-level-Daten → [ADR-0005](decisions/0005-analytics.md)
- [ ] Kennzahlen: 3-Dart-Average, First-9-Average, Checkout-Quote, Trefferverteilung → [ADR-0005](decisions/0005-analytics.md)
- [ ] Sequenz-/Reihenfolge-Auswertungen → [ADR-0005](decisions/0005-analytics.md)
- [ ] Analytics-Screens (pro Spieler / pro Match) → [ADR-0005](decisions/0005-analytics.md)

---

## Phase 6 — Delight-Schicht

- [ ] Datengetriebenes Trigger-/Animations-System (Bedingung → Animation/Text) → [ADR-0006](decisions/0006-delight-schicht.md)
- [ ] Trigger: 180, Waschmaschine, Rentnerdreieck → [ADR-0006](decisions/0006-delight-schicht.md)
- [ ] Weitere Trigger (Madhaus, Bull, Ton, …) ergänzbar machen → [ADR-0006](decisions/0006-delight-schicht.md)
- [ ] Stumme Vollbild-Animationen, Auto-Dismiss → [ADR-0006](decisions/0006-delight-schicht.md)

---

## Phase 7 — Online (Firebase, opt-in)

- [ ] Firebase-Projekt anlegen + Gradle-Einbindung (BoM, google-services, ohne Feature-Code) → [ADR-0023](decisions/0023-firebase-optionale-online-schicht.md)
- [ ] Firebase Auth: Google Sign-In mit Konto-Screen Login/Logout (strikt opt-in) → [FIREBASE](FIREBASE.md#features)
- [ ] Konto ↔ lokales Profil verknüpfen (Mapping + globaler Anzeigename) → [FIREBASE](FIREBASE.md#features)
- [ ] Firestore-Grundgerüst: Nutzer-Dokumente + Security Rules → [FIREBASE](FIREBASE.md#firebase-produktzuordnung-grob)
- [ ] Freunde-Datenmodell + Anfragen (senden/annehmen/ablehnen) → [FIREBASE](FIREBASE.md#features)
- [ ] Freunde-Screen (Liste, hinzufügen per Code, entfernen) → [FIREBASE](FIREBASE.md#features)
- [ ] Stats-Upload: lokale Kennzahlen ins Cloud-Profil syncen (opt-in) → [FIREBASE](FIREBASE.md#verhältnis-zum-offline-kern)
- [ ] Globales Leaderboard: Aggregation + Abfrage (Backend-Seite) → [FIREBASE](FIREBASE.md#features)
- [ ] Leaderboard-Screen (global + Umschalter „Freunde") → [FIREBASE](FIREBASE.md#features)
- [ ] AdMob-Werbung, nur im Online-Modus (Offline-Kern werbefrei) → [FIREBASE](FIREBASE.md#werbung-monetarisierung)
- [ ] Online-Match: Datenmodell + Würfe-Sync-Protokoll → [FIREBASE](FIREBASE.md#features)
- [ ] Online-Match: Lobby/Einladung (Freund einladen, beitreten) → [FIREBASE](FIREBASE.md#features)
- [ ] Online-Match: Spiel-Screen-Anbindung (Remote-Würfe live) → [FIREBASE](FIREBASE.md#features)
- [ ] Online-Match: Disconnect-/Wiedereinstiegs-Handling → [FIREBASE](FIREBASE.md#features)
- [ ] Datenschutz-Doku nachziehen (Privacy Policy, Play-Store-Data-Safety) → [ADR-0017](decisions/0017-veroeffentlichung-play-store.md)
