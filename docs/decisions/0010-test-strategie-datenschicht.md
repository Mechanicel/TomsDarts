# 0010 — Test-Strategie Datenschicht

**Status:** Akzeptiert

## Kontext
In der Bau-Umgebung steht **kein Gerät/Emulator** bereit. Die Datenschicht muss
trotzdem verlässlich getestet werden.

## Entscheidung
- **Datenschicht-/DAO-Tests laufen host-seitig unter Robolectric** (`4.14.1`,
  `androidx.test:core 1.6.1`) gegen eine **In-Memory-Room-DB** und werden über
  `./gradlew test` ausgeführt — **nicht** als instrumented Tests, weil in der
  Bau-Umgebung **kein Gerät/Emulator** verfügbar ist. Die Testklassen liegen
  daher unter `app/src/test/...` (nicht `androidTest`).
- Pflicht-Konfiguration: `@Config(sdk = [34])` (Robolectric unterstützt
  compileSdk 36 noch nicht) und `testOptions { unitTests { isIncludeAndroidResources = true } }`.
- Dieses Muster (Robolectric + `@Config(sdk=[34])` + In-Memory-Room) ist der
  **Standard für künftige Datenschicht-Tests**.

## Konsequenzen
- Kein Emulator im CI/Build nötig.
- **Später möglich:** Wechsel auf reine instrumented Tests (`connectedAndroidTest`)
  sobald ein Gerät/Emulator bereitsteht, bzw. höhere `sdk`-Werte sobald Robolectric
  compileSdk 36 unterstützt.
