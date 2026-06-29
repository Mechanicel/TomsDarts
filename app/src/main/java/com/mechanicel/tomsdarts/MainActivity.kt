package com.mechanicel.tomsdarts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mechanicel.tomsdarts.ui.game.GameScreen
import com.mechanicel.tomsdarts.ui.profile.ProfileScreen
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

private const val SCREEN_PROFILE = "profile"
private const val SCREEN_GAME = "game"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TomsDartsTheme {
                // Einfacher State-Switch Profil <-> Spiel ohne navigation-compose.
                var screen by rememberSaveable { mutableStateOf(SCREEN_PROFILE) }
                var playerId by rememberSaveable { mutableLongStateOf(-1L) }
                when (screen) {
                    SCREEN_GAME -> GameScreen(
                        playerId = playerId,
                        onExit = { screen = SCREEN_PROFILE },
                    )
                    else -> ProfileScreen(
                        onPlayClick = { id ->
                            playerId = id
                            screen = SCREEN_GAME
                        },
                    )
                }
            }
        }
    }
}
