package com.mechanicel.tomsdarts.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.ui.setup.MIN_MATCH_PLAYERS
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Buendelt die Callbacks der Profilverwaltung fuer die zustandslose
 * [ProfileScreenContent], damit diese @Preview-faehig bleibt.
 *
 * @param onEnterSelection Auswahlmodus aktivieren und [Player] direkt markieren
 *   (Tap im Normalmodus oder Long-Press).
 * @param onEnterSelectionMenu Auswahlmodus ohne Vorauswahl aktivieren (TopAppBar).
 * @param onToggleSelect Markierung eines [Player] im Auswahlmodus umschalten.
 * @param onExitSelection Auswahlmodus verlassen.
 * @param onStartMatch Match mit den markierten Spielern (>= 2) starten.
 */
data class ProfileScreenCallbacks(
    val onAddClick: () -> Unit = {},
    val onEditClick: (Player) -> Unit = {},
    val onDeleteClick: (Player) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onConfirmAdd: (String) -> Unit = {},
    val onConfirmEdit: (Player, String) -> Unit = { _, _ -> },
    val onConfirmDelete: (Player) -> Unit = {},
    val onEnterSelection: (Player) -> Unit = {},
    val onEnterSelectionMenu: () -> Unit = {},
    val onToggleSelect: (Player) -> Unit = {},
    val onExitSelection: () -> Unit = {},
    val onStartMatch: (List<Long>) -> Unit = {},
)

/**
 * Einstiegspunkt der Profilverwaltung. Bezieht das [ProfileViewModel] ueber die
 * [ProfileViewModel.Factory], sammelt die Zustaende lifecycle-bewusst und
 * delegiert das Rendern an die zustandslose [ProfileScreenContent].
 *
 * @param onStartMatch Navigation in das Match mit den markierten Spieler-IDs.
 */
@Composable
fun ProfileScreen(
    onStartMatch: (List<Long>) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()

    ProfileScreenContent(
        uiState = uiState,
        dialog = dialog,
        selection = selection,
        callbacks = ProfileScreenCallbacks(
            onAddClick = viewModel::onAddClick,
            onEditClick = viewModel::onEditClick,
            onDeleteClick = viewModel::onDeleteClick,
            onRetry = viewModel::retry,
            onDismissDialog = viewModel::onDismissDialog,
            onConfirmAdd = viewModel::addPlayer,
            onConfirmEdit = { player, name -> viewModel.updatePlayer(player.copy(name = name)) },
            onConfirmDelete = viewModel::deletePlayer,
            onEnterSelection = viewModel::enterSelection,
            onEnterSelectionMenu = viewModel::enterSelectionMenu,
            onToggleSelect = viewModel::toggleSelect,
            onExitSelection = viewModel::exitSelection,
            onStartMatch = { ids ->
                viewModel.startMatch(ids)
                onStartMatch(ids)
            },
        ),
    )
}

