package com.mechanicel.tomsdarts.ui.delight

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.delight.DelightAnimation
import com.mechanicel.tomsdarts.delight.DelightTextKeys
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/** Ein- und Ausblenddauer von Scrim und Karte (Millisekunden). */
private const val OVERLAY_FADE_MILLIS = 200

/** Deckkraft des Scrims ueber dem Spiel. */
private const val SCRIM_ALPHA = 0.6f

/** Startskalierung der Karte bei der allgemeinen Feier. */
private const val GENERIC_CARD_START_SCALE = 0.85f

/** Ab dieser Schriftskalierung werden Titel/Untertitel eine Stufe kleiner gesetzt. */
private const val LARGE_FONT_SCALE = 1.5f

/** Groesste Illustration (dp). */
private const val ILLUSTRATION_MAX_DP = 200f

/** Kleinste sinnvolle Illustration (dp); darunter wird sie weggelassen. */
private const val ILLUSTRATION_MIN_DP = 96f

/**
 * Anzeige-Daten einer laufenden Feier fuer das [DelightOverlay].
 *
 * @param id Event-ID (Schluessel: ein neues Event ersetzt das laufende; Seed).
 * @param animation Animations-Typ.
 * @param textKey Text-Schluessel ([DelightTextKeys]); unbekannt -> allgemeiner Text.
 * @param playerName Werfer-Name als Untertitel-Praefix oder `null` (nur bei
 *   mehreren Spielern und aufloesbarem Namen gesetzt).
 * @param animate `false` = statisches Endbild ohne Ueberblendungen (reduzierte
 *   Bewegung oder nach Rotation fortgesetzte Feier).
 */
@Immutable
data class DelightUi(
    val id: Long,
    val animation: DelightAnimation,
    val textKey: String,
    val playerName: String? = null,
    val animate: Boolean = true,
)

/**
 * Stumme Vollbild-Feier ueber dem kompletten Spiel-Bildschirm (ADR-0006,
 * ADR-0039). Zustandslos: Dauer und Ablauf haelt der Aufrufer, das Overlay
 * meldet nur das Schliessen per Tippen (ganzer Bildschirm) bzw. Zurueck.
 *
 * Schichten (unten -> oben): Scrim, Animationsebene, zentrierter Inhalt
 * (Illustration + Textkarte). Eingaben auf das Spiel darunter sind blockiert,
 * solange die Feier sichtbar ist. Ein neues Event (andere [DelightUi.id])
 * ersetzt das laufende, `null` blendet aus.
 *
 * @param delight Laufende Feier oder `null`.
 * @param onDismiss Schliessen der Feier mit ihrer ID (Tippen, Zurueck, TalkBack-Aktion).
 */
@Composable
fun DelightOverlay(
    delight: DelightUi?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = delight != null) {
        delight?.let { onDismiss(it.id) }
    }
    AnimatedContent(
        targetState = delight,
        contentKey = { it?.id },
        transitionSpec = {
            val animated = targetState?.animate ?: initialState?.animate ?: false
            val transform = if (animated) {
                fadeIn(tween(OVERLAY_FADE_MILLIS)) togetherWith fadeOut(tween(OVERLAY_FADE_MILLIS))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
            // Keine Groessen-Animation: das Overlay ist immer vollflaechig.
            transform using null
        },
        label = "delight",
        modifier = modifier.fillMaxSize(),
    ) { current ->
        if (current != null) {
            DelightOverlayContent(delight = current, onDismiss = { onDismiss(current.id) })
        }
    }
}

