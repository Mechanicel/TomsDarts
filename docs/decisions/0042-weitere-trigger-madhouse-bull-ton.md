# 0042 — Weitere Trigger: Madhouse, Bull-Finish, Ton (Prioritätsschema, Rezept)

**Status:** Akzeptiert

## Kontext

Mit [ADR-0041](0041-produkt-trigger-180-waschmaschine-rentnerdreieck.md) laufen 180,
Waschmaschine und Rentnerdreieck. [ADR-0006](0006-delight-schicht.md) nennt weitere Trigger
(Madhaus / D1, Bull, Ton ab 100). Das System ist bereits datengetrieben
([ADR-0038](0038-delight-trigger-system.md)), das Rezept für einen neuen Trigger war aber über
mehrere Dateien und ADRs verstreut. Außerdem zeigte Review #55: Drei Darts auf derselben Zahl
(1/1/1, 5/5/5, 7/7/7, 3/3/3) lösten Waschmaschine bzw. Rentnerdreieck aus.

## Entscheidung

1. **Neue Trigger** (in `delight/DelightRegistry.kt`, `ProductDelightTriggers`; alle mit
   Animation `GENERIC`, kein eigenes Animationsdesign; Bust feiert nie):

   | ID | Bedingung | Text-Schlüssel | Priorität |
   |---|---|---|---|
   | `madhouse` | Checkout des Werfers (`checkout`), letzter Dart Doppel 1 (Segment 1 × 2); Anzahl Darts egal; in jedem Modus, in dem das zutrifft (praktisch X01 mit Double-Out) | `DelightTextKeys.MADHOUSE` | 80 |
   | `bull_finish` | Checkout des Werfers, letzter Dart Doppel-Bull (Segment 25 × 2); Anzahl Darts egal; in jedem Modus | `DelightTextKeys.BULL_FINISH` | 70 |
   | `ton` | nur X01 (`modeKey == GameModeCatalog.X01`), kein Bust, **gewertete** Summe `scored >= 100`; Anzahl Darts egal | `DelightTextKeys.TON` | 10 |

   Pure Bedingungen: `isMadhouse`, `isBullFinish`, `isTon`. Texte laut Design-Vorgabe:
   „Madhouse!" / „Doppel 1 – Nerven aus Stahl.", „Bull!" / „Mitten ins Schwarze –
   Bull-Finish.", „Ton!" / „Dreistellig. Sauber.".
2. **Nur Doppel-Bull ist ein Bull-Finish (Builder-Entscheidung):** Ein Checkout auf Single-Bull
   (möglich in X01 ohne Double-Out) zählt nicht. Das „Bull-Finish" meint im Darts das Finish
   auf das Bullseye; ein Single-Bull-Checkout ist ein gewöhnlicher Checkout.
