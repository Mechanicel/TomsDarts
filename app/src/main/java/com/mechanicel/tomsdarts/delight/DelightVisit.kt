package com.mechanicel.tomsdarts.delight

import com.mechanicel.tomsdarts.game.Dart

/**
 * Eingabe-Kontext des Delight-Systems: genau EINE abgeschlossene Aufnahme
 * (3 Darts, Bust, Leg-Gewinn oder Leg-Ende), wie sie der Spielablauf an
 * [evaluateDelight] uebergibt.
 *
 * Reines Domaenen-Value-Object (kein Android/Compose/Room). Die Felder sind so
 * gewaehlt, dass Trigger-Bedingungen rein darauf formuliert werden koennen, ohne
 * das Modell zu erweitern - z.B. 180 (`dartSum == 180`), Waschmaschine/
 * Rentnerdreieck (Segmente der [darts]), Madhouse ([modeKey] X01, [checkout] und
 * letzter Dart `Dart.double(1)`), Bull-Finish ([checkout] und letzter Dart `Dart.doubleBull()`)
 * oder Ton ([modeKey] X01 und `scored >= 100`), siehe ADR-0042.
 *
 * @param darts Tatsaechlich geworfene Darts der Aufnahme in Wurf-Reihenfolge
 *   (1..3; weniger als 3 bei Bust oder Leg-Gewinn vor dem dritten Dart).
 * @param bust True, wenn die Aufnahme ein Bust war. Ob ein Bust gefeiert werden
 *   darf, entscheidet allein die Trigger-Bedingung.
 * @param modeKey Kennung des Spielmodus (siehe
 *   [com.mechanicel.tomsdarts.game.GameModeCatalog]), z.B. fuer X01-only-Trigger.
 * @param scored Vom Modus GEWERTETE Summe der Aufnahme (bei Bust 0). Modus-
 *   spezifisch; fuer die rohe Punktsumme der Darts siehe [dartSum].
 * @param checkout True, wenn der WERFER mit dieser Aufnahme das Leg selbst
 *   beendet hat (X01-Checkout, Cricket-/Around-the-Clock-Ziel-Abschluss).
 * @param legEnded True, wenn das Leg mit dieser Aufnahme entschieden ist - egal
 *   zugunsten welches Spielers (umfasst [checkout] und das rundenbasierte
 *   Leg-Ende per Rangvergleich).
 * @param playerId Kennung des Werfers; `null`, wenn unbekannt.
 */
data class DelightVisit(
    val darts: List<Dart>,
    val bust: Boolean,
    val modeKey: String,
    val scored: Int,
    val checkout: Boolean,
    val legEnded: Boolean,
    val playerId: Long?,
) {

    /**
     * Rohe Punktsumme aller Darts der Aufnahme (Summe von [Dart.value]),
     * unabhaengig von Modus und Bust - z.B. 180 bei dreimal Triple 20.
     */
    val dartSum: Int get() = darts.sumOf { it.value }
}
