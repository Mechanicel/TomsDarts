# TomsDarts — Claude Code Context

## Projekt auf einen Blick
**TomsDarts** ist eine **native Android-App**: eine lokale, vollständig **offline lauffähige, konfigurierbare Darts-App**.
- **Offline-Kern garantiert:** Alle Kernfunktionen laufen vollständig offline; Kerndaten bleiben auf dem Gerät (Room).
- **Online strikt optional (opt-in):** Firebase-Features (Login, Online-Multiplayer, Leaderboards, Freunde) sind reine Zusatzschicht; ohne Konto geht kein Feature verloren (→ docs/FIREBASE.md, docs/decisions/0023-firebase-optionale-online-schicht.md).
- **Offline-Kern werbe- und trackingfrei:** Keine Tracking-/Analytics-Abhängigkeiten im Offline-Kern; Werbung (Google AdMob, inkl. Ad-Tracking) nur im opt-in-Online-Modus (→ docs/FIREBASE.md, ADR-0023).
- Keine Netzwerkpflicht: Der Offline-Kern funktioniert ohne jede Verbindung; nur die opt-in-Online-Features brauchen Netz.

## Tech-Stack
- **Kotlin** + **Jetpack Compose** (UI)
- **Gradle mit Kotlin DSL** (`*.gradle.kts`), Gradle-Wrapper im Repo (`./gradlew`)
- **minSdk 26**
- **applicationId** `com.mechanicel.tomsdarts`
- **Persistenz: Room** (lokale DB, eingebunden über KSP; Schema-Export aktiv unter `app/schemas/`)
- **Einstellungen: DataStore Preferences** (lokal; App-weite Schalter, Room bleibt Source of Truth für Spieldaten → ADR-0040)
- **Datenschicht-Tests:** host-seitig mit Robolectric + In-Memory-Room über `./gradlew test` (kein Emulator nötig)

## Build-, Test- & Lint-Befehle
| Zweck | Befehl |
|---|---|
| Build (Debug-APK) | `./gradlew assembleDebug` |
| Auf Gerät installieren | `./gradlew installDebug` |
| Unit-Tests (JVM) | `./gradlew test` |
| Instrumented-Tests | `./gradlew connectedAndroidTest` |
| Lint | `./gradlew lint` |
| Voller Check (Build + Tests + Lint) | `./gradlew build` |

## Arbeitsmodell: Orchestrator + Builder

**Zentrale Steuerung — `docs/ROADMAP.md`:** Diese Datei ist der Taktgeber (Single Source of Truth für die Bau-Reihenfolge) von TomsDarts. Der Orchestrator arbeitet sie **von oben nach unten** ab und zieht **so viele Aufgaben wie möglich am Stück** durch — kein Stopp nach jeder Aufgabe. Aufgaben, deren Vorbedingungen noch offen sind, werden nicht begonnen; bei Unklarheit oder offener Produktentscheidung fragt der Orchestrator nach statt zu raten. Jeder Roadmap-Eintrag ist **atomar** (eine PR-große, unabhängig mergebare Änderung, Einzeiler + Link — keine mehrzeilige Prosa).

**Doku-Struktur (`docs/`):** Die Projekt-Doku ist nach Belang aufgeteilt (siehe [ADR-0016](docs/decisions/0016-doku-struktur-aufteilung.md)):
- `docs/README.md` — Wegweiser/Index über die Doku-Dateien.
- `docs/ROADMAP.md` — die atomare Bau-Checkliste (Taktgeber).
- `docs/BACKLOG.md` — zurückgestellte Ideen / offene Produktentscheidungen.
- `docs/CHANGELOG.md` — chronologisches Änderungslog + ausführliche Umsetzungsnotizen.
- `docs/FIREBASE.md` — Konzept der optionalen Firebase-/Online-Schicht (Login, Online-Multiplayer, Leaderboards, Freunde, AdMob-Werbung; Grundsatz in ADR-0023).
- `docs/decisions/` — ein ADR je bewusster Design-/Architektur-Entscheidung (mit Index).
- `docs/CHECKLISTE.md` — Pointer-Stub (abgelöst, hält Alt-Links am Leben).

**Grundprinzip:** Die CLI-Session (Haupt-Claude, Opus) agiert als **Orchestrator**: planen, Aufgaben schneiden, delegieren, bewerten, PRs öffnen, mergen. Produktionscode entsteht in Subagents.

**Rollen** (feste Agent-Definitionen unter `.claude/agents/`; Subagents teilen keinen Speicher — der Orchestrator reicht jeden nötigen Kontext explizit durch):
- `builder` (Opus) — vereint Implementierung, Tests (Happy Path + Edge-Cases), Doku (CHANGELOG/ADR/BACKLOG/ROADMAP-Haken) und Review-Fixes in einem Durchgang auf einem eigenen Branch.
- `designer` (Opus, read-only) — nur bei UI-Anteil: umsetzbare Design-Vorgabe für den `builder`.
- `reviewer` (Fable, read-only) — reviewt jeden PR unabhängig (Vier-Augen-Prinzip).
- Die früheren Einzelrollen `implementer`, `tester`, `dokumentar`, `fixer` sind im `builder` aufgegangen.

