package com.mechanicel.tomsdarts.ui.delight

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Ob der Nutzer Animationen systemweit abgeschaltet hat (Bedienungshilfen
 * "Animationen entfernen" bzw. Entwickleroptionen: Animator-Dauer-Skala 0).
 * Dann zeigen Feiern nur ihr statisches Endbild (ADR-0039).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReducedMotion(context.contentResolver) }
}

private fun isReducedMotion(contentResolver: ContentResolver): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
