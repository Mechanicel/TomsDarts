package com.mechanicel.tomsdarts.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.X01Metrics
import com.mechanicel.tomsdarts.ui.setup.gameModeLabelResIdOrNull

// Wiederverwendbare Bausteine der Statistik-Screens (Phase 5, ADR-0037):
// Kacheln, Kachelraster, Abschnitts-Ueberschrift, Balken, Trefferverteilung als
// Balkenliste sowie die Loading/Empty/Error-Zustaende. Nur Theme-Rollen,
// keine eigenen Farben.

/**
 * Anzeigedaten einer [StatTile].
 *
 * @param value Hauptwert (bereits formatiert, "–" ohne Daten).
 * @param label Beschriftung unter dem Wert.
 * @param supporting Optionale Zusatzzeile (z.B. "12 / 40").
 * @param spokenText Vollstaendiger TalkBack-Text (ein Fokus-Stopp je Kachel).
 */
data class StatTileUi(
    val value: String,
    val label: String,
    val supporting: String? = null,
    val spokenText: String,
)

/** Lokalisierter Anzeigename eines Spielmodus; Fallback ist die rohe Kennung. */
@Composable
internal fun statsModeLabel(key: String): String =
    gameModeLabelResIdOrNull(key)?.let { stringResource(it) } ?: key

/**
 * Kachel mit Wert, Beschriftung und optionaler Zusatzzeile. Nicht klickbar;
 * TalkBack liest genau [spokenText].
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    spokenText: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .heightIn(min = 72.dp)
            .clearAndSetSemantics { contentDescription = spokenText },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Raster aus [tiles] (Muster ADR-0033: `chunked`, gleich hohe Reihen per
 * `IntrinsicSize.Min`, Spacer-Fueller in der letzten Reihe). Die Spaltenzahl
 * folgt [columnsFor] aus Breite und Schriftgroesse — standardmaessig
 * [statGridColumns]; Sonderraster (z.B. Dart-Positionen, [positionGridColumns])
 * uebergeben eine eigene Regel.
 */
