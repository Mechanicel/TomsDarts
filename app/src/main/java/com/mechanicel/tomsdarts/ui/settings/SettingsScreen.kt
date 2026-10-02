package com.mechanicel.tomsdarts.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.data.settings.AppSettings
import com.mechanicel.tomsdarts.ui.delight.rememberReducedMotion
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Buendelt die Callbacks des Einstellungs-Bildschirms fuer die zustandslose
 * [SettingsScreenContent], damit diese @Preview-faehig bleibt.
 *
 * @param onBack Zurueck zur Profilliste (TopAppBar und System-Zurueck).
 * @param onDelightEnabledChange Schalter "Feier-Animationen" umgelegt.
 * @param onSaveErrorShown Fehlermeldung zum Schreibfehler wurde angezeigt.
 */
data class SettingsScreenCallbacks(
    val onBack: () -> Unit = {},
    val onDelightEnabledChange: (Boolean) -> Unit = {},
    val onSaveErrorShown: () -> Unit = {},
)

/**
 * Einstiegspunkt der App-Einstellungen (ADR-0040). Bezieht das
 * [SettingsViewModel] ueber die [SettingsViewModel.Factory], liest einmalig, ob
 * Animationen systemweit abgeschaltet sind, und delegiert an die zustandslose
 * [SettingsScreenContent].
 *
 * @param onBack Navigation zurueck zur Profilliste.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    // Gleiche Pruefung wie die Feier-Overlays (Animator-Dauer-Skala 0).
    val systemAnimationsOff = rememberReducedMotion()
    SettingsScreenContent(
        uiState = uiState,
        systemAnimationsOff = systemAnimationsOff,
        saveError = saveError,
        callbacks = SettingsScreenCallbacks(
            onBack = onBack,
            onDelightEnabledChange = viewModel::setDelightEnabled,
            onSaveErrorShown = viewModel::onSaveErrorShown,
        ),
    )
}

/**
 * Zustandsloser Bildschirminhalt der Einstellungen: TopAppBar mit Zurueck,
 * Abschnitt "Spiel" mit dem Schalter "Feier-Animationen" und ggf. dem
 * System-Hinweis. Im [SettingsUiState.Loading] bleibt die Flaeche leer (kein
 * Spinner, keine Default-Werte). Ein [saveError] wird per Snackbar gemeldet.
 *
 * @param systemAnimationsOff Ob Animationen systemweit abgeschaltet sind; dann
 *   erscheint unter dem Feier-Schalter ein Hinweis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    systemAnimationsOff: Boolean,
    saveError: Boolean,
    callbacks: SettingsScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    // System-Zurueck fuehrt zurueck zur Profilliste, nicht aus der App.
    BackHandler(onBack = callbacks.onBack)
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.settings_save_error)
    LaunchedEffect(saveError) {
        if (saveError) {
            // finally: auch beim Verlassen des Screens (Abbruch der Snackbar)
            // quittieren, sonst erschiene der Fehler beim naechsten Oeffnen erneut.
            try {
                snackbarHostState.showSnackbar(saveErrorMessage)
            } finally {
                callbacks.onSaveErrorShown()
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState is SettingsUiState.Content) {
                SettingsList(
                    settings = uiState.settings,
                    systemAnimationsOff = systemAnimationsOff,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun SettingsList(
    settings: AppSettings,
    systemAnimationsOff: Boolean,
    callbacks: SettingsScreenCallbacks,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
    ) {
        item(key = "section-game") {
            SettingsSectionHeader(text = stringResource(R.string.settings_section_game))
        }
        item(key = "delight") {
            SwitchSettingItem(
                title = stringResource(R.string.settings_delight_title),
                summary = stringResource(R.string.settings_delight_summary),
                checked = settings.delightEnabled,
                onCheckedChange = callbacks.onDelightEnabledChange,
            )
        }
        if (systemAnimationsOff) {
            item(key = "system-animations-off") {
                Text(
                    text = stringResource(R.string.settings_system_animations_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                )
            }
        }
    }
}

/** Abschnitts-Ueberschrift (fuer TalkBack als Heading markiert). */
@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * Einstellungszeile mit Schalter. Die ganze Zeile ist EIN Toggle (TalkBack liest
 * Titel, Beschreibung und Zustand als Einheit); der [Switch] selbst ist nicht
 * separat bedienbar.
 */
@Composable
private fun SwitchSettingItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

// --- Previews ---

@Preview(showBackground = true, name = "Laden")
@Composable
private fun SettingsScreenLoadingPreview() {
    TomsDartsTheme {
        SettingsScreenContent(
            uiState = SettingsUiState.Loading,
            systemAnimationsOff = false,
            saveError = false,
            callbacks = SettingsScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Feiern an")
@Composable
private fun SettingsScreenOnPreview() {
    TomsDartsTheme {
        SettingsScreenContent(
            uiState = SettingsUiState.Content(AppSettings(delightEnabled = true)),
            systemAnimationsOff = false,
            saveError = false,
            callbacks = SettingsScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Feiern aus")
@Composable
private fun SettingsScreenOffPreview() {
    TomsDartsTheme {
        SettingsScreenContent(
            uiState = SettingsUiState.Content(AppSettings(delightEnabled = false)),
            systemAnimationsOff = false,
            saveError = false,
            callbacks = SettingsScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, widthDp = 320, name = "System-Hinweis 320 dp")
@Composable
private fun SettingsScreenSystemHintPreview() {
    TomsDartsTheme {
        SettingsScreenContent(
            uiState = SettingsUiState.Content(AppSettings()),
            systemAnimationsOff = true,
            saveError = false,
            callbacks = SettingsScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dunkel")
@Composable
private fun SettingsScreenDarkPreview() {
    TomsDartsTheme {
        SettingsScreenContent(
            uiState = SettingsUiState.Content(AppSettings()),
            systemAnimationsOff = true,
            saveError = false,
            callbacks = SettingsScreenCallbacks(),
        )
    }
}
