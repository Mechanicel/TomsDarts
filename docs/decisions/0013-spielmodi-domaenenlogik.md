# 0013 — Spielmodi-Domänenlogik

**Status:** Akzeptiert

## Kontext
Festgelegt ab Phase 2. Der in [ADR-0002](0002-spielmodi.md) beschlossene
Strategie-Ansatz wird konkret ausgearbeitet — als pure, testbare Domänenlogik.

## Entscheidung
- **Austauschbare Modi über generisches Strategie-Interface `GameMode<S : Any>`**
  (Paket `com.mechanicel.tomsdarts.game`): Jeder Modus implementiert seine Regeln
  mit einem eigenen, modus-spezifischen Spielerzustand `S`. So bedeutet „neuer Modus"
  nicht „App umschreiben".
- **Pure Domänenlogik, strikt entkoppelt von Persistenz und UI:** Das `game`-Paket
  hat **keine** Android-/Room-/Compose-Abhängigkeit und ist mit reinem JUnit (ohne
  Robolectric) testbar. Die Value-Objekte sind bewusst von den Room-Entities getrennt:
  `Dart` ≠ `data.entity.Throw` (keine IDs/Timestamps), `GameConfig` ≠ `data.entity.Match`.
- **Bust-/legWon-Semantik:** `applyDart` verarbeitet **genau einen** Dart. Die Strategie
  ist **zustandslos bzgl. Aufnahme-Grenzen** (3 Darts pro Aufnahme) — das **Bündeln zu
  Aufnahmen und das Bust-Zurücksetzen** (Verwerfen der bereits geworfenen Darts,
  Rückkehr zum Aufnahme-Startzustand) verantwortet die **aufrufende Engine**, nicht die
  Strategie. `applyDart` meldet nur `bust == true` und liefert einen `newState`, den die
  Engine bei Bust verwirft. Invariante: **`bust` XOR `legWon`** (nie beide gleichzeitig).
- **`GameConfig` vs. Persistenz — Modus-Identität über `key`:** `GameConfig` ist das
  Domänen-Konfig (Startpunkte, Double-Out, Legs/Sets); der Modus wird über `GameMode.key`
  (stabile, persistierbare Kennung) identifiziert — **kein `modeType`-Feld in `GameConfig`**.
  Die spätere Engine-/Mapping-Schicht führt `GameConfig` + `key` mit der `Match`-Entity
  (`modusTyp`/`modeType`) zusammen.
- **Erweiterbarkeit (YAGNI):** Modus-spezifische Konfigfelder (z.B. Cricket-Zahlen,
  Around-the-Clock-Optionen, Count-Up-Ziel) werden **bewusst NICHT vorab** in `GameConfig`
  aufgenommen, sondern bei Bedarf modusspezifisch ergänzt — statt `GameConfig` aufzublähen.
- **X01 als erste konkrete Strategie (Phase 2):** `X01Mode : GameMode<X01State>`
  (`key="X01"`) ist die erste echte Modus-Implementierung. Bewusst
  **startScore-agnostisch** — 301/501/701 sind **ein** Modus mit unterschiedlicher
  `GameConfig.startScore`, **kein** separater Modus pro Startwert. Double-Out wird
  über `GameConfig.doubleOut` gesteuert; Doppel-Bull (50) zählt beim Finish als
  Double (`Dart.isDouble`). Eigener Zustands-Typ `X01State(remaining)` statt nacktem
  `Int`, damit der Zustand lesbar und später erweiterbar bleibt (z.B. Statistik),
  ohne den `GameMode`-Vertrag zu brechen.
- **`X01Mode` vertraut auf valide Eingaben (keine Guards):** Eingabe-Robustheit ist
  bewusst **nicht** in `X01Mode` — Plausibilitätsprüfung von Darts/Config ist Sache
  der späteren Engine bzw. eine Produktentscheidung (vgl. `Dart.isValid` +
  Config-Validierung). Siehe Backlog „Engine validiert Darts/Config". Die Tests
  dokumentieren das IST-Verhalten (kein Guard gegen `startScore <= 0`, `isValid` wird
  in `applyDart` nicht konsultiert, negativer `dart.value` erhöht den Rest).

## Konsequenzen
- Neue Modi = neue Strategie, kein App-Umbau.
- Aufnahme-Bündelung, Bust-Revert und Validierung liegen in der Engine (siehe
  [ADR-0014](0014-spiel-engine-eingabe-kopplung.md)), nicht in der Strategie.
- 301/501/701 sind kein separater Modus.
