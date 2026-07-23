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

## Arbeitsmodell: Orchestrator-Loop

**Zentrale Steuerung — `docs/ROADMAP.md`:** Diese Datei ist der Taktgeber (Single Source of Truth für die Bau-Reihenfolge) von TomsDarts. Der Orchestrator arbeitet sie **strikt von oben nach unten** ab. Pro Durchlauf wird **GENAU EINE** offene (`[ ]`) Aufgabe vollständig durch den Loop geführt. **Danach stoppt der Orchestrator und wartet auf das ausdrückliche „weiter"-Kommando von Tom — es wird NICHT automatisch zur nächsten Aufgabe übergegangen.** Aufgaben, deren Vorbedingungen noch offen sind, werden nicht begonnen; bei Unklarheit fragt der Orchestrator nach statt zu raten. Jeder Roadmap-Eintrag ist **atomar** (eine PR-große, unabhängig mergebare Änderung, Einzeiler + Link — keine mehrzeilige Prosa).

**Doku-Struktur (`docs/`):** Die Projekt-Doku ist nach Belang aufgeteilt (siehe [ADR-0016](docs/decisions/0016-doku-struktur-aufteilung.md)):
- `docs/README.md` — Wegweiser/Index über die Doku-Dateien.
- `docs/ROADMAP.md` — die atomare Bau-Checkliste (Taktgeber).
- `docs/BACKLOG.md` — zurückgestellte Ideen / offene Produktentscheidungen.
- `docs/CHANGELOG.md` — chronologisches Änderungslog + ausführliche Umsetzungsnotizen.
- `docs/FIREBASE.md` — Konzept der optionalen Firebase-/Online-Schicht (Login, Online-Multiplayer, Leaderboards, Freunde, AdMob-Werbung; Grundsatz in ADR-0023).
- `docs/decisions/` — ein ADR je bewusster Design-/Architektur-Entscheidung (mit Index).
- `docs/CHECKLISTE.md` — Pointer-Stub (abgelöst, hält Alt-Links am Leben).

**Grundprinzip:** Die CLI-Session (Haupt-Claude) agiert ausschließlich als **Orchestrator**. Sie schreibt selbst **keinen** Produktionscode. Jede Programmieraufgabe wird an einen eigenen **Subagent-Workflow** delegiert. Aufgabe des Orchestrators: planen, delegieren, bewerten, entscheiden, mergen — mehr nicht.

