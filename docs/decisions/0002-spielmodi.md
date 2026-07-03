# 0002 — Spielmodi

**Status:** Akzeptiert

## Kontext
Darts kennt viele Spielarten. Die App soll konfigurierbar sein, ohne bei jedem
neuen Modus umgeschrieben werden zu müssen.

## Entscheidung
- Kern-Modus zuerst: **X01 mit 501 und Double-Out**.
  → **Umgesetzt (Phase 2 / „X01-Modus").** `X01Mode : GameMode<X01State>`
    (`key="X01"`); **startScore-agnostisch**, deckt damit **301/501/701** über
    `GameConfig.startScore` ab — siehe [ADR-0013](0013-spielmodi-domaenenlogik.md).
- Geplantes Repertoire danach (Reihenfolge offen, anpassbar):
  301/701, Cricket, Around the Clock, Shanghai, Count Up / High Score, Killer.
  → **301/701 sind bereits durch den startScore-agnostischen `X01Mode` abgedeckt**
    (kein eigener Modus, nur andere `GameConfig.startScore`); die Konfig-Auswahl
    folgt in Phase 3 (Spiel-Setup-Screen).
- Jeder Modus wird über das gemeinsame Strategie-Interface umgesetzt, damit
  „neuer Modus" nicht „App umschreiben" bedeutet.
  → **Umgesetzt (Phase 2 / „Strategie-Interface").** Das gemeinsame Interface ist
    `GameMode<S>` im Paket `com.mechanicel.tomsdarts.game` (siehe
    [ADR-0013](0013-spielmodi-domaenenlogik.md)).

## Konsequenzen
- Neue Modi werden als Strategie ergänzt, nicht als App-Umbau.
- Startwert-Varianten (301/501/701) sind **kein** eigener Modus.
