package com.mechanicel.tomsdarts.ui.stats

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.analytics.Transition
import com.mechanicel.tomsdarts.analytics.VisitPattern
import com.mechanicel.tomsdarts.analytics.X01Metrics
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Buendelt die Callbacks des Match-Statistik-Screens fuer die zustandslose
 * [MatchStatsContent], damit diese @Preview-faehig bleibt.
 *
 * @param onBack Zurueck zum Einstieg (Spieler-Statistik bzw. Profilliste).
 * @param onRetry Erneuter Ladeversuch nach einem Fehler.
 * @param onSelectPlayer Teilnehmer fuer Trefferverteilung und Wurfmuster waehlen.
 */
data class MatchStatsCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectPlayer: (Long) -> Unit = {},
)

/**
 * Einstiegspunkt der Match-Statistik (Phase 5, Lieferung 2/2, ADR-0037).
 * Bezieht das [MatchStatsViewModel] je Match ueber
 * [MatchStatsViewModel.provideFactory] und delegiert an die zustandslose
 * [MatchStatsContent].
 */
@Composable
fun MatchStatsScreen(
    matchId: Long,
    onBack: () -> Unit,
    viewModel: MatchStatsViewModel = viewModel(
        key = "match_stats_$matchId",
        factory = MatchStatsViewModel.provideFactory(matchId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MatchStatsContent(
        uiState = uiState,
        callbacks = MatchStatsCallbacks(
            onBack = onBack,
            onRetry = viewModel::retry,
            onSelectPlayer = viewModel::selectPlayer,
        ),
    )
}

/**
 * Zustandsloser Bildschirminhalt der Match-Statistik: TopAppBar mit Zurueck und
 * der vom [uiState] abhaengige Inhalt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchStatsContent(
    uiState: MatchStatsUiState,
    callbacks: MatchStatsCallbacks,
    modifier: Modifier = Modifier,
) {
    // System-Zurueck fuehrt zum Einstieg zurueck, nicht aus der App.
    BackHandler(onBack = callbacks.onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_match_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = callbacks.onBack) {
                        Text(stringResource(R.string.game_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                MatchStatsUiState.Loading -> StatsLoading()
                MatchStatsUiState.NotFound -> StatsError(
                    message = stringResource(R.string.stats_match_not_found),
                    actionLabel = stringResource(R.string.game_back),
                    onAction = callbacks.onBack,
                )
                is MatchStatsUiState.Error -> StatsError(
                    message = stringResource(R.string.stats_error),
                    actionLabel = stringResource(R.string.profile_error_retry),
                    onAction = callbacks.onRetry,
                )
                is MatchStatsUiState.Empty -> MatchStatsColumn {
                    item(key = "header") { MatchHeaderPanel(uiState.header) }
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.stats_match_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is MatchStatsUiState.Content -> MatchStatsSections(content = uiState, callbacks = callbacks)
            }
        }
    }
}

/** Zentrierte, auf 600 dp begrenzte Abschnittsliste (wie Spieler-Screen). */
@Composable
private fun MatchStatsColumn(content: LazyListScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun MatchStatsSections(
    content: MatchStatsUiState.Content,
    callbacks: MatchStatsCallbacks,
) {
    // Aufklapp-Zustaende von Felder-/Muster-Listen gelten je gewaehltem Spieler.
    val resetKey = "player_${content.selectedPlayerKey}"
    MatchStatsColumn {
        item(key = "header") { MatchHeaderPanel(content.header) }
        if (!content.isX01) {
            item(key = "non_x01_hint") {
                Text(
                    text = stringResource(R.string.stats_non_x01_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(content.players, key = { "player_${it.key}" }) { player ->
            PlayerSection(player = player)
        }
        item(key = "distribution") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsSectionHeader(stringResource(R.string.stats_section_distribution))
                if (content.showPlayerChips) {
                    PlayerChipRow(
                        players = content.players,
                        selectedKey = content.selectedPlayerKey,
                        onSelect = callbacks.onSelectPlayer,
                    )
                }
                HitDistributionSection(distribution = content.distribution, resetKey = resetKey)
            }
        }
        // Gekoppelt an dieselbe Chip-Reihe wie die Trefferverteilung (keine zweite Reihe).
        item(key = "sequences") {
            SequenceSection(ui = content.sequences, resetKey = resetKey)
        }
        item(key = "legs") {
            LegsSection(legs = content.legs)
        }
    }
}

/** Anzeigename eines Teilnehmers; `null` = geloeschter Spieler. */
@Composable
private fun participantName(name: String?): String = name ?: stringResource(R.string.player_deleted)

/** Text eines Match-/Leg-Ergebnisses; [winnerFormat] formatiert den Sieger. */
@Composable
private fun resultText(result: MatchResultUi, @StringRes winnerFormat: Int?): String = when (result) {
    is MatchResultUi.Winner -> {
        val name = participantName(result.name)
        if (winnerFormat == null) name else stringResource(winnerFormat, name)
    }
    MatchResultUi.NoWinner -> stringResource(R.string.stats_match_no_winner)
    MatchResultUi.Open -> stringResource(R.string.stats_result_open)
}

/** Kopf-Panel: Modus, Datum, Status und gespielte Legs; ein TalkBack-Fokus-Stopp. */
@Composable
private fun MatchHeaderPanel(header: MatchHeaderUi) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = statsModeLabel(header.modeType), style = MaterialTheme.typography.titleLarge)
            Text(text = formatMatchDate(header.startedAt), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = resultText(header.result, winnerFormat = R.string.stats_match_winner),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (header.legsPlayed > 0) {
                Text(
                    text = stringResource(R.string.stats_match_legs_played, header.legsPlayed),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Abschnitt eines Teilnehmers: Name (+ Sieger-Suffix) und Kacheln. */
@Composable
private fun PlayerSection(player: MatchPlayerStatsUi) {
    val tiles = if (player.x01 != null) {
        x01MetricTiles(player.x01)
    } else {
        listOf(
            legsWonTile(legsWon = player.legsWon, legsPlayed = player.legsPlayed),
            dartsThrownTile(player.dartsThrown),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerSectionHeader(name = participantName(player.name), isWinner = player.isWinner)
        StatTileGrid(tiles = tiles)
    }
}

/** Spielername als Abschnitts-Ueberschrift; der Sieger traegt den Zusatz "· Sieger" (primary). */
@Composable
private fun PlayerSectionHeader(name: String, isWinner: Boolean) {
    val suffix = stringResource(R.string.stats_winner_suffix)
    val primary = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(name)
        if (isWinner) {
            append(" ")
            withStyle(SpanStyle(color = primary)) { append(suffix) }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/** Einzeilige Chip-Reihe der Teilnehmer (Einfachauswahl). */
@Composable
private fun PlayerChipRow(
    players: List<MatchPlayerStatsUi>,
    selectedKey: Long,
    onSelect: (Long) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(players, key = { it.key }) { player ->
            StatsFilterChip(
                label = participantName(player.name),
                selected = player.key == selectedKey,
                onClick = { onSelect(player.key) },
                maxLabelWidth = 160.dp,
            )
        }
    }
}

/** Legs-Liste: je Leg Bezeichnung links und Ergebnis rechts, ein Fokus-Stopp je Zeile. */
@Composable
private fun LegsSection(legs: List<MatchLegUi>) {
    Column {
        StatsSectionHeader(stringResource(R.string.stats_section_legs))
        legs.forEach { leg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (leg.setNumber != null) {
                        stringResource(R.string.stats_leg_label_set, leg.setNumber, leg.legNumber)
                    } else {
                        stringResource(R.string.stats_leg_label, leg.legNumber)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = resultText(leg.result, winnerFormat = null),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
            }
        }
    }
}

// --- Previews ---

private val previewMatchDistribution: HitDistribution = run {
    val fields = mapOf(
        HitField(20, 1) to 21,
        HitField(20, 3) to 9,
        HitField(5, 1) to 8,
        HitField(1, 1) to 7,
        HitField(16, 2) to 2,
        HitField(25, 1) to 1,
        HitField(0, 1) to 3,
    )
    HitDistribution(
        totalDarts = fields.values.sum(),
        byField = fields,
        bySegment = fields.entries.groupBy({ it.key.segment }, { it.value }).mapValues { it.value.sum() },
        byMultiplier = fields.filterKeys { it.segment != 0 }.entries
            .groupBy({ it.key.multiplier }, { it.value }).mapValues { it.value.sum() },
        misses = 3,
    )
}

private val previewMatchSequences = SequenceSectionUi(
    visitsCounted = 17,
    favoriteFirst = listOf(HitField(20, 1)),
    favoriteFirstShare = 0.41,
    firstMissCount = 1,
    firstMissShare = 1.0 / 17,
    positionAverages = listOf(24.1, 22.0, 19.5),
    completeVisits = 16,
    patterns = listOf(VisitPattern(listOf(HitField(1, 1), HitField(20, 1), HitField(20, 1)), 3)),
    transitions = listOf(Transition(HitField(20, 1), HitField(20, 1), 6)),
)

private fun previewX01Metrics(average: Double, legsWon: Int) = X01Metrics(
    dartsThrown = 51,
    pointsScored = (average * 17).toInt(),
    threeDartAverage = average,
    firstNineAverage = average + 6.2,
    checkoutAttempts = 6,
    checkoutHits = legsWon,
    checkoutRate = legsWon / 6.0,
    highestCheckout = if (legsWon > 0) 64 else null,
    legsPlayed = 3,
    legsWon = legsWon,
)

private val previewHeader = MatchHeaderUi(
    modeType = GameModeCatalog.X01,
    startedAt = 1_727_800_000_000L,
    result = MatchResultUi.Winner("Tom"),
    legsPlayed = 3,
)

private fun previewMatchContent(secondName: String? = "Anna Beispiel") = MatchStatsUiState.Content(
    header = previewHeader,
    isX01 = true,
    players = listOf(
        MatchPlayerStatsUi(1L, "Tom", isWinner = true, previewX01Metrics(58.4, 2), 2, 3, 51),
        MatchPlayerStatsUi(
            key = if (secondName == null) DELETED_PARTICIPANT_KEY else 2L,
            name = secondName,
            isWinner = false,
            x01 = previewX01Metrics(44.9, 1),
            legsWon = 1,
            legsPlayed = 3,
            dartsThrown = 48,
        ),
    ),
    selectedPlayerKey = 1L,
    distribution = previewMatchDistribution,
    sequences = previewMatchSequences,
    legs = listOf(
        MatchLegUi(11L, null, 1, MatchResultUi.Winner("Tom")),
        MatchLegUi(12L, null, 2, MatchResultUi.Winner(secondName)),
        MatchLegUi(13L, null, 3, MatchResultUi.Winner("Tom")),
    ),
)

@Preview(showBackground = true, widthDp = 360, heightDp = 2400, name = "Match X01, 2 Spieler")
@Composable
private fun MatchStatsX01Preview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = previewMatchContent(), callbacks = MatchStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1600, name = "Nicht-X01-Match")
@Composable
private fun MatchStatsNonX01Preview() {
    TomsDartsTheme {
        MatchStatsContent(
            uiState = previewMatchContent().copy(
                header = previewHeader.copy(modeType = GameModeCatalog.CRICKET),
                isX01 = false,
                players = previewMatchContent().players.map { it.copy(x01 = null) },
                sequences = previewMatchSequences.copy(positionAverages = null),
            ),
            callbacks = MatchStatsCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1600, name = "Unbeendet")
@Composable
private fun MatchStatsOpenPreview() {
    TomsDartsTheme {
        MatchStatsContent(
            uiState = previewMatchContent().copy(
                header = previewHeader.copy(result = MatchResultUi.Open, legsPlayed = 1),
                players = previewMatchContent().players.map { it.copy(isWinner = false) },
                legs = listOf(
                    MatchLegUi(11L, 1, 1, MatchResultUi.Winner("Tom")),
                    MatchLegUi(12L, 1, 2, MatchResultUi.Open),
                ),
            ),
            callbacks = MatchStatsCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1600, name = "Geloeschter Spieler")
@Composable
private fun MatchStatsDeletedPlayerPreview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = previewMatchContent(secondName = null), callbacks = MatchStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 1600, name = "Langer Name 320 dp")
@Composable
private fun MatchStatsLongNamePreview() {
    TomsDartsTheme {
        MatchStatsContent(
            uiState = previewMatchContent(secondName = "Maximilian-Alexander von Mustermann-Beispielhausen"),
            callbacks = MatchStatsCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 2400, fontScale = 2f, name = "Schrift 200 %")
@Composable
private fun MatchStatsFontScalePreview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = previewMatchContent(), callbacks = MatchStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 760, heightDp = 380, name = "Querformat")
@Composable
private fun MatchStatsLandscapePreview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = previewMatchContent(), callbacks = MatchStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Laden")
@Composable
private fun MatchStatsLoadingPreview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = MatchStatsUiState.Loading, callbacks = MatchStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Leer")
@Composable
private fun MatchStatsEmptyPreview() {
    TomsDartsTheme {
        MatchStatsContent(
            uiState = MatchStatsUiState.Empty(previewHeader.copy(result = MatchResultUi.Open, legsPlayed = 0)),
            callbacks = MatchStatsCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Fehler")
@Composable
private fun MatchStatsErrorPreview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = MatchStatsUiState.Error(), callbacks = MatchStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Match fehlt")
@Composable
private fun MatchStatsNotFoundPreview() {
    TomsDartsTheme {
        MatchStatsContent(uiState = MatchStatsUiState.NotFound, callbacks = MatchStatsCallbacks())
    }
}
