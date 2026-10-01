package com.mechanicel.tomsdarts.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.analytics.Transition
import com.mechanicel.tomsdarts.analytics.VisitPattern
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

// Abschnitt "Wurfmuster" der Statistik-Screens (Sequenz-Auswertungen, ADR-0036/
// ADR-0037): erster Dart, Dart-Positionen, haeufigste Aufnahmen und Folgen.
// Zustandslos bis auf den Aufklapp-Zustand der Listen — wiederverwendbar im
// Spieler- und (spaeter) im Match-Screen. Nur Theme-Rollen, keine eigenen Farben.

/**
 * Abschnitt "Wurfmuster" inkl. Ueberschrift. Ohne Aufnahmen
 * ([SequenceSectionUi.visitsCounted] == 0) nur ein Hinweistext. Der Aufklapp-Zustand
 * der Listen wird bei Aenderung von [resetKey] (Modus-Filter) zurueckgesetzt.
 */
@Composable
fun SequenceSection(
    ui: SequenceSectionUi,
    resetKey: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsSectionHeader(stringResource(R.string.stats_seq_section))
        if (ui.visitsCounted == 0) {
            Text(
                text = stringResource(R.string.stats_seq_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FirstDartBlock(ui)
            ui.positionAverages?.let { PositionBlock(it) }
            SequenceListBlock(
                header = stringResource(R.string.stats_seq_patterns_header),
                hint = stringResource(R.string.stats_seq_patterns_hint, formatCount(ui.completeVisits)),
                rows = ui.patterns,
                countOf = VisitPattern::count,
                resetKey = "$resetKey/patterns",
            ) { pattern, maxCount -> PatternRow(pattern, maxCount) }
            SequenceListBlock(
                header = stringResource(R.string.stats_seq_transitions_header),
                hint = stringResource(R.string.stats_seq_transitions_hint),
                rows = ui.transitions,
                countOf = Transition::count,
                resetKey = "$resetKey/transitions",
            ) { transition, maxCount -> TransitionRow(transition, maxCount) }
        }
    }
}

/** Hinweiszeile unter einer Unter-Ueberschrift. */
@Composable
private fun SequenceHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Block a: haeufigster erster Treffer und Anteil "erster Dart daneben". */
@Composable
private fun FirstDartBlock(ui: SequenceSectionUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsSubHeader(stringResource(R.string.stats_seq_first_header))
        StatTileGrid(tiles = listOf(favoriteTile(ui), missTile(ui)))
    }
}

@Composable
private fun favoriteTile(ui: SequenceSectionUi): StatTileUi {
    val label = stringResource(R.string.stats_seq_tile_first_favorite)
    val none = stringResource(R.string.stats_value_none)
    val share = ui.favoriteFirstShare
    if (ui.favoriteFirst.isEmpty() || share == null) {
        return StatTileUi(
            value = none,
            label = label,
            spokenText = stringResource(R.string.stats_tile_none_cd, label),
        )
    }
    val tie = ui.favoriteFirst.size > 1
    val shown = ui.favoriteVisible.map(::hitFieldShortLabel)
    val joined = if (shown.size >= 2) {
        stringResource(R.string.stats_seq_tie_join, shown[0], shown[1])
    } else {
        shown.first()
    }
    val value = if (ui.favoriteHidden > 0) {
        stringResource(R.string.stats_seq_tie_more, joined, ui.favoriteHidden)
    } else {
        joined
    }
    // TalkBack nennt bei Gleichstand alle Felder: "a, b und c".
    val spokenFields = ui.favoriteFirst.map(::hitFieldSpokenLabel)
    val spokenJoined = if (spokenFields.size == 1) {
        spokenFields.first()
    } else {
        spokenFields.dropLast(1).joinToString(", ") + stringResource(R.string.stats_seq_and) + spokenFields.last()
    }
    val percentNumber = formatPercentNumber(share) ?: none
    val percent = formatPercent(share) ?: none
    return StatTileUi(
        value = value,
        label = label,
        supporting = if (tie) {
            stringResource(R.string.stats_seq_first_share_tie, percent)
        } else {
            stringResource(R.string.stats_seq_first_share, percent)
        },
        spokenText = if (tie) {
            stringResource(R.string.stats_seq_first_tie_cd, label, spokenJoined, percentNumber)
        } else {
            stringResource(R.string.stats_seq_first_cd, label, spokenJoined, percentNumber)
        },
    )
}