/**
 * Zustandsloser Bildschirminhalt der Profilverwaltung. Rendert TopAppBar (im
 * Auswahlmodus als CAB), FAB ("Neuer Spieler" bzw. "Match starten"), den vom
 * [uiState] abhaengigen Inhalt sowie die [dialog]-abhaengigen Dialoge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    uiState: ProfileUiState,
    dialog: ProfileDialog,
    selection: ProfileSelectionState,
    callbacks: ProfileScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (selection.active) {
                SelectionTopBar(
                    count = selection.selectedIds.size,
                    onCancel = callbacks.onExitSelection,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_title)) },
                    actions = {
                        TextButton(onClick = callbacks.onEnterSelectionMenu) {
                            Text(stringResource(R.string.profile_select_match))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selection.active) {
                val canStart = selection.selectedIds.size >= MIN_MATCH_PLAYERS
                ExtendedFloatingActionButton(
                    onClick = {
                        if (canStart) callbacks.onStartMatch(selection.selectedIds)
                    },
                    containerColor = if (canStart) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(stringResource(R.string.profile_start_match, selection.selectedIds.size))
                }
            } else {
                ExtendedFloatingActionButton(onClick = callbacks.onAddClick) {
                    Text(stringResource(R.string.profile_add_fab))
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                ProfileUiState.Loading -> LoadingContent()
                ProfileUiState.Empty -> EmptyContent(onAddClick = callbacks.onAddClick)
                is ProfileUiState.Content -> PlayerList(
                    players = uiState.players,
                    selection = selection,
                    callbacks = callbacks,
                )
                is ProfileUiState.Error -> ErrorContent(onRetry = callbacks.onRetry)
            }
        }
    }

    when (val current = dialog) {
        ProfileDialog.None -> Unit
        ProfileDialog.Add -> PlayerEditDialog(
            mode = PlayerEditMode.Add,
            initialName = "",
            onConfirm = callbacks.onConfirmAdd,
            onDismiss = callbacks.onDismissDialog,
        )
        is ProfileDialog.Edit -> PlayerEditDialog(
            mode = PlayerEditMode.Edit,
            initialName = current.player.name,
            onConfirm = { name -> callbacks.onConfirmEdit(current.player, name) },
            onDismiss = callbacks.onDismissDialog,
        )
        is ProfileDialog.ConfirmDelete -> DeletePlayerDialog(
            name = current.player.name,
            onConfirm = { callbacks.onConfirmDelete(current.player) },
            onDismiss = callbacks.onDismissDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onCancel: () -> Unit,
) {
    val cancelCd = stringResource(R.string.profile_selection_cancel_cd)
    TopAppBar(
        title = { Text(stringResource(R.string.profile_selection_count, count)) },
        navigationIcon = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.semantics { contentDescription = cancelCd },
            ) {
                Text(text = "✕", modifier = Modifier.clearAndSetSemantics {})
            }
        },
    )
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
private fun EmptyContent(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.profile_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        FilledTonalButton(onClick = onAddClick) {
            Text(stringResource(R.string.profile_empty_action))
        }
    }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.profile_error_retry))
        }
    }
}

@Composable
private fun PlayerList(
    players: List<Player>,
    selection: ProfileSelectionState,
    callbacks: ProfileScreenCallbacks,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            if (selection.active) {
                item(key = "selection-hint") {
                    SelectionHint(count = selection.selectedIds.size)
                }
            }
            items(players, key = { it.id }) { player ->
                val orderIndex = selection.selectedIds.indexOf(player.id)
                PlayerListItem(
                    player = player,
                    selectionActive = selection.active,
                    selected = orderIndex >= 0,
                    selectionOrder = if (orderIndex >= 0) orderIndex + 1 else null,
                    onTap = {
                        if (selection.active) {
                            callbacks.onToggleSelect(player)
                        } else {
                            callbacks.onEnterSelection(player)
                        }
                    },
                    onLongPress = { callbacks.onEnterSelection(player) },
                    onToggle = { callbacks.onToggleSelect(player) },
                    onEdit = { callbacks.onEditClick(player) },
                    onDelete = { callbacks.onDeleteClick(player) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SelectionHint(count: Int) {
    if (count >= MIN_MATCH_PLAYERS) return
    Text(
        text = stringResource(R.string.profile_select_min_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// --- Previews ---

private val previewPlayers = listOf(
    Player(id = 1, name = "Tom", createdAt = 1_700_000_000_000),
    Player(id = 2, name = "Anna Beispiel", createdAt = 1_705_000_000_000),
    Player(id = 3, name = "Bjoern", createdAt = 1_710_000_000_000),
)

@Preview(showBackground = true)
@Composable
private fun ProfileScreenLoadingPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Loading,
            dialog = ProfileDialog.None,
            selection = ProfileSelectionState(),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenEmptyPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Empty,
            dialog = ProfileDialog.None,
            selection = ProfileSelectionState(),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenContentPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Content(previewPlayers),
            dialog = ProfileDialog.None,
            selection = ProfileSelectionState(),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Auswahlmodus")
@Composable
private fun ProfileScreenSelectionPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Content(previewPlayers),
            dialog = ProfileDialog.None,
            selection = ProfileSelectionState(active = true, selectedIds = listOf(1, 3)),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenErrorPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Error(),
            dialog = ProfileDialog.None,
            selection = ProfileSelectionState(),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenAddDialogPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Content(previewPlayers),
            dialog = ProfileDialog.Add,
            selection = ProfileSelectionState(),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenDeleteDialogPreview() {
    TomsDartsTheme {
        ProfileScreenContent(
            uiState = ProfileUiState.Content(previewPlayers),
            dialog = ProfileDialog.ConfirmDelete(previewPlayers.first()),
            selection = ProfileSelectionState(),
            callbacks = ProfileScreenCallbacks(),
        )
    }
}
