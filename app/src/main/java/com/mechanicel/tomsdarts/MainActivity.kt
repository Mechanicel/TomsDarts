package com.mechanicel.tomsdarts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.ui.game.GameScreen
import com.mechanicel.tomsdarts.ui.profile.ProfileScreen
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_DOUBLE_OUT
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_LEGS_BEST_OF
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_SETS_BEST_OF
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_START_SCORE
import com.mechanicel.tomsdarts.ui.setup.SetupScreen
import com.mechanicel.tomsdarts.ui.setup.bestOfToWin
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

private const val SCREEN_PROFILE = "profile"
private const val SCREEN_SETUP = "setup"
private const val SCREEN_GAME = "game"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TomsDartsTheme {
                // Einfacher State-Switch Profil -> Setup -> Spiel ohne
                // navigation-compose.
                var screen by rememberSaveable { mutableStateOf(SCREEN_PROFILE) }
                // Teilnehmer-IDs des laufenden Matches; LongArray ist direkt
                // Bundle-fae­hig und uebersteht damit Konfigurationswechsel.
                var playerIds by rememberSaveable { mutableStateOf(longArrayOf()) }
                // Im Setup gewaehlter Spielmodus; uebersteht Konfigurationswechsel.
                var modeKey by rememberSaveable { mutableStateOf(GameModeCatalog.DEFAULT) }
                // Im Setup gewaehlter Startpunkt; uebersteht Konfigurationswechsel.
                var startScore by rememberSaveable { mutableIntStateOf(DEFAULT_START_SCORE) }
                // Im Setup gewaehltes Double-Out; uebersteht Konfigurationswechsel.
                var doubleOut by rememberSaveable { mutableStateOf(DEFAULT_DOUBLE_OUT) }
                // Im Setup gewaehlte Gewinnschwellen (first to N), aus "Best of X"
                // umgerechnet; Initialwerte spiegeln das heutige Verhalten
                // (Best of 3 Legs -> 2, Best of 1 Set -> 1).
                var legsToWin by rememberSaveable {
                    mutableIntStateOf(bestOfToWin(DEFAULT_LEGS_BEST_OF))
                }
                var setsToWin by rememberSaveable {
                    mutableIntStateOf(bestOfToWin(DEFAULT_SETS_BEST_OF))
                }
                when (screen) {
                    SCREEN_GAME -> GameScreen(
                        modeKey = modeKey,
                        playerIds = playerIds.toList(),
                        startScore = startScore,
                        doubleOut = doubleOut,
                        legsToWin = legsToWin,
                        setsToWin = setsToWin,
                        onExit = { screen = SCREEN_PROFILE },
                    )
                    SCREEN_SETUP -> SetupScreen(
                        playerIds = playerIds.toList(),
                        onConfirm = { editedIds, mode, score, dOut, legs, sets ->
                            // Die im Setup final sortierte/reduzierte Teilnehmer-
                            // liste geht ins Match (Reihenfolge ist relevant).
                            playerIds = editedIds.toLongArray()
                            modeKey = mode
                            startScore = score
                            doubleOut = dOut
                            legsToWin = legs
                            setsToWin = sets
                            screen = SCREEN_GAME
                        },
                        onCancel = { screen = SCREEN_PROFILE },
                    )
                    else -> ProfileScreen(
                        onStartMatch = { ids ->
                            playerIds = ids.toLongArray()
                            screen = SCREEN_SETUP
                        },
                    )
                }
            }
        }
    }
}
