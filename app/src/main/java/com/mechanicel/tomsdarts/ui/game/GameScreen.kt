package com.mechanicel.tomsdarts.ui.game

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.mechanicel.tomsdarts.delight.DelightAnimation
import com.mechanicel.tomsdarts.delight.DelightTextKeys
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.ui.delight.ActiveDelight
import com.mechanicel.tomsdarts.ui.delight.DelightOverlay
import com.mechanicel.tomsdarts.ui.delight.DelightTiming
import com.mechanicel.tomsdarts.ui.delight.DelightUi
import com.mechanicel.tomsdarts.ui.delight.NO_DELIGHT_ID
import com.mechanicel.tomsdarts.ui.delight.activeDelightFromSaveable
import com.mechanicel.tomsdarts.ui.delight.planDelightIntake
import com.mechanicel.tomsdarts.ui.delight.rememberReducedMotion
import com.mechanicel.tomsdarts.ui.delight.toSaveable
import com.mechanicel.tomsdarts.ui.input.DartInputState
import com.mechanicel.tomsdarts.ui.input.DartKeypadCallbacks
import com.mechanicel.tomsdarts.ui.input.DartKeypadContent
import com.mechanicel.tomsdarts.ui.input.DartModifier
import com.mechanicel.tomsdarts.ui.input.ThrownDartsRow
import com.mechanicel.tomsdarts.ui.input.dartSpokenLabel
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
 * Feiern (ADR-0038/ADR-0039): sammelt [GameViewModel.delightEvents], haelt die
 * laufende Feier samt Startzeit ueber Rotation/Prozess-Tod (`rememberSaveable`)
 * und quittiert JEDES Schliessen (Tippen, Zurueck, Ablauf, Ersetzen,
 * Ueberspringen, abgeschaltet) per [GameViewModel.onDelightDismissed]. Die
 * Anzeigedauer laeuft per reinem `delay()` (unabhaengig von der Animations-Uhr).
 *
 * @param modeKey Kennung des Spielmodus (siehe [com.mechanicel.tomsdarts.game.GameModeCatalog]).
 * @param playerIds Teilnehmer in Reihenfolge (>= 2 fuer ein Match).
 * @param startScore Gewaehlter Startpunktwert (z.B. 301/501/701).
 * @param doubleOut Ob zum Auschecken ein Double noetig ist.
 * @param legsToWin Anzahl zu gewinnender Legs je Set (first to N).
 * @param setsToWin Anzahl zu gewinnender Sets fuer den Matchsieg (first to N).
 * @param onExit Verlassen des Spiel-Bildschirms (zurueck zur Profilliste).
 * @param onShowMatchStats Oeffnen der Match-Statistik des gerade entschiedenen
 *   Matches (Button im Sieg-Panel) mit dessen Match-ID.
 * @param delightEnabled Ob Feiern angezeigt werden. `false` verwirft jedes
 *   Event sofort (inkl. Quittierung), damit die Kontrollpause nicht wartet.
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
    onShowMatchStats: (Long) -> Unit = {},
    delightEnabled: Boolean = true,
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

    val delightEvent by vm.delightEvents.collectAsStateWithLifecycle()
    // Laufende Feier inkl. Startzeit (Uptime-Uhr): uebersteht eine Rotation
    // (gleiches ViewModel, gleiche Sitzungs-Kennung). Abgelaufene Feiern und
    // Feiern eines frueheren ViewModels (Prozess-Tod) werden verworfen. Den
    // Dedupe-Zustand ("zuletzt gezeigt") haelt das ViewModel selbst.
    val delightSaver = remember(vm.delightSessionToken) {
        activeDelightStateSaver(vm.delightSessionToken)
    }
    var activeDelight by rememberSaveable(saver = delightSaver) {
        mutableStateOf<ActiveDelight?>(null)
    }
    // Nur eine in DIESER Komposition gestartete Feier animiert; nach Rotation
    // fortgesetzte Feiern zeigen ihr statisches Endbild.
    var animatedDelightId by remember { mutableLongStateOf(NO_DELIGHT_ID) }
    val reducedMotion = rememberReducedMotion()
    val accessibilityManager = LocalAccessibilityManager.current
    val dismissDelight: (Long) -> Unit = { id ->
        if (activeDelight?.id == id) activeDelight = null
        vm.onDelightDismissed(id)
    }

    LaunchedEffect(delightEvent?.id, delightEnabled) {
        val event = delightEvent
        val plan = planDelightIntake(
            eventId = event?.id,
            enabled = delightEnabled,
            lastShownId = vm.lastShownDelightId,
            activeId = activeDelight?.id,
        )
        plan.acknowledgeIds.forEach(dismissDelight)
        if (plan.show && event != null) {
            val animation = event.presentation.animation
            vm.onDelightShown(event.id)
            animatedDelightId = event.id
            activeDelight = ActiveDelight(
                id = event.id,
                animation = animation,
                textKey = event.presentation.textKey,
                playerId = event.playerId,
                startedAtElapsed = SystemClock.elapsedRealtime(),
                // TalkBack-Nutzer bekommen ggf. mehr Lesezeit, gedeckelt unter
                // dem Sicherheitsnetz des ViewModels (DelightTiming.MAX_DISPLAY_MILLIS).
                totalMillis = DelightTiming.totalMillis(animation, reducedMotion) { base ->
                    accessibilityManager?.calculateRecommendedTimeoutMillis(
                        originalTimeoutMillis = base,
                        containsIcons = true,
                        containsText = true,
                        containsControls = false,
                    ) ?: base
                },
                sessionToken = vm.delightSessionToken,
            )
        }
    }

    // Ablauf-Timer je Feier: reines delay() auf Basis der gespeicherten
    // Startzeit (nach Rotation laeuft nur die Restzeit).
    val currentDelight = activeDelight
    LaunchedEffect(currentDelight?.id) {
        if (currentDelight == null) return@LaunchedEffect
        val remaining = DelightTiming.remainingMillis(
            startedAt = currentDelight.startedAtElapsed,
            now = SystemClock.elapsedRealtime(),
            total = currentDelight.totalMillis,
        )
        if (remaining > 0L) delay(remaining)
        dismissDelight(currentDelight.id)
    }

    val delightUi = currentDelight?.let { active ->
        DelightUi(
            id = active.id,
            animation = active.animation,
            textKey = active.textKey,
            playerName = delightPlayerName(uiState, active.playerId),
            animate = !reducedMotion && animatedDelightId == active.id,
        )
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
            onUndoWin = vm::onUndoWin,
            onNewLeg = vm::onNewLeg,
            onContinue = vm::onContinue,
            onExit = onExit,
            onShowMatchStats = onShowMatchStats,
        ),
        bustVisible = bustVisible,
        modifier = modifier,
        delight = delightUi,
        onDelightDismiss = dismissDelight,
    )
}

