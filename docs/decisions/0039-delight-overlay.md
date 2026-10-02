# 0039 — Delight-Overlay: Schichten, Anzeigedauer, Kontrollpause, Reduced Motion, Rotation

**Status:** Akzeptiert

## Kontext

[ADR-0006](0006-delight-schicht.md) verlangt stumme Vollbild-Animationen, die von selbst
verschwinden. [ADR-0038](0038-delight-trigger-system.md) liefert dafür
`GameViewModel.delightEvents` (`StateFlow<DelightEvent?>`), die Quittierung
`onDelightDismissed(id)`, die gehaltene Kontrollpause (`TurnReviewUi.heldForDelight`) und das
Sicherheitsnetz `DELIGHT_MAX_HOLD_MILLIS = 6000`. Offen waren: wie das Overlay aufgebaut ist,
wie lange es steht und woran die Dauer hängt, wie es mit der Kontrollpause, Rotation,
Prozess-Tod und Bedienungshilfen zusammenspielt und womit animiert wird.

## Entscheidung

1. **Schichten.** `ui.delight.DelightOverlay` liegt in `GameScreenContent` in einer `Box` um
   den `Scaffold`, also auch über der TopAppBar. Von unten nach oben: Scrim
   (`colorScheme.scrim`, Alpha 0,6, reicht unter die System-Bars), Animationsebene (Konfetti
   vollflächig auf einem Canvas), zentrierter Inhalt mit `windowInsetsPadding(safeDrawing)`:
   Illustration (Waschmaschine, Rentnerdreieck) und deckende Textkarte
   (`surfaceContainerHigh`, Titel `displaySmall`/`primary`, optionaler Untertitel). Im
   Querformat stehen Illustration und Karte nebeneinander. Die Illustration entfällt unter
   96 dp und im Querformat bei Schriftgröße ab 150 %. Farben kommen immer aus
   `MaterialTheme.colorScheme` (Dynamic Color).
2. **Eingabe blockiert.** Ein `pointerInput` mit Tap-Erkennung auf der ganzen Fläche schließt
   die Feier und lässt keinen Tipp zum Spiel darunter durch. `BackHandler` schließt nur die
   Feier. Bewusst `pointerInput` statt `clickable`: `clickable` würde die Semantik der Karte in
   einen ganzflächigen Klick-Knoten verschmelzen.
3. **Dauer unabhängig von der Animations-Uhr.** Die Anzeigedauer (`DelightTiming`, pur) läuft
   als reines `delay()` im Wrapper `GameScreen`: Konfetti 2500 ms, Waschmaschine/Rentnerdreieck
   2200 ms, allgemein 1800 ms, bei reduzierter Bewegung einheitlich 2000 ms. Mit der
   Animator-Dauer-Skala 0 springen Animationen sofort ans Ende; die Feier bleibt trotzdem
   lesbar stehen. Bewegung dauert höchstens 1600 ms, danach steht das Endbild.
   TalkBack-Nutzer bekommen über `AccessibilityManager.calculateRecommendedTimeoutMillis`
   mehr Zeit, **gedeckelt** auf 5000 ms (`DelightTiming.MAX_DISPLAY_MILLIS`), also 1000 ms unter
   `DELIGHT_MAX_HOLD_MILLIS`. Das Sicherheitsnetz des ViewModels startet schon beim Auslösen,
   die UI erst beim Anzeigen. Der Puffer stellt sicher, dass der reguläre Dismiss vor dem
   Sicherheitsnetz ankommt und die Kontrollpause nie unter einer sichtbaren Feier anläuft.
   `MAX_DISPLAY_MILLIS` ist eine eigene Konstante, damit `ui.delight` nicht von `ui.game`
   abhängt; `DelightTimingTest` sichert den Abstand zum Sicherheitsnetz ab.
4. **Kontrollpause.** `TurnReviewContent` bekommt `timerRunning = !heldForDelight` und startet
   den Fortschrittsbalken per `LaunchedEffect(timerRunning)` erst danach. Ablauf: Feier, Tipp
   oder Ablauf, dann die volle Kontrollpause (1,5 s), dann der Spielerwechsel. Bei Leg-/Match-
   Sieg liegt die Feier über dem Sieg-Panel.
5. **Jedes Schließen quittiert.** Tippen, Zurück, Ablauf, Ersetzen durch ein neues Event, ein
   übersprungenes Event nach Rotation und abgeschaltete Feiern rufen `onDelightDismissed(id)`.
   Die Entscheidung trifft die pure Funktion `planDelightIntake`. Ein neues Event ersetzt das
   laufende (keine Warteschlange). Dedupliziert wird per Gleichheit, weil die IDs je ViewModel
   wieder bei 1 beginnen. Der Dedupe-Zustand lebt **im ViewModel**
   (`GameViewModel.lastShownDelightId`, gesetzt über `onDelightShown(id)`), also genau so lange
   wie die IDs. Nach einem Prozess-Tod beginnen IDs und Dedupe-Zustand gemeinsam von vorn, die
   erste Feier des neuen ViewModels (ID 1) wird also angezeigt. `onDelightShown` quittiert
   nicht; das Schließen meldet weiterhin `onDelightDismissed`.