@Composable
private fun missTile(ui: SequenceSectionUi): StatTileUi {
    val label = stringResource(R.string.stats_seq_tile_first_miss)
    val share = ui.firstMissShare
        ?: return StatTileUi(
            value = stringResource(R.string.stats_value_none),
            label = label,
            spokenText = stringResource(R.string.stats_tile_none_cd, label),
        )
    return StatTileUi(
        value = formatPercent(share) ?: stringResource(R.string.stats_value_none),
        label = label,
        supporting = stringResource(R.string.stats_fraction, ui.firstMissCount, ui.visitsCounted),
        spokenText = pluralStringResource(
            R.plurals.stats_seq_miss_cd,
            ui.visitsCounted,
            formatPercentNumber(share) ?: "",
            ui.firstMissCount,
            ui.visitsCounted,
        ),
    )
}

/** Block b: Ø Punkte je Dart fuer Dart 1/2/3 (nur X01). */
@Composable
private fun PositionBlock(averages: List<Double?>) {
    val tiles = averages.mapIndexed { index, average ->
        val label = stringResource(R.string.stats_seq_position_label, index + 1)
        val value = formatAverage(average)
        if (average == null || value == null) {
            StatTileUi(
                value = stringResource(R.string.stats_value_none),
                label = label,
                spokenText = stringResource(R.string.stats_tile_none_cd, label),
            )
        } else {
            val perVisit = formatAverage(average * 3) ?: ""
            StatTileUi(
                value = value,
                label = label,
                supporting = stringResource(R.string.stats_seq_position_per_visit, perVisit),
                spokenText = stringResource(R.string.stats_seq_position_cd, label, value, perVisit),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            StatsSubHeader(stringResource(R.string.stats_seq_position_header))
            SequenceHint(stringResource(R.string.stats_seq_position_hint))
        }
        StatTileGrid(tiles = tiles, columnsFor = ::positionGridColumns)
    }
}

/**
 * Block c/d: Unter-Ueberschrift, Hinweis und Balkenliste mit Aufklapp-Button. Leere
 * [rows] (nach Rauschfilter) zeigen statt Hinweis und Liste "Noch zu wenige Aufnahmen".
 * Die Balken beziehen sich auf die Anzahl ([countOf]) der ersten Zeile.
 */
@Composable
private fun <T> SequenceListBlock(
    header: String,
    hint: String,
    rows: List<T>,
    countOf: (T) -> Int,
    resetKey: String,
    row: @Composable (item: T, maxCount: Int) -> Unit,
) {
    var expanded by rememberSaveable(resetKey) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            StatsSubHeader(header)
            SequenceHint(if (rows.isEmpty()) stringResource(R.string.stats_seq_too_few) else hint)
        }
        if (rows.isEmpty()) return@Column
        val maxCount = countOf(rows.first())
        visibleSequenceRows(rows, expanded).forEach { item -> row(item, maxCount) }
        if (rows.size > SEQUENCE_ROWS_COLLAPSED) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    if (expanded) {
                        stringResource(R.string.stats_distribution_show_less)
                    } else {
                        pluralStringResource(R.plurals.stats_seq_show_all, rows.size, rows.size)
                    },
                )
            }
        }
    }
}

@Composable
private fun PatternRow(pattern: VisitPattern, maxCount: Int) {
    val spokenFields = patternDisplayFields(pattern).joinToString(", ", transform = ::hitFieldSpokenLabel)
    SequenceRow(
        label = patternLabel(pattern),
        count = pattern.count,
        maxCount = maxCount,
        spokenText = stringResource(R.string.stats_seq_pattern_cd, spokenFields, pattern.count),
    )
}

