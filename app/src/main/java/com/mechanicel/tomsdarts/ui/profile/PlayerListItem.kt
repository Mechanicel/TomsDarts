package com.mechanicel.tomsdarts.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * Listeneintrag eines Spielers mit zwei Render-Pfaden:
 *
 * - **Normalmodus** ([selectionActive] == false): Avatar-Initiale, Name,
 *   Erstelldatum und ein Overflow-Menue zum Bearbeiten/Loeschen. Ein Tap auf die
 *   Zeile aktiviert ueber [onTap] den Auswahlmodus mit diesem Spieler, ein
 *   Long-Press ueber [onLongPress] ebenso.
 * - **Auswahlmodus** ([selectionActive] == true): Tap toggelt die Markierung
 *   ([onToggle]); markierte Spieler zeigen ihre [selectionOrder] (1-basiert) im
 *   Avatarkreis (primary/onPrimary) statt der Initiale; das Overflow-Menue ist
 *   ausgeblendet. Die Zeile traegt `toggleable`-Semantik mit [Role.Checkbox].
 *
 * @param selectionActive Ob der Auswahlmodus aktiv ist.
 * @param selected Ob dieser Spieler markiert ist.
 * @param selectionOrder 1-basierte Markierungs-Position, null wenn nicht markiert.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerListItem(
    player: Player,
    selectionActive: Boolean,
    selected: Boolean,
    selectionOrder: Int?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val supporting: @Composable () -> Unit = {
        Text(
            text = stringResource(R.string.profile_created_at, formatCreatedAt(player.createdAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val headline: @Composable () -> Unit = {
        Text(
            text = player.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (selectionActive) {
        val selectionCd = if (selected && selectionOrder != null) {
            stringResource(R.string.profile_select_order_cd, player.name, selectionOrder)
        } else {
            player.name
        }
        ListItem(
            modifier = modifier
                .toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .semantics { contentDescription = selectionCd },
            headlineContent = headline,
            supportingContent = supporting,
            leadingContent = {
                if (selected && selectionOrder != null) {
                    AvatarCircle(
                        text = selectionOrder.toString(),
                        background = MaterialTheme.colorScheme.primary,
                        foreground = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    AvatarCircle(
                        text = initialOf(player.name),
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        foreground = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            },
        )
    } else {
        val tapLabel = stringResource(R.string.profile_play_cd, player.name)
        ListItem(
            modifier = modifier
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .semantics { contentDescription = tapLabel },
            headlineContent = headline,
            supportingContent = supporting,
            leadingContent = {
                AvatarCircle(
                    text = initialOf(player.name),
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    foreground = MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
}

/** Runder Avatar mit zentriertem Text (Initiale oder Reihenfolge-Nummer). */
@Composable
private fun AvatarCircle(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        modifier = Modifier.size(40.dp).clip(CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
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
                modifier = Modifier.clearAndSetSemantics {},
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
            selectionActive = false,
            selected = false,
            selectionOrder = null,
            onTap = {},
            onLongPress = {},
            onToggle = {},
            onEdit = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, name = "Auswahl: markiert")
@Composable
private fun PlayerListItemSelectedPreview() {
    TomsDartsTheme {
        PlayerListItem(
            player = Player(id = 1, name = "Tom Mustermann", createdAt = 1_700_000_000_000),
            selectionActive = true,
            selected = true,
            selectionOrder = 1,
            onTap = {},
            onLongPress = {},
            onToggle = {},
            onEdit = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, name = "Auswahl: nicht markiert")
@Composable
private fun PlayerListItemUnselectedPreview() {
    TomsDartsTheme {
        PlayerListItem(
            player = Player(id = 2, name = "Anna Beispiel", createdAt = 1_705_000_000_000),
            selectionActive = true,
            selected = false,
            selectionOrder = null,
            onTap = {},
            onLongPress = {},
            onToggle = {},
            onEdit = {},
            onDelete = {},
        )
    }
}
