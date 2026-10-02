# 0006 — Delight-Schicht (stumme Bildschirm-Animationen, wie beim Bowling)

**Status:** Akzeptiert

**Update (ADR-0041):** Die drei Trigger sind umgesetzt. Sie gelten nur für vollständige
Aufnahmen mit genau drei Darts ohne Bust, in allen Modi. Beim Rentnerdreieck zählt
„alle drei auf 19" analog zur Waschmaschine nicht. Prioritäten: 180 = 100, Muster = 50 →
[ADR-0041](0041-produkt-trigger-180-waschmaschine-rentnerdreieck.md).

**Update (ADR-0042):** Madhouse (Checkout auf D1), Bull-Finish (Checkout auf Doppel-Bull) und
Ton (X01, ab 100) sind umgesetzt. Waschmaschine/Rentnerdreieck verlangen jetzt mindestens zwei
verschiedene Segmente (1/1/1 o.ä. feiert nicht mehr). Prioritäten: 180 = 100, Madhouse = 80,
Bull-Finish = 70, Muster = 50, Ton = 10 →
[ADR-0042](0042-weitere-trigger-madhouse-bull-ton.md).

## Kontext
Besondere Wurf-Ereignisse sollen gefeiert werden — ähnlich den Animationen an
einer Bowlingbahn, aber ohne Ton.

## Entscheidung
- **Kein Ton.** Animation als Vollbild-Overlay, verschwindet automatisch.
- Trigger-System **datengetrieben und erweiterbar** (pro Trigger: Bedingung,
  Icon/Animation, Text), gespeist aus den throw-level-Daten.
- Bereits festgelegte Trigger:
  - **180** (Maximum) — Feier mit Konfetti.
  - **Waschmaschine** — die drei Darts liegen alle in {20, 5, 1}, aber nicht alle
    auf 20 (Streuen um die 20). Dreh-Animation.
  - **Rentnerdreieck** — die drei Darts liegen alle in {19, 7, 3}. Dreieck.
- Weitere Trigger später möglich (z. B. Madhaus / D1, Bull, Ton ab 100, …).

## Konsequenzen
- Braucht die throw-level-Daten aus [ADR-0004](0004-datenhaltung-throw-level.md).
- Umsetzung erst in Phase 6 (Delight-Schicht).
