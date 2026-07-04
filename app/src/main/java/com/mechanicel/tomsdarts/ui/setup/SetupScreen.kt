package com.mechanicel.tomsdarts.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/** Auswaehlbare Startpunktwerte fuer ein X01-Match. */
val START_SCORES: List<Int> = listOf(301, 501, 701)

/** Standard-Startpunktwert (vorbelegte Auswahl im Setup). */
const val DEFAULT_START_SCORE: Int = 501

/**
 * Buendelt die Callbacks des Setup-Bildschirms fuer die zustandslose
 * [SetupScreenContent], damit diese @Preview-faehig bleibt.
 *
 * @param onSelectStartScore Auswahl eines Startpunktwerts.
 * @param onConfirm Match mit den Teilnehmern und dem gewaehlten Startpunkt starten.
 * @param onCancel Setup verlassen (zurueck zur Profilliste).
 */
data class SetupScreenCallbacks(
    val onSelectStartScore: (Int) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * Zustandsbehafteter Einstiegspunkt des Spiel-Setup-Bildschirms. Haelt den
 * gewaehlten Startpunkt konfigurationsfest ([rememberSaveable]) und delegiert das
 * Rendern an die zustandslose [SetupScreenContent]. Reine lokale Konfiguration
 * (offline-first, kein Netzwerk/Login/Tracking).
 *
 * @param playerIds Teilnehmer in Reihenfolge (>= 2 fuer ein Match).
 * @param onConfirm Match mit den Teilnehmern und dem gewaehlten Startpunkt starten.
 * @param onCancel Setup verlassen (zurueck zur Profilliste).
 */
@Composable
fun SetupScreen(
    playerIds: List<Long>,
    onConfirm: (List<Long>, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedStartScore by rememberSaveable { mutableIntStateOf(DEFAULT_START_SCORE) }

    SetupScreenContent(
        selectedStartScore = selectedStartScore,
        callbacks = SetupScreenCallbacks(
            onSelectStartScore = { selectedStartScore = it },
            onConfirm = { onConfirm(playerIds, selectedStartScore) },
            onCancel = onCancel,
        ),
        modifier = modifier,
    )
}

/**
 * Zustandsloser Bildschirminhalt des Spiel-Setup-Bildschirms. Rendert TopAppBar
 * mit Zurueck-Aktion, den Konfigurationskoerper (aktuell nur die
 * Startpunkt-Auswahl) und die Primaeraktion "Match starten" in der bottomBar.
 * Der Body ist bewusst als Liste von Sections aufgebaut, damit spaetere Optionen
 * darunter passen.
 *
 * @param selectedStartScore Aktuell gewaehlter Startpunktwert.
 * @param callbacks Aktionen des Bildschirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreenContent(
    selectedStartScore: Int,
    callbacks: SetupScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    // System-Zurueck fuehrt zurueck zur Profilliste, nicht aus der App.
    BackHandler(onBack = callbacks.onCancel)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.setup_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = callbacks.onCancel) {
                        Text(stringResource(R.string.game_back))
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = callbacks.onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.setup_start_match))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StartScoreSection(
                    selectedStartScore = selectedStartScore,
                    onSelectStartScore = callbacks.onSelectStartScore,
                )
            }
        }
    }
}

/**
 * Section mit der Startpunkt-Auswahl: Section-Label plus eine Reihe
 * auswaehlbarer Karten (Werte aus [START_SCORES]). Die Karten bilden semantisch
 * eine Radio-Gruppe, damit TalkBack den Auswahlzustand korrekt ansagt.
 */
@Composable
private fun StartScoreSection(
    selectedStartScore: Int,
    onSelectStartScore: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.setup_start_score_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            START_SCORES.forEach { score ->
                StartScoreCard(
                    score = score,
                    selected = score == selectedStartScore,
                    onSelect = { onSelectStartScore(score) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Einzelne auswaehlbare Startpunkt-Karte. Traegt die Selektions-Semantik
 * ([Role.RadioButton]) selbst, da [Card] das nicht automatisch liefert, und
 * eine eindeutige [contentDescription] fuer die Ansage.
 */
@Composable
private fun StartScoreCard(
    score: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.setup_start_score_cd, score)
    val colors = if (selected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }
    Card(
        colors = colors,
        border = border,
        modifier = modifier.selectable(
            selected = selected,
            onClick = onSelect,
            role = Role.RadioButton,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 16.dp)
                .semantics { contentDescription = cd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Standard (501)")
@Composable
private fun SetupScreenDefaultPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            selectedStartScore = 501,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "301 gewaehlt")
@Composable
private fun SetupScreen301Preview() {
    TomsDartsTheme {
        SetupScreenContent(
            selectedStartScore = 301,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "701 gewaehlt")
@Composable
private fun SetupScreen701Preview() {
    TomsDartsTheme {
        SetupScreenContent(
            selectedStartScore = 701,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Querformat", widthDp = 760, heightDp = 380)
@Composable
private fun SetupScreenLandscapePreview() {
    TomsDartsTheme {
        SetupScreenContent(
            selectedStartScore = 501,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Kleines Geraet", widthDp = 320)
@Composable
private fun SetupScreenSmallPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            selectedStartScore = 501,
            callbacks = SetupScreenCallbacks(),
        )
    }
}
