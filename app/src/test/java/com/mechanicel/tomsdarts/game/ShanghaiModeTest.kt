package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Happy-Path-Basistests fuer [ShanghaiMode]. Reines JUnit, kein Robolectric.
 *
 * Deckt initialState, die Wertung auf der Zielzahl (Single/Double/Triple), die
 * Null-Wertung von Fremdsegmenten/Miss/Bull, den Shanghai-Sofortsieg, den
 * Runden-/Zielfortschritt inkl. Verlaengerung, das rundenbasierte Leg-Ende per
 * Punktvergleich ([DartOutcome.legEnded]) sowie [GameMode.legScore] ab. Das
 * systematische Abhaerten uebernimmt der tester-Workflow.
 */
class ShanghaiModeTest {

    private val mode = ShanghaiMode()
    private val config = GameConfig()

    /** Kurzschreibweise fuer einen Spielerzustand mitten im Leg. */
    private fun state(dartsThrown: Int, points: Int, hits: Set<Int> = emptySet()): ShanghaiState =
        ShanghaiState(dartsThrown = dartsThrown, points = points, visitHits = hits)

    @Test
    fun keyUndDisplayName_sindGesetzt() {
        assertEquals("SHANGHAI", mode.key)
        assertEquals("Shanghai", mode.displayName)
    }

    @Test
    fun initialState_startetLeerInRundeEinsMitZielEins() {
        val start = mode.initialState(config)
        assertEquals(ShanghaiState(dartsThrown = 0, points = 0, visitHits = emptySet()), start)
        assertEquals(1, start.round)
        assertEquals(1, start.target)
        assertEquals(0, start.completedRounds)
    }

    @Test
    fun zieltreffer_punktetZielzahlMalMultiplier() {
        // Runde 1 -> Ziel 1: Single 1, Double 2, Triple 3 Punkte.
        val start = mode.initialState(config)
        assertEquals(1, mode.applyDart(start, Dart.single(1), config).scored)
        assertEquals(2, mode.applyDart(start, Dart.double(1), config).scored)
        assertEquals(3, mode.applyDart(start, Dart.triple(1), config).scored)

        // Runde 5 (12 Darts geworfen) -> Ziel 5: Triple bringt 15 Punkte.
        val round5 = state(dartsThrown = 12, points = 40)
        val outcome = mode.applyDart(round5, Dart.triple(5), config)
        assertEquals(15, outcome.scored)
        assertEquals(55, outcome.newState.points)
    }

    @Test
    fun fremdesSegment_missUndBull_bringenKeinePunkte_zaehlenAberAlsDart() {
        val start = mode.initialState(config)
        // Ziel ist die 1; alles andere wertet 0, verbraucht aber einen Dart.
        listOf(Dart.triple(20), Dart.single(7), Dart.miss(), Dart.bull(), Dart.doubleBull())
            .forEach { dart ->
                val o = mode.applyDart(start, dart, config)
                assertEquals("$dart darf nicht punkten", 0, o.scored)
                assertEquals(0, o.newState.points)
                assertEquals(1, o.newState.dartsThrown)
                assertTrue("$dart hinterlaesst keinen Treffer", o.newState.visitHits.isEmpty())
                assertFalse(o.bust)
                assertFalse(o.legWon)
                assertFalse(o.legEnded)
            }
    }

    @Test
    fun visitHits_sammelnDieTrefferDerLaufendenAufnahme() {
        // Dart 1: Single der 1 -> Trefferspur {1}.
        val first = mode.applyDart(mode.initialState(config), Dart.single(1), config)
        assertEquals(setOf(1), first.newState.visitHits)

        // Dart 2: Fremdsegment -> Trefferspur unveraendert.
        val second = mode.applyDart(first.newState, Dart.single(20), config)
        assertEquals(setOf(1), second.newState.visitHits)
    }

    @Test
    fun nachVollerAufnahme_istDieTrefferspurLeer_undDieNaechsteRundeAktiv() {
        // State-Vertrag: nach dem 3. Dart beschreibt der Zustand bereits die
        // naechste Runde - alte Treffer duerfen dort nicht mehr auftauchen.
        var current = mode.initialState(config)
        current = mode.applyDart(current, Dart.single(1), config).newState
        current = mode.applyDart(current, Dart.double(1), config).newState
        assertEquals(setOf(1, 2), current.visitHits)

        val third = mode.applyDart(current, Dart.single(20), config)
        assertTrue("Trefferspur wird zur neuen Runde geleert", third.newState.visitHits.isEmpty())
        assertEquals(2, third.newState.round)
        assertEquals(2, third.newState.target)
        assertEquals(1, third.newState.completedRounds)
    }

