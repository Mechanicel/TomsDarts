# 0006 — Delight-Schicht (stumme Bildschirm-Animationen, wie beim Bowling)

**Status:** Akzeptiert

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
