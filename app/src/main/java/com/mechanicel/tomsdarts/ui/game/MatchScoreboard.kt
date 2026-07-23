package com.mechanicel.tomsdarts.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.game.CricketState
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.ui.input.dartShortLabel
import com.mechanicel.tomsdarts.ui.input.dartSpokenLabel
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
    // Die letzte Aufnahme wird als Suffix an die bestehende Karten-Ansage
    // gehaengt (keine zweite Ansage). Der Empty-Platzhalter "-" wird bewusst
    // NICHT vorgelesen.
    val lastTurnCd = when {
        player.lastTurnDarts.isEmpty() -> ""
        player.lastTurnBust -> stringResource(
            R.string.game_player_last_turn_cd_bust,
            player.lastTurnDarts.joinToString(", ") { dartSpokenLabel(it) },
        )
        else -> stringResource(
            R.string.game_player_last_turn_cd,
            player.lastTurnDarts.joinToString(", ") { dartSpokenLabel(it) },
            player.lastTurnDarts.sumOf { it.value },
        )
    }
    // Modus-spezifische Karten-Ansage (Basis) je Board-Typ; der Compiler erzwingt
    // hier einen Zweig je [PlayerBoardUi]-Unterart.
    val baseCd = when (val board = player.board) {
        is PlayerBoardUi.X01 -> x01CardCd(player, board.remaining)
        is PlayerBoardUi.Cricket -> cricketCardCd(player, board)
        is PlayerBoardUi.AroundTheClock -> aroundTheClockCardCd(player, board)
    }
    val cardCd = baseCd + lastTurnCd
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
        when (val board = player.board) {
            is PlayerBoardUi.X01 -> X01Board(player, board.remaining, compact, marker)
            is PlayerBoardUi.Cricket -> CricketBoard(player, board, compact, marker)
            is PlayerBoardUi.AroundTheClock -> AroundTheClockBoard(player, board, compact, marker)
        }
    }
}

/** Karten-Ansage (Basis, ohne Last-Turn-Suffix) fuer eine X01-Karte. */
@Composable
private fun x01CardCd(player: PlayerScoreUi, remaining: Int): String =
    if (player.isCurrent) {
        stringResource(
            R.string.game_current_player_cd,
            player.name,
            remaining,
            player.legsWon,
            player.setsWon,
        )
    } else {
        stringResource(
            R.string.game_player_card_cd,
            player.name,
            remaining,
            player.legsWon,
            player.setsWon,
        )
    }

/**
 * Karten-Ansage (Basis, ohne Last-Turn-Suffix) fuer eine Cricket-Karte: Name +
 * Punkte, danach je Feld in Anzeigereihenfolge (20 -> Bull) ein Fragment
 * (offen / N Marks / geschlossen).
 */
@Composable
private fun cricketCardCd(player: PlayerScoreUi, board: PlayerBoardUi.Cricket): String {
    val head = if (player.isCurrent) {
        stringResource(R.string.game_cricket_current_player_cd, player.name, board.points)
    } else {
        stringResource(R.string.game_cricket_player_card_cd, player.name, board.points)
    }
    // Bewusst forEach (inline, erhaelt den @Composable-Kontext) statt joinToString
    // (nicht inline), damit stringResource je Feld aufgerufen werden darf.
    var fields = ""
    board.fields.forEach { field ->
        val label = cricketFieldLabel(field.target)
        fields += when {
            field.marks <= 0 -> stringResource(R.string.game_cricket_field_open_cd, label)
            field.marks >= CricketState.CLOSED_MARKS ->
                stringResource(R.string.game_cricket_field_closed_cd, label)
            else -> stringResource(R.string.game_cricket_field_marks_cd, label, field.marks)
        }
    }
    return head + fields
}

/** Feldname fuer Anzeige/Ansage: Bull fuer 25, sonst die Segmentzahl. */
@Composable
private fun cricketFieldLabel(target: Int): String =
    if (target == CricketState.BULL) stringResource(R.string.game_cricket_bull_label) else target.toString()

/**
 * Karten-Ansage (Basis, ohne Last-Turn-Suffix) fuer eine Around-the-Clock-Karte:
 * Name, aktuelles Ziel und Fortschritt (erreichte von 20). Ist das Leg bereits
 * gewonnen (defensive "Fertig"-Anzeige), meldet die Karte stattdessen den
 * Abschluss.
 */
