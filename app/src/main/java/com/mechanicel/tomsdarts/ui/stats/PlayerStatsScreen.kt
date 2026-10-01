package com.mechanicel.tomsdarts.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.analytics.HitDistribution
import com.mechanicel.tomsdarts.analytics.HitField
import com.mechanicel.tomsdarts.analytics.X01Metrics
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Buendelt die Callbacks des Spieler-Statistik-Screens fuer die zustandslose
 * [PlayerStatsContent], damit diese @Preview-faehig bleibt.
 *
 * @param onBack Zurueck zur Profilliste (TopAppBar, System-Zurueck, "Spieler fehlt").
 * @param onRetry Erneuter Ladeversuch nach einem Fehler.
 * @param onSelectMode Modus-Filter waehlen; `null` = Alle.
 */
data class PlayerStatsCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectMode: (String?) -> Unit = {},
)

/**
 * Einstiegspunkt der Spieler-Statistik (Phase 5, ADR-0037). Bezieht das
 * [PlayerStatsViewModel] je Spieler ueber [PlayerStatsViewModel.provideFactory]
 * und delegiert an die zustandslose [PlayerStatsContent].
 */
@Composable
fun PlayerStatsScreen(
    playerId: Long,
    onBack: () -> Unit,
    viewModel: PlayerStatsViewModel = viewModel(
        key = "player_stats_$playerId",
        factory = PlayerStatsViewModel.provideFactory(playerId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerStatsContent(
        uiState = uiState,
        callbacks = PlayerStatsCallbacks(
            onBack = onBack,
            onRetry = viewModel::retry,
            onSelectMode = viewModel::selectMode,
        ),
    )
}

/**
 * Zustandsloser Bildschirminhalt der Spieler-Statistik: TopAppBar mit Zurueck
 * und der vom [uiState] abhaengige Inhalt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsContent(
    uiState: PlayerStatsUiState,
    callbacks: PlayerStatsCallbacks,
    modifier: Modifier = Modifier,
) {
    // System-Zurueck fuehrt zurueck zur Profilliste, nicht aus der App.
    BackHandler(onBack = callbacks.onBack)
    val title = when (uiState) {
        is PlayerStatsUiState.Content -> stringResource(R.string.stats_player_title, uiState.playerName)
        is PlayerStatsUiState.Empty -> stringResource(R.string.stats_player_title, uiState.playerName)
        else -> stringResource(R.string.stats_title)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                PlayerStatsUiState.Loading -> StatsLoading()
                is PlayerStatsUiState.Empty -> StatsEmpty(
                    title = stringResource(R.string.stats_player_empty_title),
                    hint = stringResource(R.string.stats_player_empty_hint, uiState.playerName),
                )
                PlayerStatsUiState.PlayerNotFound -> StatsError(
                    message = stringResource(R.string.stats_player_not_found),
                    actionLabel = stringResource(R.string.game_back_to_players),
                    onAction = callbacks.onBack,
                )
                is PlayerStatsUiState.Error -> StatsError(
                    message = stringResource(R.string.stats_error),
                    actionLabel = stringResource(R.string.profile_error_retry),
                    onAction = callbacks.onRetry,
                )
                is PlayerStatsUiState.Content -> StatsSections(content = uiState, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun StatsSections(
    content: PlayerStatsUiState.Content,
    callbacks: PlayerStatsCallbacks,
) {
    val resetKey = content.selectedMode ?: "all"
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (content.showModeFilter) {
                item(key = "filter") {
                    ModeFilterRow(
                        modes = content.modeFilters,
                        selectedMode = content.selectedMode,
                        onSelectMode = callbacks.onSelectMode,
                    )
                }
            }
            content.sections.forEach { section ->
                item(key = section.key) {
                    when (section) {
                        is StatsSectionUi.Overview -> OverviewSection(section)
                        is StatsSectionUi.X01 -> X01Section(section.metrics)
                        StatsSectionUi.X01Empty -> X01EmptySection()
                        is StatsSectionUi.Distribution -> Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatsSectionHeader(stringResource(R.string.stats_section_distribution))
                            HitDistributionSection(
                                distribution = section.distribution,
                                resetKey = resetKey,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Einzeilige Filter-Leiste: "Alle" plus die gespielten Modi (Einfachauswahl). */
@Composable
private fun ModeFilterRow(
    modes: List<String>,
    selectedMode: String?,
    onSelectMode: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            ModeChip(
                label = stringResource(R.string.stats_filter_all),
                selected = selectedMode == null,
                onClick = { onSelectMode(null) },
            )
        }
        items(modes, key = { it }) { mode ->
            ModeChip(
                label = statsModeLabel(mode),
                selected = selectedMode == mode,
                onClick = { onSelectMode(mode) },
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // FilterChip erzwingt selbst ein 48-dp-Touch-Ziel (minimumInteractiveComponentSize).
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
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

@Composable
private fun OverviewSection(overview: StatsSectionUi.Overview) {
    val matchesLabel = stringResource(R.string.stats_tile_matches)
    val winsLabel = stringResource(R.string.stats_tile_wins)
    val matches = formatCount(overview.matches)
    val wins = formatCount(overview.wins)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsSectionHeader(stringResource(R.string.stats_section_overview))
        StatTileGrid(
            tiles = listOf(
                StatTileUi(
                    value = matches,
                    label = matchesLabel,
                    spokenText = stringResource(R.string.stats_tile_cd, matchesLabel, matches),
                ),
                StatTileUi(
                    value = wins,
                    label = winsLabel,
                    spokenText = stringResource(R.string.stats_tile_cd, winsLabel, wins),
                ),
            ),
        )
    }
}

/** Kachel-Daten mit "–" und "keine Daten"-Ansage, falls [value] fehlt. */
@Composable
private fun simpleTile(label: String, value: String?, supporting: String? = null): StatTileUi =
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

@Composable
private fun X01Section(metrics: X01Metrics) {
    val checkoutLabel = stringResource(R.string.stats_tile_checkout_rate)
    val checkoutTile = if (metrics.checkoutRate == null) {
        simpleTile(label = checkoutLabel, value = null)
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
    val tiles = listOf(
        simpleTile(stringResource(R.string.stats_tile_avg3), formatAverage(metrics.threeDartAverage)),
        simpleTile(stringResource(R.string.stats_tile_first9), formatAverage(metrics.firstNineAverage)),
        checkoutTile,
        simpleTile(stringResource(R.string.stats_tile_highest_checkout), metrics.highestCheckout?.toString()),
        simpleTile(
            label = stringResource(R.string.stats_tile_legs_won),
            value = formatCount(metrics.legsWon),
            supporting = stringResource(R.string.stats_of_total, metrics.legsPlayed),
        ),
        simpleTile(stringResource(R.string.stats_tile_darts_thrown), formatCount(metrics.dartsThrown)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        X01Header()
        StatTileGrid(tiles = tiles)
    }
}

@Composable
private fun X01Header() {
    Column {
        StatsSectionHeader(stringResource(R.string.stats_section_x01))
        Text(
            text = stringResource(R.string.stats_x01_scope_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun X01EmptySection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        X01Header()
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.stats_x01_empty),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.stats_x01_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// --- Previews ---

private val previewMetrics = X01Metrics(
    dartsThrown = 1234,
    pointsScored = 23_957,
    threeDartAverage = 58.25,
    firstNineAverage = 64.1,
    checkoutAttempts = 40,
    checkoutHits = 12,
    checkoutRate = 0.3,
    highestCheckout = 121,
    legsPlayed = 25,
    legsWon = 14,
)

private val previewDistribution: HitDistribution = run {
    val fields = buildMap {
        put(HitField(0, 1), 31)
        put(HitField(25, 1), 9)
        put(HitField(25, 2), 2)
        (1..20).forEach { segment ->
            put(HitField(segment, 1), segment * 3)
            if (segment % 2 == 0) put(HitField(segment, 2), segment / 4)
            if (segment >= 15) put(HitField(segment, 3), segment - 12)
        }
    }.filterValues { it > 0 }
    HitDistribution(
        totalDarts = fields.values.sum(),
        byField = fields,
        bySegment = fields.entries.groupBy({ it.key.segment }, { it.value }).mapValues { it.value.sum() },
        byMultiplier = fields.filterKeys { it.segment != 0 }.entries
            .groupBy({ it.key.multiplier }, { it.value }).mapValues { it.value.sum() },
        misses = fields[HitField(0, 1)] ?: 0,
    )
}

private fun previewContent(name: String = "Tom") = PlayerStatsUiState.Content(
    playerName = name,
    modeFilters = listOf(GameModeCatalog.X01, GameModeCatalog.CRICKET),
    selectedMode = null,
    sections = listOf(
        StatsSectionUi.Overview(matches = 18, wins = 11),
        StatsSectionUi.X01(previewMetrics),
        StatsSectionUi.Distribution(previewDistribution),
    ),
)

@Preview(showBackground = true, widthDp = 360, heightDp = 1400, name = "Inhalt 360 dp")
@Composable
private fun PlayerStatsContentPreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = previewContent(), callbacks = PlayerStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 900, name = "Langer Name 320 dp")
@Composable
private fun PlayerStatsLongNamePreview() {
    TomsDartsTheme {
        PlayerStatsContent(
            uiState = previewContent(name = "Maximilian-Alexander von Mustermann-Beispielhausen"),
            callbacks = PlayerStatsCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1600, fontScale = 2f, name = "Schrift 200 %")
@Composable
private fun PlayerStatsFontScalePreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = previewContent(), callbacks = PlayerStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 760, heightDp = 380, name = "Querformat")
@Composable
private fun PlayerStatsLandscapePreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = previewContent(), callbacks = PlayerStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "X01 leer")
@Composable
private fun PlayerStatsX01EmptyPreview() {
    TomsDartsTheme {
        PlayerStatsContent(
            uiState = previewContent().copy(
                modeFilters = listOf(GameModeCatalog.CRICKET),
                sections = listOf(
                    StatsSectionUi.Overview(matches = 3, wins = 1),
                    StatsSectionUi.X01Empty,
                    StatsSectionUi.Distribution(previewDistribution),
                ),
            ),
            callbacks = PlayerStatsCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Laden")
@Composable
private fun PlayerStatsLoadingPreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = PlayerStatsUiState.Loading, callbacks = PlayerStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Leer")
@Composable
private fun PlayerStatsEmptyPreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = PlayerStatsUiState.Empty("Tom"), callbacks = PlayerStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Fehler")
@Composable
private fun PlayerStatsErrorPreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = PlayerStatsUiState.Error(), callbacks = PlayerStatsCallbacks())
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Spieler fehlt")
@Composable
private fun PlayerStatsNotFoundPreview() {
    TomsDartsTheme {
        PlayerStatsContent(uiState = PlayerStatsUiState.PlayerNotFound, callbacks = PlayerStatsCallbacks())
    }
}
