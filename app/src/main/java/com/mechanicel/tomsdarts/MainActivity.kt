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
import com.mechanicel.tomsdarts.ui.game.GameScreen
import com.mechanicel.tomsdarts.ui.profile.ProfileScreen
import com.mechanicel.tomsdarts.ui.setup.DEFAULT_START_SCORE
import com.mechanicel.tomsdarts.ui.setup.SetupScreen
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
                // Im Setup gewaehlter Startpunkt; uebersteht Konfigurationswechsel.
                var startScore by rememberSaveable { mutableIntStateOf(DEFAULT_START_SCORE) }
                when (screen) {
                    SCREEN_GAME -> GameScreen(
                        playerIds = playerIds.toList(),
                        startScore = startScore,
                        onExit = { screen = SCREEN_PROFILE },
                    )
                    SCREEN_SETUP -> SetupScreen(
                        playerIds = playerIds.toList(),
                        onConfirm = { _, score ->
                            startScore = score
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
