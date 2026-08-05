package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basistests (Happy Path) fuer den [KillerMode]: die deterministische
 * Zahlen-Zuweisung aus dem eingefrorenen Seed, die beiden Wurf-Phasen
 * (Killer werden / Leben nehmen), die abgeleiteten Leben samt Eliminierung sowie
 * den Leg-Gewinn. Reines JUnit, kein Robolectric (der Modus hat keinen
 * Android-/Room-Bezug).
 *
 * Konvention der Tests: Der Werfer ist immer `state`, die Mitspieler stehen in
 * `opponents` - identisch zum Vertrag von [GameMode.applyDart].
 */
class KillerModeTest {

    private val mode = KillerMode()
    private val config = GameConfig(killerSeed = 4711L)

    // Feste Zahlen fuer die Regel-Tests; unabhaengig von der Seed-Mischung,
    // damit die Regeln fuer sich lesbar bleiben.
    private val tom = KillerState(number = 5)
    private val anna = KillerState(number = 12)
    private val bjoern = KillerState(number = 18)

    @Test
    fun initialState_gleicherSeed_liefertImmerDieselbenZahlen() {
        // Kern der Replay-Sicherheit: der Startzustand wird bei Undo und
        // Leg-Wechsel neu erzeugt und muss dieselbe Zahl liefern.
        val first = (0..3).map { mode.initialState(config, it).number }
        val second = (0..3).map { mode.initialState(config, it).number }
        assertEquals(first, second)
    }

    @Test
    fun initialState_verschiedeneSitzplaetze_bekommenVerschiedeneZahlen() {
        val numbers = (0..3).map { mode.initialState(config, it).number }
        assertEquals("Zahlen sind eindeutig", numbers.size, numbers.toSet().size)
        assertTrue("Zahlen liegen in 1..20", numbers.all { it in 1..20 })
    }

    @Test
    fun initialState_andererSeed_liefertEineAndereZuweisung() {
        val a = (0..3).map { mode.initialState(GameConfig(killerSeed = 1L), it).number }
        val b = (0..3).map { mode.initialState(GameConfig(killerSeed = 2L), it).number }
        assertNotEquals(a, b)
    }

    @Test
    fun initialState_startetOhneKillerStatusUndOhneTreffer() {
        val state = mode.initialState(config, 0)
        assertFalse("zu Beginn kein Killer", state.isKiller)
        assertTrue("zu Beginn keine Treffer", state.hitsOn.isEmpty())
    }

    @Test
    fun applyDart_eigenesDouble_machtZumKiller_undZaehltAlsWirksam() {
        val outcome = mode.applyDart(tom, Dart.double(5), config, listOf(anna))

        assertTrue("Double der eigenen Zahl macht zum Killer", outcome.newState.isKiller)
        assertEquals(1, outcome.scored)
        assertFalse(outcome.legWon)
    }

    @Test
    fun applyDart_singleUndTripleDerEigenenZahl_sindWirkungslos() {
        val single = mode.applyDart(tom, Dart.single(5), config, listOf(anna))
        val triple = mode.applyDart(tom, Dart.triple(5), config, listOf(anna))

        assertEquals(tom, single.newState)
        assertEquals(0, single.scored)
        assertEquals(tom, triple.newState)
        assertEquals(0, triple.scored)
    }

    @Test
    fun applyDart_fremdesDoubleVorDerKillerWerdung_istWirkungslos() {
        // Ohne Killer-Status nimmt kein Treffer Leben.
        val outcome = mode.applyDart(tom, Dart.double(12), config, listOf(anna))

        assertEquals(tom, outcome.newState)
        assertEquals(0, outcome.scored)
    }

    @Test
    fun applyDart_alsKillerAufGegnerDouble_zaehltTrefferUndNimmtEinLeben() {
        val killer = tom.copy(isKiller = true)

        val outcome = mode.applyDart(killer, Dart.double(12), config, listOf(anna))

        assertEquals(1, outcome.newState.hitsOn[12])
        assertEquals(1, outcome.scored)
        // Anna hat aus Sicht des neuen Werfer-Zustands noch 2 Leben.
        assertEquals(
            KillerState.LIVES - 1,
            KillerState.livesOf(anna.number, listOf(outcome.newState)),
        )
        assertFalse("ein Treffer eliminiert noch nicht", mode.isEliminated(anna, listOf(outcome.newState)))
    }

