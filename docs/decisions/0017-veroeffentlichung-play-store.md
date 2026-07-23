# 0017 — Veröffentlichung im Play Store

**Status:** Akzeptiert

**Update (ADR-0023):** Bei Umsetzung der Online-Features und der AdMob-Werbung
müssen Play-Store-Data-Safety-Angaben nachgezogen werden: Ad-Datenerfassung,
Nutzer-Consent-Anforderung (UMP/DSGVO), Serverregion, Konto-Löschungs-Prozess →
[ADR-0023](0023-firebase-optionale-online-schicht.md).

## Kontext
TomsDarts ist als lokale, vollständig offline lauffähige Android-App konzipiert
(kein Login, kein Backend, keine Cloud). Eine **spätere** Veröffentlichung im
Google Play Store ist denkbar, steht aber aktuell **nicht** an. Auch wenn dieser
Schritt noch nicht ansteht, haben spätere Publishing-Anforderungen bereits jetzt
Konsequenzen für Konfiguration und Pflege, die vorausschauend beachtet werden.

## Entscheidung
Eine Play-Store-Veröffentlichung wird als **spätere Option** offengehalten (nicht
jetzt). Damit dieser Weg jederzeit gangbar bleibt, gilt bereits heute:

- Die `applicationId` (`com.mechanicel.tomsdarts`) bleibt **dauerhaft stabil** —
  sie ist der unveränderliche Identifikator einer Play-Store-App.
- `targetSdk` wird **aktuell gehalten**, um künftige Play-Store-Anforderungen an
  die Ziel-API-Ebene ohne Nachlauf zu erfüllen.

## Konsequenzen
- Ein Umbenennen der `applicationId` ist tabu, solange die Publishing-Option
  offengehalten wird.
- `targetSdk` wird regelmäßig nachgezogen (Wartungsaufwand einplanen).
- Für die tatsächliche Veröffentlichung fallen einmalig **25 USD**
  Entwicklerregistrierung an; neue Personen-Accounts erfordern zusätzlich einen
  **geschlossenen Test** sowie eine **Identitätsprüfung**.
- Die Offline-/No-Backend-Produktprinzipien bleiben davon unberührt — eine
  Veröffentlichung ändert nichts am lokalen Charakter der App.
