package com.mechanicel.tomsdarts.ui.delight

import com.mechanicel.tomsdarts.delight.DelightAnimation
import com.mechanicel.tomsdarts.ui.game.GameViewModel

/**
 * Pure Zeitlogik der Feier-Overlays (ADR-0039). Bewusst ohne Compose/Android,
 * damit sie host-seitig testbar ist.
 *
 * Die Anzeigedauer wird mit einem reinen `delay()` abgewartet, NICHT ueber die
 * Animations-Uhr: bei Animator-Dauer-Skala 0 (Entwickleroptionen,
 * Bedienungshilfen) springen Animationen sofort ans Ende, die Feier soll aber
 * trotzdem lesbar stehen bleiben.
 */
object DelightTiming {

    /** Anzeigedauer der Konfetti-Feier (Millisekunden). */
    const val CONFETTI_MILLIS: Long = 2500L

    /** Anzeigedauer der Dreh- und Dreieck-Feier (Millisekunden). */
    const val SPIN_TRIANGLE_MILLIS: Long = 2200L

    /** Anzeigedauer der allgemeinen Feier (Millisekunden). */
    const val GENERIC_MILLIS: Long = 1800L

    /** Einheitliche Anzeigedauer bei reduzierter Bewegung (statisches Bild). */
    const val REDUCED_MOTION_MILLIS: Long = 2000L

    /** Maximale Bewegungsdauer einer Animation; danach steht das Endbild. */
    const val MOTION_MILLIS: Int = 1600

    /**
     * Abstand zum Sicherheitsnetz des ViewModels (Millisekunden). Das
     * [GameViewModel] startet seinen Halte-Timer schon beim Ausloesen der Feier,
     * die UI erst beim Anzeigen (Sammeln, Komposition, ggf. Hintergrund). Der
     * Puffer sorgt dafuer, dass der regulaere Dismiss der UI immer vor dem
     * Sicherheitsnetz ankommt und die Kontrollpause nicht unter einer noch
     * sichtbaren Feier anlaeuft.
     */
    const val HOLD_SAFETY_MARGIN_MILLIS: Long = 1000L

    /**
     * Obergrenze jeder Anzeigedauer, auch nach Verlaengerung durch Bedienungs-
     * hilfen (TalkBack-Zeitvorgabe): [GameViewModel.DELIGHT_MAX_HOLD_MILLIS]
     * minus [HOLD_SAFETY_MARGIN_MILLIS].
     */
    const val MAX_DISPLAY_MILLIS: Long =
        GameViewModel.DELIGHT_MAX_HOLD_MILLIS - HOLD_SAFETY_MARGIN_MILLIS

    /** Grund-Anzeigedauer je Animations-Typ; bei [reducedMotion] einheitlich. */
    fun displayMillis(animation: DelightAnimation, reducedMotion: Boolean): Long {
        if (reducedMotion) return REDUCED_MOTION_MILLIS
        return when (animation) {
            DelightAnimation.CONFETTI -> CONFETTI_MILLIS
            DelightAnimation.SPIN, DelightAnimation.TRIANGLE -> SPIN_TRIANGLE_MILLIS
            DelightAnimation.GENERIC -> GENERIC_MILLIS
        }
    }

    /**
     * Tatsaechliche Anzeigedauer: [displayMillis], von [recommend] ggf.
     * verlaengert (z.B. `AccessibilityManager.calculateRecommendedTimeoutMillis`),
     * dann auf `[displayMillis, MAX_DISPLAY_MILLIS]` begrenzt. Eine Empfehlung
     * unterhalb der Grunddauer verkuerzt nie.
     */
    fun totalMillis(
        animation: DelightAnimation,
        reducedMotion: Boolean,
        recommend: (Long) -> Long = { it },
    ): Long {
        val base = displayMillis(animation, reducedMotion)
        return recommend(base).coerceIn(base, MAX_DISPLAY_MILLIS)
    }

    /**
     * Restliche Anzeigedauer einer Feier, die zum Zeitpunkt [startedAt] (Uptime-
     * Uhr, z.B. `SystemClock.elapsedRealtime()`) mit Gesamtdauer [total] begann.
     * Liefert `<= 0`, wenn die Feier abgelaufen ist. Liegt [now] VOR [startedAt]
     * (Uhr zurueckgesetzt, z.B. nach Neustart des Geraets), gilt die Feier als
     * abgelaufen (0).
     */
    fun remainingMillis(startedAt: Long, now: Long, total: Long): Long {
        if (now < startedAt) return 0L
        return total - (now - startedAt)
    }
}
