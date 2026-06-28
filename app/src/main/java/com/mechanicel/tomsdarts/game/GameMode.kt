package com.mechanicel.tomsdarts.game

/**
 * Austauschbare Strategie fuer einen Dart-Spielmodus.
 *
 * Jeder Modus (X01, Cricket, Around the Clock, Count Up, ...) implementiert
 * dieses Interface mit seinem eigenen, modus-spezifischen Spielerzustand [S].
 * Die Abstraktion ist reine Domaenenlogik: kein Android-/Room-/Compose-Bezug,
 * dadurch mit reinem JUnit testbar.
 *
 * Der Zustand [S] beschreibt den Fortschritt EINES Spielers in einem Leg
 * (z.B. die Restpunktzahl bei X01). Mehrspieler-, Aufnahme- und Match-Verwaltung
 * (Reihenfolge, Sets/Legs zusammenzaehlen) sind NICHT Aufgabe der Strategie,
 * sondern der aufrufenden Engine.
 *
 * @param S Modus-spezifischer Spielerzustand. `Any` (nicht-nullable), damit der
 *   Zustand stets ein konkretes Objekt ist.
 */
interface GameMode<S : Any> {

    /** Stabile, persistierbare Kennung des Modus (z.B. "X01", "CRICKET"). */
    val key: String

    /** Menschenlesbarer Anzeigename des Modus (z.B. "501 Double Out"). */
    val displayName: String

    /**
     * Erzeugt den Startzustand EINES Spielers zu Beginn eines Legs, abgeleitet
     * aus der [config] (z.B. Restpunktzahl == [GameConfig.startScore] bei X01).
     */
    fun initialState(config: GameConfig): S

    /**
     * Verarbeitet GENAU EINEN Dart gegen den laufenden Spielerzustand [state]
     * und meldet das Ergebnis als [DartOutcome].
     *
     * Vertrag:
     * - Die Strategie ist zustandslos bzgl. Aufnahme-Grenzen (3 Darts pro
     *   Aufnahme). Das Buendeln von Darts zu Aufnahmen sowie das
     *   **Zuruecksetzen bei Bust** (Verwerfen der bereits geworfenen Darts der
     *   aktuellen Aufnahme, Rueckkehr zum Aufnahme-Startzustand) obliegt der
     *   aufrufenden Engine. `applyDart` signalisiert lediglich `bust == true`
     *   und liefert einen `newState`, den die Engine bei Bust verwirft.
     * - Bei `bust == true` ist `legWon` immer `false`.
     * - Bei `legWon == true` ist `bust` immer `false`.
     * - `scored` ist der tatsaechlich gewertete Punktwert dieses Darts; bei
     *   Bust typischerweise 0.
     *
     * Beispiel X01 (zur Veranschaulichung der Flag-Interpretation, hier NICHT
     * implementiert): Ausgehend vom Rest `r` und `dart.value`:
     * - Rest wuerde unter 0 fallen -> `bust = true`.
     * - Rest wuerde genau 1 und `config.doubleOut == true` -> `bust = true`
     *   (mit Rest 1 ist kein Double-Finish mehr moeglich).
     * - Rest wuerde genau 0, aber der letzte Dart ist kein Double und
     *   `config.doubleOut == true` -> `bust = true`.
     * - Rest wuerde genau 0 und (Double-Out erfuellt oder `doubleOut == false`)
     *   -> `legWon = true`.
     * - sonst -> regulaerer Wurf: neuer Rest = `r - dart.value`,
     *   `scored = dart.value`.
     */
    fun applyDart(state: S, dart: Dart, config: GameConfig): DartOutcome<S>
}