3. **Waschmaschine/Rentnerdreieck präzisiert (Orchestrator-Entscheidung aus Review #55):** Alle
   drei Darts in der Segmentmenge **und mindestens zwei verschiedene Segmente**. Das ersetzt
   „nicht alle auf 20" bzw. „nicht alle auf 19": 20/20/20 und 19/19/19 bleiben ausgeschlossen,
   zusätzlich jetzt auch 1/1/1, 5/5/5, 7/7/7 und 3/3/3.
4. **Prioritätsschema komplett** (höher gewinnt; bei Gleichstand die Registrierungsreihenfolge,
   ADR-0038):

   | Priorität | Konstante | Trigger |
   |---|---|---|
   | 100 | `PRIORITY_MAX_SCORE` | 180 |
   | 80 | `PRIORITY_MADHOUSE` | Madhouse |
   | 70 | `PRIORITY_BULL_FINISH` | Bull-Finish |
   | 50 | `PRIORITY_PATTERN` | Waschmaschine, Rentnerdreieck |
   | 10 | `PRIORITY_TON` | Ton |

   Grundsatz: Seltenes schlägt Häufiges. Seltene Finishes stehen über den Zahlenmustern, die
   häufige Ton nur ganz unten — sie feiert nur, wenn nichts Spezielleres passt.
   Kollisionen (gewollt, getestet):
   - T20/T20/T20 ist auch eine Ton → **180** gewinnt.
   - T20/T20/S5 = 125 ist auch eine Ton → **Waschmaschine** gewinnt (ebenso T19/T19/S7 →
     Rentnerdreieck).
   - S20/S5/D1 als Checkout ist auch eine Waschmaschine → **Madhouse** gewinnt.
   - T20/T20/D-Bull = 170 als Checkout ist auch eine Ton → **Bull-Finish** gewinnt.
   - T20/T20/D1 = 122 als Checkout ist auch eine Ton → **Madhouse** gewinnt (entgegen der
     ersten Annahme ist die Kombination also möglich).
   - Madhouse und Bull-Finish schließen sich aus (letzter Dart ist entweder D1 oder D-Bull).
5. **Rezept „Neuen Trigger hinzufügen"** (verbindlich für künftige Trigger):
   1. **Text-Schlüssel:** Konstante in `delight/DelightTextKeys.kt` anlegen und in
      `DelightTextKeys.ALL` eintragen.
   2. **Strings:** `delight_<thema>_title` (und optional `_subtitle`) in
      `res/values/strings.xml`; Texte kommen vom `designer` ([ADR-0039](0039-delight-overlay.md)).
   3. **UI-Mapping:** Zweig in `ui/delight/DelightTexts.kt` (`delightTextResOrNull`).
   4. **Trigger:** In `ProductDelightTriggers` eine ID-Konstante, eine pure Bedingung
      (`fun isXyz(visit: DelightVisit): Boolean`, nur auf `DelightVisit`-Feldern), eine
      Priorität aus dem Schema oben (neue Stufe als eigene Konstante) und ein `DelightTrigger`
      mit `DelightPresentation(animation, textKey)`.
   5. **Registrieren:** Eintrag in `ProductDelightTriggers.ALL`; ADR/CHANGELOG nachziehen.

   **Test-Wächter:** `DelightTextsTest` schlägt fehl, wenn ein Schlüssel in
   `DelightTextKeys.ALL` kein eigenes Text-Mapping, einen leeren Text oder einen doppelten Titel
   hat. `ProductDelightTriggersTest` prüft, dass jeder Trigger in `ALL` einen bekannten
   Text-Schlüssel nutzt, und hält Reihenfolge, IDs und Prioritätsschema fest.
   `DelightRegistry` wirft bei doppelten Trigger-IDs.
6. **Test-Isolation:** Spielablauf-Tests unter `ui/game/`, die keine Feiern prüfen, bauen das
   `GameViewModel` explizit mit `DelightRegistry.EMPTY`. Neue Trigger (v.a. die Ton) halten
   dort sonst unbemerkt die Kontrollpause. Nur `GameViewModelDelightTest` und
   `ProductDelightTriggersTest` nutzen die Default-Registry.

## Konsequenzen

- In X01 feiert jetzt jede Aufnahme ab 100 Punkten. Das ist bewusst die häufigste Feier;
  wem das zu viel ist, schaltet „Feier-Animationen" ab ([ADR-0040](0040-app-einstellungen-datastore.md)).
  Ein feinerer Schalter je Trigger ist nicht Teil dieser Änderung.
- Madhouse und Bull-Finish feiern auch beim Leg-/Match-Gewinn; die Feier läuft dann über dem
  Sieg-Panel (Verhalten aus ADR-0038/ADR-0039 unverändert).
- Neue Trigger brauchen nur die fünf Schritte oben; Spielablauf, Registry und Overlay bleiben
  unverändert.
- Die Texte sind noch nicht auf einem echten Gerät geprüft (siehe BACKLOG, „Geräteprüfung der
  Feiern").

## Verweise

- [ADR-0006](0006-delight-schicht.md) — Delight-Schicht (Produktidee, Trigger-Liste)
- [ADR-0038](0038-delight-trigger-system.md) — Trigger-System
- [ADR-0039](0039-delight-overlay.md) — Overlay und Texte
- [ADR-0041](0041-produkt-trigger-180-waschmaschine-rentnerdreieck.md) — erste Produkt-Trigger