    @Test
    fun applyDart_eigenesDoubleAlsKiller_istWirkungslos() {
        // Bewusst KEINE Selbst-Treffer-Variante in v1.
        val killer = tom.copy(isKiller = true)

        val outcome = mode.applyDart(killer, Dart.double(5), config, listOf(anna))

        assertEquals(killer, outcome.newState)
        assertEquals(0, outcome.scored)
    }

    @Test
    fun applyDart_doubleEinesAusgeschiedenenGegners_istWirkungslos() {
        // Anna ist bereits aus (3 Treffer auf ihrer 12); weitere Treffer werden
        // nicht mitgezaehlt, sonst wuerde die Kappung der Lebens-Formel verrutschen.
        val killer = tom.copy(isKiller = true, hitsOn = mapOf(12 to KillerState.LIVES))
        assertTrue(mode.isEliminated(anna, listOf(killer, bjoern)))

        val outcome = mode.applyDart(killer, Dart.double(12), config, listOf(anna, bjoern))

        assertEquals(killer, outcome.newState)
        assertEquals(0, outcome.scored)
        assertEquals(KillerState.LIVES, outcome.newState.hitsOn.getValue(12))
    }

    @Test
    fun dreiTreffer_eliminierenDenGegner() {
        var killer = tom.copy(isKiller = true)
        repeat(KillerState.LIVES) {
            killer = mode.applyDart(killer, Dart.double(12), config, listOf(anna, bjoern)).newState
        }

        assertEquals(0, KillerState.livesOf(anna.number, listOf(killer, bjoern)))
        assertTrue(mode.isEliminated(anna, listOf(killer, bjoern)))
        assertFalse("Bjoern lebt weiter", mode.isEliminated(bjoern, listOf(killer, anna)))
    }

    @Test
    fun legWon_erstWennDerLetzteLebendeGegnerAusscheidet() {
        // Anna ist bereits aus, Bjoern hat noch ein Leben (2 Treffer).
        var killer = tom.copy(
            isKiller = true,
            hitsOn = mapOf(12 to KillerState.LIVES, 18 to KillerState.LIVES - 1),
        )

        // Vorletzter Treffer auf Bjoern: noch kein Sieg.
        val before = mode.applyDart(
            tom.copy(isKiller = true, hitsOn = mapOf(12 to KillerState.LIVES, 18 to 1)),
            Dart.double(18),
            config,
            listOf(anna, bjoern),
        )
        assertFalse("Bjoern lebt noch -> kein Sieg", before.legWon)

        // Letzter Treffer: alle Mitspieler eliminiert -> Leg gewonnen.
        val outcome = mode.applyDart(killer, Dart.double(18), config, listOf(anna, bjoern))
        killer = outcome.newState
        assertTrue("alle Gegner aus -> legWon", outcome.legWon)
        assertEquals(1, outcome.scored)
        assertTrue(mode.isEliminated(bjoern, listOf(killer, anna)))
    }

    @Test
    fun applyDart_bustetNie_undMeldetNieLegEnded() {
        val killer = tom.copy(isKiller = true)
        val darts = listOf(
            Dart.miss(),
            Dart.bull(),
            Dart.doubleBull(),
            Dart.single(12),
            Dart.triple(12),
            Dart.double(12),
            Dart.double(5),
            Dart.double(20),
        )

        darts.forEach { dart ->
            listOf(tom, killer).forEach { state ->
                val outcome = mode.applyDart(state, dart, config, listOf(anna, bjoern))
                assertFalse("Killer bustet nie ($dart)", outcome.bust)
                assertFalse("Killer meldet nie legEnded ($dart)", outcome.legEnded)
            }
        }
    }

    @Test
    fun keyUndDisplayName_sindStabil() {
        assertEquals("KILLER", mode.key)
        assertEquals("Killer", mode.displayName)
    }
}
