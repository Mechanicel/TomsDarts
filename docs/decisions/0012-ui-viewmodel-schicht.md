# 0012 — UI-/ViewModel-Schicht

**Status:** Akzeptiert

## Kontext
Festgelegt ab der Profilverwaltung — die erste Compose-UI der App braucht ein
konsistentes State-/ViewModel-Muster, ohne DI-Framework.

## Entscheidung
- **Manuelles DI bis in die UI:** Kein DI-Framework auch in der UI-Schicht. Eine
  `TomsDartsApp : Application` hält den `AppContainer` (im Manifest als
  `android:name=".TomsDartsApp"`). ViewModels ziehen ihre Repositories über eine
  companion-`Factory` aus der `Application` — konsistent zum manuellen `AppContainer`-DI
  (siehe [ADR-0011](0011-repository-di-schicht.md)).
- **ViewModel-Anbindung an Compose:** über `androidx.lifecycle:lifecycle-viewmodel-compose`
  + `lifecycle-runtime-compose` (an lifecycle `2.6.1` gebunden); UI konsumiert State mit
  `viewModel(factory = …)` und `collectAsStateWithLifecycle()`.
- **UI-State-Muster:** pro Screen ein **sealed** UI-State (z. B. `ProfileUiState`:
  Loading/Empty/Content/Error) plus ein eigener sealed Dialog-State (z. B. `ProfileDialog`:
  None/Add/Edit/ConfirmDelete) als `StateFlow`. Listen-State entsteht reaktiv aus dem
  Repository-`Flow` (`observePlayers().map{…}.catch{Error}.stateIn(WhileSubscribed)`), ein
  `retryTrigger` erlaubt Neuversuch nach Fehler.
- **Stateless/Stateful-Trennung:** Jeder Screen wird in eine stateful Hülle
  (holt ViewModel) und eine stateless `…Content`-Composable (nimmt State + Callbacks)
  zerlegt — letztere ist Preview- und testbar (mehrere `@Preview` pro Screen-Zustand).
- **Bewusst KEINE Icon-Dependency** (`material-icons-*`): Symbole werden als Text-Glyphen
  bzw. Avatar-Initialen dargestellt, hält die App schlank.
- **Listeneintrag-Konvention:** Material3 `ListItem` + `HorizontalDivider`.
- **Test-Muster ViewModel:** ViewModel-/UI-State-Logik wird host-seitig getestet; der
  Coroutine-Test-Scheduler wird über eine gemeinsame `testing/MainDispatcherRule`
  (setzt `Dispatchers.Main` auf einen `UnconfinedTestDispatcher`) **an den Test-Scope
  gekoppelt**, damit `stateIn(WhileSubscribed)`-Flows deterministisch (nicht flaky) laufen.
  `kotlinx-coroutines-test` als `testImplementation`.

## Konsequenzen
- Einheitliches, preview-/testbares Screen-Muster für alle künftigen Screens.
- Kein Framework-Overhead, schlanke App.
- Deterministische ViewModel-Tests über `MainDispatcherRule`.
