# 0009 — Persistenz-Tech

**Status:** Akzeptiert

## Kontext
Für die lokale, offline-first Persistenz braucht die App eine erprobte
Datenbank-Lösung samt Build-Einbindung.

## Entscheidung
- **Room `2.7.2`** als lokale Datenbank, eingebunden über KSP (`com.google.devtools.ksp`,
  `2.2.10-2.0.2`) als Annotation-Processor; alle Versionen im Versionskatalog
  `gradle/libs.versions.toml`.
- **Schema-Export ist an** (`room.schemaLocation`), die exportierten Schema-JSONs
  werden committet (`app/schemas/...`) — Grundlage für spätere Migrationstests.
- **AGP-9-Eigenheit:** AGP 9 bringt eingebautes Kotlin mit (kein separates
  `org.jetbrains.kotlin.android`-Plugin). Damit KSP funktioniert, ist
  `android.disallowKotlinSourceSets=false` in `gradle.properties` gesetzt.

## Konsequenzen
- Alle Datenschicht-Tests laufen host-seitig (siehe
  [ADR-0010](0010-test-strategie-datenschicht.md)).
- Schema-JSONs im Repo ermöglichen später Migrationstests.
