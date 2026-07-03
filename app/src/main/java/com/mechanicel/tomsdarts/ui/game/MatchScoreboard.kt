package com.mechanicel.tomsdarts.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/** Ab dieser Breite werden die Spieler-Karten kompakt (einzeilig) dargestellt. */
private val COMPACT_BREAKPOINT = 480.dp

/**
 * Mehrspieler-Scoreboard: Leg-/Set-Fortschritt plus eine gleichgewichtete Karte
 * je Spieler. Der aktuell werfende Spieler ist hervorgehoben.
 *
 * Im Portrait stehen die Karten gestapelt (Name / Restpunkte / Stand) nebeneinander;
 * auf breiten Layouts (Querformat) werden sie ueber [BoxWithConstraints] kompakt
 * einzeilig dargestellt. Die Breite ist auf 600.dp begrenzt.
 *
 * @param players Stand aller Spieler; der aktuelle Werfer ueber [PlayerScoreUi.isCurrent].
 * @param currentLegNumber 1-basierte Nummer des laufenden Legs im Set.
 * @param currentSetNumber 1-basierte Nummer des laufenden Sets.
 * @param legsToWin Anzahl Legs fuer einen Set-Gewinn (Best-of-N: N = 2*legsToWin-1).
 * @param setsToWin Anzahl Sets fuer den Match-Gewinn.
 */
@Composable
fun MatchScoreboard(
    players: List<PlayerScoreUi>,
    currentLegNumber: Int,
    currentSetNumber: Int,
    legsToWin: Int,
    setsToWin: Int,
    modifier: Modifier = Modifier,
) {
    val bestOfLegs = 2 * legsToWin - 1
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = maxWidth >= COMPACT_BREAKPOINT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.game_leg_progress, currentLegNumber, bestOfLegs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (setsToWin > 1) {
                Text(
                    text = stringResource(R.string.game_set_progress, currentSetNumber, 2 * setsToWin - 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                players.forEach { player ->
                    PlayerScoreCard(
                        player = player,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Karte eines Spielers im Scoreboard. Der aktuelle Werfer ist mit einem
 * [R.string.game_current_marker]-Glyph und der `primaryContainer`-Farbe
 * hervorgehoben (sonst `surfaceVariant`); seine Karte ist als
 * [LiveRegionMode.Polite] ausgezeichnet, damit TalkBack den Wechsel ansagt.
 *
 * @param player Anzuzeigender Spieler-Stand.
 * @param compact True fuer die einzeilige (Querformat-)Darstellung.
 */
@Composable
fun PlayerScoreCard(
    player: PlayerScoreUi,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (player.isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (player.isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val standing = stringResource(R.string.game_player_standing, player.legsWon, player.setsWon)
    val cardCd = if (player.isCurrent) {
        stringResource(
            R.string.game_current_player_cd,
            player.name,
            player.remaining,
            player.legsWon,
            player.setsWon,
        )
    } else {
        stringResource(
            R.string.game_player_card_cd,
            player.name,
            player.remaining,
            player.legsWon,
            player.setsWon,
        )
    }
    val marker = stringResource(R.string.game_current_marker)

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = cardCd
            if (player.isCurrent) liveRegion = LiveRegionMode.Polite
        },
    ) {
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NameLabel(
                    name = player.name,
                    marker = if (player.isCurrent) marker else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = player.remaining.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = standing,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NameLabel(
                    name = player.name,
                    marker = if (player.isCurrent) marker else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = player.remaining.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = standing,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NameLabel(
    name: String,
    marker: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marker != null) {
            Text(text = marker, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

// --- Previews ---

private val previewPlayers = listOf(
    PlayerScoreUi(playerId = 1, name = "Tom", remaining = 287, legsWon = 1, setsWon = 0, isCurrent = true),
    PlayerScoreUi(playerId = 2, name = "Anna Beispiel", remaining = 340, legsWon = 0, setsWon = 0, isCurrent = false),
)

@Preview(showBackground = true, name = "Scoreboard Portrait", widthDp = 360)
@Composable
private fun MatchScoreboardPortraitPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = previewPlayers,
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Scoreboard Querformat", widthDp = 640)
@Composable
private fun MatchScoreboardLandscapePreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = previewPlayers,
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}
