package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM-Tests fuer [computeHitDistribution] (ADR-0035). */
class HitDistributionTest {

    private val p1 = 1L
    private val p2 = 2L
    private val eps = 1e-9

    @Test
    fun `zaehlt je Feld, Segment und Multiplier inkl Miss und Bull ueber alle Modi`() {
        val x01 = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), MISS),
                visit(p2, 1, t(19), t(19), t(19)), // anderer Spieler
                visit(p1, 2, BULL, DBULL, d(20), bust = true), // Bust-Darts zaehlen physisch
            ),
            legId = 1,
        )
        val cricket = leg(
            visits = listOf(visit(p1, 0, t(20), MISS)),
            modeType = GameModeCatalog.CRICKET,
            legId = 2,
        )

        val h = computeHitDistribution(listOf(x01, cricket), p1)

        assertEquals(8, h.totalDarts)
        assertEquals(2, h.misses)
        assertEquals(
            mapOf(
                HitField(0, 1) to 2,
                HitField(20, 1) to 1,
                HitField(20, 2) to 1,
                HitField(20, 3) to 2,
                HitField(25, 1) to 1,
                HitField(25, 2) to 1,
            ),
            h.byField,
        )
        assertEquals(mapOf(0 to 2, 20 to 4, 25 to 2), h.bySegment)
        assertEquals(mapOf(1 to 2, 2 to 2, 3 to 2), h.byMultiplier)
        assertEquals(0.25, h.singleShare!!, eps)
        assertEquals(0.25, h.doubleShare!!, eps)
        assertEquals(0.25, h.tripleShare!!, eps)
        assertEquals(0.25, h.missShare!!, eps)
        assertTrue(HitField(19, 3) !in h.byField)
    }

    @Test
    fun `Anteile summieren sich zu 1`() {
        val l = leg(listOf(visit(p1, 0, s(1), s(2), t(3)), visit(p1, 1, MISS, d(4))))
        val h = computeHitDistribution(listOf(l), p1)

        val sum = h.singleShare!! + h.doubleShare!! + h.tripleShare!! + h.missShare!!
        assertEquals(1.0, sum, eps)
        assertEquals(0.4, h.singleShare!!, eps)
    }

    @Test
    fun `leere Eingabe liefert leere Verteilung ohne Anteile`() {
        val h = computeHitDistribution(emptyList(), p1)

        assertEquals(0, h.totalDarts)
        assertEquals(0, h.misses)
        assertTrue(h.byField.isEmpty())
        assertTrue(h.bySegment.isEmpty())
        assertTrue(h.byMultiplier.isEmpty())
        assertNull(h.singleShare)
        assertNull(h.doubleShare)
        assertNull(h.tripleShare)
        assertNull(h.missShare)
    }

    @Test
    fun `Spieler ohne eigene Aufnahmen liefert leere Verteilung`() {
        val l = leg(listOf(visit(p2, 0, t(20)), visit(null, 1, s(5))))
        val h = computeHitDistribution(listOf(l), p1)

        assertEquals(0, h.totalDarts)
        assertNull(h.missShare)
    }
}
