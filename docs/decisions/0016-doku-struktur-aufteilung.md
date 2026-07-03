# 0016 — Doku-Struktur-Aufteilung

**Status:** Akzeptiert

## Kontext
`docs/CHECKLISTE.md` war als **Single Source of Truth** gedacht und bündelte
Arbeitsprotokoll, Projekt-Überblick, Tech-Stack, alle Design-Entscheidungen, die
Bau-Roadmap, den Backlog und das Änderungslog in **einer** Datei. Diese Datei ist
auf ~957 Zeilen / ~68 KB angewachsen und vermischte fünf Belange. Die
Checklisten-Punkte waren Epic-groß (ein Punkt = ein ganzes Feature mit 20–50
Zeilen Prosa-Notiz).

Historie: Es gab früher schon einmal `CHANGELOG.md` + `docs/decisions/`, die per
Commit `a19ee18` bewusst zugunsten der einzigen CHECKLISTE entfernt wurden. Diese
Konsolidierung wird hiermit **bewusst umgekehrt**, weil die Einzeldatei zu groß
geworden ist.

## Entscheidung
Die Doku unter `docs/` wird nach Belang aufgeteilt:

| Datei | Inhalt |
|---|---|
| `docs/README.md` | Wegweiser/Index über die Doku-Dateien + Verweis auf den Orchestrator-Loop in `CLAUDE.md` |
| `docs/ROADMAP.md` | **Nur** die atomare Bau-Checkliste (Setup + Phasen 1–6, `[ ]`/`[x]`, je Punkt Einzeiler + Link) |
| `docs/BACKLOG.md` | „Backlog / spätere Ideen" |
| `docs/CHANGELOG.md` | Chronologisches Änderungslog + die ausführlichen Umsetzungsnotizen |
| `docs/decisions/README.md` | ADR-Index (Nr., Titel, Status) |
| `docs/decisions/NNNN-thema.md` | Ein ADR pro Design-Entscheidung |
| `docs/CHECKLISTE.md` | 3-Zeilen-Pointer-Stub auf README/ROADMAP (hält Alt-Links am Leben) |

Zusätzlich wird die **Atomaritäts-Konvention** eingeführt: Ein Roadmap-Task = eine
PR-große, unabhängig mergebare Änderung; **keine mehrzeilige Prosa** im
Roadmap-Eintrag (nur Einzeiler + Link). Die bisherigen Epic-Punkte werden
**rückwirkend atomisiert**; die ausführlichen `→`-Prosa-Notizen wandern ins
CHANGELOG bzw. in die passenden ADRs.

`docs/ROADMAP.md` wird der neue **Taktgeber** für den Orchestrator-Loop (ersetzt
`docs/CHECKLISTE.md` in dieser Rolle). `CLAUDE.md` und der `dokumentar`-Agent
werden entsprechend angeglichen.

## Konsequenzen
- Löst den früheren „CHECKLISTE-only"-Ansatz aus Commit `a19ee18` ab.
- Jede Datei hat genau einen Belang; die Roadmap bleibt kurz und scanbar.
- Design-Entscheidungen sind einzeln versioniert und verlinkbar (ADRs).
- Der `dokumentar` ist ab jetzt Eigentümer von `docs/ROADMAP.md` (statt der
  CHECKLISTE) und pflegt CHANGELOG, ADRs und BACKLOG mit.
- Alt-Links auf `docs/CHECKLISTE.md` bleiben über den Stub gültig.
