package com.mechanicel.tomsdarts.delight

/**
 * Ein datengetriebener Delight-Trigger: Bedingung -> Darstellung (ADR-0006).
 *
 * Ein neuer Trigger ist genau EIN Eintrag dieser Art (siehe
 * [ProductDelightTriggers.ALL]); weder Spielablauf noch Registry muessen dafuer
 * angepasst werden.
 *
 * @param id Stabile, eindeutige Kennung des Triggers (z.B. `"180"`).
 * @param priority Rang bei mehreren passenden Triggern: der hoechste Wert gewinnt;
 *   bei Gleichstand gewinnt der zuerst registrierte.
 * @param condition Reine, seiteneffektfreie Bedingung auf der abgeschlossenen
 *   Aufnahme.
 * @param presentation Darstellung, die bei einem Treffer ausgeloest wird.
 */
data class DelightTrigger(
    val id: String,
    val priority: Int,
    val condition: (DelightVisit) -> Boolean,
    val presentation: DelightPresentation,
)

/**
 * Ergebnis einer Delight-Auswertung: der gewinnende Trigger fuer genau eine
 * Aufnahme.
 *
 * @param id Eindeutige Kennung dieses Events. Der Spielablauf vergibt sie streng
 *   monoton steigend (ab 1) je ausgeloester Feier; die UI nutzt sie zum
 *   Deduplizieren, als Seed (z.B. fuer Konfetti) und zum Quittieren
 *   (`GameViewModel.onDelightDismissed`). Rein lokale Auswertungen ohne
 *   Spielablauf (Tests) duerfen den Default 0 nutzen.
 * @param triggerId Kennung des ausgeloesten [DelightTrigger].
 * @param presentation Darstellung des Triggers (Animation + Text-Schluessel).
 * @param visit Die ausloesende Aufnahme.
 */
data class DelightEvent(
    val id: Long,
    val triggerId: String,
    val presentation: DelightPresentation,
    val visit: DelightVisit,
) {

    /** Kennung des Werfers (z.B. fuer den Spielernamen in der UI); `null` = unbekannt. */
    val playerId: Long? get() = visit.playerId
}