Jeder Workflow wird als Subagent gestartet (Task-Tool). Die wiederkehrenden Rollen sind als feste Agent-Definitionen unter `.claude/agents/` hinterlegt — `designer` (nur UI/UX), `implementer`, `tester`, `dokumentar`, `reviewer`, `fixer` — und werden vom Orchestrator pro Schritt explizit aufgerufen (z.B. „Use the implementer subagent on …"). Subagents teilen keinen Speicher — der Orchestrator reicht jeden nötigen Kontext explizit durch.

**Der Loop (pro Aufgabe):**

1. **Aufgabe schneiden.** Der Orchestrator nimmt das nächste Vorhaben und schneidet es zu einer klar abgegrenzten Aufgabe zu (Ziel, betroffene Pfade, relevante Konventionen, Definition-of-Done).
2. **Design-Gate (nur bei UI-Anteil).** Der Orchestrator startet den `designer`: er plant und bewertet genau, an welchen Stellen die Oberfläche ergänzt oder verändert werden muss — betroffene Screens/Komponenten, Zustände (Loading/Empty/Error), Verhalten über verschiedene Bildschirmgrößen, Interaktion, Konsistenz und Barrierefreiheit. Ergebnis: eine umsetzbare Design-Vorgabe für den `implementer`. **Reine Logik-/Nicht-UI-Aufgaben überspringen diesen Schritt.**
3. **Implementierung.** Der Orchestrator startet den `implementer`. Er arbeitet auf einem eigenen Branch (siehe Git-Workflow), schreibt Produktionscode + Basis-Tests (Happy Path) — bei UI **gegen die Design-Vorgabe aus Schritt 2** —, und meldet zurück: Was gemacht, welche Dateien, Test-Status, Überraschungen / offene Fragen.
4. **Test-Gate.** Der Orchestrator startet den `tester` auf demselben Branch: härtet die Tests ab (Edge-Cases, Fehlerpfade, Regressionen — nur Testdateien), führt die Suite aus, meldet grün/rot + gefundene Bugs.
   - **Rot / Bugs** → `fixer` behebt sie auf dem Branch → zurück zu Schritt 4.
   - **Grün** → weiter.
5. **Doku-Gate.** Der Orchestrator startet den `dokumentar` auf demselben Branch: aktualisiert betroffene Doku, korrigiert veraltete Stellen und schreibt einen Changelog-/Decision-Eintrag zur Änderung — **nur Doku-Dateien** — und committet (`docs:`).
6. **PR öffnen.** Der Orchestrator pusht den Branch (Code + Tests + Doku) und öffnet den zugehörigen **PR**.
7. **Review-Gate.** Der Orchestrator startet den `reviewer`, der den PR reviewt: Korrektheit, Konventionen, Tests, Doku-Aktualität, **bei UI die Umsetzung gegen die Design-Vorgabe**, Regressionsrisiko. Urteil: approve **oder** Änderungen nötig + konkrete Punkte.
   - **Findings vorhanden** → `fixer` mit genau diesen Punkten → zurück zu Schritt 7 (neuer PR-Stand → erneutes Review).
   - **Review sauber** → **PR mergen**.
8. **Abhaken & Stopp.** Nach dem Merge lässt der Orchestrator den `dokumentar` die erledigte Aufgabe in `docs/ROADMAP.md` abhaken (`[x]` + Link; Details im CHANGELOG/ADR). Dann **stoppt der Orchestrator und wartet auf Toms ausdrückliches „weiter"** — erst danach beginnt der Loop mit der nächsten offenen Aufgabe von vorn. **Keine automatische Fortsetzung.**

**Regeln für den Orchestrator:**
- **Roadmap ist Taktgeber.** Reihenfolge und Auswahl der Aufgaben kommen aus `docs/ROADMAP.md` (strikt top-down). Pro Durchlauf genau eine offene Aufgabe, danach Stopp und Warten auf Toms „weiter".
- **Kein Selbst-Implementieren.** Der Orchestrator editiert keine Produktionsdateien direkt. Code, Tests und Fixes entstehen immer in einem Subagent-Workflow.
- **Ein Workflow = eine abgegrenzte Aufgabe = ein Branch = atomare Commits.**
- **Trennung von Implementierung und Review.** Beide laufen immer in getrennten Workflows — ein Subagent reviewt nie seinen eigenen Code (Vier-Augen-Prinzip ist der Zweck der Trennung).
- **Kontext durchreichen.** Jeder Subagent bekommt den nötigen Kontext explizit mit (Aufgabe, betroffene Dateien, Konventionen aus dieser Datei).
- **Circuit-Breaker.** Fix-Loops laufen, bis ein Review sauber ist. Wiederholen sich dieselben Findings ohne Fortschritt (Richtwert: 3×), wird der Loop gestoppt und an Tom berichtet — statt endlos zu drehen.
- **Doku synchron halten.** Ändert ein Review-Fix dokumentiertes Verhalten, zieht der Orchestrator den `dokumentar` vor dem Merge nochmal nach.

## Produktprinzipien (immer einhalten)
- **Offline-Kern garantiert:** Der Kern (Spielen, Profile, Konfiguration, Statistik) setzt niemals Netzwerk, Cloud oder Konto voraus und ist werbe- sowie trackingfrei.
- **Online strikt opt-in:** Firebase-Online-Schicht (Login, Online-Multiplayer, Leaderboards, Freunde) ist optional — kein Zwangs-Login, kein Feature-Verlust ohne Konto (ADR-0023).
- **Werbung/Tracking nur im Online-Modus:** Der Offline-Kern ist frei von Werbung, Tracking und Telemetrie; Firebase-Nutzung ist rein funktional (kein Firebase Analytics/Crashlytics). Im opt-in-Online-Modus ist Werbung via Google AdMob inkl. üblichem Ad-Tracking bewusst zugelassen (ADR-0023).
- **Lokale Persistenz:** Daten (Spielstände, Profile, Einstellungen) bleiben auf dem Gerät. Room bleibt Source of Truth; Cloud-Daten sind opt-in-Ergänzung.
- **Konfigurierbarkeit:** Spielmodi/Regeln/Einstellungen sind anpassbar — Konfiguration ist ein Kernmerkmal, kein Nachgedanke.

## Git-Workflow
- **Keine Worktrees:** Es wird immer im bestehenden Arbeitsverzeichnis gearbeitet. `git worktree add` ist tabu, außer Tom fordert es ausdrücklich an.
- Pro Thema/Workflow ein Branch von aktuellem `main`: `feature/<thema>`, `fix/<thema>` oder `docs/<thema>`. Nie direkt auf `main` committen.
- Atomare Commits im Conventional-Style (`feat:`, `fix:`, `docs:`, `chore:` …). Ein Commit = eine logische Änderung, keine Sammel-Commits.
- **PR-getrieben:** Branch pushen und PR öffnen sind Teil des Orchestrator-Loops (Schritt 6). Der **Merge nach `main`** erfolgt durch den Orchestrator erst nach **sauberem Review-Durchlauf** (Schritt 7) — nie durch einen Implementierungs-Subagent und nie direkt auf `main`.
- **Subagent-Rückmeldung statt blindem STOP:** Nach Abschluss eines Workflows meldet der Subagent an den Orchestrator zurück (Commits, geänderte Dateien, Test-Status, Überraschungen / offene Fragen). Der Orchestrator führt den Loop fort. An Tom wird berichtet, wenn der Loop sauber durchläuft (gemerged) **oder** wenn er hängt (Circuit-Breaker im Arbeitsmodell).
- Pre-existing Test-Failures gegen `main` verifizieren und nur dokumentieren — nicht eigenmächtig fixen.

## Konventionen
- **Build/Test/Lint:** über den Gradle-Wrapper (`./gradlew …`), siehe Tabelle oben. Vor dem PR mindestens `./gradlew test` und `./gradlew lint` grün.
- **Klein und abgegrenzt:** Eine Aufgabe = ein Branch = atomare Commits. Kein Scope-Creep, keine opportunistischen Refactors nebenbei.
- **Tests gehören dazu:** Neuer Code bekommt Basis-Tests (Happy Path) vom `implementer`; der `tester` härtet ab.
- **Konsistenz vor Kreativität:** Bestehende Strukturen und Muster respektieren, nicht neu erfinden.
- **Produktprinzipien** (siehe oben — Offline-Kern garantiert, Online strikt opt-in) sind bei jeder Änderung einzuhalten.