@Composable
private fun DelightOverlayContent(
    delight: DelightUi,
    onDismiss: () -> Unit,
) {
    val texts = delightTextRes(delight.textKey)
    val title = stringResource(texts.title)
    val baseSubtitle = texts.subtitle?.let { stringResource(it) }
    val subtitle = when {
        delight.playerName == null -> baseSubtitle
        baseSubtitle == null -> delight.playerName
        else -> stringResource(R.string.delight_player_prefix, delight.playerName, baseSubtitle)
    }
    val spoken = if (subtitle != null) "$title. $subtitle" else title
    val dismissLabel = stringResource(R.string.delight_dismiss_action)
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Bewusst pointerInput statt clickable: blockiert JEDEN Tipp auf das
            // Spiel darunter und schliesst, ohne die Semantik der Karte in einen
            // ganzflaechigen Klick-Knoten zu verschmelzen.
            .pointerInput(delight.id) { detectTapGestures { onDismiss() } }
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        val landscape = maxWidth > maxHeight
        val illustrationDp = if (delight.animation == DelightAnimation.CONFETTI) {
            null
        } else {
            delightIllustrationSizeDp(maxWidth.value, maxHeight.value, fontScale)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
        )
        if (delight.animation == DelightAnimation.CONFETTI) {
            ConfettiAnimation(
                seed = delight.id,
                count = confettiCount(maxWidth.value),
                animate = delight.animate,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        val card: @Composable (Modifier) -> Unit = { cardModifier ->
            DelightCard(
                delight = delight,
                ringSizeDp = illustrationDp,
                title = title,
                subtitle = subtitle,
                largeFont = fontScale >= LARGE_FONT_SCALE,
                modifier = cardModifier.clearAndSetSemantics {
                    contentDescription = spoken
                    onClick(label = dismissLabel) {
                        onDismiss()
                        true
                    }
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (landscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DelightIllustration(delight = delight, sizeDp = illustrationDp)
                    card(Modifier.weight(1f, fill = false).widthIn(max = 420.dp))
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DelightIllustration(delight = delight, sizeDp = illustrationDp)
                    card(Modifier.fillMaxWidth(0.85f).widthIn(max = 480.dp))
                }
            }
        }
    }
}

/**
 * Illustrationsflaeche fuer SPIN/TRIANGLE. GENERIC zeichnet seinen Ring direkt
 * hinter der Karte ([DelightCard]), CONFETTI vollflaechig - beide haben hier
 * nichts.
 */
@Composable
private fun DelightIllustration(delight: DelightUi, sizeDp: Float?) {
    if (sizeDp == null) return
    val size = sizeDp.dp
    val illustrationModifier = Modifier.clearAndSetSemantics {}
    when (delight.animation) {
        DelightAnimation.SPIN ->
            SpinAnimation(size = size, animate = delight.animate, modifier = illustrationModifier)
        DelightAnimation.TRIANGLE ->
            TriangleAnimation(size = size, animate = delight.animate, modifier = illustrationModifier)
        DelightAnimation.CONFETTI, DelightAnimation.GENERIC -> Unit
    }
}

/**
 * Deckende Textkarte (Titel + optionaler Untertitel). Bei GENERIC federt sie
 * einmal ein, dahinter waechst ein Ring auf Basis von [ringSizeDp] (`null` =
 * kein Ring, z.B. zu wenig Platz).
 */
@Composable
private fun DelightCard(
    delight: DelightUi,
    ringSizeDp: Float?,
    title: String,
    subtitle: String?,
    largeFont: Boolean,
    modifier: Modifier = Modifier,
) {
    val generic = delight.animation == DelightAnimation.GENERIC
    val cardScale = remember(delight.id) {
        Animatable(if (generic && delight.animate) GENERIC_CARD_START_SCALE else 1f)
    }
    LaunchedEffect(delight.id) {
        if (generic && delight.animate) {
            cardScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (generic && ringSizeDp != null) {
            // matchParentSize: der Ring beeinflusst das Layout der Karte nicht.
            GenericRingAnimation(
                size = ringSizeDp.dp,
                animate = delight.animate,
                modifier = Modifier.matchParentSize().clearAndSetSemantics {},
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val titleStyle = if (largeFont) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.displaySmall
                }
                Text(
                    text = title,
                    style = titleStyle.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = if (largeFont) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Kantenlaenge (dp) der Illustration je verfuegbarer Flaeche, `null` = weglassen.
 *
 * Hochformat: `min(Breite * 0.5, Hoehe * 0.3, 200)`; Querformat (Breite > Hoehe):
 * `min(Hoehe * 0.6, 200)`, bei Schriftskalierung >= 1.5 weggelassen (Platz fuer
 * den Text). Unter 96 dp wird sie immer weggelassen.
 */
fun delightIllustrationSizeDp(maxWidthDp: Float, maxHeightDp: Float, fontScale: Float): Float? {
    val landscape = maxWidthDp > maxHeightDp
    if (landscape && fontScale >= LARGE_FONT_SCALE) return null
    val size = if (landscape) {
        minOf(maxHeightDp * 0.6f, ILLUSTRATION_MAX_DP)
    } else {
        minOf(maxWidthDp * 0.5f, maxHeightDp * 0.3f, ILLUSTRATION_MAX_DP)
    }
    return size.takeIf { it >= ILLUSTRATION_MIN_DP }
}

// --- Previews (statisch: animate = false, Konfetti mit festem Seed bei t = 0.5) ---

@Composable
private fun DelightPreviewFrame(delight: DelightUi) {
    TomsDartsTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DelightOverlay(delight = delight, onDismiss = {})
        }
    }
}

@Preview(name = "Feier 180 (Konfetti)", widthDp = 360, heightDp = 760)
@Composable
private fun DelightConfettiPreview() {
    DelightPreviewFrame(
        DelightUi(7L, DelightAnimation.CONFETTI, DelightTextKeys.ONE_EIGHTY, "Tom", animate = false),
    )
}

@Preview(name = "Feier 180 @320", widthDp = 320, heightDp = 640)
@Composable
private fun DelightConfettiNarrowPreview() {
    DelightPreviewFrame(
        DelightUi(7L, DelightAnimation.CONFETTI, DelightTextKeys.ONE_EIGHTY, "Anna Beispiel", animate = false),
    )
}

@Preview(name = "Feier Waschmaschine", widthDp = 360, heightDp = 760)
@Composable
private fun DelightSpinPreview() {
    DelightPreviewFrame(
        DelightUi(2L, DelightAnimation.SPIN, DelightTextKeys.WASHING_MACHINE, animate = false),
    )
}

@Preview(name = "Feier Rentnerdreieck", widthDp = 360, heightDp = 760)
@Composable
private fun DelightTrianglePreview() {
    DelightPreviewFrame(
        DelightUi(3L, DelightAnimation.TRIANGLE, DelightTextKeys.RENTNERDREIECK, animate = false),
    )
}

@Preview(name = "Feier allgemein", widthDp = 360, heightDp = 760)
@Composable
private fun DelightGenericPreview() {
    DelightPreviewFrame(
        DelightUi(4L, DelightAnimation.GENERIC, DelightTextKeys.GENERIC, "Tom", animate = false),
    )
}

@Preview(name = "Feier Rentnerdreieck (Querformat)", widthDp = 760, heightDp = 380)
@Composable
private fun DelightTriangleLandscapePreview() {
    DelightPreviewFrame(
        DelightUi(3L, DelightAnimation.TRIANGLE, DelightTextKeys.RENTNERDREIECK, "Tom", animate = false),
    )
}

@Preview(name = "Feier Waschmaschine (Schrift 200 %)", widthDp = 360, heightDp = 760, fontScale = 2f)
@Composable
private fun DelightSpinLargeFontPreview() {
    DelightPreviewFrame(
        DelightUi(2L, DelightAnimation.SPIN, DelightTextKeys.WASHING_MACHINE, "Anna Beispiel", animate = false),
    )
}

@Preview(
    name = "Feier 180 (Dunkel)",
    widthDp = 360,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DelightConfettiDarkPreview() {
    DelightPreviewFrame(
        DelightUi(7L, DelightAnimation.CONFETTI, DelightTextKeys.ONE_EIGHTY, "Tom", animate = false),
    )
}

@Preview(name = "Feier unbekannter Schluessel (Fallback)", widthDp = 360, heightDp = 760)
@Composable
private fun DelightFallbackPreview() {
    DelightPreviewFrame(
        DelightUi(5L, DelightAnimation.GENERIC, "gibt_es_nicht", animate = false),
    )
}