    @Test
    fun shanghai_singleDoubleTriple_inEinerAufnahme_gewinntSofort() {
        var current = mode.initialState(config)
        val first = mode.applyDart(current, Dart.single(1), config)
        assertFalse("Ein Dart ist noch kein Shanghai", first.legWon)
        current = first.newState

        val second = mode.applyDart(current, Dart.double(1), config)
        assertFalse("Zwei Darts sind noch kein Shanghai", second.legWon)
        current = second.newState

        val third = mode.applyDart(current, Dart.triple(1), config)
        assertTrue("S + D + T der Zielzahl gewinnen sofort", third.legWon)
        // Vertrag: legWon schliesst bust und legEnded aus.
        assertFalse(third.bust)
        assertFalse(third.legEnded)
        assertEquals(3, third.scored)
        assertEquals(6, third.newState.points)
    }

    @Test
    fun shanghai_reihenfolgeIstEgal() {
        // Dieselbe Aufnahme in drei Reihenfolgen - immer ein Sofort-Sieg.
        val orders = listOf(
            listOf(Dart.triple(1), Dart.single(1), Dart.double(1)),
            listOf(Dart.double(1), Dart.triple(1), Dart.single(1)),
            listOf(Dart.triple(1), Dart.double(1), Dart.single(1)),
        )
        orders.forEach { darts ->
            var current = mode.initialState(config)
            val outcomes = darts.map { dart ->
                val outcome = mode.applyDart(current, dart, config)
                current = outcome.newState
                outcome
            }
            assertFalse("Erst der 3. Dart vollendet das Shanghai", outcomes.first().legWon)
            assertTrue("Reihenfolge $darts ist ein Shanghai", outcomes.last().legWon)
            assertEquals(6, current.points)
        }
    }

    @Test
    fun fremdDartInDerAufnahme_machtShanghaiUnmoeglich() {
        // Fuer S + D + T braucht es alle drei Darts der Aufnahme; ein Fehlwurf
        // dazwischen schliesst den Sofort-Sieg in dieser Aufnahme aus.
        var current = mode.initialState(config)
        current = mode.applyDart(current, Dart.single(1), config).newState
        current = mode.applyDart(current, Dart.miss(), config).newState

        val third = mode.applyDart(current, Dart.triple(1), config)
        assertFalse("Ohne Double kein Shanghai", third.legWon)
        assertEquals(4, third.newState.points)
        // Aufnahme voll -> Trefferspur geleert, Runde 2 aktiv.
        assertTrue(third.newState.visitHits.isEmpty())
        assertEquals(2, third.newState.round)
    }

    @Test
    fun rundenFortschritt_zielLaeuftVonEinsBisSieben() {
        // Ein Gegner, der noch in Runde 1 steht, verhindert jedes Rundenende -
        // hier interessiert allein der Ziel-Fortschritt des Werfers.
        val laggingOpponent = listOf(ShanghaiState.initial())
        var current = mode.initialState(config)
        for (round in 1..ShanghaiState.ROUNDS) {
            assertEquals("Runde $round", round, current.round)
            assertEquals("Ziel in Runde $round", round, current.target)
            repeat(ShanghaiState.DARTS_PER_ROUND) {
                val o = mode.applyDart(current, Dart.single(round), config, laggingOpponent)
                assertEquals("Treffer auf $round wertet $round", round, o.scored)
                assertFalse(o.bust)
                assertFalse(o.legWon)
                assertFalse("Gegner haengt zurueck -> kein Rundenende", o.legEnded)
                current = o.newState
            }
        }
        // 3 * (1+2+...+7) = 84 Punkte nach sieben vollen Runden.
        assertEquals(84, current.points)
        assertEquals(21, current.dartsThrown)
        assertEquals(ShanghaiState.ROUNDS, current.completedRounds)
    }

    @Test
    fun verlaengerung_zielLaeuftZyklischWeiter() {
        // 21 Darts == sieben volle Runden -> Runde 8 zielt wieder auf die 1.
        val round8 = state(dartsThrown = 21, points = 84)
        assertEquals(8, round8.round)
        assertEquals(1, round8.target)
        assertEquals(3, mode.applyDart(round8, Dart.triple(1), config).scored)

        // Runde 9 zielt auf die 2, Runde 14 auf die 7.
        assertEquals(2, state(dartsThrown = 24, points = 84).target)
        assertEquals(7, state(dartsThrown = 39, points = 84).target)
    }