6. **Rotation und Prozess-Tod.** Die laufende Feier (`ActiveDelight`: ID, Animationsname,
   Text-Schlüssel, Werfer, Startzeit auf `SystemClock.elapsedRealtime()`, Gesamtdauer) liegt in
   `rememberSaveable` als Liste aus `Long`/`String`. Ressourcen-IDs werden nicht gespeichert,
   weil sie zwischen Builds nicht stabil sind. Beim Wiederherstellen läuft nur die Restzeit
   (`DelightTiming.remainingMillis`); eine abgelaufene Feier (auch nach Geräteneustart) wird
   verworfen. Eine fortgesetzte Feier zeigt ihr statisches Endbild statt neu zu animieren.
   Die gespeicherte Feier trägt die Kennung ihrer ViewModel-Instanz
   (`GameViewModel.delightSessionToken`, zufällige UUID je Instanz) und wird nur bei gleicher
   Kennung wiederhergestellt. Nach einer Rotation (gleiches ViewModel) läuft sie weiter. Nach
   einem Prozess-Tod (neues ViewModel) wird eine alte Sitzung nie auf neue IDs angewendet: Sie
   wird verworfen und **nicht** quittiert, weil ihre ID nicht zum neuen ViewModel gehört.
7. **Reduced Motion.** `rememberReducedMotion()` liest `Settings.Global.ANIMATOR_DURATION_SCALE
   == 0`. Dann zeigen alle Feiern ein statisches Endbild ohne Überblendungen (Konfetti:
   24 Partikel, eingefroren bei t = 0,5).
8. **Keine Animations-Library.** Nur Bordmittel: `Animatable`, `Canvas`, `graphicsLayer`,
   `PathMeasure`. Konfetti ist ein Canvas mit einem `Animatable`; die Bahnen sind pur
   (`generateConfetti(seed, count)` mit `Random(seed)`, Seed = Event-ID, 60/100 Partikel, max.
   120; `particlePosition`). Waschmaschine: ein drehender Chip-Ring (720°, Chips gegengedreht).
   Rentnerdreieck: per `PathMeasure` nachgezogener Strich, danach eine Füllung, dazu drei
   gestaffelte Chips. Allgemein: Karte federt ein, dahinter ein Ring. Keine
   `InfiniteTransition`, nichts blinkt öfter als 3× pro Sekunde, kein Rot-Flackern.
9. **Texte.** Die Text-Schlüssel stehen einmalig in `delight.DelightTextKeys` (pur, für die
   Produkt-Trigger); `ui.delight.delightTextRes` bildet sie auf `R.string.delight_*` ab und
   fällt bei unbekanntem Schlüssel auf den allgemeinen Text zurück. Den Spielernamen zeigt die
   Feier nur bei mehr als einem Spieler als Präfix im Untertitel (`delight_player_prefix`).
10. **Barrierefreiheit.** Die Wurzel ist ein Pane (`paneTitle`). Die Karte fasst Titel und
    Untertitel zu einer Beschreibung zusammen, trägt die höfliche Live-Region (damit TalkBack den
    vollen Text ansagt; das Assertive-Sieg-Panel hat Vorrang) und bietet die Aktion „Feier
    schließen". Illustrationen sind stumm. Es gibt keinen Fokus-Sprung. Solange eine Feier
    läuft, ist der Scaffold-Inhalt darunter für Bedienungshilfen ausgeblendet
    (`clearAndSetSemantics { hideFromAccessibility() }`), passend zur blockierten Eingabe.
11. **Schalter vorbereitet.** `GameScreen(delightEnabled = true)`: Bei `false` verwirft der
    Bildschirm jedes Event sofort und quittiert es. Der Einstellungs-Schalter folgt als eigener
    Roadmap-Punkt.

## Konsequenzen

- Produkt-Trigger können jetzt in `ProductDelightTriggers.ALL`; sie verwenden für `textKey`
  die Konstanten aus `DelightTextKeys` (neue Schlüssel: Konstante, Eintrag in `ALL`, Zweig in
  `delightTextResOrNull`, Strings; `DelightTextsTest` wacht darüber).
- Die Lesezeit für TalkBack-Nutzer ist durch das Sicherheitsnetz auf 5 s begrenzt.
  Wer länger will, muss `DELIGHT_MAX_HOLD_MILLIS` mitziehen.
- Ein Compose-UI-Test (Tippen schließt, Button darunter bekommt keinen Klick) fehlt, weil es
  host-seitig keine Compose-Test-Infrastruktur gibt. Abgesichert sind die puren Teile
  (Timing, Konfetti, Speichern/Wiederherstellen, Event-Annahme, Layout-Größen, Texte,
  Spielername) sowie Previews.
- Offline-Kern unberührt: keine neue Abhängigkeit, keine Schemaänderung, kein Netz.

## Verweise

- [ADR-0006](0006-delight-schicht.md) — Delight-Schicht (Grundsatz, Trigger-Liste)
- [ADR-0026](0026-turn-review-kontrollpause.md) — Kontrollpause nach dem dritten Dart
- [ADR-0038](0038-delight-trigger-system.md) — Delight-Trigger-System, Event-Muster, gehaltene Kontrollpause
