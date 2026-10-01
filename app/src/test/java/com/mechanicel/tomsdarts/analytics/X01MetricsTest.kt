package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM-Tests fuer [computeX01Metrics] und [isOneDartCheckout] (ADR-0035) mit
 * handgerechneten Szenarien.
 */
class X01MetricsTest {

    private val p1 = 1L
    private val p2 = 2L
    private val eps = 1e-9

    /**
     * 501, Double-Out, gewonnen mit 11 Darts:
     * 180 (Rest 321), 137 (Rest 184), 124 (Rest 60), S20 D20 (Checkout 60).
     */
    private fun standard501Leg() = leg(
        visits = listOf(
            visit(p1, 0, t(20), t(20), t(20)),
            visit(p1, 1, t(20), t(19), s(20)),
            visit(p1, 2, t(20), t(20), s(4)),
            visit(p1, 3, s(20), d(20)),
        ),
        winnerId = p1,
    )

    @Test
    fun `501-Leg liefert handgerechneten Average, First-9 und Checkout`() {
        val m = computeX01Metrics(listOf(standard501Leg()), p1)

        assertEquals(11, m.dartsThrown)
        assertEquals(501, m.pointsScored)
        assertEquals(501.0 / 11 * 3, m.threeDartAverage!!, eps)
        assertEquals(147.0, m.firstNineAverage!!, eps) // (180 + 137 + 124) / 9 * 3
        assertEquals(1, m.checkoutAttempts) // nur bei Rest 40
        assertEquals(1, m.checkoutHits)
        assertEquals(1.0, m.checkoutRate!!, eps)
        assertEquals(60, m.highestCheckout)
        assertEquals(1, m.legsPlayed)
        assertEquals(1, m.legsWon)
    }

    @Test
    fun `Bust-Darts zaehlen als geworfen mit 0 Punkten, Rest wird zurueckgesetzt`() {
        // Start 60: S20 S20 S19 -> Rest 1 = Bust (Rest zurueck auf 60); dann S20 D20.
        val l = leg(
            visits = listOf(
                visit(p1, 0, s(20), s(20), s(19), bust = true),
                visit(p1, 1, s(20), d(20)),
            ),
            startScore = 60,
            winnerId = p1,
        )
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(5, m.dartsThrown)
        assertEquals(60, m.pointsScored)
        assertEquals(36.0, m.threeDartAverage!!, eps)
        // Versuche: Rest 40, Rest 20 (Bust-Aufnahme), Rest 40 (zweite Aufnahme).
        assertEquals(3, m.checkoutAttempts)
        assertEquals(1, m.checkoutHits)
        assertEquals(1.0 / 3, m.checkoutRate!!, eps)
        assertEquals(60, m.highestCheckout)
    }