    @Test
    fun legEnded_beimLetztenWerferDerSiebtenRunde_mitEindeutigemFuehrenden() {
        // Werfer beendet mit dem 21. Dart seine 7. Runde; der Gegner ist bereits
        // durch -> Leg entschieden, der Werfer fuehrt.
        val opponent = state(dartsThrown = 21, points = 40)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 50, hits = setOf(1)),
            Dart.single(7),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legEnded)
        // Vertrag: legEnded schliesst bust und legWon aus.
        assertFalse(outcome.bust)
        assertFalse(outcome.legWon)
        assertEquals(57, outcome.newState.points)
    }

    @Test
    fun legEnded_auchWennDerWerferNichtFuehrt() {
        // Der Werfer schliesst die Runde ab, liegt aber hinten: das Leg endet
        // trotzdem - den Gewinner kuert die Engine ueber legScore.
        val opponent = state(dartsThrown = 21, points = 90)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 20),
            Dart.single(7),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legEnded)
        assertFalse(outcome.legWon)
        assertEquals(27, outcome.newState.points)
        assertTrue(
            "Der Gegner hat den hoeheren Rangwert",
            mode.legScore(opponent) > mode.legScore(outcome.newState),
        )
    }

    @Test
    fun keinLegEnded_beiGleichstandAnDerSpitze_suddenDeath() {
        // 40 : 40 nach Runde 7 -> kein Rundenende, es wird weitergespielt.
        val opponent = state(dartsThrown = 21, points = 40)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 33),
            Dart.single(7),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(40, outcome.newState.points)
        assertFalse("Gleichstand entscheidet nicht", outcome.legEnded)
        assertFalse(outcome.legWon)
        // Der Werfer ist bereits in der Verlaengerung (Runde 8).
        assertEquals(8, outcome.newState.round)
    }

    @Test
    fun keinLegEnded_vorDerSiebtenRunde() {
        // Beide Spieler beenden ihre 6. Runde - zu frueh fuer den Punktvergleich.
        val opponent = state(dartsThrown = 18, points = 20)
        val outcome = mode.applyDart(
            state(dartsThrown = 17, points = 50),
            Dart.single(6),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(6, outcome.newState.completedRounds)
        assertFalse(outcome.legEnded)
    }

    @Test
    fun keinLegEnded_wennEinGegnerDieRundeNochNichtBeendetHat() {
        // Der Werfer ist als Erster durch Runde 7; der Gegner wirft noch.
        val opponent = state(dartsThrown = 18, points = 90)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 50),
            Dart.single(7),
            config,
            opponents = listOf(opponent),
        )
        assertFalse("Der Werfer ist nicht der Letzte der Runde", outcome.legEnded)
        assertFalse(outcome.legWon)
    }

    @Test
    fun shanghai_hatVorrangVorDemRundenende() {
        // Letzter Werfer der 7. Runde, der Gegner fuehrt klar - doch der Werfer
        // vollendet ein Shanghai: legWon (Werfer-Sieg), NICHT legEnded.
        val opponent = state(dartsThrown = 21, points = 200)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 50, hits = setOf(1, 2)),
            Dart.triple(7),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legWon)
        assertFalse(outcome.legEnded)
        assertFalse(outcome.bust)
        assertEquals(21, outcome.scored)
    }

    @Test
    fun legScore_entsprichtDemPunktestand() {
        assertEquals(0, mode.legScore(ShanghaiState.initial()))
        assertEquals(84, mode.legScore(state(dartsThrown = 21, points = 84)))
    }

    @Test
    fun keinBust_undHoechstensEinesDerFlags_ueberEinGanzesLeg() {
        // Zwei Spieler ueber sieben Runden: nie Bust, und die Flag-Invariante
        // (bust/legWon/legEnded paarweise exklusiv) haelt in jedem Schritt.
        var thrower = mode.initialState(config)
        var opponent = mode.initialState(config)
        for (round in 1..ShanghaiState.ROUNDS) {
            repeat(ShanghaiState.DARTS_PER_ROUND) {
                val o = mode.applyDart(thrower, Dart.single(round), config, listOf(opponent))
                assertFalse("Shanghai bustet nie", o.bust)
                assertFalse(o.bust && o.legWon)
                assertFalse(o.bust && o.legEnded)
                assertFalse(o.legWon && o.legEnded)
                thrower = o.newState
            }
            repeat(ShanghaiState.DARTS_PER_ROUND) {
                val o = mode.applyDart(opponent, Dart.miss(), config, listOf(thrower))
                assertFalse(o.bust)
                assertFalse(o.legWon && o.legEnded)
                opponent = o.newState
            }
        }
        assertEquals(84, thrower.points)
        assertEquals(0, opponent.points)
    }
}
