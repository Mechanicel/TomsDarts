# TomsDarts — Doku-Wegweiser

Diese `docs/`-Ablage ist nach Belang aufgeteilt. Was liegt wo:

| Datei | Inhalt |
|---|---|
| [ROADMAP.md](ROADMAP.md) | Die atomare Bau-Checkliste (Setup + Phasen 1–7, `[ ]`/`[x]`, je Punkt Einzeiler + Link) — der **Taktgeber** für den Bau |
| [BACKLOG.md](BACKLOG.md) | Backlog / spätere Ideen, offene Produktentscheidungen, zurückgestellte Punkte |
| [FIREBASE.md](FIREBASE.md) | Konzept der optionalen Firebase-/Online-Schicht (Login, Online-Multiplayer, Leaderboards, Freunde, AdMob-Werbung) — Grundsatz in ADR-0023 |
| [CHANGELOG.md](CHANGELOG.md) | Chronologisches Änderungslog + ausführliche Umsetzungsnotizen je erledigtem Roadmap-Punkt |
| [decisions/](decisions/README.md) | Architektur-Entscheidungen (ADRs) — ein Eintrag je bewusster Design-Entscheidung, mit Index |
| [CHECKLISTE.md](CHECKLISTE.md) | Pointer-Stub auf diese Datei + ROADMAP (hält Alt-Links am Leben; abgelöst) |

## Zur Arbeitsweise

TomsDarts wird von einem **Orchestrator** gebaut: Die CLI-Session plant, schneidet
Aufgaben zu und delegiert sie an feste Subagent-Rollen (`builder` für Code + Tests +
Doku, `designer` bei UI, `reviewer` für das unabhängige PR-Review). Der Orchestrator
arbeitet [ROADMAP.md](ROADMAP.md) von oben nach unten ab, zieht so viele Aufgaben
wie möglich am Stück durch und lässt dateidisjunkte Aufgaben parallel in eigenen
Worktrees bauen. Details zum Ablauf und den Rollen stehen in [`../CLAUDE.md`](../CLAUDE.md).