/**
 * Speichert die laufende Feier als Bundle-taugliche Liste (siehe
 * [toSaveable]); beim Wiederherstellen wird eine inzwischen abgelaufene oder zu
 * einer anderen ViewModel-Instanz gehoerende Feier verworfen
 * ([activeDelightFromSaveable] mit [sessionToken]).
 */
private fun activeDelightStateSaver(sessionToken: String) =
    Saver<MutableState<ActiveDelight?>, List<Any>>(
        save = { state -> state.value?.toSaveable() ?: emptyList() },
        restore = { saved ->
            mutableStateOf(
                activeDelightFromSaveable(saved, SystemClock.elapsedRealtime(), sessionToken),
            )
        },
    )

/**
 * Name des Werfers fuer den Untertitel einer Feier: nur bei mehr als einem
 * Spieler und wenn [playerId] im aktuellen [uiState] aufloesbar ist (ADR-0039),
 * sonst `null`.
 */
internal fun delightPlayerName(uiState: GameUiState, playerId: Long?): String? {
    if (playerId == null) return null
    val players = when (uiState) {
        is GameUiState.Playing -> uiState.players
        is GameUiState.LegWon -> uiState.players
        is GameUiState.MatchWon -> uiState.players
        GameUiState.Loading, GameUiState.Error, GameUiState.NoPlayer -> return null
    }
    if (players.size <= 1) return null
    return players.firstOrNull { it.playerId == playerId }?.name?.takeIf { it.isNotBlank() }
}

