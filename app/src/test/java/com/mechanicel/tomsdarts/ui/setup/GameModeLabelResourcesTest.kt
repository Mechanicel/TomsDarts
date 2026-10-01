package com.mechanicel.tomsdarts.ui.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests der Zuordnung Modus-Kennung -> Anzeigename ([gameModeLabelResIdOrNull])
 * und der dahinterliegenden `mode_label_*`-Ressourcen. Laeuft host-seitig unter
 * Robolectric (SDK 34 gepinnt), da die Labels echte String-Ressourcen sind und
 * ueber einen [Context] aufgeloest werden.
 *
 * Kernzweck ist der Waechter gegen den vergessenen Eintrag: kommt ein neuer Modus
 * in [GameModeCatalog.entries] dazu, ohne dass ein `mode_label_*`-String und der
 * passende `when`-Zweig existieren, faellt der erste Test - statt dass im Setup
 * still die rohe Kennung (z.B. "AROUND_THE_CLOCK") auf der Karte landet.
 *
 * Die reine Compose-Darstellung der Karten (Umbruch, Auswahl-Optik) ist mangels
 * Instrumentation/Geraet nicht host-testbar; hier wird ausschliesslich die
 * zugrunde liegende Ressourcen-Aufloesung geprueft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameModeLabelResourcesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun jederKatalogEintragHatEineLabelRessource() {
        // Waechter: jeder auswaehlbare Modus braucht einen Anzeigenamen.
        GameModeCatalog.entries.forEach { mode ->
            assertNotNull(
                "Kein mode_label_* fuer ${mode.key}",
                gameModeLabelResIdOrNull(mode.key),
            )
        }
    }

    @Test
    fun jedesLabelIstNichtLeerUndKeinRoherKey() {
        // Ein Label darf nie leer sein und nie wie eine Kennung aussehen
        // (Kennungen sind SCREAMING_SNAKE_CASE, tragen also Unterstriche).
        // Bewusst KEIN Vergleich "Label != Kennung": bei X01 sind beide
        // identisch - und das ist korrekt so.
        GameModeCatalog.entries.forEach { mode ->
            val resId = requireNotNull(gameModeLabelResIdOrNull(mode.key)) {
                "Kein mode_label_* fuer ${mode.key}"
            }
            val label = context.getString(resId)
            assertTrue("Leeres Label fuer ${mode.key}", label.isNotBlank())
            assertFalse("Roher Key als Label fuer ${mode.key}: $label", label.contains("_"))
        }
    }

    @Test
    fun labelsSindUeberAlleModiEindeutig() {
        // Zwei Modi mit demselben Anzeigenamen waeren in der Auswahl (und in der
        // TalkBack-Ansage "Spielmodus X") nicht unterscheidbar.
        val labels = GameModeCatalog.entries.map { mode ->
            context.getString(requireNotNull(gameModeLabelResIdOrNull(mode.key)))
        }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun unbekannteKennungLiefertNull() {
        // Fallback-Vertrag: unbekannt -> null, die UI zeigt dann die rohe
        // Kennung statt eines leeren Textes.
        assertNull(gameModeLabelResIdOrNull("GIBT_ES_NICHT"))
    }
}