@Composable
private fun aroundTheClockCardCd(player: PlayerScoreUi, board: PlayerBoardUi.AroundTheClock): String {
    val total = PlayerBoardUi.AroundTheClock.TOTAL
    val done = board.target > total || board.completed >= total
    return when {
        done -> stringResource(R.string.game_atc_done_cd, player.name, total)
        player.isCurrent -> stringResource(
            R.string.game_atc_current_player_cd,
            player.name,
            board.target,
            board.completed,
            total,
        )
        else -> stringResource(
            R.string.game_atc_player_card_cd,
            player.name,
            board.target,
            board.completed,
            total,
        )
    }
}

/**
 * Kart-Inhalt fuer den X01-Modus (Rest als Hero, Legs/Sets-Stand). Kopf, Fuss und
 * Responsivverhalten bleiben exakt wie zuvor; nur aus der [PlayerScoreCard]
 * herausgezogen, damit dort der Modus-Zweig sauber greift.
 */
@Composable
private fun X01Board(
    player: PlayerScoreUi,
    remaining: Int,
    compact: Boolean,
    marker: String,
) {
    val standing = stringResource(R.string.game_player_standing, player.legsWon, player.setsWon)
    if (compact) {
        // Einzeiler (Name | Rest | Stand) in eine Column gefasst, damit die
        // letzte Aufnahme als zweite, schmale Zeile darunter passt.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NameLabel(
                    name = player.name,
                    marker = if (player.isCurrent) marker else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = remaining.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = standing,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LastTurnLine(
                darts = player.lastTurnDarts,
                bust = player.lastTurnBust,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
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
                text = remaining.toString(),
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
            LastTurnLine(
                darts = player.lastTurnDarts,
                bust = player.lastTurnBust,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Kartinhalt fuer den Cricket-Modus: Kopf (Name + Punkte-Hero), das Marks-Raster
 * ueber die 7 Felder und die uebernommene [LastTurnLine].
 *
 * Portrait (non-compact): Name ueber zentriertem Punkte-Hero, darunter das Raster
 * vertikal (7 Zeilen `Feld | Mark`). Compact/Querformat: Name und Punkte in einer
 * Zeile, das Raster horizontal als 7 Mini-Zellen (Label ueber [CricketMarkCell]).
 */
@Composable
private fun CricketBoard(
    player: PlayerScoreUi,
    board: PlayerBoardUi.Cricket,
    compact: Boolean,
    marker: String,
) {
    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NameLabel(
                    name = player.name,
                    marker = if (player.isCurrent) marker else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = board.points.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                board.fields.forEach { field ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = cricketFieldLabel(field.target),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (field.marks >= CricketState.CLOSED_MARKS) FontWeight.Bold else null,
                        )
                        CricketMarkCell(marks = field.marks)
                    }
                }
            }
            LastTurnLine(
                darts = player.lastTurnDarts,
                bust = player.lastTurnBust,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NameLabel(
                name = player.name,
                marker = if (player.isCurrent) marker else null,
                modifier = Modifier.fillMaxWidth(),
            )
            CricketPointsHero(points = board.points)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                board.fields.forEach { field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cricketFieldLabel(field.target),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (field.marks >= CricketState.CLOSED_MARKS) FontWeight.Bold else null,
                        )
                        CricketMarkCell(marks = field.marks)
                    }
                }
            }
            LastTurnLine(
                darts = player.lastTurnDarts,
                bust = player.lastTurnBust,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Punkte-Hero der Cricket-Karte (Portrait): Label ueber der Zahl, zentriert. */
@Composable
private fun CricketPointsHero(points: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.game_cricket_points_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = points.toString(),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * Kartinhalt fuer den Around-the-Clock-Modus: Kopf (Name), Ziel-Hero und eine
 * Fortschritt-Zeile ("erreicht / 20"), dazu die uebernommene [LastTurnLine]. Es
 * gibt bewusst KEIN L/S-Standing auf dieser Karte; der Fortschritt ersetzt die
 * Kennzahlzeile.
 *
 * Portrait (non-compact): Name ueber zentriertem Ziel-Hero, darunter die
 * Fortschritt-Zeile. Compact/Querformat: Name, Ziel und Fortschritt in einer
 * Zeile. Ist das Leg gewonnen (defensiv `completed >= 20` bzw. `target > 20`),
 * zeigt der Hero "Fertig" statt der nackten 21 und der Fortschritt "20 / 20".
 */
@Composable
private fun AroundTheClockBoard(
    player: PlayerScoreUi,
    board: PlayerBoardUi.AroundTheClock,
    compact: Boolean,
    marker: String,
) {
    val total = PlayerBoardUi.AroundTheClock.TOTAL
    val done = board.target > total || board.completed >= total
    val shownCompleted = board.completed.coerceIn(0, total)
    val targetLabel = if (done) stringResource(R.string.game_atc_done) else board.target.toString()
    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NameLabel(
                    name = player.name,
                    marker = if (player.isCurrent) marker else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.game_atc_progress, shownCompleted, total),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            LastTurnLine(
                darts = player.lastTurnDarts,
                bust = player.lastTurnBust,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NameLabel(
                name = player.name,
                marker = if (player.isCurrent) marker else null,
                modifier = Modifier.fillMaxWidth(),
            )
            AroundTheClockTargetHero(targetLabel = targetLabel)
            Text(
                text = stringResource(R.string.game_atc_progress, shownCompleted, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LastTurnLine(
                darts = player.lastTurnDarts,
                bust = player.lastTurnBust,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Ziel-Hero der Around-the-Clock-Karte (Portrait): Label ueber der Zielzahl,
 * zentriert. [targetLabel] traegt bereits die "Fertig"-Sonderbehandlung.
 */
@Composable
private fun AroundTheClockTargetHero(targetLabel: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.game_atc_target_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = targetLabel,
            style = MaterialTheme.typography.displaySmall,
        )
    }
}

/**
 * Zeichnet die Marks eines Cricket-Feldes per [Canvas]: 0 == leer, 1 ==
 * Schraegstrich, 2 == Kreuz, 3 == Kreuz mit umschliessendem Kreis. Feste
 * Zellgroesse (24.dp), Strichfarbe = [LocalContentColor] (kontrastsicher auf
 * beiden Kartenfarben). Traegt bewusst KEINE eigene Semantik; die Ansage laeuft
 * zentral ueber die Karte.
 *
 * @param marks Anzeige-Markwert 0..3.
 */
@Composable
private fun CricketMarkCell(marks: Int, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val inset = size.minDimension * 0.18f
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        // 1 Mark: Schraegstrich (unten-links -> oben-rechts).
        if (marks >= 1) {
            drawLine(
                color = color,
                start = Offset(left, bottom),
                end = Offset(right, top),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // 2 Marks: Gegenschraege ergaenzt das Kreuz.
        if (marks >= 2) {
            drawLine(
                color = color,
                start = Offset(left, top),
                end = Offset(right, bottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // 3 Marks: umschliessender Kreis (geschlossen).
        if (marks >= 3) {
            drawCircle(
                color = color,
                radius = size.minDimension / 2f - stroke,
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * Einzeilige Anzeige der letzten abgeschlossenen Aufnahme eines Spielers auf
 * seiner Scoreboard-Karte, z. B. "T-20 · 5 · D-16 (41)" bzw. "... (Bust)".
 *
 * Zeigt die Kurzlabels ([dartShortLabel]) der geworfenen Darts (verbunden mit
 * " · ") plus die Zugsumme in Klammern; bei einem Bust steht statt der Summe
 * "(Bust)" und der Text (nur der Text, nicht die Karte) faerbt sich in die
 * Fehlerfarbe. Ist noch keine Aufnahme abgeschlossen, wird ein dezenter
 * Platzhalter ("-") gerendert, damit die Kartenhoehe stabil bleibt.
 *
 * Rein informativ; die contentDescription wird zentral auf der Karte gesetzt
 * (siehe [PlayerScoreCard]), daher traegt diese Zeile keine eigene Semantik.
 *
 * @param darts Geworfene Darts der letzten Aufnahme (leer = Platzhalter).
 * @param bust True, wenn die letzte Aufnahme ein Bust war.
 * @param textAlign Ausrichtung des Texts (zentriert im Portrait, links im Kompaktmodus).
 */
@Composable
private fun LastTurnLine(
    darts: List<Dart>,
    bust: Boolean,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    if (darts.isEmpty()) {
        Text(
            text = stringResource(R.string.game_last_turn_empty),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val dartsText = darts.joinToString(" · ") { dartShortLabel(it) }
    val summary = if (bust) {
        stringResource(R.string.game_last_turn_bust)
    } else {
        darts.sumOf { it.value }.toString()
    }
    Text(
        text = stringResource(R.string.game_last_turn_value, dartsText, summary),
        style = MaterialTheme.typography.labelMedium,
        // Normal erbt die contentColor der Karte; bei Bust nur der Text in error.
        color = if (bust) MaterialTheme.colorScheme.error else Color.Unspecified,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
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

/** Standard-Paarung: beide Spieler mit letzter Aufnahme (3 Darts / 2-Dart-Checkout). */
private val previewPlayers = listOf(
    PlayerScoreUi(
        playerId = 1, name = "Tom", board = PlayerBoardUi.X01(287), legsWon = 1, setsWon = 0, isCurrent = true,
        lastTurnDarts = listOf(Dart.triple(20), Dart.single(5), Dart.double(16)),
    ),
    PlayerScoreUi(
        playerId = 2, name = "Anna Beispiel", board = PlayerBoardUi.X01(340), legsWon = 0, setsWon = 0, isCurrent = false,
        lastTurnDarts = listOf(Dart.triple(20), Dart.double(20)),
    ),
)

@Preview(showBackground = true, name = "Portrait: beide mit Aufnahme", widthDp = 360)
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

@Preview(showBackground = true, name = "Portrait: einer ohne Aufnahme (Platzhalter)", widthDp = 360)
@Composable
private fun MatchScoreboardEmptyLastTurnPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = PlayerBoardUi.X01(461), legsWon = 0, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.single(20), Dart.single(20), Dart.single(0)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = PlayerBoardUi.X01(501), legsWon = 0, setsWon = 0, isCurrent = false,
                ),
            ),
            currentLegNumber = 1,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Portrait: 4 Spieler schmal (Ellipsis)", widthDp = 360)
@Composable
private fun MatchScoreboardFourNarrowPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = PlayerBoardUi.X01(287), legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.triple(19), Dart.triple(18)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna", board = PlayerBoardUi.X01(340), legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.single(5), Dart.double(16)),
                ),
                PlayerScoreUi(
                    playerId = 3, name = "Bjoern", board = PlayerBoardUi.X01(410), legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.bull(), Dart.bull(), Dart.doubleBull()),
                ),
                PlayerScoreUi(
                    playerId = 4, name = "Clara", board = PlayerBoardUi.X01(92), legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.single(1), Dart.single(1), Dart.single(1)),
                ),
            ),
            currentLegNumber = 1,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Portrait: Bust (auch beim Werfer)", widthDp = 360)
@Composable
private fun MatchScoreboardBustPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = PlayerBoardUi.X01(40), legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.single(20), Dart.single(20), Dart.single(20)),
                    lastTurnBust = true,
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = PlayerBoardUi.X01(340), legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.triple(20), Dart.single(20)),
                    lastTurnBust = true,
                ),
            ),
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

// --- Cricket-Previews ---

/**
 * Baut ein [PlayerBoardUi.Cricket] fuer Previews in fester Anzeigereihenfolge
 * (20,19,18,17,16,15,Bull). [marksByTarget] enthaelt die abweichenden Marks;
 * fehlende Felder starten bei 0.
 */
private fun cricketBoard(points: Int, marksByTarget: Map<Int, Int> = emptyMap()): PlayerBoardUi.Cricket =
    PlayerBoardUi.Cricket(
        fields = listOf(20, 19, 18, 17, 16, 15, 25).map { target ->
            CricketFieldUi(target = target, marks = marksByTarget[target] ?: 0)
        },
        points = points,
    )

@Preview(showBackground = true, name = "Cricket Portrait: beide mit Aufnahme", widthDp = 360)
@Composable
private fun MatchScoreboardCricketPortraitPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom",
                    board = cricketBoard(points = 60, marksByTarget = mapOf(20 to 3, 19 to 2, 18 to 1)),
                    legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.double(19), Dart.single(18)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel",
                    board = cricketBoard(points = 0, marksByTarget = mapOf(20 to 1, 25 to 2)),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.single(20), Dart.doubleBull()),
                ),
            ),
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Cricket Portrait: Leg-Start (leer)", widthDp = 360)
@Composable
private fun MatchScoreboardCricketEmptyPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = cricketBoard(points = 0),
                    legsWon = 0, setsWon = 0, isCurrent = true,
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = cricketBoard(points = 0),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
            ),
            currentLegNumber = 1,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Cricket Portrait: 4 Spieler schmal", widthDp = 360)
@Composable
private fun MatchScoreboardCricketFourNarrowPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom",
                    board = cricketBoard(points = 135, marksByTarget = mapOf(20 to 3, 19 to 3, 18 to 2)),
                    legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.triple(19), Dart.double(18)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna",
                    board = cricketBoard(points = 40, marksByTarget = mapOf(20 to 3, 17 to 1)),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
                PlayerScoreUi(
                    playerId = 3, name = "Bjoern",
                    board = cricketBoard(points = 0, marksByTarget = mapOf(25 to 2)),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
                PlayerScoreUi(
                    playerId = 4, name = "Clara",
                    board = cricketBoard(points = 25, marksByTarget = mapOf(15 to 3, 16 to 1)),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
            ),
            currentLegNumber = 1,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Cricket Querformat: geschlossene Felder + Vorsprung", widthDp = 640)
@Composable
private fun MatchScoreboardCricketLandscapePreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom",
                    board = cricketBoard(
                        points = 180,
                        marksByTarget = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 2, 16 to 1),
                    ),
                    legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.triple(18), Dart.single(17)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel",
                    board = cricketBoard(points = 60, marksByTarget = mapOf(20 to 3, 19 to 2)),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.double(19)),
                ),
            ),
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

// --- Around-the-Clock-Previews ---

/** Baut ein [PlayerBoardUi.AroundTheClock] fuer Previews (Ziel + Fortschritt). */
private fun atcBoard(target: Int, completed: Int): PlayerBoardUi.AroundTheClock =
    PlayerBoardUi.AroundTheClock(target = target, completed = completed)

@Preview(showBackground = true, name = "ATC Portrait: beide mit Aufnahme", widthDp = 360)
@Composable
private fun MatchScoreboardAtcPortraitPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = atcBoard(target = 8, completed = 7),
                    legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.single(5), Dart.triple(6), Dart.double(7)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = atcBoard(target = 4, completed = 3),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.single(2), Dart.single(3)),
                ),
            ),
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "ATC Portrait: Leg-Start (Ziel 1)", widthDp = 360)
@Composable
private fun MatchScoreboardAtcEmptyPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = atcBoard(target = 1, completed = 0),
                    legsWon = 0, setsWon = 0, isCurrent = true,
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = atcBoard(target = 1, completed = 0),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
            ),
            currentLegNumber = 1,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "ATC Portrait: 4 Spieler schmal", widthDp = 360)
