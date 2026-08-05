package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Happy-Path-Basistests fuer [CountUpMode]. Reines JUnit, kein Robolectric.
 *
 * Deckt initialState, die ungefilterte Wertung jedes Darts (Single/Double/Triple,
 * Bull, Doppel-Bull, Miss), die Punkte-Akkumulation ueber Runden, den
 * Rundenfortschritt, das rundenbasierte Leg-Ende per Punktvergleich
 * ([DartOutcome.legEnded]) inkl. Sudden Death bei Gleichstand, [GameMode.legScore]
 * sowie die Flag-Invariante (nie bust, nie legWon) ab. Das systematische
 * Abhaerten uebernimmt der tester-Workflow.
 */
class CountUpModeTest {

    private val mode = CountUpMode()
    private val config = GameConfig()

    /** Kurzschreibweise fuer einen Spielerzustand mitten im Leg. */
    private fun state(dartsThrown: Int, points: Int): CountUpState =
        CountUpState(dartsThrown = dartsThrown, points = points)

    @Test
    fun keyUndDisplayName_sindGesetzt() {
        assertEquals("COUNT_UP", mode.key)
        assertEquals("Count Up", mode.displayName)
    }

    @Test
    fun initialState_startetLeerInRundeEins() {
        val start = mode.initialState(config)
        assertEquals(CountUpState(dartsThrown = 0, points = 0), start)
        assertEquals(1, start.round)
        assertEquals(0, start.completedRounds)
    }

    @Test
    fun jederDart_punktetSeinenEigenenWert() {
        val start = mode.initialState(config)
        // Segment mal Multiplikator - ohne Zielzahl-Filter.
        assertEquals(20, mode.applyDart(start, Dart.single(20), config).scored)
        assertEquals(40, mode.applyDart(start, Dart.double(20), config).scored)
        assertEquals(60, mode.applyDart(start, Dart.triple(20), config).scored)
        assertEquals(7, mode.applyDart(start, Dart.single(7), config).scored)
    }

    @Test
    fun bullUndDoppelBullUndMiss_werdenKorrektGewertet() {
        val start = mode.initialState(config)
        val bull = mode.applyDart(start, Dart.bull(), config)
        assertEquals(25, bull.scored)
        assertEquals(25, bull.newState.points)

        val doubleBull = mode.applyDart(start, Dart.doubleBull(), config)
        assertEquals(50, doubleBull.scored)
        assertEquals(50, doubleBull.newState.points)

        val miss = mode.applyDart(start, Dart.miss(), config)
        assertEquals("Ein Fehlwurf bringt 0 Punkte", 0, miss.scored)
        assertEquals(0, miss.newState.points)
        // Der Fehlwurf zaehlt trotzdem als geworfener Dart.
        assertEquals(1, miss.newState.dartsThrown)
    }

    @Test
    fun punkteAkkumulieren_ueberMehrereRunden() {
        // Drei volle Aufnahmen a 3 x T-20 == 3 x 180 == 540 Punkte.
        var current = mode.initialState(config)
        repeat(3 * CountUpState.DARTS_PER_ROUND) {
            current = mode.applyDart(current, Dart.triple(20), config).newState
        }
        assertEquals(540, current.points)
        assertEquals(9, current.dartsThrown)
        assertEquals(3, current.completedRounds)
        assertEquals(4, current.round)
    }

    @Test
    fun rundenFortschritt_folgtDenGeworfenenDarts() {
        // Ein Gegner, der noch in Runde 1 steht, verhindert jedes Rundenende -
        // hier interessiert allein der Rundenfortschritt des Werfers.
        val laggingOpponent = listOf(CountUpState.initial())
        var current = mode.initialState(config)
        for (round in 1..CountUpState.ROUNDS) {
            assertEquals("Runde $round", round, current.round)
            repeat(CountUpState.DARTS_PER_ROUND) {
                val outcome = mode.applyDart(current, Dart.single(20), config, laggingOpponent)
                assertFalse("Gegner haengt zurueck -> kein Rundenende", outcome.legEnded)
                current = outcome.newState
            }
            assertEquals(round, current.completedRounds)
        }
        // 8 Runden a 3 x S-20 == 480 Punkte, 24 Darts.
        assertEquals(480, current.points)
        assertEquals(24, current.dartsThrown)
    }

    @Test
    fun keinSofortSieg_auchNichtBeiEiner180erAufnahme() {
        // Count Up kennt keinen Werfer-Sieg: legWon ist in jedem Schritt false.
        var current = mode.initialState(config)
        repeat(CountUpState.DARTS_PER_ROUND) {
            val outcome = mode.applyDart(current, Dart.triple(20), config)
            assertFalse("Count Up kennt keinen Sofort-Sieg", outcome.legWon)
            assertFalse(outcome.bust)
            current = outcome.newState
        }
        assertEquals(180, current.points)
    }

