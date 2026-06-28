package com.mechanicel.tomsdarts.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

/**
 * Buendelt die Callbacks des Eingabe-Ziffernblocks fuer die zustandslose
 * [DartKeypadContent], damit diese @Preview-faehig bleibt.
 *
 * Die Eingabe-Callbacks ([onToggleDouble] bis [onUndo]) treiben die
 * State-Transitionen. [onDart] und [onTurnComplete] sind die nach aussen
 * gerichteten Signale, die der zustandsbehaftete [DartKeypad]-Holder aus den
 * State-Uebergaengen ableitet (die zustandslose [DartKeypadContent] feuert sie
 * nicht selbst).
 */
data class DartKeypadCallbacks(
    val onToggleDouble: () -> Unit = {},
    val onToggleTriple: () -> Unit = {},
    val onNumber: (Int) -> Unit = {},
    val onBull: () -> Unit = {},
    val onOut: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onDart: (Dart) -> Unit = {},
    val onTurnComplete: (List<Dart>) -> Unit = {},
)

/**
 * [androidx.compose.runtime.saveable.Saver] fuer [DartInputState], damit die
 * laufende Aufnahme eine Konfigurationsaenderung (z. B. Rotation) uebersteht.
 *
 * Serialisiert als flache Int-Liste: erstes Element ist `modifier.ordinal`,
 * danach folgen pro Dart zwei Werte (segment, multiplier).
 */
val DartInputStateSaver = listSaver<DartInputState, Int>(
    save = { state ->
        buildList {
            add(state.modifier.ordinal)
            state.darts.forEach { dart ->
                add(dart.segment)
                add(dart.multiplier)
            }
        }
    },
    restore = { values ->
        val modifier = DartModifier.entries[values[0]]
        val darts = values.drop(1).chunked(2).map { (segment, multiplier) ->
            Dart(segment, multiplier)
        }
        DartInputState(modifier = modifier, darts = darts)
    },
)

/**
 * Zustandsbehafteter Einstiegspunkt des Eingabe-Ziffernblocks.
 *
 * Haelt den [DartInputState] ueber [rememberSaveable] (mit [DartInputStateSaver])
 * und berechnet bei jeder Eingabe einen neuen State ueber die jeweilige
 * Transition. Kam dadurch ein Dart hinzu, wird [onDart] mit dem neuen Dart
 * gefeuert; ist die Aufnahme damit voll, zusaetzlich [onTurnComplete] mit allen
 * drei Darts.
 *
 * Bewusst ohne State-Parameter und ohne Spiel-/Persistenz-Kopplung: eine reine,
 * wiederverwendbare Eingabe-Komponente.
 */
@Composable
fun DartKeypad(
    modifier: Modifier = Modifier,
    onDart: (Dart) -> Unit = {},
    onTurnComplete: (List<Dart>) -> Unit = {},
) {
    var state by rememberSaveable(stateSaver = DartInputStateSaver) {
        mutableStateOf(DartInputState())
    }

    fun applyTransition(transition: (DartInputState) -> DartInputState) {
        val previous = state
        val next = transition(previous)
        if (next === previous) return
        state = next
        if (next.darts.size > previous.darts.size) {
            onDart(next.darts.last())
            if (next.isComplete) {
                onTurnComplete(next.darts)
            }
        }
    }

    DartKeypadContent(
        state = state,
        callbacks = DartKeypadCallbacks(
            onToggleDouble = { applyTransition(DartInputState::toggleDouble) },
            onToggleTriple = { applyTransition(DartInputState::toggleTriple) },
            onNumber = { n -> applyTransition { it.pressNumber(n) } },
            onBull = { applyTransition(DartInputState::pressBull) },
            onOut = { applyTransition(DartInputState::pressOut) },
            onUndo = { applyTransition(DartInputState::undo) },
            onDart = onDart,
            onTurnComplete = onTurnComplete,
        ),
        modifier = modifier,
    )
}

/**
 * Zustandslose Oberflaeche des Eingabe-Ziffernblocks: Aufnahme-Slots plus
 * 5x5-Raster (vier Zifferntasten + eine Sondertaste je Zeile). Rendert sich
 * vollstaendig aus dem uebergebenen [state]; im Querformat stehen Slots und
 * Raster nebeneinander, sonst untereinander.
 */
