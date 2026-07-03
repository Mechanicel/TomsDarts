# TomsDarts

TomsDarts ist eine native Android-App: eine lokale, vollständig **offline
lauffähige, konfigurierbare Darts-App**.

- **Kein Login, kein Backend, keine Cloud.** Alle Daten bleiben auf dem Gerät.
- **Keine Tracking-/Analytics-Abhängigkeiten.**
- **Konfigurierbarkeit** ist ein Kernmerkmal — Spielmodi, Regeln und Einstellungen
  sind anpassbar.

## Tech-Stack

- **Kotlin** + **Jetpack Compose** (UI)
- **Gradle mit Kotlin DSL** (`*.gradle.kts`), Gradle-Wrapper im Repo (`./gradlew`)
- **minSdk 26**, **applicationId** `com.mechanicel.tomsdarts`
- **Persistenz: Room** (lokale DB, eingebunden über KSP; Schema-Export aktiv unter
  `app/schemas/`)
- **Datenschicht-Tests:** host-seitig mit Robolectric + In-Memory-Room über
  `./gradlew test` (kein Emulator nötig)

## Build-, Test- & Lint-Befehle

| Zweck | Befehl |
|---|---|
| Build (Debug-APK) | `./gradlew assembleDebug` |
| Auf Gerät installieren | `./gradlew installDebug` |
| Unit-Tests (JVM) | `./gradlew test` |
| Instrumented-Tests | `./gradlew connectedAndroidTest` |
| Lint | `./gradlew lint` |
| Voller Check (Build + Tests + Lint) | `./gradlew build` |

## Dokumentation

Die Projekt-Doku liegt unter [`docs/`](docs/README.md) und ist nach Belang aufgeteilt:

- [docs/README.md](docs/README.md) — Wegweiser über die Doku
- [docs/ROADMAP.md](docs/ROADMAP.md) — die atomare Bau-Checkliste (Taktgeber)
- [docs/decisions/](docs/decisions/README.md) — Architektur-Entscheidungen (ADRs)
- [docs/BACKLOG.md](docs/BACKLOG.md) — Backlog / spätere Ideen
- [docs/CHANGELOG.md](docs/CHANGELOG.md) — Änderungslog + Umsetzungsnotizen

Zur Arbeitsweise (Orchestrator-Loop mit Subagent-Rollen) siehe [`CLAUDE.md`](CLAUDE.md).
