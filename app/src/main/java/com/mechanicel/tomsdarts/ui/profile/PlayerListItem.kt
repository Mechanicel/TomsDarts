package com.mechanicel.tomsdarts.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val createdAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/**
 * Formatiert einen Erstellungszeitpunkt (Epoch-Millis) als lokales Datum.
 */
private fun formatCreatedAt(epochMillis: Long): String =
    createdAtFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
    )

/**
 * Erste Initiale des Namens als Grossbuchstabe; leerer Name -> "?".
 */
private fun initialOf(name: String): String =
    name.trim().firstOrNull()?.uppercase(Locale.GERMANY) ?: "?"

/**
 * Listeneintrag eines Spielers: Avatar-Initiale, Name, Erstelldatum und ein
 * Overflow-Menue zum Bearbeiten/Loeschen. Ein Tap auf die Zeile loest [onEdit] aus.
 */
@Composable
fun PlayerListItem(
    player: Player,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onEdit),
        headlineContent = {
            Text(
                text = player.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.profile_created_at, formatCreatedAt(player.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initialOf(player.name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        trailingContent = {
            PlayerOverflowMenu(
                playerName = player.name,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        },
    )
}

/**
 * Overflow-Trigger (48dp Touch-Ziel) mit Dropdown zum Bearbeiten/Loeschen.
 */
@Composable
private fun PlayerOverflowMenu(
    playerName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(R.string.profile_item_options, playerName)
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = optionsLabel },
        ) {
            Text(
                text = "⋮",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_item_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_item_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerListItemPreview() {
    TomsDartsTheme {
        PlayerListItem(
            player = Player(id = 1, name = "Tom Mustermann", createdAt = 1_700_000_000_000),
            onEdit = {},
            onDelete = {},
        )
    }
}
