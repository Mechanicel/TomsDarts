# 0041 — Produkt-Trigger: 180, Waschmaschine, Rentnerdreieck

**Status:** Akzeptiert

## Kontext

Das Trigger-System ([ADR-0038](0038-delight-trigger-system.md)), das Feier-Overlay
([ADR-0039](0039-delight-overlay.md)) und der Schalter „Feier-Animationen"
([ADR-0040](0040-app-einstellungen-datastore.md)) sind fertig, die Produkt-Registry
`ProductDelightTriggers.ALL` war aber noch leer. [ADR-0006](0006-delight-schicht.md) legt drei
Trigger fest: 180, Waschmaschine und Rentnerdreieck. Offen war, für welche Aufnahmen sie
gelten (Modi, Bust, weniger als drei Darts), ob beim Rentnerdreieck „alle auf 19" zählt und
welche Prioritätswerte künftige Trigger einordnen.

## Entscheidung

1. **Gemeinsame Regel:** Ein Trigger zählt nur bei einer **vollständigen Aufnahme mit genau drei
   Darts und ohne Bust**. Bewertet werden die physischen Würfe (`DelightVisit.darts`), nicht die
   Wertung des Modus. Deshalb gelten alle drei Trigger **in allen Modi** (X01, Cricket, Around the
   Clock, Shanghai, Count Up, Killer). Ein Fehlwurf (Segment 0) liegt in keinem Muster und
   schließt alle drei aus. Ein Checkout mit drei Darts zählt als vollständige Aufnahme;
   Abbrüche vor dem dritten Dart (Bust, Leg-Gewinn) zählen nicht.
2. **Die Trigger** (in `delight/DelightRegistry.kt`, `ProductDelightTriggers`):

   | ID | Bedingung | Animation | Text-Schlüssel | Priorität |
   |---|---|---|---|---|
   | `180` | alle drei Darts Triple 20 | `CONFETTI` | `DelightTextKeys.ONE_EIGHTY` | 100 |
   | `washing_machine` | alle drei Darts auf Segment 20, 5 oder 1 (beliebiger Multiplier), aber nicht alle drei auf 20 | `SPIN` | `DelightTextKeys.WASHING_MACHINE` | 50 |
   | `rentnerdreieck` | alle drei Darts auf Segment 19, 7 oder 3 (beliebiger Multiplier), aber nicht alle drei auf 19 | `TRIANGLE` | `DelightTextKeys.RENTNERDREIECK` | 50 |

   Die Bedingungen sind als pure Funktionen öffentlich (`isOneEighty`, `isWashingMachine`,
   `isRentnerdreieck`) und einzeln testbar.
3. **Rentnerdreieck „nicht alle auf 19" (Orchestrator-Entscheidung):** ADR-0006 nennt die
   Ausnahme nur für die Waschmaschine. Analog gilt sie auch hier: Drei Darts auf derselben
   Zahl sind kein „Dreieck", sondern einfach ein Treffer auf die 19. Beide Muster verhalten
   sich damit gleich.
4. **Prioritätsschema** (höher gewinnt, siehe ADR-0038):
   - **100** (`PRIORITY_MAX_SCORE`): Höchstleistungen, aktuell die 180.
   - **50** (`PRIORITY_PATTERN`): Zahlenmuster (Waschmaschine, Rentnerdreieck). Sie schließen
     sich gegenseitig aus und auch die 180 aus. Gleiche Priorität ist deshalb unkritisch.
   - **unter 50:** künftige generische Kleinigkeiten (z.B. Ton ab 100, Bull). Eine 180 oder ein
     Muster gewinnt dann gegen „Ton".
   - **zwischen den Stufen:** seltene Sonderfälle wie Madhouse (Checkout auf D1) lassen sich
     dort ohne Umnummerieren einsortieren.

## Konsequenzen

- Feiern sind jetzt erstmals im Spiel sichtbar (abschaltbar über ADR-0040).
- Ein Spiel-Test, der zufällig 20/20/5 o.ä. warf, hält jetzt die Kontrollpause für die Feier.
  Tests, die nur die Pause prüfen, verwenden deshalb Aufnahmen ohne Muster (oder eine eigene
  Registry).
- Neue Trigger: Konstante für die ID, Bedingung, ein Eintrag in `ProductDelightTriggers.ALL`
  und eine Priorität aus dem Schema oben; bei neuem Text zusätzlich ein Schlüssel in
  `DelightTextKeys` samt String-Ressource.
- Animationen und Texte sind noch nicht auf einem echten Gerät geprüft (siehe BACKLOG).
