# TomsDarts — Doku-Wegweiser

Diese `docs/`-Ablage ist nach Belang aufgeteilt. Was liegt wo:

| Datei | Inhalt |
|---|---|
| [ROADMAP.md](ROADMAP.md) | Die atomare Bau-Checkliste (Setup + Phasen 1–6, `[ ]`/`[x]`, je Punkt Einzeiler + Link) — der **Taktgeber** für den Bau |
| [BACKLOG.md](BACKLOG.md) | Backlog / spätere Ideen, offene Produktentscheidungen, zurückgestellte Punkte |
| [CHANGELOG.md](CHANGELOG.md) | Chronologisches Änderungslog + ausführliche Umsetzungsnotizen je erledigtem Roadmap-Punkt |
| [decisions/](decisions/README.md) | Architektur-Entscheidungen (ADRs) — ein Eintrag je bewusster Design-Entscheidung, mit Index |
| [CHECKLISTE.md](CHECKLISTE.md) | Pointer-Stub auf diese Datei + ROADMAP (hält Alt-Links am Leben; abgelöst) |

## Zur Arbeitsweise

TomsDarts wird über einen **Orchestrator-Loop** gebaut: Die CLI-Session plant und
delegiert, jede Programmieraufgabe läuft durch feste Subagent-Rollen (`designer`,
`implementer`, `tester`, `dokumentar`, `reviewer`, `fixer`). Der Loop arbeitet
[ROADMAP.md](ROADMAP.md) strikt von oben nach unten ab — genau eine offene Aufgabe
pro Durchlauf, danach Stopp und Warten auf Toms „weiter". Details zum Loop und den
Rollen stehen in [`../CLAUDE.md`](../CLAUDE.md).
