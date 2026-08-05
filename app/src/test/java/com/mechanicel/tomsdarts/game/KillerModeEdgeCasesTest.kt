package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung fuer [KillerMode]/[KillerState], ergaenzend zu den
 * Happy-Path-Faellen in [KillerModeTest]. Schwerpunkt: Grenzwerte und
 * IST-Verhalten-Dokumentation, die im Basis-Test-Gate nicht abgedeckt sind -
 * [KillerState.numberFor] ausserhalb des in der App genutzten Sitzplatz-Bereichs
 * (0..19) sowie die defensive Kappung von [KillerState.livesOf]. Reines JUnit,
 * kein Robolectric.
 */
class KillerModeEdgeCasesTest {

    private val mode = KillerMode()
    private val config = GameConfig(killerSeed = 13L)

    // --- numberFor: Sitzplatz-Index ausserhalb 0..19 (IST-Verhalten) -----------

    @Test
    fun numberFor_indexGenauZwanzig_wickeltZyklischAufIndexNullZurueck() {
        // Dokumentiertes IST-Verhalten (siehe KDoc von numberFor): Sitzplaetze
        // ausserhalb 0..19 werden per .mod(20) zyklisch abgebildet, statt eine
        // Exception zu werfen. Index 20 landet damit exakt auf derselben Zahl wie
        // Index 0 - eine echte KOLLISION, keine neue eindeutige Zahl.
        val seed = 4711L
        assertEquals(KillerState.numberFor(seed, 0), KillerState.numberFor(seed, 20))
        assertEquals(KillerState.numberFor(seed, 1), KillerState.numberFor(seed, 21))
    }

    @Test
    fun numberFor_negativerIndex_liefertUeberDenPositivenModEinGueltigesSegment() {
        // Kotlins Int.mod() (anders als %) liefert fuer negative Zahlen ein
        // NICHT-negatives Ergebnis: -1.mod(20) == 19 -> letzter Eintrag der
        // Mischung, kein Crash und kein negativer Index-Zugriff.
        val seed = 4711L
        assertEquals(KillerState.numberFor(seed, 19), KillerState.numberFor(seed, -1))
        val result = KillerState.numberFor(seed, -1)
        assertTrue("liegt weiterhin in 1..20", result in 1..20)
    }

    @Test
    fun numberFor_mehrAlsZwanzigSitzplaetze_verliertDieEindeutigkeit() {
        // Regression/Dokumentation: der Vertrag "eindeutig 1..20" gilt nur fuer
        // hoechstens 20 Sitzplaetze (0..19). Mit einem 21. Spieler kollidiert die
        // Zahl zwangslaeufig mit einem bereits vergebenen Sitzplatz, weil die
        // Permutation nur 20 Elemente hat. Kein Bug im Sinne eines Crashes, aber
        // ein Produkt-relevanter Grenzfall (siehe Testbericht): die MatchEngine
        // selbst begrenzt die Spieleranzahl nicht.
        val seed = 4711L
        val numbers = (0..20).map { KillerState.numberFor(seed, it) }
        assertEquals("21 Sitzplaetze, aber nur 20 moegliche Zahlen -> Duplikat", 20, numbers.toSet().size)
    }

    @Test
    fun numberFor_innerhalbDesGueltigenBereichs_bleibtEindeutigFuerBisZuZwanzigSitzplaetze() {
        // Positiver Gegenpol: fuer den in der App tatsaechlich erreichbaren
        // Bereich (0..19) bleibt die Zuweisung eine echte Permutation.
        val seed = 4711L
        val numbers = (0..19).map { KillerState.numberFor(seed, it) }
        assertEquals(20, numbers.toSet().size)
        assertEquals((1..20).toSet(), numbers.toSet())
    }

    // --- livesOf: defensive Kappung ----------------------------------------------

    @Test
    fun livesOf_hitsOnSummeUeberDenLebenspunkten_coerctAufNullStattNegativ() {
        // Ueber applyDart ist eine hitsOn-Summe > LIVES fuer eine Zahl fachlich
        // nicht erreichbar (die Kappung in KillerMode.nextState verhindert
        // weiteres Wachstum nach der Eliminierung). livesOf() selbst bleibt aber
        // defensiv: direkt konstruierte Zustaende mit ueberhoehter hitsOn-Summe
        // (z.B. aus einer zukuenftigen Persistenz-Migration) duerfen NIE negative
        // Leben liefern.
        val victimNumber = 7
        val attackerA = KillerState(number = 1, hitsOn = mapOf(victimNumber to 5))
        val attackerB = KillerState(number = 2, hitsOn = mapOf(victimNumber to 5))

        assertEquals(0, KillerState.livesOf(victimNumber, listOf(attackerA, attackerB)))
    }

    @Test
    fun livesOf_ohneGegner_liefertVolleLeben() {
        assertEquals(KillerState.LIVES, KillerState.livesOf(5, emptyList()))
    }

    @Test
    fun livesOf_negativeHitsOnKonstruiert_coerctEbenfallsAufMaximal() {
        // Ebenso defensiv in die andere Richtung: eine (fachlich nicht
        // erreichbare) negative hitsOn-Zahl darf die Leben nicht ueber LIVES
        // hinaus aufblasen.
        val attacker = KillerState(number = 1, hitsOn = mapOf(9 to -10))
        assertEquals(KillerState.LIVES, KillerState.livesOf(9, listOf(attacker)))
    }

    // --- initialState ohne Index (Interface-Default-Fallback) -------------------

    @Test
    fun initialState_ohneIndex_liefertImmerDieZahlDesErstenSitzplatzes() {
        // KDoc-Vertrag: Aufrufer, die die index-lose Variante nutzen, bekommen
        // bewusst fuer ALLE Spieler dieselbe Zahl (die des Sitzplatzes 0). Die
        // MatchEngine nutzt diese Variante nie, aber der Fallback muss stabil sein.
        val first = mode.initialState(config)
        val second = mode.initialState(config)
        assertEquals(mode.initialState(config, 0).number, first.number)
        assertEquals(first, second)
    }

    @Test
    fun initialState_mitUndOhneIndex_koennenUnterschiedlicheZahlenLiefern() {
        // Gegenpol: Sitzplatz 1 bekommt (bei den meisten Seeds) eine ANDERE Zahl
        // als der index-lose Fallback (== Sitzplatz 0) - Beleg, dass der Index
        // tatsaechlich etwas bewirkt.
        val withoutIndex = mode.initialState(config)
        val seatOne = mode.initialState(config, 1)
        assertNotEquals(withoutIndex.number, seatOne.number)
    }
}
