package com.mechanicel.tomsdarts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mechanicel.tomsdarts.ui.profile.ProfileScreen
import com.mechanicel.tomsdarts.ui.theme.TomsDartsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TomsDartsTheme {
                ProfileScreen()
            }
        }
    }
}
