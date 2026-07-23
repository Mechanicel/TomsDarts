# 0023 — Firebase als optionale Online-Schicht

**Status:** Akzeptiert

## Kontext

Seit der Gründung von TomsDarts (ADR-0001, ADR-0009, ADR-0017) ist die App als
**vollständig offline funktionierendes, lokales Produkt ohne Login, Backend oder
Cloud-Abhängigkeiten** konzipiert. Diese Grundfestschreibung hat sich bewährt und
bleibt erhalten: Der Offline-Kern ist und bleibt werbe- und trackingfrei, keine
Netzwerkverbindung ist zwingend erforderlich.

**Im Juli 2026 hat Tom eine neue Produktentscheidung getroffen:** TomsDarts wird
um **optionale Online-Features** erweitert — nicht als Ersatz des Offline-Kerns,
sondern als reine, strikt opt-in-Zusatzschicht. Gleichzeitig wird die Monetarisierung
durch **Google AdMob-Werbung, jedoch ausschließlich im Online-Modus**, realisiert.

Diese Entscheidung präzisiert die alt-geltenden ADRs (0001, 0009, 0015, 0017) und
bricht bewusst mit dem bisherigen absoluten Prinzip „keine Tracking-/Analytics-Abhängigkeiten"
— aber nur für den Online-Modus. Der Offline-Kern bleibt unverändert.

## Entscheidung

### Optionale Online-Schicht statt Zwangs-Login oder Cloud-Voraussetzung

**Grundsatz:** Firebase-Online-Features sind **rein optional**. Die App lädt und
läuft vollständig offline, ohne jede Netzwerkverbindung oder ein Benutzerkonto zu
erfordern. Nutzer, die die App nur lokal nutzen, erleben keinen Feature-Verlust
und kein Tracking.

**Feature-Umfang (für Details → FIREBASE.md):**
- **Login via Google Sign-In** — Firebase Auth, Google einziger Anbieter
  (via Android Credential Manager); keine eigene Registrierung. E-Mail/Passwort
  nur als optionale, spätere Ergänzung.
- **Echtzeit-Online-Multiplayer** — Live-Matches zwischen Freunden, Würfe-Sync,
  Disconnect-Handling.
- **Globales Leaderboard** — Kennzahlen-Aggregation auf Basis throw-level-Daten
  (3-Dart-Average, Checkout-Quote, u. ä., aus ADR-0005).
- **Freunde-System** — Code/Suche, Anfragen (senden/annehmen/ablehnen), Verwaltung.
- **Freunde-Leaderboards** — gleiche Kennzahlen wie globales Leaderboard,
  gefiltert auf Freundes-Kreis.

### Werbung (Monetarisierung): Google AdMob, ausschließlich im Online-Modus

**Entscheidung:** Google AdMob-Werbung wird integriert, **aber nur im Online-Modus**,
d. h. nur wenn der Nutzer authentifiziert und Online-Features nutzt. Der reine
Offline-Betrieb bleibt strikt werbefrei und trackingfrei.

**Rationale:** Werbung ist ein marktübliches Monetarisierungs-Modell; AdMob-Tracking
(Ad-Klicks, Impressionen, einfache Nutzer-Kriterien) ist bei Werbung unvermeidbar.
Dieses Tracking bleibt jedoch **auf den Online-Modus beschränkt**. Offline-Nutzer
laden kein Ad-SDK, sehen keine Werbung, unterliegen keinem Tracking.

### Firebase-Produktzuordnung (grob, technische Details in FIREBASE.md)

- **Firebase Auth:** Google Sign-In via Credential Manager.
- **Firestore:** Nutzer-Profile, Freunde-Verzeichnis, Freunde-Anfragen, globales
  + Freunde-Leaderboards (skalierbar, Security Rules verpflichtend).
- **Firestore vs. Realtime DB:** Die Wahl zwischen Firestore und Realtime Database
  (für Live-Multiplayer-Sync) bleibt **offen** — Entscheidung in Phase 7 im Prototyping
  oder Proof-of-Concept.
- **Cloud Functions (optional):** Ggf. Backend-seitige Aggregation von Leaderboard-Daten
  (kein Zwang; lässt sich auch client-seitig lösen).
- **Security Rules:** Verpflichtend, um Nutzer-Daten zu schützen (kein öffentlicher
  DB-Zugriff).