@Composable
private fun MatchScoreboardAtcFourNarrowPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = atcBoard(target = 15, completed = 14),
                    legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.single(12), Dart.single(13), Dart.single(14)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna", board = atcBoard(target = 9, completed = 8),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
                PlayerScoreUi(
                    playerId = 3, name = "Bjoern", board = atcBoard(target = 3, completed = 2),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
                // Defensive "Fertig"-Anzeige (Ziel > 20 nach Leg-Gewinn).
                PlayerScoreUi(
                    playerId = 4, name = "Clara", board = atcBoard(target = 21, completed = 20),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                ),
            ),
            currentLegNumber = 1,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "ATC Querformat: beide mit Aufnahme", widthDp = 640)
@Composable
private fun MatchScoreboardAtcLandscapePreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = atcBoard(target = 18, completed = 17),
                    legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.single(15), Dart.single(16), Dart.single(17)),
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = atcBoard(target = 11, completed = 10),
                    legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.single(9), Dart.single(10)),
                ),
            ),
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}

@Preview(showBackground = true, name = "Querformat: mit Aufnahme", widthDp = 640)
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

@Preview(showBackground = true, name = "Querformat: Bust", widthDp = 640)
@Composable
private fun MatchScoreboardLandscapeBustPreview() {
    TomsDartsTheme {
        MatchScoreboard(
            players = listOf(
                PlayerScoreUi(
                    playerId = 1, name = "Tom", board = PlayerBoardUi.X01(40), legsWon = 1, setsWon = 0, isCurrent = true,
                    lastTurnDarts = listOf(Dart.single(20), Dart.single(20), Dart.single(20)),
                    lastTurnBust = true,
                ),
                PlayerScoreUi(
                    playerId = 2, name = "Anna Beispiel", board = PlayerBoardUi.X01(340), legsWon = 0, setsWon = 0, isCurrent = false,
                    lastTurnDarts = listOf(Dart.triple(20), Dart.single(5), Dart.double(16)),
                ),
            ),
            currentLegNumber = 2,
            currentSetNumber = 1,
            legsToWin = 2,
            setsToWin = 1,
        )
    }
}
