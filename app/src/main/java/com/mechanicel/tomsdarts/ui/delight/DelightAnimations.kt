package com.mechanicel.tomsdarts.ui.delight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Stumme Feier-Animationen (ADR-0006/ADR-0039). Gemeinsame Regeln: nur
// Bordmittel (Animatable, Canvas, graphicsLayer, PathMeasure), keine
// InfiniteTransition, Bewegung hoechstens DelightTiming.MOTION_MILLIS, danach
// steht das Endbild. animate = false zeigt sofort das statische Endbild
// (reduzierte Bewegung oder nach Rotation fortgesetzte Feier). Farben kommen
// immer aus MaterialTheme.colorScheme (Dynamic Color).

/** Dauer der Trommel-Drehung der Waschmaschine (Millisekunden). */
private const val SPIN_MILLIS = 1400

/** Gesamtdrehung der Trommel in Grad (zwei Umdrehungen, Endbild = Startbild). */
private const val SPIN_DEGREES = 720f

/** Dauer des Dreieck-Strichs (Millisekunden). */
private const val TRIANGLE_STROKE_MILLIS = 900

/** Dauer der Dreieck-Fuellung nach dem Strich (Millisekunden). */
private const val TRIANGLE_FILL_MILLIS = 300

/** Deckkraft der fertigen Dreieck-Fuellung. */
private const val TRIANGLE_FILL_ALPHA = 0.4f

/** Abstand, in dem die Ecken-Chips des Dreiecks nacheinander erscheinen (Millisekunden). */
private const val TRIANGLE_CHIP_STAGGER_MILLIS = 300L

/** Dauer des Rings der allgemeinen Feier (Millisekunden). */
private const val GENERIC_RING_MILLIS = 700

/** Anteil der Bewegung, ab dem das Konfetti ausblendet. */
private const val CONFETTI_FADE_START = 0.8f

/** Chip-Durchmesser als Anteil der Illustrationsgroesse. */
private const val CHIP_FRACTION = 0.26f

/**
 * Konfetti-Regen ueber die ganze Flaeche: EIN Canvas, EIN Animatable (0 -> 1
 * linear ueber [DelightTiming.MOTION_MILLIS]); die Partikel-Bahnen sind pur
 * ([particlePosition]), Gelesen wird der Fortschritt nur in der Zeichenphase
 * (kein Recompose je Frame). Ab t > 0.8 blendet das Konfetti aus.
 *
 * @param seed Seed der Partikel (Event-ID): gleiche Feier, gleiches Bild.
 * @param count Partikelzahl der Animation (statisch immer [CONFETTI_STATIC_COUNT]).
 * @param animate `false` = statisches Bild bei t = [CONFETTI_STATIC_T].
 */
