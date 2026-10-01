package com.mechanicel.tomsdarts.ui.delight

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Form eines Konfetti-Partikels. */
enum class ConfettiShape {
    /** Rechteck 6 x 10 dp. */
    RECT,

    /** Kreis mit 6 dp Durchmesser. */
    CIRCLE,
}

/**
 * Ein Konfetti-Partikel mit normierten Startwerten (Anteile der Flaeche, 0..1).
 * Rein beschreibend; die Position zu einem Zeitpunkt liefert [particlePosition].
 *
 * @param x0 Start-X (0.05..0.95).
 * @param y0 Start-Y (-0.1..0, also knapp ueber dem oberen Rand).
 * @param vx Horizontale Drift je Durchlauf (-0.15..0.15).
 * @param vy Vertikale Startgeschwindigkeit je Durchlauf (0..0.2, nach unten).
 * @param rotationDeg Startrotation in Grad (0..360).
 * @param spin Drehgeschwindigkeit in Umdrehungen je Durchlauf (-2..2).
 * @param sway Schwingungen der seitlichen Pendelbewegung je Durchlauf (0.5..2).
 * @param colorIndex Index in die sechs Theme-Farben (0..5).
 * @param shape Form (Rechteck 70 %, Kreis 30 %).
 */
data class ConfettiParticle(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val rotationDeg: Float,
    val spin: Float,
    val sway: Float,
    val colorIndex: Int,
    val shape: ConfettiShape,
)

/**
 * Normierte Position (Anteile der Flaeche) und Rotation eines Partikels zum
 * Zeitpunkt t.
 */
data class ParticlePosition(val x: Float, val y: Float, val rotationDeg: Float)

/** Anzahl der Theme-Farben, aus denen Konfetti gefaerbt wird. */
const val CONFETTI_COLOR_COUNT: Int = 6

/** Obergrenze der Partikelzahl (Performance). */
const val CONFETTI_MAX_COUNT: Int = 120

/** Partikelzahl der statischen Darstellung (reduzierte Bewegung). */
const val CONFETTI_STATIC_COUNT: Int = 24

/** Zeitpunkt t, zu dem die statische Darstellung eingefroren wird. */
const val CONFETTI_STATIC_T: Float = 0.5f

/** Breite (dp), ab der das Overlay mehr Partikel zeigt. */
private const val CONFETTI_WIDE_MIN_DP = 600f

/** Amplitude der seitlichen Pendelbewegung (Anteil der Breite). */
private const val SWAY_AMPLITUDE = 0.03f

/** Anteil der Rechtecke an allen Partikeln. */
private const val RECT_SHARE = 0.7f

/**
 * Erzeugt [count] Partikel deterministisch aus [seed] (gleicher Seed -> gleiche
 * Partikel, z.B. Seed = Event-ID). [count] wird auf `0..CONFETTI_MAX_COUNT`
 * begrenzt.
 */
fun generateConfetti(seed: Long, count: Int): List<ConfettiParticle> {
    val random = Random(seed)
    return List(count.coerceIn(0, CONFETTI_MAX_COUNT)) {
        ConfettiParticle(
            x0 = random.range(0.05f, 0.95f),
            y0 = random.range(-0.1f, 0f),
            vx = random.range(-0.15f, 0.15f),
            vy = random.range(0f, 0.2f),
            rotationDeg = random.range(0f, 360f),
            spin = random.range(-2f, 2f),
            sway = random.range(0.5f, 2f),
            colorIndex = random.nextInt(CONFETTI_COLOR_COUNT),
            shape = if (random.nextFloat() < RECT_SHARE) ConfettiShape.RECT else ConfettiShape.CIRCLE,
        )
    }
}

/**
 * Position eines Partikels zum Zeitpunkt [t] (0..1 ueber die Bewegungsdauer):
 * `y = y0 + vy*t + g*t^2/2` (faellt monoton), `x = x0 + vx*t + 0.03*sin(t*2*pi*sway)`.
 */
fun particlePosition(p: ConfettiParticle, t: Float, gravity: Float = 1.1f): ParticlePosition =
    ParticlePosition(
        x = p.x0 + p.vx * t + SWAY_AMPLITUDE * sin(t * 2f * PI.toFloat() * p.sway),
        y = p.y0 + p.vy * t + 0.5f * gravity * t * t,
        rotationDeg = p.rotationDeg + p.spin * 360f * t,
    )

/** Partikelzahl je verfuegbarer Breite: 60 unter 600 dp, sonst 100. */
fun confettiCount(maxWidthDp: Float): Int = if (maxWidthDp < CONFETTI_WIDE_MIN_DP) 60 else 100

private fun Random.range(from: Float, until: Float): Float = from + nextFloat() * (until - from)