/**
 * Zustandsloser Bildschirminhalt des Spiel-Bildschirms. Rendert TopAppBar mit
 * Zurueck-Aktion und den vom [uiState] abhaengigen Inhalt: Ladeanzeige, Fehler-,
 * Kein-Spieler-, Spiel-, Leg-Sieg- oder Match-Sieg-Zustand. Der TopAppBar-Titel
 * im Spiel zeigt den aktuell werfenden Spieler.
 *
 * Eine laufende Feier ([delight]) liegt als Vollbild-Overlay ueber dem GESAMTEN
 * Bildschirm inkl. TopAppBar und blockiert solange die Eingabe darunter.
 *
 * @param uiState Aktueller Spielzustand.
 * @param callbacks Aktionen des Bildschirms.
 * @param bustVisible Ob das transiente Bust-Banner aktuell sichtbar ist.
 * @param delight Laufende Feier oder `null`.
 * @param onDelightDismiss Schliessen der Feier (Tippen/Zurueck) mit ihrer ID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreenContent(
    uiState: GameUiState,
    callbacks: GameScreenCallbacks,
    bustVisible: Boolean,
    modifier: Modifier = Modifier,
    delight: DelightUi? = null,
    onDelightDismiss: (Long) -> Unit = {},
) {
    val title = when (uiState) {
        is GameUiState.Playing ->
            uiState.players.firstOrNull { it.isCurrent }?.name
                ?: stringResource(R.string.game_title)
        else -> stringResource(R.string.game_title)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
        // Feier ueber allem (auch der TopAppBar), Scrim reicht unter die System-Bars.
        DelightOverlay(delight = delight, onDismiss = onDelightDismiss)
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
        val review = playing.turnReview
        if (review != null) {
            // Kontroll-Pause: statt des Keypads der Aufnahme-Review-Block. Das
            // Scoreboard darueber bleibt sichtbar (Kontext des Werfers).
            TurnReviewContent(
                review = review,
                // Waehrend einer Feier haelt das ViewModel den Pausen-Timer an
                // (ADR-0038); der Balken startet erst danach mit voller Dauer.
                timerRunning = !review.heldForDelight,
                onContinue = callbacks.onContinue,
                onUndo = callbacks.onUndo,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
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
}

/**
 * Inhalt der Kontroll-Pause nach einem regulaeren 3-Dart-Aufnahmeende: zeigt dem
 * Werfer seine geworfenen Darts gross zur Kontrolle und bietet "Weiter" (sofortiger
 * Wechsel, ueberspringt Timer) sowie "Korrigieren" (Undo, oeffnet die Aufnahme
 * wieder). Eine dezente, stumme Fortschrittsanzeige laeuft ueber
 * [GameViewModel.TURN_REVIEW_MILLIS] ab und signalisiert den automatischen Wechsel.
 *
 * Responsiv wie [DartKeypadContent] (Hoch-/Querformat ueber [BoxWithConstraints]).
 * Barrierefrei: der Block ist eine hoefliche Live-Region mit zusammengefasster
 * [contentDescription]; Kachel-Reihe und Fortschrittsanzeige sind von der
 * Sprachausgabe ausgenommen, die Aktionsbuttons bleiben einzeln fokussierbar.
 *
 * @param review Aufnahme-Daten (Werfer, Darts, Summe, naechster Spieler).
 * @param timerRunning Ob der Pausen-Timer laeuft. Solange `false` (Feier liegt
 *   darueber, [TurnReviewUi.heldForDelight]), bleibt der Balken voll; er laeuft
 *   erst ab dem Wechsel auf `true` ueber die volle Pausendauer ab.
 * @param onContinue "Weiter" - sofortiger Wechsel zum naechsten Spieler.
 * @param onUndo "Korrigieren" - oeffnet die soeben abgeschlossene Aufnahme wieder.
 */
@Composable
private fun TurnReviewContent(
    review: TurnReviewUi,
    timerRunning: Boolean,
    onContinue: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spokenDarts = review.darts.joinToString(", ") { dartSpokenLabel(it) }
    val blockCd = stringResource(
        R.string.game_turn_review_cd,
        review.throwerName,
        spokenDarts,
        review.turnSum,
    )
    // Stumme Fortschrittsanzeige: 1f -> 0f linear ueber die Pausendauer; erst ab
    // timerRunning, bis dahin steht sie voll.
    val progress = remember { Animatable(1f) }
    LaunchedEffect(timerRunning) {
        if (!timerRunning) return@LaunchedEffect
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = GameViewModel.TURN_REVIEW_MILLIS.toInt(),
                easing = LinearEasing,
            ),
        )
    }

    BoxWithConstraints(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val landscape = maxWidth > maxHeight
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = blockCd
                },
        ) {
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TurnReviewHeader(review = review)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TurnReviewProgress(progress = progress.value)
                        TurnReviewActions(onContinue = onContinue, onUndo = onUndo)
                        TurnReviewNext(review = review)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TurnReviewHeader(review = review)
                    TurnReviewProgress(progress = progress.value)
                    TurnReviewActions(onContinue = onContinue, onUndo = onUndo)
                    TurnReviewNext(review = review)
                }
            }
        }
    }
}

/** Titel, geworfene Darts (Kachel-Reihe) und Zugsumme der Kontroll-Pause. */
@Composable
private fun TurnReviewHeader(review: TurnReviewUi) {
    Text(
        text = stringResource(R.string.game_turn_review_title),
        style = MaterialTheme.typography.titleMedium,
    )
    ThrownDartsRow(
        darts = review.darts,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.keypad_turn_sum, review.turnSum),
        style = MaterialTheme.typography.headlineSmall,
    )
}

/** Stumme, ablaufende Fortschrittsanzeige der Kontroll-Pause (kein TalkBack). */
@Composable
private fun TurnReviewProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
    )
}

/** "Weiter" (primaer) und "Korrigieren" (sekundaer) der Kontroll-Pause. */
@Composable
private fun TurnReviewActions(
    onContinue: () -> Unit,
    onUndo: () -> Unit,
) {
    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.game_turn_review_continue))
    }
    OutlinedButton(
        onClick = onUndo,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.game_turn_review_correct))
    }
}

