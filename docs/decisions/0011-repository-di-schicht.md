# 0011 — Repository-/DI-Schicht

**Status:** Akzeptiert

## Kontext
Zwischen DAOs und UI braucht die App eine schlanke, offline-taugliche Zugriffs-
und Verdrahtungsschicht — ohne schweres DI-Framework.

## Entscheidung
- **Manuelles DI über `AppContainer`** (`data/AppContainer.kt`) statt eines
  DI-Frameworks (kein Hilt/Dagger/Koin): bewusst minimal, hält die App schlank und
  offline. `AppContainer(context)` baut das DB-Singleton und stellt
  `playerRepository` / `matchRepository` (`by lazy`) bereit — der einzige
  Einstiegspunkt für die spätere UI-/ViewModel-Schicht.
- **DB-Singleton** `TomsDartsDatabase.getInstance(context)`: thread-sicheres,
  prozessweites Singleton (`@Volatile` + double-checked locking), file-basiert in
  `tomsdarts.db`, mit `fallbackToDestructiveMigration()` — konsistent zur
  Schema-Versionsstrategie „regenerieren statt migrieren" (solange unveröffentlicht,
  siehe [ADR-0008](0008-datenmodell-entscheidungen.md)).
- **Repository-Schicht ist regelfrei:** Repositories sind dünne Persistenz-Wrapper
  über den DAOs (reines Durchreichen). Spielregeln, Bust-/Checkout-/Score-Logik
  gehören **nicht** hierher, sondern in eine Domain-/ViewModel-Schicht in Phase 2.
- **Reaktives Lesen über `Flow`:** Listen, die die UI beobachten soll
  (`PlayerRepository.observePlayers` ↔ `PlayerDao.observeAll`, ORDER BY name),
  werden als `Flow` geliefert; punktuelle Reads/Writes bleiben `suspend`. Kein
  zusätzliches `withContext(Dispatchers.IO)` in den Repositories, da Rooms
  `suspend`-DAOs bereits auf dem eigenen Executor laufen.

## Konsequenzen
- Klare Trennung: Persistenz (Repository) vs. Regeln (Domäne/Engine, Phase 2).
- UI beobachtet reaktiv via `Flow`.
- Kein Framework-Overhead; die Verdrahtung reicht bis in die UI (siehe
  [ADR-0012](0012-ui-viewmodel-schicht.md)).