@Composable
fun StatTileGrid(
    tiles: List<StatTileUi>,
    modifier: Modifier = Modifier,
    columnsFor: (maxWidth: Dp, fontScale: Float) -> Int = ::statGridColumns,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = columnsFor(maxWidth, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowTiles.forEach { tile ->
                        StatTile(
                            value = tile.value,
                            label = tile.label,
                            supporting = tile.supporting,
                            spokenText = tile.spokenText,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(columns - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Kachel-Daten mit "–" und "keine Daten"-Ansage, falls [value] fehlt
 * (gemeinsam fuer Spieler- und Match-Statistik).
 */
@Composable
internal fun statValueTile(label: String, value: String?, supporting: String? = null): StatTileUi =
    StatTileUi(
        value = value ?: stringResource(R.string.stats_value_none),
        label = label,
        supporting = supporting,
        spokenText = if (value == null) {
            stringResource(R.string.stats_tile_none_cd, label)
        } else {
            stringResource(R.string.stats_tile_cd, label, listOfNotNull(value, supporting).joinToString(" "))
        },
    )

/**
 * Die sechs X01-Kacheln (3-Dart-Average, First-9-Average, Checkout-Quote,
 * Hoechster Checkout, Legs gewonnen, Darts geworfen) — identisch im
 * Spieler-Screen und je Spieler im Match-Screen. Ohne geworfene Darts zeigt
 * "Darts geworfen" "–" statt 0.
 */
@Composable
internal fun x01MetricTiles(metrics: X01Metrics): List<StatTileUi> {
    val checkoutLabel = stringResource(R.string.stats_tile_checkout_rate)
    val checkoutTile = if (metrics.checkoutRate == null) {
        statValueTile(label = checkoutLabel, value = null)
    } else {
        StatTileUi(
            value = formatPercent(metrics.checkoutRate) ?: stringResource(R.string.stats_value_none),
            label = checkoutLabel,
            supporting = stringResource(R.string.stats_fraction, metrics.checkoutHits, metrics.checkoutAttempts),
            spokenText = pluralStringResource(
                R.plurals.stats_checkout_cd,
                metrics.checkoutAttempts,
                formatPercentNumber(metrics.checkoutRate) ?: "",
                metrics.checkoutHits,
                metrics.checkoutAttempts,
            ),
        )
    }
    return listOf(
        statValueTile(stringResource(R.string.stats_tile_avg3), formatAverage(metrics.threeDartAverage)),
        statValueTile(stringResource(R.string.stats_tile_first9), formatAverage(metrics.firstNineAverage)),
        checkoutTile,
        statValueTile(stringResource(R.string.stats_tile_highest_checkout), metrics.highestCheckout?.toString()),
        legsWonTile(legsWon = metrics.legsWon, legsPlayed = metrics.legsPlayed),
        dartsThrownTile(metrics.dartsThrown.takeIf { it > 0 }),
    )
}

/** Kachel "Legs gewonnen" mit Zusatz "von N". */
@Composable
internal fun legsWonTile(legsWon: Int, legsPlayed: Int): StatTileUi =
    statValueTile(
        label = stringResource(R.string.stats_tile_legs_won),
        value = formatCount(legsWon),
        supporting = stringResource(R.string.stats_of_total, legsPlayed),
    )

/** Kachel "Darts geworfen"; `null` (keine Aufnahme) zeigt "–". */
@Composable
internal fun dartsThrownTile(dartsThrown: Int?): StatTileUi =
    statValueTile(stringResource(R.string.stats_tile_darts_thrown), dartsThrown?.let(::formatCount))

/**
 * Filter-Chip der Statistik-Screens (Einfachauswahl, Haken bei Auswahl). Mit
 * [maxLabelWidth] wird ein langes Label (z.B. Spielername) einzeilig gekuerzt.
 * FilterChip erzwingt selbst ein 48-dp-Touch-Ziel (minimumInteractiveComponentSize).
 */
@Composable
internal fun StatsFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    maxLabelWidth: Dp? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (maxLabelWidth != null) Modifier.widthIn(max = maxLabelWidth) else Modifier,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

/** Abschnitts-Ueberschrift (TalkBack-Heading). */
@Composable
fun StatsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/** Unter-Ueberschrift innerhalb eines Abschnitts (z.B. "Nach Ring"). */
@Composable
internal fun StatsSubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * Trefferverteilung als Balkenliste (ADR-0037, statt Heatmap): Ring-Anteile als
 * Kacheln, darunter eine Zeile je getroffenem Segment. Die ersten
 * [HIT_ROWS_COLLAPSED] Zeilen sind sichtbar; der Aufklapp-Zustand wird bei
 * Aenderung von [resetKey] (Modus-Filter) zurueckgesetzt.
 */
@Composable
fun HitDistributionSection(
    distribution: HitDistribution,
    resetKey: String,
    modifier: Modifier = Modifier,
) {
    if (distribution.totalDarts == 0) {
        Text(
            text = stringResource(R.string.stats_distribution_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    var expanded by rememberSaveable(resetKey) { mutableStateOf(false) }
    val rows = hitSegmentRows(distribution)
    val visibleRows = if (expanded) rows else rows.take(HIT_ROWS_COLLAPSED)
    val maxCount = rows.maxOf { it.count }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsSubHeader(stringResource(R.string.stats_section_rings))
        StatTileGrid(tiles = ringTiles(distribution))
        StatsSubHeader(stringResource(R.string.stats_section_fields))
        visibleRows.forEach { row ->
            HitRow(row = row, totalDarts = distribution.totalDarts, maxCount = maxCount)
        }
        if (rows.size > HIT_ROWS_COLLAPSED) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    if (expanded) {
                        stringResource(R.string.stats_distribution_show_less)
                    } else {
                        pluralStringResource(R.plurals.stats_distribution_show_all, rows.size, rows.size)
                    },
                )
            }
        }
    }
}

/** Die vier Ring-Kacheln Single/Double/Triple/Out. */
@Composable
private fun ringTiles(distribution: HitDistribution): List<StatTileUi> = listOf(
    ringTile(
        label = stringResource(R.string.stats_ring_single),
        share = distribution.singleShare,
        count = distribution.byMultiplier[1] ?: 0,
    ),
    ringTile(
        label = stringResource(R.string.stats_ring_double),
        share = distribution.doubleShare,
        count = distribution.byMultiplier[2] ?: 0,
    ),
    ringTile(
        label = stringResource(R.string.stats_ring_triple),
        share = distribution.tripleShare,
        count = distribution.byMultiplier[3] ?: 0,
    ),
    ringTile(
        label = stringResource(R.string.keypad_out),
        share = distribution.missShare,
        count = distribution.misses,
    ),
)

@Composable
private fun ringTile(label: String, share: Double?, count: Int): StatTileUi {
    val none = stringResource(R.string.stats_value_none)
    val dartsText = pluralStringResource(R.plurals.stats_darts_count, count, count)
    return StatTileUi(
        value = formatPercent(share) ?: none,
        label = label,
        supporting = dartsText,
        spokenText = if (share == null) {
            stringResource(R.string.stats_tile_none_cd, label)
        } else {
            stringResource(R.string.stats_ring_cd, label, formatPercentNumber(share) ?: none, dartsText)
        },
    )
}

/** Anzeigename eines Segments: Zahl, "Bull" oder "Out". */
@Composable
private fun segmentLabel(segment: Int): String = when (segment) {
    MISS_SEGMENT -> stringResource(R.string.keypad_out)
    BULL_SEGMENT -> stringResource(R.string.stats_field_bull)
    else -> segment.toString()
}

/** Eine Zeile der Felder-Liste: Label + Anzahl/Anteil, Balken, Ring-Aufteilung. */
@Composable
private fun HitRow(
    row: HitSegmentRow,
    totalDarts: Int,
    maxCount: Int,
) {
    val label = segmentLabel(row.segment)
    val share = row.count.toDouble() / totalDarts
    val none = stringResource(R.string.stats_value_none)

    // Ring-Aufteilung: Nullwerte aus, Out ohne Aufteilung (Bull hat nie Triples).
    val splitVisible = buildList {
        if (row.segment != MISS_SEGMENT) {
            if (row.singles > 0) add(stringResource(R.string.stats_hit_split_s, row.singles))
            if (row.doubles > 0) add(stringResource(R.string.stats_hit_split_d, row.doubles))
            if (row.triples > 0) add(stringResource(R.string.stats_hit_split_t, row.triples))
        }
    }
    val splitSpoken = buildList {
        if (row.segment != MISS_SEGMENT) {
            if (row.singles > 0) add("${stringResource(R.string.stats_ring_single)} ${row.singles}")
            if (row.doubles > 0) add("${stringResource(R.string.stats_ring_double)} ${row.doubles}")
            if (row.triples > 0) add("${stringResource(R.string.stats_ring_triple)} ${row.triples}")
        }
    }
    val spoken = pluralStringResource(
        R.plurals.stats_hit_row_cd,
        row.count,
        label,
        row.count,
        formatPercentNumber(share) ?: none,
    ) +
        if (splitSpoken.isEmpty()) "" else stringResource(R.string.stats_hit_split_cd, splitSpoken.joinToString(", "))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_hit_count_share, row.count, formatPercent(share) ?: none),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        StatBar(fraction = if (maxCount > 0) row.count.toFloat() / maxCount else 0f)
        if (splitVisible.isNotEmpty()) {
            Text(
                text = splitVisible.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Stiller Balken (8 dp, fuer TalkBack ausgeblendet) mit Fuellgrad [fraction] (0..1).
 * Bei [fraction] > 0 ist die Fuellung mindestens 2 dp breit, damit seltene Eintraege
 * sichtbar bleiben; bei 0 bleibt nur die Spur.
 */
@Composable
internal fun StatBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics {},
    ) {
        if (fraction > 0f) {
            // widthIn VOR fillMaxWidth: hebt die Mindestbreite der Constraints auf
            // 2 dp an, fillMaxWidth(fraction) wird darauf begrenzt. Umgekehrt waere
            // die Breite bereits fix und das Minimum wirkungslos.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 2.dp)
                    .fillMaxWidth(fraction.coerceAtMost(1f))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Ladezustand: zentrierter Fortschrittsindikator (wie Profil). */
@Composable
fun StatsLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Zentrierter Hinweis mit Titel und Erklaertext (z.B. Spieler ohne Spiele). */
@Composable
fun StatsEmpty(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Zentrierte Meldung mit einer Aktion (Fehler mit Retry, Spieler fehlt mit Zurueck). */
@Composable
fun StatsError(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
