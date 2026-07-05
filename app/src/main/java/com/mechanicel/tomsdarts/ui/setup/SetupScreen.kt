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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/** Auswaehlbare Startpunktwerte fuer ein X01-Match. */
val START_SCORES: List<Int> = listOf(301, 501, 701)

/** Standard-Startpunktwert (vorbelegte Auswahl im Setup). */
const val DEFAULT_START_SCORE: Int = 501

/** Standard-Double-Out (vorbelegter Toggle-Zustand im Setup). */
const val DEFAULT_DOUBLE_OUT: Boolean = true

/** Auswaehlbare "Best of X"-Werte fuer die Anzahl Legs (nur ungerade). */
val LEGS_BEST_OF_OPTIONS: List<Int> = listOf(1, 3, 5)

/** Standard-"Best of X" fuer Legs (Best of 3 = heutiges Verhalten, legsToWin=2). */
const val DEFAULT_LEGS_BEST_OF: Int = 3

/** Auswaehlbare "Best of X"-Werte fuer die Anzahl Sets (nur ungerade). */
val SETS_BEST_OF_OPTIONS: List<Int> = listOf(1, 3, 5)

/** Standard-"Best of X" fuer Sets (Best of 1 = heutiges Verhalten, setsToWin=1). */
const val DEFAULT_SETS_BEST_OF: Int = 1

/**
 * Bildet einen "Best of X"-Wert auf die zugehoerige Gewinnschwelle "first to N"
 * ab (die Domaenendarstellung in [com.mechanicel.tomsdarts.game.GameConfig] und
 * der [com.mechanicel.tomsdarts.game.engine.MatchEngine]). Einzige
 * Umrechnungsstelle der Setup-UI. Best of 1 -> 1, Best of 3 -> 2, Best of 5 -> 3.
 */
fun bestOfToWin(bestOf: Int): Int = (bestOf + 1) / 2

/**
 * Buendelt die Callbacks des Setup-Bildschirms fuer die zustandslose
 * [SetupScreenContent], damit diese @Preview-faehig bleibt.
 *
 * @param onMovePlayerUp Teilnehmer am Index eine Position nach oben schieben.
 * @param onMovePlayerDown Teilnehmer am Index eine Position nach unten schieben.
 * @param onRemovePlayer Teilnehmer am Index aus dem Match entfernen.
 * @param onSelectStartScore Auswahl eines Startpunktwerts.
 * @param onToggleDoubleOut Umschalten des Double-Out (Auschecken mit Doppel).
 * @param onSelectLegsBestOf Auswahl der Legs-Anzahl als "Best of X".
 * @param onSelectSetsBestOf Auswahl der Sets-Anzahl als "Best of X".
 * @param onConfirm Match mit den Teilnehmern und den gewaehlten Optionen starten.
 * @param onCancel Setup verlassen (zurueck zur Profilliste).
 */