**Ablauf pro Aufgabe:**
1. **Schneiden.** Ziel, betroffene Pfade, Konventionen, Definition-of-Done.
2. **Design (nur bei UI).** `designer` liefert die Vorgabe.
3. **Bauen.** `builder` implementiert inkl. Tests und Doku, `./gradlew test` + `./gradlew lint` grün, atomare Commits.
4. **PR.** Orchestrator pusht den Branch und öffnet den PR.
5. **Review.** `reviewer` urteilt: approve **oder** konkrete Findings → `builder` behebt → erneutes Review.
6. **Merge** durch den Orchestrator nach sauberem Review, dann direkt die nächste Aufgabe.

**Parallelität:** Mehrere `builder` dürfen gleichzeitig laufen — jeder in einem **eigenen Git-Worktree** und auf eigenem Branch —, sofern der Orchestrator die Scopes **dateidisjunkt** schneidet, sodass sich die PRs beim Mergen nicht in die Quere kommen. Geteilte Hotspots (z.B. `strings.xml`, `docs/CHANGELOG.md`, `docs/ROADMAP.md`, Room-Schema/DB-Version) bekommt pro Runde nur eine Aufgabe, oder sie werden nach den Merges seriell nachgezogen. Merges erfolgen seriell; offene Branches werden danach auf `main` rebased. Wo es effizienter ist, darf der Orchestrator mehrere Agents per Workflow orchestrieren.

**Regeln für den Orchestrator:**
- **Roadmap ist Taktgeber.** Reihenfolge und Auswahl kommen aus `docs/ROADMAP.md`; unabhängige Aufgaben dürfen parallel laufen.
- **Ein Builder-Auftrag = eine abgegrenzte Aufgabe = ein Branch = atomare Commits.**
- **Trennung von Bauen und Review.** Ein Subagent reviewt nie seinen eigenen Code.
- **Circuit-Breaker.** Wiederholen sich dieselben Review-Findings ohne Fortschritt (Richtwert: 3×), wird gestoppt und an Tom berichtet.
- **An Tom berichten** bei Blockern (z.B. fehlende Konten/Konfiguration), offenen Produktentscheidungen oder ausgelöstem Circuit-Breaker — sonst weiterarbeiten.

## Produktprinzipien (immer einhalten)
- **Offline-Kern garantiert:** Der Kern (Spielen, Profile, Konfiguration, Statistik) setzt niemals Netzwerk, Cloud oder Konto voraus und ist werbe- sowie trackingfrei.
- **Online strikt opt-in:** Firebase-Online-Schicht (Login, Online-Multiplayer, Leaderboards, Freunde) ist optional — kein Zwangs-Login, kein Feature-Verlust ohne Konto (ADR-0023).
- **Werbung/Tracking nur im Online-Modus:** Der Offline-Kern ist frei von Werbung, Tracking und Telemetrie; Firebase-Nutzung ist rein funktional (kein Firebase Analytics/Crashlytics). Im opt-in-Online-Modus ist Werbung via Google AdMob inkl. üblichem Ad-Tracking bewusst zugelassen (ADR-0023).
- **Lokale Persistenz:** Daten (Spielstände, Profile, Einstellungen) bleiben auf dem Gerät. Room bleibt Source of Truth; Cloud-Daten sind opt-in-Ergänzung.
- **Konfigurierbarkeit:** Spielmodi/Regeln/Einstellungen sind anpassbar — Konfiguration ist ein Kernmerkmal, kein Nachgedanke.

## Git-Workflow
- **Worktrees erlaubt:** Parallel laufende `builder` arbeiten je in einem eigenen Git-Worktree; nie zwei schreibende Agents im selben Arbeitsverzeichnis.
- Pro Thema/Workflow ein Branch von aktuellem `main`: `feature/<thema>`, `fix/<thema>` oder `docs/<thema>`. Nie direkt auf `main` committen.
- Atomare Commits im Conventional-Style (`feat:`, `fix:`, `docs:`, `chore:` …). Ein Commit = eine logische Änderung, keine Sammel-Commits.
- **PR-getrieben:** Branch pushen und PR öffnen sind Aufgabe des Orchestrators. Der **Merge nach `main`** erfolgt durch den Orchestrator erst nach **sauberem Review-Durchlauf** — nie durch einen `builder` und nie direkt auf `main`.
- **Subagent-Rückmeldung statt blindem STOP:** Nach Abschluss eines Workflows meldet der Subagent an den Orchestrator zurück (Commits, geänderte Dateien, Test-Status, Überraschungen / offene Fragen). Der Orchestrator macht weiter; an Tom wird bei Blockern oder ausgelöstem Circuit-Breaker berichtet.
- Pre-existing Test-Failures gegen `main` verifizieren und nur dokumentieren — nicht eigenmächtig fixen.

## Konventionen
- **Build/Test/Lint:** über den Gradle-Wrapper (`./gradlew …`), siehe Tabelle oben. Vor dem PR mindestens `./gradlew test` und `./gradlew lint` grün.
- **Klein und abgegrenzt:** Eine Aufgabe = ein Branch = atomare Commits. Kein Scope-Creep, keine opportunistischen Refactors nebenbei.
- **Tests gehören dazu:** Neuer Code bekommt vom `builder` Tests für Happy Path und Edge-Cases.
- **Konsistenz vor Kreativität:** Bestehende Strukturen und Muster respektieren, nicht neu erfinden.
- **Produktprinzipien** (siehe oben — Offline-Kern garantiert, Online strikt opt-in) sind bei jeder Änderung einzuhalten.
