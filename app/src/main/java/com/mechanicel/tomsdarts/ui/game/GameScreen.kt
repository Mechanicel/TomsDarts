package com.mechanicel.tomsdarts.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.ui.input.DartInputState
import com.mechanicel.tomsdarts.ui.input.DartKeypadCallbacks
import com.mechanicel.tomsdarts.ui.input.DartKeypadContent
import com.mechanicel.tomsdarts.ui.input.DartModifier
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme
import kotlinx.coroutines.delay

/** Dauer, fuer die das transiente Bust-Banner sichtbar bleibt (Millisekunden). */
private const val BUST_BANNER_MILLIS = 1500L

/**
 * Zustandsbehafteter Einstiegspunkt des Spiel-Bildschirms (Mehrspieler, X01).
 *
 * Bezieht das [GameViewModel] ueber [GameViewModel.provideFactory], sammelt
 * [GameViewModel.uiState] und [GameViewModel.bustEvents] lifecycle-bewusst und
 * leitet aus dem hochzaehlenden Bust-Zaehler ein transientes, selbst
 * abklingendes Bust-Banner ab. Das Rendern delegiert er an die zustandslose
 * [GameScreenContent].
 *
 * @param modeKey Kennung des Spielmodus (siehe [com.mechanicel.tomsdarts.game.GameModeCatalog]).
 * @param playerIds Teilnehmer in Reihenfolge (>= 2 fuer ein Match).
 * @param startScore Gewaehlter Startpunktwert (z.B. 301/501/701).
 * @param doubleOut Ob zum Auschecken ein Double noetig ist.
 * @param legsToWin Anzahl zu gewinnender Legs je Set (first to N).
 * @param setsToWin Anzahl zu gewinnender Sets fuer den Matchsieg (first to N).
 * @param onExit Verlassen des Spiel-Bildschirms (zurueck zur Profilliste).
 */
@Composable
fun GameScreen(
    modeKey: String,
    playerIds: List<Long>,
    startScore: Int,
    doubleOut: Boolean,
    legsToWin: Int,
    setsToWin: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: GameViewModel<*> =
        viewModel(
            factory = GameViewModel.provideFactory(
                modeKey, playerIds, startScore, doubleOut, legsToWin, setsToWin,
            ),
        )
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val bustCounter by vm.bustEvents.collectAsStateWithLifecycle()

    var bustVisible by remember { mutableStateOf(false) }
    // Der initiale Zaehlerwert ist 0; nur tatsaechliche Erhoehungen sollen das
    // Banner ausloesen, daher wird der Effekt beim ersten Wert (0) uebersprungen.
    LaunchedEffect(bustCounter) {
        if (bustCounter > 0) {
            bustVisible = true
            delay(BUST_BANNER_MILLIS)
            bustVisible = false
        }
    }

    GameScreenContent(
        uiState = uiState,
        callbacks = GameScreenCallbacks(
            onNumber = vm::onNumber,
            onBull = vm::onBull,
            onOut = vm::onOut,
            onToggleDouble = vm::onToggleDouble,
            onToggleTriple = vm::onToggleTriple,
            onUndo = vm::onUndo,
            onNewLeg = vm::onNewLeg,
            onExit = onExit,
        ),
        bustVisible = bustVisible,
        modifier = modifier,
    )
}