data class SetupScreenCallbacks(
    val onMovePlayerUp: (Int) -> Unit = {},
    val onMovePlayerDown: (Int) -> Unit = {},
    val onRemovePlayer: (Int) -> Unit = {},
    val onSelectStartScore: (Int) -> Unit = {},
    val onToggleDoubleOut: (Boolean) -> Unit = {},
    val onSelectLegsBestOf: (Int) -> Unit = {},
    val onSelectSetsBestOf: (Int) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * Zustandsbehafteter Einstiegspunkt des Spiel-Setup-Bildschirms. Bezieht die im
 * Setup editierbare Teilnehmerliste ueber das [SetupViewModel] (Namensaufloesung
 * zu den [playerIds] + Reorder/Remove), haelt den gewaehlten Startpunkt, das
 * Double-Out und die Legs/Sets-Auswahl konfigurationsfest ([rememberSaveable])
 * und delegiert das Rendern an die zustandslose [SetupScreenContent]. Reine
 * lokale Konfiguration (offline-first, kein Netzwerk/Login/Tracking).
 *
 * @param playerIds Teilnehmer in Eingangsreihenfolge (>= 2 fuer ein Match).
 * @param onConfirm Match mit den im Setup final sortierten/reduzierten
 *   Teilnehmern (ID-Liste, Reihenfolge relevant), dem gewaehlten Startpunkt, dem
 *   Double-Out sowie den Domaenenwerten legsToWin/setsToWin starten (die
 *   "Best of X"->"first to N"-Umrechnung ist hier bereits gekapselt).
 * @param onCancel Setup verlassen (zurueck zur Profilliste).
 */
@Composable
fun SetupScreen(
    playerIds: List<Long>,
    onConfirm: (List<Long>, Int, Boolean, Int, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.provideFactory(playerIds)),
) {
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    var selectedStartScore by rememberSaveable { mutableIntStateOf(DEFAULT_START_SCORE) }
    var doubleOut by rememberSaveable { mutableStateOf(DEFAULT_DOUBLE_OUT) }
    var legsBestOf by rememberSaveable { mutableIntStateOf(DEFAULT_LEGS_BEST_OF) }
    var setsBestOf by rememberSaveable { mutableIntStateOf(DEFAULT_SETS_BEST_OF) }

    SetupScreenContent(
        participants = participants,
        selectedStartScore = selectedStartScore,
        doubleOut = doubleOut,
        legsBestOf = legsBestOf,
        setsBestOf = setsBestOf,
        callbacks = SetupScreenCallbacks(
            onMovePlayerUp = viewModel::movePlayerUp,
            onMovePlayerDown = viewModel::movePlayerDown,
            onRemovePlayer = viewModel::removePlayer,
            onSelectStartScore = { selectedStartScore = it },
            onToggleDoubleOut = { doubleOut = it },
            onSelectLegsBestOf = { legsBestOf = it },
            onSelectSetsBestOf = { setsBestOf = it },
            onConfirm = {
                // Die im Setup editierte, geordnete ID-Liste geht ins Match
                // (nicht die urspruengliche Eingabe).
                onConfirm(
                    viewModel.orderedPlayerIds(),
                    selectedStartScore,
                    doubleOut,
                    bestOfToWin(legsBestOf),
                    bestOfToWin(setsBestOf),
                )
            },
            onCancel = onCancel,
        ),
        modifier = modifier,
    )
}

/**
 * Zustandsloser Bildschirminhalt des Spiel-Setup-Bildschirms. Rendert TopAppBar
 * mit Zurueck-Aktion, den Konfigurationskoerper (Teilnehmerverwaltung,
 * Startpunkt-Auswahl, Double-Out und Legs/Sets) und die Primaeraktion
 * "Match starten" in der bottomBar. Der Body ist bewusst als Liste von Sections
 * aufgebaut, damit spaetere Optionen darunter passen.
 *
 * @param participants Im Setup editierte, geordnete Teilnehmerliste.
 * @param selectedStartScore Aktuell gewaehlter Startpunktwert.
 * @param doubleOut Ob Double-Out (Auschecken mit Doppel) aktiv ist.
 * @param legsBestOf Aktuell gewaehltes "Best of X" fuer die Legs.
 * @param setsBestOf Aktuell gewaehltes "Best of X" fuer die Sets.
 * @param callbacks Aktionen des Bildschirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreenContent(
    participants: List<SetupPlayer>,
    selectedStartScore: Int,
    doubleOut: Boolean,
    legsBestOf: Int,
    setsBestOf: Int,
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
                enabled = participants.size >= MIN_MATCH_PLAYERS,
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
                ParticipantsSection(
                    participants = participants,
                    onMovePlayerUp = callbacks.onMovePlayerUp,
                    onMovePlayerDown = callbacks.onMovePlayerDown,
                    onRemovePlayer = callbacks.onRemovePlayer,
                )
                StartScoreSection(
                    selectedStartScore = selectedStartScore,
                    onSelectStartScore = callbacks.onSelectStartScore,
                )
                DoubleOutSection(
                    checked = doubleOut,
                    onCheckedChange = callbacks.onToggleDoubleOut,
                )
                BestOfSection(
                    label = stringResource(R.string.setup_legs_label),
                    options = LEGS_BEST_OF_OPTIONS,
                    selectedBestOf = legsBestOf,
                    onSelectBestOf = callbacks.onSelectLegsBestOf,
                    cardContentDescription = { bestOf ->
                        pluralStringResource(R.plurals.setup_legs_card_cd, bestOf, bestOf)
                    },
                )
                BestOfSection(
                    label = stringResource(R.string.setup_sets_label),
                    options = SETS_BEST_OF_OPTIONS,
                    selectedBestOf = setsBestOf,
                    onSelectBestOf = callbacks.onSelectSetsBestOf,
                    cardContentDescription = { bestOf ->
                        pluralStringResource(R.plurals.setup_sets_card_cd, bestOf, bestOf)
                    },
                )
            }
        }
    }
}

/**
 * Section mit der Teilnehmerverwaltung: Section-Label ("Teilnehmer (N)") plus je
 * Teilnehmer eine [PlayerReorderRow] zum Umsortieren (Nachbar-Swap ueber die
 * ↑/↓-Buttons) und Entfernen. Die Positions-Nummern ergeben sich aus dem
 * Listen-Index und aktualisieren sich sofort. Bewusst als [Column] im bereits
 * scrollenden Body gerendert (kein verschachteltes LazyColumn); die Zeilen
 * tragen einen stabilen [key] auf die Spieler-ID.
 *
 * Solange nur [MIN_MATCH_PLAYERS] Teilnehmer uebrig sind, ist das Entfernen in
 * allen Zeilen deaktiviert und ein Hinweis wird eingeblendet.
 */
@Composable
private fun ParticipantsSection(
    participants: List<SetupPlayer>,
    onMovePlayerUp: (Int) -> Unit,
    onMovePlayerDown: (Int) -> Unit,
    onRemovePlayer: (Int) -> Unit,
) {
    val canRemove = participants.size > MIN_MATCH_PLAYERS
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pluralStringResource(
                R.plurals.setup_players_label,
                participants.size,
                participants.size,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        participants.forEachIndexed { index, player ->
            key(player.id) {
                PlayerReorderRow(
                    position = index + 1,
                    player = player,
                    isFirst = index == 0,
                    isLast = index == participants.lastIndex,
                    canRemove = canRemove,
                    onMoveUp = { onMovePlayerUp(index) },
                    onMoveDown = { onMovePlayerDown(index) },
                    onRemove = { onRemovePlayer(index) },
                )
            }
        }
        if (participants.size == MIN_MATCH_PLAYERS) {
            Text(
                text = stringResource(R.string.setup_players_min_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Fallback fuer die Barrierefreiheit: den Grenzfall aktiv ansagen,
                // statt dem verschobenen Element den Fokus nachzufuehren.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Einzelne Teilnehmer-Zeile der Teilnehmerverwaltung: Positions-Avatar (1-basiert),
 * Name und die drei Aktions-Buttons ↑ (nach oben), ↓ (nach unten) und ✕
 * (entfernen). Aufbau bewusst analog zu [BestOfSection]/[BestOfCard] gehalten.
 *
 * Grenzzustaende halten die Geometrie stabil (Buttons bleiben sichtbar, nur
 * deaktiviert): ↑ ist beim ersten, ↓ beim letzten Element deaktiviert, ✕ solange
 * die [MIN_MATCH_PLAYERS]-Grenze erreicht ist ([canRemove] == false). Die Zeile
 * traegt die Positions-/Namensansage, die Icon-Glyphen selbst sind stumm
 * (contentDescription = null); die Ansage jedes Buttons haengt am [IconButton].
 *
 * @param position 1-basierte Anzeigeposition (aus dem Listen-Index).
 * @param isFirst Ob dies das erste Element ist (↑ deaktiviert).
 * @param isLast Ob dies das letzte Element ist (↓ deaktiviert).
 * @param canRemove Ob Entfernen erlaubt ist (ueber [MIN_MATCH_PLAYERS]).
 */
@Composable
private fun PlayerReorderRow(
    position: Int,
    player: SetupPlayer,
    isFirst: Boolean,
    isLast: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val positionCd = stringResource(R.string.setup_players_position_cd, position, player.name)
    val moveUpCd = stringResource(R.string.setup_players_move_up_cd, player.name)
    val moveDownCd = stringResource(R.string.setup_players_move_down_cd, player.name)
    val removeCd = stringResource(R.string.setup_players_remove_cd, player.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .semantics { contentDescription = positionCd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PositionAvatar(position = position)
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onMoveUp,
            enabled = !isFirst,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = moveUpCd },
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
            )
        }
        IconButton(
            onClick = onMoveDown,
            enabled = !isLast,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = moveDownCd },
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = canRemove,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = removeCd },
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
            )
        }
    }
}

/**
 * Runder Positions-Avatar mit zentrierter, 1-basierter Nummer. Spiegelt optisch
 * den Avatar-Stil des Profil-Auswahlmodus (primary/onPrimary, 40 dp) und ist
 * rein dekorativ (die Position wird bereits von der Zeile angesagt).
 */
@Composable
private fun PositionAvatar(position: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
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
 * Section mit dem Double-Out-Schalter: Section-Label plus eine toggelbare Zeile
 * (Titel + Erklaertext links, [Switch] rechts). Die gesamte Zeile ist die
 * einzige semantische Toggle-Node ([Modifier.toggleable] mit [Role.Switch]),
 * damit TalkBack den Zustand nur einmal ansagt; der [Switch] selbst ist daher
 * mit `onCheckedChange = null` rein dekorativ. Touch-Target >= 48 dp.
 */
@Composable
private fun DoubleOutSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.setup_double_out_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setup_double_out_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.setup_double_out_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
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

/**
 * Generische Section fuer eine "Best of X"-Auswahl (Legs oder Sets): Section-Label
 * plus eine Reihe auswaehlbarer Karten (Werte aus [options]). Aufbau bewusst 1:1
 * an [StartScoreSection] gespiegelt; die Karten bilden semantisch eine
 * Radio-Gruppe, damit TalkBack den Auswahlzustand korrekt ansagt.
 *
 * @param label Section-Beschriftung (z.B. "Anzahl Legs").
 * @param options Auswaehlbare "Best of X"-Werte in Anzeigereihenfolge.
 * @param selectedBestOf Aktuell gewaehlter "Best of X"-Wert.
 * @param onSelectBestOf Auswahl eines "Best of X"-Werts.
 * @param cardContentDescription Liefert die TalkBack-Ansage je Kartenwert
 *   (z.B. "1 Leg" / "3 Legs") - als Lambda, da @Composable (pluralStringResource).
 */
@Composable
private fun BestOfSection(
    label: String,
    options: List<Int>,
    selectedBestOf: Int,
    onSelectBestOf: (Int) -> Unit,
    cardContentDescription: @Composable (Int) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { bestOf ->
                BestOfCard(
                    bestOf = bestOf,
                    selected = bestOf == selectedBestOf,
                    contentDescription = cardContentDescription(bestOf),
                    onSelect = { onSelectBestOf(bestOf) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Einzelne auswaehlbare "Best of X"-Karte. Aufbau bewusst 1:1 an [StartScoreCard]
 * gespiegelt (gleiche Farben/Border/Touch-Target); traegt die Selektions-Semantik
 * ([Role.RadioButton]) selbst und eine eindeutige [contentDescription] fuer die
 * Ansage. Der Kartentext ist die nackte Zahl (z.B. "3"), nicht "Bo3".
 */
@Composable
private fun BestOfCard(
    bestOf: Int,
    selected: Boolean,
    contentDescription: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = contentDescription
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
                .semantics { this.contentDescription = cd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bestOf.toString(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Previews ---

/** Beispiel-Teilnehmer fuer die Previews (Normalfall mit vier Spielern). */
private val previewParticipants: List<SetupPlayer> = listOf(
    SetupPlayer(id = 1, name = "Tom"),
    SetupPlayer(id = 2, name = "Anna Beispiel"),
    SetupPlayer(id = 3, name = "Bjoern"),
    SetupPlayer(id = 4, name = "Chris"),
)

@Preview(showBackground = true, name = "Standard (501)")
@Composable
private fun SetupScreenDefaultPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "301 gewaehlt")
@Composable
private fun SetupScreen301Preview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 301,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "701 gewaehlt")
@Composable
private fun SetupScreen701Preview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 701,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Querformat", widthDp = 760, heightDp = 380)
@Composable
private fun SetupScreenLandscapePreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Kleines Geraet", widthDp = 320)
@Composable
private fun SetupScreenSmallPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Double-Out an")
@Composable
private fun SetupScreenDoubleOutOnPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Double-Out aus")
@Composable
private fun SetupScreenDoubleOutOffPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = false,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Best of 5 Legs")
@Composable
private fun SetupScreenBestOf5LegsPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = 5,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Mit Sets (Bo3)")
@Composable
private fun SetupScreenWithSetsPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = 3,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Teilnehmer: 2 (Entfernen aus, Hinweis)")
@Composable
private fun SetupScreenTwoParticipantsPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = listOf(
                SetupPlayer(id = 1, name = "Tom"),
                SetupPlayer(id = 2, name = "Anna Beispiel"),
            ),
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Teilnehmer: 4 (Normalfall)")
@Composable
private fun SetupScreenFourParticipantsPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = previewParticipants,
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Teilnehmer: langer Name @320dp", widthDp = 320)
@Composable
private fun SetupScreenLongNameParticipantPreview() {
    TomsDartsTheme {
        SetupScreenContent(
            participants = listOf(
                SetupPlayer(id = 1, name = "Maximilian Alexander von Beispielhausen-Muenchhausen"),
                SetupPlayer(id = 2, name = "Anna"),
                SetupPlayer(id = 3, name = "Bjoern"),
            ),
            selectedStartScore = 501,
            doubleOut = true,
            legsBestOf = DEFAULT_LEGS_BEST_OF,
            setsBestOf = DEFAULT_SETS_BEST_OF,
            callbacks = SetupScreenCallbacks(),
        )
    }
}
