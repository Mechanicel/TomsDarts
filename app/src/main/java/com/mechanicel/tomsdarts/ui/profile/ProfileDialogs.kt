package com.mechanicel.tomsdarts.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Modus des [PlayerEditDialog]: Anlegen oder Bearbeiten.
 */
enum class PlayerEditMode { Add, Edit }

/**
 * Dialog zum Anlegen bzw. Bearbeiten eines Spielernamens. Der bestaetigende
 * Button ist nur aktiv, wenn der getrimmte Name nicht leer ist; gespeichert
 * wird stets der getrimmte Name. Bei leerer Eingabe (nach Beruehrung bzw.
 * Done) erscheint ein Fehlertext.
 */
@Composable
fun PlayerEditDialog(
    mode: PlayerEditMode,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val trimmed = name.trim()
    val isValid = trimmed.isNotEmpty()
    val showError = touched && !isValid

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val confirm = {
        touched = true
        if (isValid) {
            onConfirm(trimmed)
        }
    }

    val titleRes = when (mode) {
        PlayerEditMode.Add -> R.string.profile_dialog_add_title
        PlayerEditMode.Edit -> R.string.profile_dialog_edit_title
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        touched = true
                    },
                    label = { Text(stringResource(R.string.profile_dialog_name_label)) },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.profile_dialog_name_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = confirm,
                enabled = isValid,
            ) {
                Text(stringResource(R.string.profile_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_dialog_cancel))
            }
        },
    )
}

/**
 * Bestaetigungsdialog vor dem Loeschen eines Spielers.
 */
@Composable
fun DeletePlayerDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_title)) },
        text = { Text(stringResource(R.string.profile_delete_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.profile_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_dialog_cancel))
            }
        },
    )
}

/**
 * Fehlerhinweis, falls das Loeschen eines Spielers fehlschlug. Bietet nur einen
 * bestaetigenden Button an, damit der Fehler dem Nutzer sichtbar wird.
 */
@Composable
fun DeletePlayerErrorDialog(
    name: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_error_title)) },
        text = { Text(stringResource(R.string.profile_delete_error_message, name)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_delete_error_dismiss))
            }
        },
    )
}

@Preview
@Composable
private fun PlayerEditDialogAddPreview() {
    TomsDartsTheme {
        PlayerEditDialog(
            mode = PlayerEditMode.Add,
            initialName = "",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PlayerEditDialogEditPreview() {
    TomsDartsTheme {
        PlayerEditDialog(
            mode = PlayerEditMode.Edit,
            initialName = "Tom",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeletePlayerDialogPreview() {
    TomsDartsTheme {
        DeletePlayerDialog(
            name = "Tom",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeletePlayerErrorDialogPreview() {
    TomsDartsTheme {
        DeletePlayerErrorDialog(
            name = "Tom",
            onDismiss = {},
        )
    }
}
