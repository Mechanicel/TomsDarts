package com.mechanicel.tomsdarts.ui.delight

import com.mechanicel.tomsdarts.delight.DelightAnimation

/** Platzhalter-ID "noch keine Feier gezeigt" (Event-IDs beginnen bei 1). */
const val NO_DELIGHT_ID: Long = 0L

/** Platzhalter fuer "Werfer unbekannt" im gespeicherten Zustand (Room-IDs sind >= 1). */
private const val NO_PLAYER_ID: Long = -1L

/**
 * Die aktuell angezeigte Feier, wie sie der Spiel-Bildschirm ueber
 * Konfigurationswechsel und Prozess-Tod rettet (ADR-0039).
 *
 * Bewusst ohne `@StringRes`-Werte: Ressourcen-IDs sind zwischen Builds nicht
 * stabil, gespeichert wird der stabile [textKey].
 *
 * @param id Event-ID ([com.mechanicel.tomsdarts.delight.DelightEvent.id]).
 * @param animation Animations-Typ.
 * @param textKey Text-Schluessel ([com.mechanicel.tomsdarts.delight.DelightTextKeys]).
 * @param playerId Werfer oder `null`.
 * @param startedAtElapsed Anzeigebeginn auf der Uptime-Uhr
 *   (`SystemClock.elapsedRealtime()`, laeuft auch im Tiefschlaf weiter).
 * @param totalMillis Gesamte Anzeigedauer ([DelightTiming.totalMillis]).
 * @param sessionToken Kennung der ViewModel-Instanz, zu der [id] gehoert
 *   (`GameViewModel.delightSessionToken`); bindet die Feier an genau diese Instanz.
 */
data class ActiveDelight(
    val id: Long,
    val animation: DelightAnimation,
    val textKey: String,
    val playerId: Long?,
    val startedAtElapsed: Long,
    val totalMillis: Long,
    val sessionToken: String,
)

/** Bundle-taugliche Form fuer `rememberSaveable` (nur Long/String, keine Nullwerte). */
fun ActiveDelight.toSaveable(): List<Any> = listOf(
    id,
    animation.name,
    textKey,
    playerId ?: NO_PLAYER_ID,
    startedAtElapsed,
    totalMillis,
    sessionToken,
)

/**
 * Stellt eine gespeicherte Feier wieder her ([toSaveable]). Liefert `null`, wenn
 * die Daten unvollstaendig sind, die Feier zum Zeitpunkt [now] bereits
 * abgelaufen ist ([DelightTiming.remainingMillis] `<= 0`) oder zu einer anderen
 * ViewModel-Instanz gehoert ([sessionToken] weicht ab, z.B. nach Prozess-Tod:
 * die IDs beginnen dort wieder bei 1 und duerfen nicht mit der alten Sitzung
 * verwechselt werden; die verworfene Feier wird auch NICHT quittiert, ihre ID
 * gehoert nicht zum neuen ViewModel). Ein unbekannter Animations-Name faellt
 * auf [DelightAnimation.GENERIC] zurueck.
 */
fun activeDelightFromSaveable(saved: List<*>, now: Long, sessionToken: String): ActiveDelight? {
    if (saved.size != SAVED_FIELD_COUNT) return null
    val id = saved[0] as? Long ?: return null
    val animationName = saved[1] as? String ?: return null
    val textKey = saved[2] as? String ?: return null
    val playerId = saved[3] as? Long ?: return null
    val startedAt = saved[4] as? Long ?: return null
    val total = saved[5] as? Long ?: return null
    val token = saved[6] as? String ?: return null
    if (token != sessionToken) return null
    if (DelightTiming.remainingMillis(startedAt, now, total) <= 0L) return null
    val animation = DelightAnimation.entries.firstOrNull { it.name == animationName }
        ?: DelightAnimation.GENERIC
    return ActiveDelight(
        id = id,
        animation = animation,
        textKey = textKey,
        playerId = playerId.takeIf { it != NO_PLAYER_ID },
        startedAtElapsed = startedAt,
        totalMillis = total,
        sessionToken = token,
    )
}

private const val SAVED_FIELD_COUNT = 7

/**
 * Was der Spiel-Bildschirm mit dem aktuell ausstehenden Delight-Event tun soll.
 *
 * @param show Das Event als neue Feier anzeigen (ersetzt eine laufende).
 * @param acknowledgeIds IDs, fuer die sofort `GameViewModel.onDelightDismissed`
 *   gerufen werden muss (ersetzte, uebersprungene oder abgeschaltete Feiern).
 */
data class DelightIntakePlan(
    val show: Boolean,
    val acknowledgeIds: List<Long>,
)

/**
 * Pure Entscheidung, wie ein ausstehendes Event behandelt wird (ADR-0038/0039):
 *
 * - kein Event, Feiern an: nichts tun.
 * - Feiern abgeschaltet ([enabled] `false`): nichts zeigen, laufende Feier und
 *   ausstehendes Event sofort quittieren (die Kontrollpause darf nicht auf eine
 *   unsichtbare Feier warten).
 * - Event bereits gezeigt (`eventId == lastShownId`, Gleichheit statt `>`, da
 *   IDs je ViewModel wieder bei 1 beginnen; [lastShownId] stammt aus dem
 *   ViewModel, `GameViewModel.lastShownDelightId`): laeuft es noch ([activeId]), quittiert
 *   es sein eigener Timer; sonst (nach Rotation abgelaufen) sofort quittieren.
 * - neues Event: anzeigen; eine laufende andere Feier wird ersetzt und quittiert.
 *
 * @param eventId ID des ausstehenden Events oder `null`.
 * @param lastShownId Zuletzt angezeigte ID oder [NO_DELIGHT_ID].
 * @param activeId ID der gerade sichtbaren Feier oder `null`.
 */
fun planDelightIntake(
    eventId: Long?,
    enabled: Boolean,
    lastShownId: Long,
    activeId: Long?,
): DelightIntakePlan {
    if (!enabled) {
        return DelightIntakePlan(show = false, acknowledgeIds = listOfNotNull(activeId, eventId).distinct())
    }
    if (eventId == null) return DelightIntakePlan(show = false, acknowledgeIds = emptyList())
    if (eventId == lastShownId) {
        val ack = if (activeId == eventId) emptyList() else listOf(eventId)
        return DelightIntakePlan(show = false, acknowledgeIds = ack)
    }
    return DelightIntakePlan(
        show = true,
        acknowledgeIds = listOfNotNull(activeId).filter { it != eventId },
    )
}