/** Dezenter Hinweis auf den naechsten Spieler ("Nächster: %s"). */
@Composable
private fun TurnReviewNext(review: TurnReviewUi) {
    Text(
        text = stringResource(R.string.game_turn_review_next, review.nextPlayerName),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
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

/**
 * Sieg-Panel nach einem gewonnenen Leg: Gewinner, verwendete Darts, naechster
 * Starter, Zwischenstand und die Aktionen "Naechstes Leg", "Sieg zuruecknehmen"
 * (abgesetzt, gegen Fehltipps) sowie "Zurueck".
 *
 * Die Spalte ist vertikal scrollbar, damit die drei Aktionen auch im Querformat
 * und bei grosser Schrift erreichbar bleiben.
 */
@Composable
private fun LegWonContent(
    legWon: GameUiState.LegWon,
    callbacks: GameScreenCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        UndoWinButton(onUndoWin = callbacks.onUndoWin)
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

/**
 * "Sieg zuruecknehmen" auf den Sieg-Panels: nimmt einen versehentlich
 * eingegebenen Sieg-Dart zurueck und oeffnet die Aufnahme wieder.
 *
 * Bewusst als sekundaerer [OutlinedButton] mit deutlichem Abstand nach oben
 * (Schutz gegen Fehltipps) und ohne Bestaetigungsdialog - die Aktion ist selbst
 * eine Korrektur und jederzeit wiederholbar (erneut werfen).
 */
@Composable
private fun UndoWinButton(onUndoWin: () -> Unit) {
    val undoCd = stringResource(R.string.game_won_undo_cd)
    OutlinedButton(
        onClick = onUndoWin,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            // Abstand VOR der Mindesthoehe: die 48 dp gelten fuer die Schaltflaeche
            // selbst (Touch-Ziel), nicht fuer Abstand + Schaltflaeche.
            .padding(top = 24.dp)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = undoCd },
    ) {
        Text(stringResource(R.string.game_won_undo))
    }
}

/**
 * Sieg-Panel nach gewonnenem Match: Gewinner, Endstand und die Aktionen
 * "Match-Statistik" (primaer), "Sieg zuruecknehmen" (abgesetzt, gegen
 * Fehltipps) sowie "Zurueck". Vertikal scrollbar, damit die Aktionen auch im
 * Querformat erreichbar bleiben.
 */
@Composable
private fun MatchWonContent(
    matchWon: GameUiState.MatchWon,
    callbacks: GameScreenCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        // Erst aktiv, wenn der Match-Abschluss persistiert ist (matchId gesetzt).
        val matchId = matchWon.matchId
        Button(
            onClick = { if (matchId != null) callbacks.onShowMatchStats(matchId) },
            enabled = matchId != null,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.game_match_stats))
        }
        UndoWinButton(onUndoWin = callbacks.onUndoWin)
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
    turnReview: TurnReviewUi? = null,
) = GameUiState.Playing(
    players = players,
    startScore = 501,
    input = DartInputState(modifier = DartModifier.SINGLE, darts = darts),
    currentLegNumber = 2,
    currentSetNumber = 1,
    legsToWin = 2,
    setsToWin = 1,
    checkout = checkout,
    turnReview = turnReview,
)

private fun previewTurnReview() = TurnReviewUi(
    throwerName = "Tom",
    darts = listOf(Dart.triple(20), Dart.single(5), Dart.double(16)),
    turnSum = 97,
    nextPlayerName = "Anna Beispiel",
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

@Preview(showBackground = true, name = "Leg gewonnen (Querformat)", widthDp = 760, heightDp = 380)
@Composable
private fun GameScreenLegWonLandscapePreview() {
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
                matchId = 1L,
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

@Preview(showBackground = true, name = "Kontroll-Pause (Hochformat)", heightDp = 760)
@Composable
private fun GameScreenTurnReviewPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(turnReview = previewTurnReview()),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Kontroll-Pause (Querformat)", widthDp = 760, heightDp = 380)
@Composable
private fun GameScreenTurnReviewLandscapePreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(turnReview = previewTurnReview()),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Feier ueber Kontroll-Pause", heightDp = 760)
@Composable
private fun GameScreenDelightOverTurnReviewPreview() {
    TomsDartsTheme {
        GameScreenContent(
            uiState = previewPlaying(turnReview = previewTurnReview().copy(heldForDelight = true)),
            callbacks = GameScreenCallbacks(),
            bustVisible = false,
            delight = DelightUi(
                id = 1L,
                animation = DelightAnimation.CONFETTI,
                textKey = DelightTextKeys.ONE_EIGHTY,
                playerName = "Tom",
                animate = false,
            ),
        )
    }
}
