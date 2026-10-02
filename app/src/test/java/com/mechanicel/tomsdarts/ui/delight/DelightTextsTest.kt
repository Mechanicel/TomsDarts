package com.mechanicel.tomsdarts.ui.delight

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.R
import com.mechanicel.tomsdarts.delight.DelightTextKeys
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
 * Tests der Zuordnung Text-Schluessel -> String-Ressourcen ([delightTextRes]).
 * Laeuft unter Robolectric (SDK 34 gepinnt), weil die Texte echte Ressourcen
 * sind. Waechter: ein neuer Schluessel in [DelightTextKeys.ALL] ohne Mapping
 * faellt hier auf, statt still den allgemeinen Text zu zeigen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DelightTextsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun jederSchluesselHatEinenEigenenText() {
        DelightTextKeys.ALL.forEach { key ->
            val res = assertNotNullRes(key)
            assertTrue("Leerer Titel fuer $key", context.getString(res.title).isNotBlank())
            res.subtitle?.let { assertTrue("Leerer Untertitel fuer $key", context.getString(it).isNotBlank()) }
        }
    }

    @Test
    fun titelSindEindeutig() {
        val titles = DelightTextKeys.ALL.map { context.getString(assertNotNullRes(it).title) }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun schluesselSindEindeutig() {
        assertEquals(DelightTextKeys.ALL.size, DelightTextKeys.ALL.toSet().size)
    }

    @Test
    fun unbekannterSchluesselFaelltAufAllgemeinenTextZurueck() {
        assertNull(delightTextResOrNull("gibt_es_nicht"))
        assertEquals(delightTextRes(DelightTextKeys.GENERIC), delightTextRes("gibt_es_nicht"))
        assertEquals("Starke Aufnahme!", context.getString(delightTextRes("").title))
    }

    @Test
    fun produktTexteStimmen() {
        assertEquals("180!", context.getString(delightTextRes(DelightTextKeys.ONE_EIGHTY).title))
        assertEquals("Waschmaschine!", context.getString(delightTextRes(DelightTextKeys.WASHING_MACHINE).title))
        assertEquals("Rentnerdreieck!", context.getString(delightTextRes(DelightTextKeys.RENTNERDREIECK).title))
        assertNull(delightTextRes(DelightTextKeys.GENERIC).subtitle)
    }

    @Test
    fun spielernamePraefixSetztNameVorDenUntertitel() {
        val text = context.getString(R.string.delight_player_prefix, "Tom", "Maximum. Mehr geht nicht.")
        assertEquals("Tom · Maximum. Mehr geht nicht.", text)
        assertFalse(context.getString(R.string.delight_dismiss_action).isBlank())
    }

    private fun assertNotNullRes(key: String): DelightTextRes {
        val res = delightTextResOrNull(key)
        assertNotNull("Kein Text-Mapping fuer $key", res)
        return res!!
    }
}