    @Test
    fun legEnded_beimLetztenWerferDerAchtenRunde_mitEindeutigemFuehrenden() {
        // Werfer beendet mit dem 24. Dart seine 8. Runde; der Gegner ist bereits
        // durch -> Leg entschieden, der Werfer fuehrt.
        val opponent = state(dartsThrown = 24, points = 300)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 340),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legEnded)
        // Vertrag: legEnded schliesst bust und legWon aus.
        assertFalse(outcome.bust)
        assertFalse(outcome.legWon)
        assertEquals(360, outcome.newState.points)
    }

    @Test
    fun legEnded_auchWennDerWerferNichtFuehrt() {
        // Der Werfer schliesst die Runde ab, liegt aber hinten: das Leg endet
        // trotzdem - den Gewinner kuert die Engine ueber legScore.
        val opponent = state(dartsThrown = 24, points = 400)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 200),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legEnded)
        assertFalse(outcome.legWon)
        assertEquals(220, outcome.newState.points)
        assertTrue(
            "Der Gegner hat den hoeheren Rangwert",
            mode.legScore(opponent) > mode.legScore(outcome.newState),
        )
    }

    @Test
    fun keinLegEnded_beiGleichstandAnDerSpitze_suddenDeath() {
        // 300 : 300 nach Runde 8 -> kein Rundenende, es wird weitergespielt.
        val opponent = state(dartsThrown = 24, points = 300)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 280),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(300, outcome.newState.points)
        assertFalse("Gleichstand entscheidet nicht", outcome.legEnded)
        assertFalse(outcome.legWon)
        // Der Werfer ist bereits in der Verlaengerung (Runde 9).
        assertEquals(9, outcome.newState.round)
    }

    @Test
    fun keinLegEnded_vorDerAchtenRunde() {
        // Beide Spieler beenden ihre 7. Runde - zu frueh fuer den Punktvergleich.
        val opponent = state(dartsThrown = 21, points = 200)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 260),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(7, outcome.newState.completedRounds)
        assertFalse(outcome.legEnded)
    }

    @Test
    fun keinLegEnded_wennEinGegnerDieRundeNochNichtBeendetHat() {
        // Der Werfer ist als Erster durch Runde 8; der Gegner wirft noch.
        val opponent = state(dartsThrown = 21, points = 400)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 300),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertFalse("Der Werfer ist nicht der Letzte der Runde", outcome.legEnded)
        assertFalse(outcome.legWon)
    }

    @Test
    fun keinLegEnded_mittenInDerAufnahme() {
        // Auch in Runde 8 entscheidet erst der 3. Dart der Aufnahme.
        val opponent = state(dartsThrown = 24, points = 100)
        val outcome = mode.applyDart(
            state(dartsThrown = 22, points = 300),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertFalse("Die Aufnahme ist noch nicht voll", outcome.legEnded)
    }

    @Test
    fun legScore_entsprichtDemPunktestand() {
        assertEquals(0, mode.legScore(CountUpState.initial()))
        assertEquals(480, mode.legScore(state(dartsThrown = 24, points = 480)))
    }

    @Test
    fun keinBustUndKeinLegWon_ueberEinGanzesLeg() {
        // Zwei Spieler ueber acht Runden: nie Bust, nie Werfer-Sieg, und die
        // Flag-Invariante (bust/legWon/legEnded paarweise exklusiv) haelt in
        // jedem Schritt.
        var thrower = mode.initialState(config)
        var opponent = mode.initialState(config)
        for (round in 1..CountUpState.ROUNDS) {
            repeat(CountUpState.DARTS_PER_ROUND) {
                val o = mode.applyDart(thrower, Dart.triple(20), config, listOf(opponent))
                assertFalse("Count Up bustet nie", o.bust)
                assertFalse("Count Up kennt keinen Werfer-Sieg", o.legWon)
                assertFalse(o.bust && o.legEnded)
                assertFalse(o.legWon && o.legEnded)
                thrower = o.newState
            }
            repeat(CountUpState.DARTS_PER_ROUND) {
                val o = mode.applyDart(opponent, Dart.miss(), config, listOf(thrower))
                assertFalse(o.bust)
                assertFalse(o.legWon)
                opponent = o.newState
            }
        }
        assertEquals(8 * 180, thrower.points)
        assertEquals(0, opponent.points)
    }
}
