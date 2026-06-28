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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Buendelt die Callbacks der Profilverwaltung fuer die zustandslose
 * [ProfileScreenContent], damit diese @Preview-faehig bleibt.
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
)

/**
 * Einstiegspunkt der Profilverwaltung. Bezieht das [ProfileViewModel] ueber die
 * [ProfileViewModel.Factory], sammelt die Zustaende lifecycle-bewusst und
 * delegiert das Rendern an die zustandslose [ProfileScreenContent].
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()

    ProfileScreenContent(
        uiState = uiState,
        dialog = dialog,
        callbacks = ProfileScreenCallbacks(
            onAddClick = viewModel::onAddClick,
            onEditClick = viewModel::onEditClick,
            onDeleteClick = viewModel::onDeleteClick,
            // Das ViewModel besitzt (noch) keine Retry-Aktion; der Stream wird
            // beim erneuten Sammeln automatisch neu gestartet. Daher no-op.
            onRetry = {},
            onDismissDialog = viewModel::onDismissDialog,
            onConfirmAdd = viewModel::addPlayer,
            onConfirmEdit = { player, name -> viewModel.updatePlayer(player.copy(name = name)) },
            onConfirmDelete = viewModel::deletePlayer,
        ),
    )
}

/**
 * Zustandsloser Bildschirminhalt der Profilverwaltung. Rendert TopAppBar, FAB,
 * den vom [uiState] abhaengigen Inhalt sowie die [dialog]-abhaengigen Dialoge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    uiState: ProfileUiState,
    dialog: ProfileDialog,
    callbacks: ProfileScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.profile_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = callbacks.onAddClick) {
                Text(stringResource(R.string.profile_add_fab))
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
                    onEditClick = callbacks.onEditClick,
                    onDeleteClick = callbacks.onDeleteClick,
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
    onEditClick: (Player) -> Unit,
    onDeleteClick: (Player) -> Unit,
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
            items(players, key = { it.id }) { player ->
                PlayerListItem(
                    player = player,
                    onEdit = { onEditClick(player) },
                    onDelete = { onDeleteClick(player) },
                )
                HorizontalDivider()
            }
        }
    }
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
            callbacks = ProfileScreenCallbacks(),
        )
    }
}