@Composable
fun ConfettiAnimation(
    seed: Long,
    count: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        colorScheme.primaryContainer,
        colorScheme.secondaryContainer,
        colorScheme.tertiaryContainer,
    )
    val particles = remember(seed, count, animate) {
        generateConfetti(seed, if (animate) count else CONFETTI_STATIC_COUNT)
    }
    val progress = remember(seed, animate) { Animatable(if (animate) 0f else CONFETTI_STATIC_T) }
    LaunchedEffect(seed, animate) {
        if (animate) {
            progress.animateTo(1f, tween(DelightTiming.MOTION_MILLIS, easing = LinearEasing))
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val alpha = if (t > CONFETTI_FADE_START) {
            ((1f - t) / (1f - CONFETTI_FADE_START)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val rectSize = Size(6.dp.toPx(), 10.dp.toPx())
        val circleRadius = 3.dp.toPx()
        particles.forEach { particle ->
            val pos = particlePosition(particle, t)
            val center = Offset(pos.x * size.width, pos.y * size.height)
            val color = colors[particle.colorIndex]
            rotate(degrees = pos.rotationDeg, pivot = center) {
                when (particle.shape) {
                    ConfettiShape.RECT -> drawRect(
                        color = color,
                        topLeft = center - Offset(rectSize.width / 2f, rectSize.height / 2f),
                        size = rectSize,
                        alpha = alpha,
                    )
                    ConfettiShape.CIRCLE -> drawCircle(
                        color = color,
                        radius = circleRadius,
                        center = center,
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/**
 * Waschmaschine: Trommel (Kreis + Bullauge) mit den Chips "5", "20", "1" im
 * 120-Grad-Abstand. Der Chip-Ring dreht sich zweimal (FastOutSlowIn), die Chips
 * drehen gegen und bleiben lesbar. Endbild: "20" oben.
 *
 * @param size Kantenlaenge der quadratischen Illustration.
 * @param animate `false` = sofort das Endbild.
 */
@Composable
fun SpinAnimation(
    size: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rotation = remember(animate) { Animatable(if (animate) 0f else SPIN_DEGREES) }
    LaunchedEffect(animate) {
        if (animate) {
            rotation.animateTo(SPIN_DEGREES, tween(SPIN_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    val chipSize = size * CHIP_FRACTION
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val drumStroke = 6.dp.toPx()
            val drumRadius = this.size.minDimension / 2f - drumStroke
            drawCircle(
                color = colorScheme.primary,
                radius = drumRadius,
                style = Stroke(width = drumStroke),
            )
            drawCircle(
                color = colorScheme.outline,
                radius = drumRadius * 0.62f,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value },
        ) {
            // "20" oben (-90 Grad), "5" und "1" im 120-Grad-Abstand links/rechts davon.
            SPIN_CHIPS.forEach { (label, angleDeg) ->
                DelightChip(
                    label = label,
                    container = colorScheme.primaryContainer,
                    content = colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            val radius = size.toPx() / 2f - 6.dp.toPx()
                            val rad = Math.toRadians(angleDeg.toDouble())
                            IntOffset(
                                (radius * cos(rad)).roundToInt(),
                                (radius * sin(rad)).roundToInt(),
                            )
                        }
                        .size(chipSize)
                        .graphicsLayer { rotationZ = -rotation.value },
                )
            }
        }
    }
}

/** Chip-Beschriftung und Winkel (Grad, 0 = rechts, -90 = oben) der Waschmaschine. */
private val SPIN_CHIPS = listOf("5" to -210f, "20" to -90f, "1" to 30f)

/**
 * Rentnerdreieck: gleichseitiges Dreieck (Spitze oben) wird per [PathMeasure]
 * nachgezogen (900 ms FastOutSlowIn), danach fuellt es sich dezent (300 ms).
 * Die Ecken-Chips "19", "7", "3" springen gestaffelt (300/600/900 ms) auf.
 * Ein Animatable fuer Strich + Fuellung, drei fuer die Chips.
 *
 * @param size Kantenlaenge der quadratischen Illustration.
 * @param animate `false` = sofort fertig mit Fuellung und Chips.
 */
@Composable
fun TriangleAnimation(
    size: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val totalMillis = TRIANGLE_STROKE_MILLIS + TRIANGLE_FILL_MILLIS
    val progress = remember(animate) { Animatable(if (animate) 0f else 1f) }
    val chipScales = remember(animate) { List(3) { Animatable(if (animate) 0f else 1f) } }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(totalMillis, easing = LinearEasing))
    }
    chipScales.forEachIndexed { index, scale ->
        LaunchedEffect(animate, index) {
            if (animate) {
                delay(TRIANGLE_CHIP_STAGGER_MILLIS * (index + 1))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }
    val chipSize = size * CHIP_FRACTION
    val corners = triangleCornerFractions(inset = CHIP_FRACTION / 2f)
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val outline = Path().apply {
                        corners.forEachIndexed { i, (fx, fy) ->
                            val x = fx * this@drawWithCache.size.width
                            val y = fy * this@drawWithCache.size.height
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    val measure = PathMeasure().apply { setPath(outline, forceClosed = true) }
                    val segment = Path()
                    val stroke = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    onDrawBehind {
                        val elapsed = progress.value * totalMillis
                        val strokeFraction = FastOutSlowInEasing.transform(
                            (elapsed / TRIANGLE_STROKE_MILLIS).coerceIn(0f, 1f),
                        )
                        val fillFraction =
                            ((elapsed - TRIANGLE_STROKE_MILLIS) / TRIANGLE_FILL_MILLIS).coerceIn(0f, 1f)
                        if (fillFraction > 0f) {
                            drawPath(
                                path = outline,
                                color = colorScheme.tertiaryContainer,
                                alpha = TRIANGLE_FILL_ALPHA * fillFraction,
                            )
                        }
                        segment.reset()
                        measure.getSegment(0f, measure.length * strokeFraction, segment, true)
                        drawPath(path = segment, color = colorScheme.tertiary, style = stroke)
                    }
                },
        )
        TRIANGLE_CHIPS.forEachIndexed { index, label ->
            val (fx, fy) = corners[index]
            DelightChip(
                label = label,
                container = colorScheme.tertiaryContainer,
                content = colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .offset(x = size * fx - chipSize / 2, y = size * fy - chipSize / 2)
                    .size(chipSize)
                    .graphicsLayer {
                        scaleX = chipScales[index].value
                        scaleY = chipScales[index].value
                    },
            )
        }
    }
}

/** Ecken-Beschriftungen des Rentnerdreiecks: Spitze, unten links, unten rechts. */
private val TRIANGLE_CHIPS = listOf("19", "7", "3")

/**
 * Ecken eines gleichseitigen Dreiecks (Spitze oben) als Anteile einer
 * quadratischen Flaeche: Spitze, unten links, unten rechts. Seitlich um [inset]
 * eingerueckt (Platz fuer die Chips) und vertikal zentriert.
 */
fun triangleCornerFractions(inset: Float): List<Pair<Float, Float>> {
    val side = 1f - 2f * inset
    val height = side * sqrt(3f) / 2f
    val top = (1f - height) / 2f
    val bottom = top + height
    return listOf(0.5f to top, inset to bottom, (1f - inset) to bottom)
}

/**
 * Allgemeine Feier: ein Ring hinter der Karte waechst einmal von 0.6 auf 1.4
 * der [size] (Durchmesser) und blendet dabei von 0.8 auf 0 aus (700 ms).
 * Gezeichnet wird zentriert in der Flaeche von [modifier], ohne Clipping - so
 * kann der Ring ueber die Karte hinausragen, ohne deren Layout zu beeinflussen.
 * Statisch: nichts (nur die Karte).
 */
@Composable
fun GenericRingAnimation(
    size: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!animate) return
    val color = MaterialTheme.colorScheme.primary
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(GENERIC_RING_MILLIS, easing = LinearEasing))
    }
    Canvas(modifier = modifier) {
        val t = progress.value
        val scale = 0.6f + 0.8f * t
        drawCircle(
            color = color,
            radius = size.toPx() / 2f * scale,
            alpha = 0.8f * (1f - t),
            style = Stroke(width = 4.dp.toPx()),
        )
    }
}

/** Runder Zahlen-Chip der Illustrationen (stumm, Semantik setzt das Overlay). */
@Composable
private fun DelightChip(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}