/**
 * Zustandsloser Bildschirminhalt des Spiel-Bildschirms. Rendert TopAppBar mit
 * Zurueck-Aktion und den vom [uiState] abhaengigen Inhalt: Ladeanzeige, Fehler-,
 * Kein-Spieler-, Spiel-, Leg-Sieg- oder Match-Sieg-Zustand. Der TopAppBar-Titel
 * im Spiel zeigt den aktuell werfenden Spieler.
 *
 * @param uiState Aktueller Spielzustand.
 * @param callbacks Aktionen des Bildschirms.
 * @param bustVisible Ob das transiente Bust-Banner aktuell sichtbar ist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreenContent(
    uiState: GameUiState,
    callbacks: GameScreenCallbacks,
    bustVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = when (uiState) {
        is GameUiState.Playing ->
            uiState.players.firstOrNull { it.isCurrent }?.name
                ?: stringResource(R.string.game_title)
        else -> stringResource(R.string.game_title)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TextButton(onClick = callbacks.onExit) {
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
                GameUiState.Loading -> LoadingContent()
                GameUiState.Error -> ErrorContent(onExit = callbacks.onExit)
                GameUiState.NoPlayer -> NoPlayerContent(onExit = callbacks.onExit)
                is GameUiState.Playing -> PlayingContent(
                    playing = uiState,
                    callbacks = callbacks,
                    bustVisible = bustVisible,
                )
                is GameUiState.LegWon -> LegWonContent(legWon = uiState, callbacks = callbacks)
                is GameUiState.MatchWon -> MatchWonContent(matchWon = uiState, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.game_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        // Kein eigener Retry-Hook im ViewModel: "Erneut versuchen" verlaesst den
        // Bildschirm; ein erneuter Einstieg startet ein frisches Match.
        OutlinedButton(onClick = onExit) {
            Text(stringResource(R.string.game_retry))
        }
    }
}

@Composable
private fun NoPlayerContent(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.game_no_player),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        OutlinedButton(onClick = onExit) {
            Text(stringResource(R.string.game_back_to_players))
        }
    }
}

@Composable
private fun PlayingContent(
    playing: GameUiState.Playing,
    callbacks: GameScreenCallbacks,
    bustVisible: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = bustVisible) {
            BustBanner()
        }
        MatchScoreboard(
            players = playing.players,
            currentLegNumber = playing.currentLegNumber,
            currentSetNumber = playing.currentSetNumber,
            legsToWin = playing.legsToWin,
            setsToWin = playing.setsToWin,
        )
        DartKeypadContent(
            state = playing.input,
            callbacks = DartKeypadCallbacks(
                onToggleDouble = callbacks.onToggleDouble,
                onToggleTriple = callbacks.onToggleTriple,
                onNumber = callbacks.onNumber,
                onBull = callbacks.onBull,
                onOut = callbacks.onOut,
                onUndo = callbacks.onUndo,
            ),
            checkout = playing.checkout,
            canUndo = playing.canUndo,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun BustBanner() {
    val text = stringResource(R.string.game_bust)
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

@Composable
private fun LegWonContent(
    legWon: GameUiState.LegWon,
    callbacks: GameScreenCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.game_leg_won_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = legWon.legWinnerName,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                legWon.dartsUsed?.let { darts ->
                    Text(
                        text = stringResource(R.string.game_won_darts, darts),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.game_next_starter, legWon.nextStarterName),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        StandingsBlock(
            label = stringResource(R.string.game_leg_standing_label),
            players = legWon.players,
        )
        Button(
            onClick = callbacks.onNewLeg,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.game_next_leg))
        }
        OutlinedButton(
            onClick = callbacks.onExit,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.game_back))
        }
    }
}

@Composable
private fun MatchWonContent(
    matchWon: GameUiState.MatchWon,
    callbacks: GameScreenCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.game_match_won_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = matchWon.matchWinnerName,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (matchWon.players.size == 2) {
                    Text(
                        text = stringResource(
                            R.string.game_final_standing_value,
                            matchWon.players[0].legsWon,
                            matchWon.players[1].legsWon,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                matchWon.dartsUsed?.let { darts ->
                    Text(
                        text = stringResource(R.string.game_won_darts, darts),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        StandingsBlock(
            label = stringResource(R.string.game_final_standing_label),
            players = matchWon.players,
        )
        OutlinedButton(
            onClick = callbacks.onExit,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.game_back))
        }
    }
}

/** Beschrifteter Stand-Block: Label plus eine Zeile (Name + L/S) je Spieler. */
@Composable
private fun StandingsBlock(
    label: String,
    players: List<PlayerScoreUi>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        players.forEach { player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(
                        R.string.game_player_standing,
                        player.legsWon,
                        player.setsWon,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

// --- Previews ---

private fun previewPlayers(currentIndex: Int = 0) = listOf(
    PlayerScoreUi(
        playerId = 1, name = "Tom", board = PlayerBoardUi.X01(287), legsWon = 1, setsWon = 0,
        isCurrent = currentIndex == 0,
        lastTurnDarts = listOf(Dart.triple(20), Dart.single(5), Dart.double(16)),
    ),
    PlayerScoreUi(
        playerId = 2, name = "Anna Beispiel", board = PlayerBoardUi.X01(340), legsWon = 0, setsWon = 0,
        isCurrent = currentIndex == 1,
        lastTurnDarts = listOf(Dart.triple(20), Dart.double(20)),
    ),
)

private fun previewPlaying(
    darts: List<Dart> = listOf(Dart.triple(20), Dart.single(14)),
    players: List<PlayerScoreUi> = previewPlayers(),
    checkout: List<Dart>? = null,
) = GameUiState.Playing(
    players = players,
    startScore = 501,
    input = DartInputState(modifier = DartModifier.SINGLE, darts = darts),
    currentLegNumber = 2,
    currentSetNumber = 1,
    legsToWin = 2,
    setsToWin = 1,
    checkout = checkout,
)

@Preview(showBackground = true, name = "Spiel laeuft", heightDp = 760)
@Composable
private fun GameScreenPlayingPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Bust + Checkout", heightDp = 760)
@Composable
private fun GameScreenBustPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(
                darts = emptyList(),
                players = previewPlayers().map { it.copy(lastTurnDarts = emptyList()) },
                checkout = listOf(Dart.double(20)),
            ),
            callbacks = GameScreenCallbacks(),
            bustVisible = true,
        )
    }
}

@Preview(showBackground = true, name = "Leg gewonnen", heightDp = 760)
@Composable
private fun GameScreenLegWonPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = GameUiState.LegWon(
                players = previewPlayers(currentIndex = 1),
                legWinnerName = "Tom",
                nextStarterName = "Anna Beispiel",
                nextLegNumber = 2,
                dartsUsed = 15,
            ),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Match gewonnen", heightDp = 760)
@Composable
private fun GameScreenMatchWonPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = GameUiState.MatchWon(
                players = listOf(
                    PlayerScoreUi(1, "Tom", PlayerBoardUi.X01(0), 2, 1, isCurrent = false),
                    PlayerScoreUi(2, "Anna Beispiel", PlayerBoardUi.X01(84), 1, 0, isCurrent = false),
                ),
                matchWinnerName = "Tom",
                dartsUsed = 12,
            ),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Laedt", heightDp = 760)
@Composable
private fun GameScreenLoadingPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = GameUiState.Loading,
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Kein Spieler", heightDp = 760)
@Composable
private fun GameScreenNoPlayerPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = GameUiState.NoPlayer,
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Querformat mit Checkout", widthDp = 760, heightDp = 380)
@Composable
private fun GameScreenLandscapePreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(
                checkout = listOf(Dart.triple(20), Dart.double(20)),
            ),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}