@Composable
fun DartKeypadContent(
    state: DartInputState,
    callbacks: DartKeypadCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TurnSlots(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Keypad(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TurnSlots(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                )
                Keypad(
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Aufnahme-Anzeige: aktuelle Zugsumme plus drei gleich breite Slot-Kacheln.
 */
@Composable
private fun TurnSlots(
    state: DartInputState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.keypad_turn_sum, state.turnSum),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (index in 0 until DartInputState.MAX_DARTS) {
                SlotTile(
                    slotNumber = index + 1,
                    dart = state.darts.getOrNull(index),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Einzelne Slot-Kachel: gefuellt zeigt sie das [dartShortLabel], leer einen
 * gedaempften Platzhalter.
 */
@Composable
private fun SlotTile(
    slotNumber: Int,
    dart: Dart?,
    modifier: Modifier = Modifier,
) {
    val filled = dart != null
    val description = if (dart != null) {
        stringResource(R.string.keypad_cd_slot_filled, slotNumber, dartShortLabel(dart))
    } else {
        stringResource(R.string.keypad_cd_slot_empty, slotNumber)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clearAndSetSemantics { contentDescription = description },
        color = if (filled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = dart?.let { dartShortLabel(it) }
                    ?: stringResource(R.string.keypad_slot_empty),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Das 5x5-Tastenraster: fuenf Zeilen aus je vier Zifferntasten und einer
 * Sondertaste (Undo, Out, Bull, Double, Triple).
 */
@Composable
private fun Keypad(
    state: DartInputState,
    callbacks: DartKeypadCallbacks,
    modifier: Modifier = Modifier,
) {
    val numberRows = listOf(
        1..4,
        5..8,
        9..12,
        13..16,
        17..20,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        numberRows.forEachIndexed { rowIndex, numbers ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    numbers.forEach { n ->
                        NumberKey(
                            number = n,
                            state = state,
                            onNumber = callbacks.onNumber,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
                SpecialKey(
                    rowIndex = rowIndex,
                    state = state,
                    callbacks = callbacks,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Eine Zifferntaste 1..20. Zeigt live das [numberKeyLabel] inklusive
 * `D-`/`T-`-Praefix und liefert je nach Modifikator eine sprechende
 * contentDescription. Deaktiviert, sobald die Aufnahme voll ist.
 */
@Composable
private fun NumberKey(
    number: Int,
    state: DartInputState,
    onNumber: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = number * state.modifier.multiplier
    val description = when (state.modifier) {
        DartModifier.SINGLE -> stringResource(R.string.keypad_cd_number_single, number, points)
        DartModifier.DOUBLE -> stringResource(R.string.keypad_cd_number_double, number, points)
        DartModifier.TRIPLE -> stringResource(R.string.keypad_cd_number_triple, number, points)
    }
    FilledTonalButton(
        onClick = { onNumber(number) },
        enabled = state.inputEnabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(2.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = numberKeyLabel(number, state.modifier),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Liefert die Sondertaste der jeweiligen Rasterzeile:
 * Undo, Out, Bull, Double, Triple (von oben nach unten).
 */
@Composable
private fun SpecialKey(
    rowIndex: Int,
    state: DartInputState,
    callbacks: DartKeypadCallbacks,
    modifier: Modifier = Modifier,
) {
    when (rowIndex) {
        0 -> UndoKey(canUndo = state.canUndo, onUndo = callbacks.onUndo, modifier = modifier)
        1 -> OutKey(enabled = state.inputEnabled, onOut = callbacks.onOut, modifier = modifier)
        2 -> BullKey(state = state, onBull = callbacks.onBull, modifier = modifier)
        3 -> ToggleKey(
            label = stringResource(R.string.keypad_double),
            description = stringResource(R.string.keypad_cd_toggle_double),
            selected = state.modifier == DartModifier.DOUBLE,
            enabled = state.inputEnabled,
            onClick = callbacks.onToggleDouble,
            modifier = modifier,
        )
        else -> ToggleKey(
            label = stringResource(R.string.keypad_triple),
            description = stringResource(R.string.keypad_cd_toggle_triple),
            selected = state.modifier == DartModifier.TRIPLE,
            enabled = state.inputEnabled,
            onClick = callbacks.onToggleTriple,
            modifier = modifier,
        )
    }
}

@Composable
private fun UndoKey(
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.keypad_cd_undo)
    FilledTonalButton(
        onClick = onUndo,
        enabled = canUndo,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(2.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = stringResource(R.string.keypad_undo), maxLines = 1)
    }
}

@Composable
private fun OutKey(
    enabled: Boolean,
    onOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.keypad_cd_out)
    FilledTonalButton(
        onClick = onOut,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(2.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = stringResource(R.string.keypad_out), maxLines = 1)
    }
}

@Composable
private fun BullKey(
    state: DartInputState,
    onBull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDouble = state.modifier == DartModifier.DOUBLE
    val description = when {
        !state.bullEnabled && state.modifier == DartModifier.TRIPLE ->
            stringResource(R.string.keypad_cd_bull_disabled)
        isDouble -> stringResource(R.string.keypad_cd_bull_double)
        else -> stringResource(R.string.keypad_cd_bull_single)
    }
    // Im Doppel-Modus zeigt bullKeyLabel das "D-"-Praefix; sonst der
    // lokalisierte Kurzbegriff.
    val label = if (isDouble) bullKeyLabel(state.modifier) else stringResource(R.string.keypad_bull)
    FilledTonalButton(
        onClick = onBull,
        enabled = state.bullEnabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(2.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = label, maxLines = 1)
    }
}

/**
 * Toggle-Sondertaste (Double/Triple) mit sichtbarem und semantischem
 * [selected]-Zustand: aktiv als gefuellter Button (primary), inaktiv tonal.
 */
@Composable
private fun ToggleKey(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticsModifier = modifier
        .heightIn(min = 56.dp)
        .semantics {
            this.selected = selected
            contentDescription = description
        }
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(2.dp),
            modifier = semanticsModifier,
        ) {
            Text(text = label, maxLines = 1)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(2.dp),
            modifier = semanticsModifier,
        ) {
            Text(text = label, maxLines = 1)
        }
    }
}

// --- Previews ---

private fun previewState(
    modifier: DartModifier = DartModifier.SINGLE,
    darts: List<Dart> = emptyList(),
) = DartInputState(modifier = modifier, darts = darts)

@Preview(showBackground = true, name = "Leer", heightDp = 640)
@Composable
private fun DartKeypadEmptyPreview() {
    TomsDartsTheme {
        DartKeypadContent(
            state = previewState(),
            callbacks = DartKeypadCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Zwei Darts", heightDp = 640)
@Composable
private fun DartKeypadTwoDartsPreview() {
    TomsDartsTheme {
        DartKeypadContent(
            state = previewState(
                darts = listOf(Dart.triple(20), Dart.single(19)),
            ),
            callbacks = DartKeypadCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Double aktiv", heightDp = 640)
@Composable
private fun DartKeypadDoublePreview() {
    TomsDartsTheme {
        DartKeypadContent(
            state = previewState(modifier = DartModifier.DOUBLE),
            callbacks = DartKeypadCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Triple aktiv (Bull gesperrt)", heightDp = 640)
@Composable
private fun DartKeypadTriplePreview() {
    TomsDartsTheme {
        DartKeypadContent(
            state = previewState(modifier = DartModifier.TRIPLE),
            callbacks = DartKeypadCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Voll (drei Darts)", heightDp = 640)
@Composable
private fun DartKeypadFullPreview() {
    TomsDartsTheme {
        DartKeypadContent(
            state = previewState(
                darts = listOf(Dart.triple(20), Dart.triple(20), Dart.doubleBull()),
            ),
            callbacks = DartKeypadCallbacks(),
        )
    }
}

@Preview(showBackground = true, name = "Querformat", widthDp = 720, heightDp = 360)
@Composable
private fun DartKeypadLandscapePreview() {
    TomsDartsTheme {
        DartKeypadContent(
            state = previewState(
                modifier = DartModifier.DOUBLE,
                darts = listOf(Dart.single(20)),
            ),
            callbacks = DartKeypadCallbacks(),
        )
    }
}
