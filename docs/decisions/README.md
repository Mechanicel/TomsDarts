# Architektur-Entscheidungen (ADRs)

Jede bewusste Design-/Architektur-Entscheidung von TomsDarts ist als eigener,
leichtgewichtiger ADR (Architecture Decision Record) festgehalten. Format:
Titel, Status, Kontext, Entscheidung, Konsequenzen.

Nummeriert wird chronologisch. Die Reihenfolge 0001–0015 entspricht der Reihenfolge,
in der die Entscheidungen ursprünglich in `docs/CHECKLISTE.md` unter
„Design-Entscheidungen (festgelegt)" standen.

| Nr. | Titel | Status |
|---|---|---|
| [0001](0001-profile.md) | Profile | Akzeptiert |
| [0002](0002-spielmodi.md) | Spielmodi | Akzeptiert |
| [0003](0003-eingabe-ziffernblock.md) | Eingabe (Ziffernblock) | Akzeptiert |
| [0004](0004-datenhaltung-throw-level.md) | Datenhaltung (throw-level) | Akzeptiert |
| [0005](0005-analytics.md) | Analytics | Akzeptiert |
| [0006](0006-delight-schicht.md) | Delight-Schicht | Akzeptiert |
| [0007](0007-datenmodell-room.md) | Datenmodell (Room) | Akzeptiert |
| [0008](0008-datenmodell-entscheidungen.md) | Datenmodell-Entscheidungen | Akzeptiert |
| [0009](0009-persistenz-tech.md) | Persistenz-Tech | Akzeptiert |
| [0010](0010-test-strategie-datenschicht.md) | Test-Strategie Datenschicht | Akzeptiert |
| [0011](0011-repository-di-schicht.md) | Repository-/DI-Schicht | Akzeptiert |
| [0012](0012-ui-viewmodel-schicht.md) | UI-/ViewModel-Schicht | Akzeptiert |
| [0013](0013-spielmodi-domaenenlogik.md) | Spielmodi-Domänenlogik | Akzeptiert |
| [0014](0014-spiel-engine-eingabe-kopplung.md) | Spiel-Engine & Eingabe-Kopplung | Akzeptiert |
| [0015](0015-mehrspieler-match-legs-sets.md) | Mehrspieler-Match / Legs/Sets | Akzeptiert |
| [0016](0016-doku-struktur-aufteilung.md) | Doku-Struktur-Aufteilung | Akzeptiert |
| [0017](0017-veroeffentlichung-play-store.md) | Veröffentlichung im Play Store | Akzeptiert |
| [0018](0018-setup-screen-startpunkt.md) | Setup-Screen: Startpunkt-Auswahl | Akzeptiert |
| [0019](0019-setup-teilnehmerverwaltung.md) | Setup-Screen: Teilnehmerverwaltung | Akzeptiert |
| [0020](0020-spieler-loeschen-set-null.md) | Spieler-Loeschen per SET_NULL | Akzeptiert |
| [0021](0021-undo-cross-turn-replay.md) | Undo über Aufnahmen hinweg (Replay-Ansatz) | Akzeptiert |
| [0022](0022-modus-infrastruktur.md) | Modus-Infrastruktur (Gegner-Lesezugriff, Katalog, UI-Abstraktion) | Akzeptiert |
| [0023](0023-firebase-optionale-online-schicht.md) | Firebase als optionale Online-Schicht | Akzeptiert |
| [0024](0024-standard-cricket-katalog-modus.md) | Standard-Cricket als erster Katalog-Modus | Akzeptiert |
| [0025](0025-around-the-clock-katalog-modus.md) | Around the Clock als zweiter Katalog-Modus | Akzeptiert |
| [0026](0026-turn-review-kontrollpause.md) | Kontrollpause nach dem dritten Dart (Turn-Review) | Akzeptiert |
| [0027](0027-undo-im-gewonnen-zustand.md) | Undo im Gewonnen-Zustand (Leg-/Match-Gewinn) | Akzeptiert |
| [0028](0028-leg-ende-ohne-werfer-sieg.md) | Leg-Ende ohne Werfer-Sieg (legEnded/legScore) — Vertragserweiterung für rundenbasierte Modi | Akzeptiert |
| [0029](0029-shanghai-katalog-modus.md) | Shanghai als vierter Katalog-Modus (erster Nutzer der legEnded-Infrastruktur) | Akzeptiert |
| [0030](0030-count-up-katalog-modus.md) | Count Up als fünfter Katalog-Modus (zweiter Nutzer der legEnded-Infrastruktur) | Akzeptiert |
| [0031](0031-modus-infrastruktur-killer-spieler-identitaet-eliminierung-gegner-sicht.md) | Modus-Infrastruktur für Killer — Spieler-Identität, Eliminierung, Gegner-Sicht | Akzeptiert |
| [0032](0032-killer-sechster-katalog-modus.md) | Killer als sechster Katalog-Modus | Akzeptiert |
| [0033](0033-modus-auswahl-raster-setup.md) | Modus-Auswahl im Setup als Raster (2/3 Spalten, lokalisierte Labels) | Akzeptiert |
| [0034](0034-analytics-datenzugriff.md) | Analytics-Datenzugriff: flacher StatsDao + pures Domänenmodell | Akzeptiert |
| [0035](0035-analytics-kennzahlen-definitionen.md) | Analytics-Kennzahlen: Definitionen (Average, First-9, Checkout-Quote, Trefferverteilung) | Akzeptiert |
| [0036](0036-analytics-sequenz-auswertungen.md) | Analytics-Sequenz-Auswertungen: erster Dart, Dart-Positionen, Übergänge, Aufnahme-Muster | Akzeptiert |
| [0037](0037-analytics-screens.md) | Analytics-Screens: Einstieg über Overflow-Menü, Balkenliste statt Heatmap, Kachelraster, Abschnitts-Modell, Modus-Filter, Abschnitt „Wurfmuster", Match-Statistik mit Einstiegen | Akzeptiert |
| [0038](0038-delight-trigger-system.md) | Delight-Trigger-System: pures Regelwerk, Auslösezeitpunkt, Event-Muster, gehaltene Kontrollpause | Akzeptiert |
| [0039](0039-delight-overlay.md) | Delight-Overlay: Schichten, Anzeigedauer, Kontrollpause, Reduced Motion, Rotation | Akzeptiert |
| [0040](0040-app-einstellungen-datastore.md) | App-Einstellungen: DataStore Preferences, Schalter „Feier-Animationen" | Akzeptiert |
| [0041](0041-produkt-trigger-180-waschmaschine-rentnerdreieck.md) | Produkt-Trigger: 180, Waschmaschine, Rentnerdreieck (Regeln, Prioritätsschema) | Akzeptiert |
| [0042](0042-weitere-trigger-madhouse-bull-ton.md) | Weitere Trigger: Madhouse, Bull-Finish, Ton (Prioritätsschema komplett, Rezept „Neuen Trigger hinzufügen", Muster mit mind. zwei Segmenten) | Akzeptiert |