- **Explizit ausgeschlossen:**
  - Firebase Analytics / Crashlytics (keine Telemetrie über Nutzerverhalten).
  - Firebase Remote Config (keine dynamic Feature-Gating ohne Kontrolle).

### Verhältnis zu bisherigen Prinzipien

**ADR-0001 (Profile):** Lokale Profile bleiben Basis und Source of Truth. Das
Konto (Firebase Auth) ist **optional**, kann mit einem lokalen Profil verlinkt
werden (Mapping), muss aber nicht.

**ADR-0009 (Persistenz-Tech):** Room bleibt die lokale, alleinige Persistenz.
Firestore-Daten sind reine opt-in-Ergänzung, nie Quelle of Truth.

**ADR-0015 (Mehrspieler):** Lokaler Mehrspieler (bisherig, auf einem Gerät)
bleibt unverändert. Online-Multiplayer kommt als zusätzlicher, optionaler Modus
hinzu.

**ADR-0017 (Play Store):** Play-Store-Datenschutzangaben (Data-Safety-Formular)
müssen nachgezogen werden: Ad-Datenerfassung deklarieren, Nutzer-Consent-Anforderung
(UMP/DSGVO), Serverregion und Konto-Löschungs-Prozess dokumentieren.

### Produktprinzipien: bewusste Aufweichung des Tracking-Verbots

**Alt-Prinzip (CLAUDE.md):** „Keine Tracking-/Analytics-/Telemetrie-Abhängigkeiten.
Keine Drittanbieter-SDKs, die Nutzerverhalten erfassen."

**Neu-Prinzip (ab Commit 5):** Der Offline-Kern ist **uneingeschränkt** werbefrei
und trackingfrei. **Im opt-in-Online-Modus** ist Google AdMob-Werbung inkl. üblichem
Ad-Tracking (Impressionen, Klicks, zielgruppen-basierte Anpassung) bewusst zugelassen
und akzeptiert. Firebase Analytics und Crashlytics bleiben explizit ausgeschlossen;
AdMob ist die einzige zugelassene Ausnahme.

**Timing:** Phase-7-Umsetzung; vorher keine Firebase/AdMob-Abhängigkeiten im Build.

## Konsequenzen

- **Neue Dependencies erst mit Phase 7:**
  - Firebase BoM + Einzelmodule (Auth, Firestore, ggf. Realtime DB).
  - `google-services.json` (aus Firebase Console) ins Repo aufnehmen (nicht `.gitignore`).
  - Gradle-Plugin: `com.google.gms.google-services` in top-level `build.gradle.kts`.
  - Google Mobile Ads SDK (`com.google.android.gms:play-services-ads`).

- **CLAUDE.md-Umformulierung:** Produktprinzipien präzisieren (siehe Commit 5).

- **Datenschutz/Compliance:**
  - **Privacy Policy:** Neue Abschnitte zu Firebase Auth, Firestore-Daten, AdMob-Werbung,
    Nutzer-Datenrechte (Zugriff, Berichtigung, Löschung).
  - **Play-Store-Data-Safety-Formular** (ADR-0017): Ad-Datenerfassung deklarieren,
    Nutzer-Consent-Anforderung (UMP), Serverregion, Datenschutzbefähigung,
    Konto-Löschungs-Prozess.
  - **DSGVO/Serverregion:** Firebase-Serverregion (z. B. EU) auswählen; bei
    Personendaten im EU-Raum GDPR-konform arbeiten.
  - **AdMob-Nutzer-Consent (UMP):** Google User Messaging Platform einbinden,
    um Nutzer vor Ad-Personalisierung zu fragen (verpflichtend bei Werbung).

- **Verhältnis zu Alt-ADRs bleibt stabil:**
  - ADR-0001/0009/0015 bleiben in voller Kraft (lokale Basis, optionale Schicht).
  - ADR-0017 wird präzisiert (Datenschutz-Updates für Play Store).
  - ADR-0005 wird ggf. erweitert (Leaderboard-Kennzahlen definieren).

- **Testing & QA:**
  - Unit-Tests für Offline-Kern unverändert.
  - Neue Integration-Tests für Firebase-Schicht (Firestore Security Rules,
    Auth-Flows, Leaderboard-Abfragen).
  - Instrumented-Tests auf Emulator mit Firebase Emulator Suite (lokal entwickeln,
    ohne echten Firebase-Projekt).