    @Test
    fun `Bust-Aufnahme mit abweichendem totalScored wird nicht doppelt gewertet`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, s(20), s(20), s(19), bust = true, totalScored = 59),
                visit(p1, 1, s(20), d(20)),
            ),
            startScore = 60,
            winnerId = p1,
        )
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(60, m.pointsScored)
        assertEquals(5, m.dartsThrown)
    }

    @Test
    fun `Gewinn mit einem Dart`() {
        val l = leg(listOf(visit(p1, 0, d(20))), startScore = 40, winnerId = p1)
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(1, m.dartsThrown)
        assertEquals(120.0, m.threeDartAverage!!, eps)
        assertEquals(1, m.checkoutAttempts)
        assertEquals(1, m.checkoutHits)
        assertEquals(40, m.highestCheckout)
    }

    @Test
    fun `Gewinn mit zwei Darts zaehlt nur zwei Darts`() {
        val l = leg(listOf(visit(p1, 0, t(20), d(20))), startScore = 100, winnerId = p1)
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(2, m.dartsThrown)
        assertEquals(150.0, m.threeDartAverage!!, eps)
        assertEquals(1, m.checkoutAttempts)
        assertEquals(1, m.checkoutHits)
        assertEquals(100, m.highestCheckout)
    }

    @Test
    fun `Gewinn mit drei Darts zaehlt Versuche bei Rest 40 und 20`() {
        val l = leg(listOf(visit(p1, 0, t(20), s(20), d(10))), startScore = 100, winnerId = p1)
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(3, m.dartsThrown)
        assertEquals(2, m.checkoutAttempts)
        assertEquals(1, m.checkoutHits)
        assertEquals(0.5, m.checkoutRate!!, eps)
        assertEquals(100, m.highestCheckout)
    }

    @Test
    fun `ohne Double-Out zaehlt jeder mit einem Dart erreichbare Rest als Versuch`() {
        // Start 100: T20 (Rest 40), S1 (Rest 39), S19 (Rest 20) | S20 -> 0.
        val visits = listOf(
            visit(p1, 0, t(20), s(1), s(19)),
            visit(p1, 1, s(20)),
        )
        val withoutDo = computeX01Metrics(
            listOf(leg(visits, startScore = 100, doubleOut = false, winnerId = p1)),
            p1,
        )
        // Versuche: Rest 40 (S1), Rest 39 = T13 (S19), Rest 20 (S20) -> 3 Versuche, 1 Erfolg.
        assertEquals(3, withoutDo.checkoutAttempts)
        assertEquals(1, withoutDo.checkoutHits)
        assertEquals(20, withoutDo.highestCheckout)

        // Dieselben Wuerfe mit Double-Out (unbeendet, da das Single-Finish dort Bust waere):
        // nur Rest 40 und 20 sind 1-Dart-Checkouts, 39 nicht.
        val withDo = computeX01Metrics(
            listOf(leg(visits, startScore = 100, doubleOut = true, winnerId = null, finished = false)),
            p1,
        )
        assertEquals(2, withDo.checkoutAttempts)
        assertEquals(0, withDo.checkoutHits)
        assertEquals(0.0, withDo.checkoutRate!!, eps)
    }

    @Test
    fun `Checkout-Versuche ueber mehrere Aufnahmen`() {
        // Start 40: D10 (Rest 20), Miss, Miss | D5 (Rest 10), D5 -> 0.
        val l = leg(
            visits = listOf(
                visit(p1, 0, d(10), MISS, MISS),
                visit(p1, 1, d(5), d(5)),
            ),
            startScore = 40,
            winnerId = p1,
        )
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(5, m.checkoutAttempts)
        assertEquals(1, m.checkoutHits)
        assertEquals(0.2, m.checkoutRate!!, eps)
        assertEquals(20, m.highestCheckout) // Rest vor der gewinnenden Aufnahme
    }

    @Test
    fun `Bull-Finish auf 50 zaehlt als Checkout`() {
        val l = leg(listOf(visit(p1, 0, DBULL)), startScore = 50, winnerId = p1)
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(1, m.checkoutAttempts)
        assertEquals(1, m.checkoutHits)
        assertEquals(50, m.highestCheckout)
    }

    @Test
    fun `hoechster Checkout ist das Maximum ueber alle Legs`() {
        val a = leg(listOf(visit(p1, 0, d(20))), startScore = 40, winnerId = p1, legId = 1)
        val b = leg(listOf(visit(p1, 0, t(20), d(20))), startScore = 100, winnerId = p1, legId = 2)
        val m = computeX01Metrics(listOf(a, b), p1)

        assertEquals(100, m.highestCheckout)
        assertEquals(2, m.checkoutHits)
        assertEquals(2, m.legsPlayed)
        assertEquals(2, m.legsWon)
    }

    @Test
    fun `Nicht-X01-Legs werden ignoriert`() {
        val cricket = leg(
            listOf(visit(p1, 0, t(20), t(20), t(20))),
            modeType = GameModeCatalog.CRICKET,
            winnerId = p1,
            legId = 2,
        )
        val m = computeX01Metrics(listOf(cricket, standard501Leg()), p1)

        assertEquals(11, m.dartsThrown)
        assertEquals(501, m.pointsScored)
        assertEquals(1, m.legsPlayed)

        val onlyCricket = computeX01Metrics(listOf(cricket), p1)
        assertEquals(0, onlyCricket.dartsThrown)
        assertNull(onlyCricket.threeDartAverage)
        assertEquals(0, onlyCricket.legsPlayed)
        assertEquals(0, onlyCricket.legsWon)
    }

    @Test
    fun `Match-Ansicht filtert Aufnahmen anderer Spieler`() {
        // Start 101: p1 wirft 60, p2 checkt mit T20 S1 D20 aus.
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), MISS, MISS),
                visit(p2, 1, t(20), s(1), d(20)),
                visit(null, 2, t(20)), // geloeschter Spieler, nie gezaehlt
            ),
            startScore = 101,
            winnerId = p2,
        )
        val m1 = computeX01Metrics(listOf(l), p1)
        assertEquals(3, m1.dartsThrown)
        assertEquals(60, m1.pointsScored)
        assertEquals(0, m1.checkoutAttempts)
        assertEquals(0, m1.checkoutHits)
        assertNull(m1.checkoutRate)
        assertNull(m1.highestCheckout)
        assertEquals(1, m1.legsPlayed)
        assertEquals(0, m1.legsWon)

        val m2 = computeX01Metrics(listOf(l), p2)
        assertEquals(3, m2.dartsThrown)
        assertEquals(101, m2.pointsScored)
        assertEquals(1, m2.checkoutAttempts) // nur bei Rest 40
        assertEquals(1, m2.checkoutHits)
        assertEquals(101, m2.highestCheckout)
        assertEquals(1, m2.legsWon)
    }

    @Test
    fun `leere Eingabe liefert Nullen statt Division durch 0`() {
        val m = computeX01Metrics(emptyList(), p1)

        assertEquals(0, m.dartsThrown)
        assertEquals(0, m.pointsScored)
        assertNull(m.threeDartAverage)
        assertNull(m.firstNineAverage)
        assertEquals(0, m.checkoutAttempts)
        assertEquals(0, m.checkoutHits)
        assertNull(m.checkoutRate)
        assertNull(m.highestCheckout)
        assertEquals(0, m.legsPlayed)
        assertEquals(0, m.legsWon)
    }

    @Test
    fun `unbeendetes Leg zaehlt fuer Averages, nicht fuer legsPlayed`() {
        val l = leg(
            listOf(visit(p1, 0, t(20), t(20), t(20))),
            finished = false,
            winnerId = null,
        )
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(3, m.dartsThrown)
        assertEquals(180.0, m.threeDartAverage!!, eps)
        assertEquals(180.0, m.firstNineAverage!!, eps)
        assertEquals(0, m.legsPlayed)
        assertEquals(0, m.legsWon)
        assertNull(m.highestCheckout)
    }

    @Test
    fun `Rest 0 ohne Leg-Gewinn des Spielers zaehlt nicht als Erfolg`() {
        val l = leg(listOf(visit(p1, 0, d(20))), startScore = 40, winnerId = p2)
        val m = computeX01Metrics(listOf(l), p1)

        assertEquals(1, m.checkoutAttempts)
        assertEquals(0, m.checkoutHits)
        assertNull(m.highestCheckout)
    }

    @Test
    fun `First-9 nimmt die ersten drei Aufnahmen nach turnIndex, leere Aufnahmen zaehlen nicht`() {
        val l = leg(
            visits = listOf(
                // bewusst unsortiert uebergeben
                visit(p1, 6, s(1), s(1), s(1)),
                visit(p1, 4, t(20), t(20), t(20)),
                visit(p1, 0, s(20), s(20), s(20)),
                visit(p1, 2), // keine persistierten Darts
                visit(p1, 3, s(5), s(5), s(5)),
            ),
            finished = false,
        )
        val m = computeX01Metrics(listOf(l), p1)

        // First-9: 60 + 15 + 180 = 255 / 9 * 3 = 85.
        assertEquals(85.0, m.firstNineAverage!!, eps)
        // Gesamt: 258 Punkte / 12 Darts * 3 = 64.5 (leere Aufnahme ohne Darts).
        assertEquals(12, m.dartsThrown)
        assertEquals(64.5, m.threeDartAverage!!, eps)
    }

    @Test
    fun `isOneDartCheckout mit Double-Out`() {
        listOf(2, 20, 40, 50).forEach { assertTrue("$it", isOneDartCheckout(it, doubleOut = true)) }
        listOf(0, 1, 21, 39, 41, 42, 60, 170).forEach {
            assertFalse("$it", isOneDartCheckout(it, doubleOut = true))
        }
    }

    @Test
    fun `isOneDartCheckout ohne Double-Out`() {
        listOf(1, 20, 21, 25, 39, 50, 57, 60).forEach {
            assertTrue("$it", isOneDartCheckout(it, doubleOut = false))
        }
        listOf(0, 23, 29, 35, 41, 59, 61, 100).forEach {
            assertFalse("$it", isOneDartCheckout(it, doubleOut = false))
        }
    }
}