@Composable
private fun TransitionRow(transition: Transition, maxCount: Int) {
    SequenceRow(
        label = transitionLabel(transition),
        count = transition.count,
        maxCount = maxCount,
        spokenText = stringResource(
            R.string.stats_seq_transition_cd,
            hitFieldSpokenLabel(transition.from),
            hitFieldSpokenLabel(transition.to),
            transition.count,
        ),
    )
}

/** Zeile wie in der Felder-Liste: Label + Anzahl, darunter der stille Balken. */
@Composable
private fun SequenceRow(label: String, count: Int, maxCount: Int, spokenText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spokenText },
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_seq_count, count),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        StatBar(fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f)
    }
}

// --- Previews ---

private fun previewPattern(count: Int, vararg fields: Pair<Int, Int>) =
    VisitPattern(fields.map { HitField(it.first, it.second) }, count)

private fun previewTransition(count: Int, from: Pair<Int, Int>, to: Pair<Int, Int>) =
    Transition(HitField(from.first, from.second), HitField(to.first, to.second), count)

private val previewSequenceUi = SequenceSectionUi(
    visitsCounted = 150,
    favoriteFirst = listOf(HitField(20, 3)),
    favoriteFirstShare = 0.34,
    firstMissCount = 12,
    firstMissShare = 0.08,
    positionAverages = listOf(21.4, 19.8, 18.1),
    completeVisits = 132,
    patterns = listOf(
        previewPattern(8, 1 to 1, 20 to 1, 20 to 3),
        previewPattern(6, 5 to 1, 20 to 1, 20 to 1),
        previewPattern(5, 1 to 1, 5 to 1, 20 to 1),
        previewPattern(4, 20 to 1, 20 to 1, 20 to 3),
        previewPattern(3, 0 to 1, 20 to 1, 20 to 3),
        previewPattern(2, 25 to 1, 25 to 2, 20 to 3),
        previewPattern(2, 1 to 1, 1 to 1, 5 to 1),
    ),
    transitions = listOf(
        previewTransition(12, 20 to 3, 20 to 1),
        previewTransition(9, 20 to 1, 20 to 1),
        previewTransition(4, 20 to 1, 1 to 1),
    ),
)

@Composable
private fun SequencePreviewFrame(ui: SequenceSectionUi) {
    TomsDartsTheme {
        Column(modifier = Modifier.widthIn(max = 600.dp).padding(16.dp)) {
            SequenceSection(ui = ui, resetKey = "preview")
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1100, name = "Wurfmuster X01")
@Composable
private fun SequenceSectionPreview() {
    SequencePreviewFrame(previewSequenceUi)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1100, name = "Gleichstand inkl. Miss")
@Composable
private fun SequenceSectionTiePreview() {
    SequencePreviewFrame(
        previewSequenceUi.copy(
            favoriteFirst = listOf(HitField(20, 3), HitField(20, 1), HitField(19, 1)),
            favoriteFirstShare = 0.3,
            firstMissCount = 45,
            firstMissShare = 0.3,
        ),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900, name = "Nur Cricket")
@Composable
private fun SequenceSectionCricketPreview() {
    SequencePreviewFrame(previewSequenceUi.copy(positionAverages = null))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Listen leer")
@Composable
private fun SequenceSectionTooFewPreview() {
    SequencePreviewFrame(
        previewSequenceUi.copy(visitsCounted = 3, completeVisits = 2, patterns = emptyList(), transitions = emptyList()),
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 1100, name = "Wurfmuster 320 dp")
@Composable
private fun SequenceSectionNarrowPreview() {
    SequencePreviewFrame(previewSequenceUi)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1800, fontScale = 2f, name = "Wurfmuster Schrift 200 %")
@Composable
private fun SequenceSectionFontScalePreview() {
    SequencePreviewFrame(previewSequenceUi)
}

@Preview(showBackground = true, widthDp = 760, heightDp = 1000, name = "Wurfmuster Querformat")
@Composable
private fun SequenceSectionLandscapePreview() {
    SequencePreviewFrame(previewSequenceUi)
}
