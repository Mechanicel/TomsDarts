package com.mechanicel.tomsdarts.ui.delight

import com.mechanicel.tomsdarts.delight.DelightAnimation
import com.mechanicel.tomsdarts.delight.DelightTextKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests der puren Ablauf-Logik des Spiel-Bildschirms rund um Feiern:
 * Speichern/Wiederherstellen ueber Rotation und Prozess-Tod
 * ([toSaveable]/[activeDelightFromSaveable]) und die Entscheidung, wie ein
 * ausstehendes Event behandelt wird ([planDelightIntake], ADR-0038/0039).
 */
class DelightSessionTest {

    private val active = ActiveDelight(
        id = 3L,
        animation = DelightAnimation.SPIN,
        textKey = DelightTextKeys.WASHING_MACHINE,
        playerId = 12L,
        startedAtElapsed = 10_000L,
        totalMillis = 2200L,
        sessionToken = TOKEN,
    )

    private companion object {
        const val TOKEN = "vm-1"
    }

    // --- Speichern / Wiederherstellen ---

    @Test
    fun laufendeFeierUeberstehtDasSpeichern() {
        assertEquals(active, activeDelightFromSaveable(active.toSaveable(), now = 10_500L, sessionToken = TOKEN))
    }

    @Test
    fun gespeicherteFormEnthaeltNurBundleTauglicheWerte() {
        active.copy(playerId = null).toSaveable().forEach { value ->
            assertTrue("$value", value is Long || value is String)
        }
    }

    @Test
    fun unbekannterWerferBleibtUnbekannt() {
        val restored = activeDelightFromSaveable(active.copy(playerId = null).toSaveable(), now = 10_500L, sessionToken = TOKEN)
        assertNull(restored!!.playerId)
    }

    @Test
    fun abgelaufeneFeierWirdNichtWiederhergestellt() {
        assertNull(activeDelightFromSaveable(active.toSaveable(), now = 12_200L, sessionToken = TOKEN))
        assertNull(activeDelightFromSaveable(active.toSaveable(), now = 99_000L, sessionToken = TOKEN))
    }

    @Test
    fun nachGeraeteNeustartWirdNichtsWiederhergestellt() {
        assertNull(activeDelightFromSaveable(active.toSaveable(), now = 5L, sessionToken = TOKEN))
    }

    @Test
    fun leereOderKaputteDatenLiefernNull() {
        assertNull(activeDelightFromSaveable(emptyList<Any>(), now = 10_500L, sessionToken = TOKEN))
        assertNull(activeDelightFromSaveable(listOf("x", 1, 2, 3, 4, 5, 6), now = 10_500L, sessionToken = TOKEN))
        // Altes Format (ohne Sitzungs-Kennung) wird verworfen.
        assertNull(activeDelightFromSaveable(active.toSaveable().dropLast(1), now = 10_500L, sessionToken = TOKEN))
    }

    @Test
    fun unbekannterAnimationsNameFaelltAufGenericZurueck() {
        val saved = active.toSaveable().toMutableList().apply { this[1] = "GIBT_ES_NICHT" }
        assertEquals(DelightAnimation.GENERIC, activeDelightFromSaveable(saved, now = 10_500L, sessionToken = TOKEN)!!.animation)
    }

    @Test
    fun feierEinesAnderenViewModelsWirdNichtWiederhergestellt() {
        // Prozess-Tod: neues ViewModel mit neuer Kennung, IDs beginnen wieder bei 1.
        assertNull(activeDelightFromSaveable(active.toSaveable(), now = 10_500L, sessionToken = "anderes-vm"))
    }

    @Test
    fun nachProzessTodWirdDieErsteFeierDesNeuenViewModelsAngezeigt() {
        // Gespeichert war Feier ID 1 des alten ViewModels (noch nicht abgelaufen).
        val saved = active.copy(id = 1L).toSaveable()
        // Das neue ViewModel hat eine neue Kennung und noch nichts gezeigt.
        val restored = activeDelightFromSaveable(saved, now = 10_500L, sessionToken = "neues-vm")
        assertNull(restored)
        // Sein erstes Event traegt wieder ID 1 und muss angezeigt werden - ohne
        // die alte ID 1 zu quittieren (sie gehoert nicht zum neuen ViewModel).
        val plan = planDelightIntake(eventId = 1L, enabled = true, lastShownId = NO_DELIGHT_ID, activeId = restored?.id)
        assertTrue(plan.show)
        assertTrue(plan.acknowledgeIds.isEmpty())
    }

    // --- Event-Annahme ---

    @Test
    fun ohneEventPassiertNichts() {
        val plan = planDelightIntake(eventId = null, enabled = true, lastShownId = NO_DELIGHT_ID, activeId = null)
        assertFalse(plan.show)
        assertTrue(plan.acknowledgeIds.isEmpty())
    }

    @Test
    fun neuesEventWirdAngezeigt() {
        val plan = planDelightIntake(eventId = 1L, enabled = true, lastShownId = NO_DELIGHT_ID, activeId = null)
        assertTrue(plan.show)
        assertTrue(plan.acknowledgeIds.isEmpty())
    }

    @Test
    fun neuesEventErsetztLaufendeFeierUndQuittiertSie() {
        val plan = planDelightIntake(eventId = 5L, enabled = true, lastShownId = 4L, activeId = 4L)
        assertTrue(plan.show)
        assertEquals(listOf(4L), plan.acknowledgeIds)
    }

    @Test
    fun nachRotationNochLaufendeFeierWirdWederNeuGezeigtNochQuittiert() {
        // Ihr eigener Ablauf-Timer quittiert spaeter.
        val plan = planDelightIntake(eventId = 4L, enabled = true, lastShownId = 4L, activeId = 4L)
        assertFalse(plan.show)
        assertTrue(plan.acknowledgeIds.isEmpty())
    }

    @Test
    fun uebersprungenesEventWirdTrotzdemQuittiert() {
        // Nach Rotation abgelaufen: nicht erneut zeigen, aber onDelightDismissed
        // rufen, sonst haelt die Kontrollpause bis zum Sicherheitsnetz (ADR-0038).
        val plan = planDelightIntake(eventId = 4L, enabled = true, lastShownId = 4L, activeId = null)
        assertFalse(plan.show)
        assertEquals(listOf(4L), plan.acknowledgeIds)
    }

    @Test
    fun dedupeVergleichtPerGleichheitNichtPerGroesser() {
        // Neues ViewModel (neues Spiel) beginnt wieder bei 1: muss gezeigt werden.
        val plan = planDelightIntake(eventId = 1L, enabled = true, lastShownId = 7L, activeId = null)
        assertTrue(plan.show)
    }

    @Test
    fun abgeschaltetVerwirftUndQuittiertSofort() {
        val plan = planDelightIntake(eventId = 2L, enabled = false, lastShownId = NO_DELIGHT_ID, activeId = null)
        assertFalse(plan.show)
        assertEquals(listOf(2L), plan.acknowledgeIds)
    }

    @Test
    fun abschaltenBeendetAuchDieLaufendeFeier() {
        val plan = planDelightIntake(eventId = 2L, enabled = false, lastShownId = 2L, activeId = 2L)
        assertFalse(plan.show)
        assertEquals(listOf(2L), plan.acknowledgeIds)

        val other = planDelightIntake(eventId = null, enabled = false, lastShownId = 1L, activeId = 1L)
        assertEquals(listOf(1L), other.acknowledgeIds)
    }
}
