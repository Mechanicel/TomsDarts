# TomsDarts — Firebase-/Online-Konzept

> Konzeptebene zu den optionalen Online-Features, die in Roadmap-Phase 7 umgesetzt
> werden. Der Grundsatz liegt in [ADR-0023](decisions/0023-firebase-optionale-online-schicht.md);
> die atomare Bau-Checkliste findet sich in [ROADMAP.md](ROADMAP.md#phase-7--online-firebase-opt-in).

## Grundprinzip

**Offline-Kern garantiert, Online strikt opt-in.** Die App lädt und läuft
vollständig offline, ohne Netzwerkverbindung oder Benutzerkonto. Alle Firebase-Features
(Login, Online-Multiplayer, Leaderboards, Freunde) sind reine Zusatzschicht.
Nutzer, die kein Konto haben oder online-Funktionen nicht nutzen, sehen kein
Feature-Verlust und kein Tracking.

Sobald ein Nutzer sich mit Google Sign-In authentifiziert oder Online-Features
nutzt, werden:
- Lokale Daten optional ins Cloud-Profil synchronisiert.
- Werbung (Google AdMob) geladen und angezeigt.
- Ad-Tracking (Impressionen, Klicks, zielgruppen-basiert) aktiviert.

Diese Trennung ist das Kernmerkmal: **Offline bleibt frei, Online trägt Werbung**.

## Features

### Login (Firebase Auth, Google Sign-In)

**Einziger Anbieter:** Google via Android Credential Manager (Unified Sign-In,
Passkeys, biometrisch, PINs). Keine eigene Registrierung (E-Mail/Passwort nur
als optionale Ergänzung später).

**Verhalten:**
- Tap „Mit Google anmelden" in der App.
- Credential Manager öffnet Auswahlmenü (bestehende Konten oder neues Konto).
- Nach Authentifizierung: Nutzer-Email + Google ID lokal speichern, lokales
  Profil mit Cloud-Profil verlinken (Mapping).
- **Logout:** Cloud-Link-Auflösen, Gerätedaten bleiben lokal.
- **Konto-Löschung:** Cloud-Profil + alle Cloud-Daten (Freunde, Leaderboard-Einträge)
  löschen; lokale Daten bleiben (Spieler, Matches, Stats).

**Anti-Pattern:** Kein Zwangs-Login, keine Netzwerk-Blockade für Offline-Nutzung.

### Echtzeit-Online-Multiplayer

**Match-Architektur:**
- Ein Spieler erstellt ein Online-Match in der App (Spielmodus, Regeln).
- Ein oder mehrere Freunde erhalten eine Einladung (über das Freunde-System).
- Beigetroffene Spieler sehen Live-Würfe der anderen Spieler (Echtzeit-Sync).

**Synchronisation:**
- Würfe eines lokalen Spiels sind zunächst lokal (Room). Beim Beigetreten zu
  einem Online-Match werden Würfe live als **Events** an die anderen Teilnehmer
  übertragen.
- **Source of Truth bleibt lokal:** Jeder Teilnehmer führt das Spiel lokal auf
  seinem Gerät durch (mit MatchEngine); Würfe werden als Events synchronisiert.
- **Disconnect-Handling:** Netzwerkabbruch → Match pausiert, bei Wiederkehr
  können lokale Würfe nachgezogen werden (Replay/Conflict-Auflösung).

**Lobby/Einladung:**
- Nutzer kann Freunde per In-App-Einladung zu einem Match laden.
- Freund akzeptiert → tritt dem Spiels bei.
- Start-Kriterium: Alle geladen-en Freunde Bereit signalisiert haben (oder Timeout).

### Globales Leaderboard

**Kennzahlen-Kandidaten** (aus [ADR-0005](decisions/0005-analytics.md) + Phase-7-Entscheidung):
- **3-Dart-Average:** Durchschnittliche Punktzahl pro Dreier-Satz.
- **First-9-Average:** Durchschnittliche Punktzahl in den ersten 9 Darts.
- **Checkout-Quote (%):** Anteil erfolgreich geschlossener Beine / Spiele.
- Evtl. weitere: Trefferverteilung, Beste Session, …

**Aggregation & Abfrage:**
- Lokal berechnete Kennzahlen werden (opt-in) ins Cloud-Profil uploaded.
- Backend (Cloud Function oder aggregierte Firestore-Queries) berechnet Rankings.
- Client fragt Top-100 (oder Top-10 unter Freunden) ab → zeigt Leaderboard-Screen.

**Anti-Cheat:**
- Bei Selbsteingabe von Würfen besteht Cheat-Risiko. Mitigationen:
  - Vertrauens-Scores auf Basis von Match-Konsistenz (z. B. statistische Ausreißer).
  - Markierung verdächtiger Einträge / Ausschluss aus Ranking.
  - Später: Video-Verifikation (TBD).

### Freunde-System

**Operationen:**
- **Code/Suche:** Freunde via eindeutigem Code oder Nutzernamen-Suche hinzufügen.
- **Anfragen:** „Anfrage senden" → Empfänger sieht „Anfrage hängig" → akzeptieren oder
  ablehnen.
- **Verwaltung:** Freund entfernen (bidirektional).

**Datenmodell (Firestore):**
```
users/{userId}
  ├── email: string
  ├── displayName: string
  └── …

friendships/{userId}
  └── {friendUserId}: { status: "accepted", createdAt: timestamp }

friendRequests/{userId}
  └── {senderId}: { sentAt: timestamp, status: "pending" }
```

**Offline-Fallback:** Freundesliste wird lokal gecacht; App funktioniert mit
veraltetem Stand, synct bei Verbindung nach.

### Freunde-Leaderboards

**Funktionalität:** Dieselben Kennzahlen wie das globale Leaderboard, aber
gefiltert auf den eigenen Freundes-Kreis.

**UI-Integration:** Leaderboard-Screen mit Umschalter „Alle / Freunde"
(Toggle oder Tabs).

## Firebase-Produktzuordnung grob

- **Firebase Auth:** Google Sign-In via Credential Manager.
- **Firestore:** Nutzer-Profile, Freundschaften, Freunde-Anfragen, Rankings
  (Leaderboard-Einträge). Security Rules sind **verpflichtend** (kein öffentlicher
  Lesezugriff auf alle Nutzer-Daten).
- **Firestore vs. Realtime Database:** Für Live-Multiplayer-Sync (Würfe als Events)
  bleibt die Wahl **offen**. Firestore ist transaktionssicher, Realtime DB ist
  schneller für echtzeitige Sync. Proof-of-Concept in Phase 7 entscheidet.
- **Cloud Functions:** Optional für Backend-seitige Leaderboard-Aggregation
  (z. B. täglich Kennzahlen rollup-en); kann auch client-seitig erfolgen.
- **Google Mobile Ads SDK:** AdMob-Banners/Interstitials (siehe unten).

**Explizit ausgeschlossen:**
- Firebase Analytics / Crashlytics (keine Telemetrie).
- Firebase Remote Config (keine dynamische Feature-Gating ohne lokale Kontrolle).

## Werbung Monetarisierung

**Platzierung:** Google AdMob-Werbung wird **nur im Online-Modus** geladen und
angezeigt. Offline-Kern zeigt keine Anzeigen.

**Formate (Kandidaten):**
- **Banner (320x50 / 320x100):** Unten im Match-Screen während Online-Spiels.
- **Interstitial (Full-Screen):** Nach Match-Ende (optional, nicht aufdringlich).
- **Rewarded:** TBD (z. B. freie Match-Replays für Ad-Anschauen).

**Ad-Consent (UMP / DSGVO):**
- Google User Messaging Platform (UMP SDK) einbinden.
- Nutzer beim ersten Online-Login fragen: „Personalisierte Werbung erlauben?"
  (mit Opt-Out-Option).
- DSGVO-konform: Essentielle Cookies (Auth, Session) funktionieren auch bei Opt-Out;
  personalisierte Ads nur mit Consent.

**Verhältnis zum Tracking-Prinzip:**
- Offline-Kern: **Null Werbung, null Tracking.**
- Online-Modus: **Werbung + Ad-Tracking akzeptiert** (Impressionen, Klicks,
  Zielgruppen-Daten).
- Bewusste Ausnahme von „keine Tracking-Abhängigkeiten" (siehe ADR-0023).
- Firebase Analytics/Crashlytics weiterhin ausgeschlossen.

## Verhältnis zum Offline-Kern

**Room bleibt Source of Truth:** Alle lokalen Spielstände, Profile, Statistiken
sind in Room persistiert. Firestore-Daten sind **Ergänzung**, nicht Quelle.

**Sync-Modell:**
- Lokale Matches/Würfe werden in Room geschrieben wie bisher.
- Opt-in: Nutzer kann lokale Kennzahlen ins Cloud-Profil syncen (Button „Statistik teilen").
- Offline-Käufe (lokale Matches) werden lokal persistiert und — bei Verbindung —
  optional gespeichert; kein Zwangs-Upload.

**Online-Matches zusätzlich lokal persistiert:** Online-Multiplayer-Spiele
werden lokal wie normale Matches in Room gespeichert + zusätzlich in Firestore
(für Replikation/Freunde-Einsicht).

**Lokaler Mehrspieler unverändert:** Der bisherige lokale 2-Spieler-Modus
([ADR-0015](decisions/0015-mehrspieler-match-legs-sets.md)) bleibt komplett offline,
kein Firebase-Call. Online-Multiplayer ist ein zusätzlicher Spielmodus.

**Offline-Kern + Werbung:** Werbung wird nur geladen, wenn der Nutzer online ist
und authentifiziert. Offline-Nutzer laden kein AdMob-SDK, sehen keine Ads.

## Datenschutz

**Privacy Policy (Doku nachziehen):**
- Abschnitt zu Firebase Auth (Google-Login, Email-Speicherung).
- Abschnitt zu Firestore (Nutzer-Profil, Freundschaften, Cloud-Leaderboard-Daten).
- Abschnitt zu Google AdMob (Ad-Tracking, Personalisierung).
- Nutzer-Rechte: Zugriff auf Cloud-Daten, Berichtigung, Löschung / Kontolöschung.

**Play-Store-Data-Safety-Formular (ADR-0017):**
- Ad-Datenerfassung: ja (bei Online-Nutzung).
- Nutzer-Daten: E-Mail (für Auth), Profil (Cloud), Ad-Impression-Log.
- Sicherheitspraktiken: HTTPS, Authentifizierung, Security Rules.
- Data-Sharing: Google Ads (für Werbung), kein Datenverkauf.

**DSGVO / Serverregion:**
- Firebase-Serverregion **EU** wählen (z. B. `europe-west1` für Europäer).
- Datenschutzbefähigung: Google Data Processing Amendment (DPA).
- Konto-Löschungs-Prozess: In-App-Menü → „Konto löschen" → Cloud-Daten + Auth
  gelöscht, lokale Daten bleiben.

**Datensicherheit (Security Rules):**
```
// Beispiel: Nutzer darf nur sein eigenes Profil lesen
match /users/{userId} {
  allow read: if request.auth.uid == userId;
  allow write: if request.auth.uid == userId;
}

// Freundschaften: beidseitig
match /friendships/{userId} {
  allow read: if request.auth.uid == userId;
  allow write: if request.auth.uid == userId;
}
```

## Offene Fragen

- **Firestore vs. Realtime Database:** Welches Produkt für Live-Match-Sync?
  Prototyping in Phase 7 entscheidet.

- **Disconnect- & Konflikt-Handling:** Wie werden Würf-Events bei Netzwerkabbruch
  / doppelten Eingaben aufgelöst? Race-Conditions?

- **Leaderboard-Metrik & Cheat-Schutz:** Welche Kennzahl ist die Haupt-Kennzahl
  (3DA vs. Checkout-Quote)? Wie stark sollen Cheat-Schutz-Algorithmen sein
  (markieren, ausschließen, Manual-Review)?

- **Identitätsmodell:** Bleibt Google-Email der Anzeigename, oder kann der Nutzer
  einen Custom-Namen setzen? Profil↔Konto-Mapping bei Neugeräten?

- **E-Mail/Passwort später:** Google Sign-In einziger Auth-Provider jetzt; E-Mail/PW
  als Fallback später erwünscht?

- **Kosten & Free-Tier:** Firebase Free-Tier abdeckt die erwartete Last? Ab wann
  zahlungspflichtig?

- **Firebase-SDK im APK:** SDK immer gebündelt (auch für Offline-Nutzer) oder
  Flavor-Trennung (z. B. `offline`-Flavor ohne Firebase)? Größen-Impact?

- **Ad-Formate / Platzierung:** Wo sollten Ads sichtbar sein (nur in
  Leaderboard-Screen, oder überall im Online-Modus)? Interstitials nach jedem
  Match oder sparsamer?

- **Personalisierte vs. nicht-personalisierte Ads:** Nur personalisierte Ads
  anzeigen (höhere Einnahmen, Consent erforderlich), oder auch non-personalized
  als Fallback?

- **AdMob-SDK im APK:** AdMob-SDK in allen Builds, oder nur für `online`-Flavor?
  (Betrifft Offine-Nutzer-Größe + Test-Komplexität.)
