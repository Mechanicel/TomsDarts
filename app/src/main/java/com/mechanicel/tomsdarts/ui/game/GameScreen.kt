package com.mechanicel.tomsdarts.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Zustandsbehafteter Einstiegspunkt des Spiel-Bildschirms (Einzelspieler, X01).
 *
 * Bezieht das [GameViewModel] ueber [GameViewModel.provideFactory], sammelt
 * [GameViewModel.uiState] und [GameViewModel.bustEvents] lifecycle-bewusst und
 * leitet aus dem hochzaehlenden Bust-Zaehler ein transientes, selbst
 * abklingendes Bust-Banner ab. Das Rendern delegiert er an die zustandslose
 * [GameScreenContent].
 *
 * @param playerId Der werfende Spieler.
 * @param onExit Verlassen des Spiel-Bildschirms (zurueck zur Profilliste).
 */
@Composable
fun GameScreen(
    playerId: Long,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: GameViewModel = viewModel(factory = GameViewModel.provideFactory(playerId))
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
 * Kein-Spieler-, Spiel- oder Sieg-Zustand.
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
        is GameUiState.Playing -> uiState.playerName
        is GameUiState.Won -> uiState.playerName
        else -> stringResource(R.string.game_title)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                is GameUiState.Won -> WonContent(won = uiState, callbacks = callbacks)
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
        // Bildschirm; ein erneuter Einstieg startet ein frisches Leg.
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
        Scoreboard(
            remaining = playing.remaining,
            startScore = playing.startScore,
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
private fun Scoreboard(
    remaining: Int,
    startScore: Int,
) {
    val remainingCd = stringResource(R.string.game_remaining_cd, remaining)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = remaining.toString(),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                contentDescription = remainingCd
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            text = stringResource(R.string.game_of_start, startScore),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WonContent(
    won: GameUiState.Won,
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.game_won_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                won.dartsUsed?.let { darts ->
                    Text(
                        text = stringResource(R.string.game_won_darts, darts),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        Button(
            onClick = callbacks.onNewLeg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.game_new_leg))
        }
        OutlinedButton(
            onClick = callbacks.onExit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.game_back))
        }
    }
}

// --- Previews ---

private fun previewPlaying(
    remaining: Int = 287,
    darts: List<Dart> = listOf(Dart.triple(20), Dart.single(14)),
) = GameUiState.Playing(
    playerName = "Tom",
    startScore = 501,
    remaining = remaining,
    input = DartInputState(modifier = DartModifier.SINGLE, darts = darts),
)

@Preview(showBackground = true, name = "Spiel laeuft", heightDp = 720)
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

@Preview(showBackground = true, name = "Bust-Banner", heightDp = 720)
@Composable
private fun GameScreenBustPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(remaining = 501, darts = emptyList()),
            callbacks = GameScreenCallbacks(),
            bustVisible = true,
        )
    }
}

@Preview(showBackground = true, name = "Gewonnen", heightDp = 720)
@Composable
private fun GameScreenWonPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = GameUiState.Won(playerName = "Tom", dartsUsed = 15),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Laedt", heightDp = 720)
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

@Preview(showBackground = true, name = "Kein Spieler", heightDp = 720)
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

@Preview(showBackground = true, name = "Querformat", widthDp = 720, heightDp = 360)
@Composable
private fun GameScreenLandscapePreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}
