package com.mechanicel.tomsdarts.analytics

import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM-Tests fuer [computeSequenceStats] (ADR-0036), handgerechnete Szenarien. */
class SequencesTest {

    private val p1 = 1L
    private val p2 = 2L
    private val eps = 1e-9

    private fun f(pair: Pair<Int, Int>) = HitField(pair.first, pair.second)

    // --- Erster Dart ---------------------------------------------------------

    @Test
    fun `erster Dart wird ueber mehrere Legs gezaehlt, meistgetroffenes Feld mit Anteil`() {
        val leg1 = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), s(1)),
                visit(p1, 2, s(20), t(20), s(5)),
                visit(p1, 4, t(20), MISS, s(20)),
            ),
            legId = 1,
        )
        val leg2 = leg(
            visits = listOf(
                visit(p1, 0, t(20), t(20), t(20)),
                visit(p1, 2, s(19), s(19), s(19)),
            ),
            legId = 2,
        )

        val s = computeSequenceStats(listOf(leg1, leg2), p1)

        assertEquals(5, s.visitsCounted)
        assertEquals(mapOf(f(s(19)) to 1, f(s(20)) to 1, f(t(20)) to 3), s.firstDart)
        assertEquals(listOf(f(s(19)), f(s(20)), f(t(20))), s.firstDart.keys.toList())
        assertEquals(f(t(20)), s.mostFrequentFirstDart)
        assertEquals(3.0 / 5, s.mostFrequentFirstDartShare!!, eps)
    }

    @Test
    fun `erster Dart bei Gleichstand gewinnt das kleinere Feld in Feld-Ordnung`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(1), s(1)),
                visit(p1, 2, s(20), s(1), s(1)),
            ),
        )

        val s = computeSequenceStats(listOf(l), p1)

        // s(20) = (20,1) < t(20) = (20,3)
        assertEquals(f(s(20)), s.mostFrequentFirstDart)
        assertEquals(0.5, s.mostFrequentFirstDartShare!!, eps)
    }

    // --- Positionen ----------------------------------------------------------

    @Test
    fun `Positions-Averages zaehlen Bust-Darts mit 0 und kurze Aufnahmen nur an ihren Positionen`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), s(5)), // 60, 20, 5
                visit(p1, 2, t(20), t(20), t(20), bust = true), // geworfen, aber 0/0/0
                visit(p1, 4, s(19), d(20)), // 2 Darts: 19, 40
                visit(p1, 6, d(16)), // Checkout mit 1 Dart: 32
            ),
            startScore = 501,
        )

        val s = computeSequenceStats(listOf(l), p1)
        val (pos1, pos2, pos3) = s.byPosition

        assertEquals(listOf(1, 2, 3), s.byPosition.map { it.position })

        assertEquals(4, pos1.darts)
        assertEquals(4, pos1.x01Darts)
        assertEquals(60 + 0 + 19 + 32, pos1.x01Points)
        assertEquals(111.0 / 4, pos1.averagePoints!!, eps)

        assertEquals(3, pos2.darts)
        assertEquals(20 + 0 + 40, pos2.x01Points)
        assertEquals(60.0 / 3, pos2.averagePoints!!, eps)

        assertEquals(2, pos3.darts)
        assertEquals(5 + 0, pos3.x01Points)
        assertEquals(2.5, pos3.averagePoints!!, eps)

        // Bust-Darts erscheinen physisch in der Positions-Trefferverteilung.
        assertEquals(mapOf(f(s(20)) to 1, f(d(20)) to 1, f(t(20)) to 1), pos2.byField)
        assertEquals(mapOf(f(s(5)) to 1, f(t(20)) to 1), pos3.byField)
    }

    @Test
    fun `Bust-Dart-Werte werden ignoriert, auch wenn totalScored inkonsistent ist`() {
        val l = leg(visits = listOf(visit(p1, 0, t(20), t(20), bust = true, totalScored = 120)))

        val s = computeSequenceStats(listOf(l), p1)

        assertEquals(0, s.byPosition[0].x01Points)
        assertEquals(0.0, s.byPosition[0].averagePoints!!, eps)
        assertEquals(1, s.byPosition[1].x01Darts)
    }

    @Test
    fun `Positions-Punkte nur aus X01-Legs, Darts und Felder ueber alle Modi`() {
        val x01 = leg(visits = listOf(visit(p1, 0, t(20), s(20), s(1))), legId = 1)
        val cricket = leg(
            visits = listOf(visit(p1, 0, t(19), t(18), t(17))),
            modeType = GameModeCatalog.CRICKET,
            legId = 2,
        )

        val pos1 = computeSequenceStats(listOf(x01, cricket), p1).byPosition[0]

        assertEquals(2, pos1.darts)
        assertEquals(1, pos1.x01Darts)
        assertEquals(60, pos1.x01Points)
        assertEquals(60.0, pos1.averagePoints!!, eps)
        assertEquals(mapOf(f(t(19)) to 1, f(t(20)) to 1), pos1.byField)
    }

    @Test
    fun `Nicht-X01 liefert Positions-Punkte null, Rest gefuellt`() {
        val cricket = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), t(19)),
                visit(p1, 2, t(20), s(20), t(19)),
            ),
            modeType = GameModeCatalog.CRICKET,
        )

        val s = computeSequenceStats(listOf(cricket), p1)

        for (pos in s.byPosition) {
            assertEquals(2, pos.darts)
            assertEquals(0, pos.x01Darts)
            assertEquals(0, pos.x01Points)
            assertNull(pos.averagePoints)
        }
        assertEquals(mapOf(f(t(20)) to 2), s.firstDart)
        assertEquals(
            listOf(Transition(f(s(20)), f(t(19)), 2), Transition(f(t(20)), f(s(20)), 2)),
            s.transitions,
        )
        assertEquals(listOf(VisitPattern(listOf(f(t(20)), f(s(20)), f(t(19))), 2)), s.topOrderedVisits)
        assertEquals(listOf(VisitPattern(listOf(f(t(19)), f(s(20)), f(t(20))), 2)), s.topCombinations)
    }

    // --- Uebergaenge ---------------------------------------------------------

    @Test
    fun `Uebergaenge inkl Bull und Miss nur innerhalb derselben Aufnahme`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), MISS),
                visit(p1, 2, BULL, DBULL), // 2-Dart-Aufnahme: ein Uebergang
                visit(p1, 4, MISS, t(20), s(20)),
            ),
        )

        val s = computeSequenceStats(listOf(l), p1)

        // Kein Uebergang MISS (Aufnahme 1) -> BULL (Aufnahme 2) oder DBULL -> MISS.
        assertEquals(
            mapOf(
                f(MISS) to mapOf(f(t(20)) to 1),
                f(s(20)) to mapOf(f(MISS) to 1),
                f(t(20)) to mapOf(f(s(20)) to 2),
                f(BULL) to mapOf(f(DBULL) to 1),
            ),
            s.transitionMatrix,
        )
        assertEquals(
            listOf(
                Transition(f(t(20)), f(s(20)), 2),
                Transition(f(MISS), f(t(20)), 1),
                Transition(f(s(20)), f(MISS), 1),
                Transition(f(BULL), f(DBULL), 1),
            ),
            s.transitions,
        )
        assertEquals(2, s.transitionMatrix[f(t(20))]!![f(s(20))])
    }

    @Test
    fun `Top-N kuerzt Uebergaenge, Gleichstand stabil nach von- dann nach-Feld`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, s(5), s(1)),
                visit(p1, 2, s(1), s(20)),
                visit(p1, 4, s(1), s(5)),
                visit(p1, 6, s(20), s(20)),
                visit(p1, 8, s(20), s(20)),
            ),
        )

        val s = computeSequenceStats(listOf(l), p1, topN = 3)

        assertEquals(
            listOf(
                Transition(f(s(20)), f(s(20)), 2),
                Transition(f(s(1)), f(s(5)), 1),
                Transition(f(s(1)), f(s(20)), 1),
            ),
            s.transitions,
        )
        // Die Lookup-Map bleibt vollstaendig.
        assertEquals(1, s.transitionMatrix[f(s(5))]!![f(s(1))])
    }

    // --- Geordnete Sequenzen und Kombinationen -------------------------------

    @Test
    fun `gleiche Kombination, verschiedene Sequenzen`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), t(20)),
                visit(p1, 2, s(20), t(20), t(20)),
                visit(p1, 4, t(20), s(20), t(20)),
            ),
        )

        val s = computeSequenceStats(listOf(l), p1)

        assertEquals(
            listOf(
                VisitPattern(listOf(f(t(20)), f(s(20)), f(t(20))), 2),
                VisitPattern(listOf(f(s(20)), f(t(20)), f(t(20))), 1),
            ),
            s.topOrderedVisits,
        )
        assertEquals(
            listOf(VisitPattern(listOf(f(s(20)), f(t(20)), f(t(20))), 3)),
            s.topCombinations,
        )
        assertEquals(3, s.completeVisits)
        assertEquals(0, s.incompleteVisits)
    }

    @Test
    fun `Top-N-Muster sortiert nach Anzahl, Gleichstand lexikografisch nach Feld-Ordnung`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, s(19), s(19), s(19)),
                visit(p1, 2, s(5), s(5), s(5)),
                visit(p1, 4, MISS, MISS, MISS),
                visit(p1, 6, s(19), s(19), s(19)),
            ),
        )

        val s = computeSequenceStats(listOf(l), p1, topN = 2)

        assertEquals(
            listOf(
                VisitPattern(List(3) { f(s(19)) }, 2),
                VisitPattern(List(3) { f(MISS) }, 1), // MISS (0) vor s(5)
            ),
            s.topOrderedVisits,
        )
        assertEquals(s.topOrderedVisits, s.topCombinations)
    }

    @Test
    fun `Aufnahmen mit weniger als 3 Darts gehen nicht in die 3-Dart-Muster ein`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), t(20), t(20)),
                visit(p1, 2, t(20), d(20)), // Checkout mit 2 Darts
                visit(p1, 4, t(20), bust = true), // Bust nach 1 Dart
            ),
            startScore = 301,
        )

        val s = computeSequenceStats(listOf(l), p1)

        assertEquals(3, s.visitsCounted)
        assertEquals(1, s.completeVisits)
        assertEquals(2, s.incompleteVisits)
        assertEquals(listOf(VisitPattern(List(3) { f(t(20)) }, 1)), s.topOrderedVisits)
        assertEquals(listOf(VisitPattern(List(3) { f(t(20)) }, 1)), s.topCombinations)
        // ... aber in ersten Dart, Positionen und Uebergaenge.
        assertEquals(mapOf(f(t(20)) to 3), s.firstDart)
        assertEquals(listOf(3, 2, 1), s.byPosition.map { it.darts })
        assertEquals(1, s.transitionMatrix[f(t(20))]!![f(d(20))])
    }

    @Test
    fun `Darts werden nach dartIndex sortiert ausgewertet`() {
        val v = visit(p1, 0, t(20), s(20), s(1))
        val shuffled = v.copy(darts = v.darts.reversed())

        val s = computeSequenceStats(listOf(leg(visits = listOf(shuffled))), p1)

        assertEquals(mapOf(f(t(20)) to 1), s.firstDart)
        assertEquals(listOf(VisitPattern(listOf(f(t(20)), f(s(20)), f(s(1))), 1)), s.topOrderedVisits)
    }

    // --- Filter und Randfaelle ----------------------------------------------

    @Test
    fun `Mehrspieler-Match zaehlt nur Aufnahmen des Spielers, geloeschte Spieler nie`() {
        val l = leg(
            visits = listOf(
                visit(p1, 0, t(20), s(20), s(1)),
                visit(p2, 1, t(19), t(19), t(19)),
                visit(null, 2, BULL, BULL, BULL),
                visit(p1, 3, s(20), s(20), s(20)),
            ),
        )

        val s = computeSequenceStats(listOf(l), p1)

        assertEquals(2, s.visitsCounted)
        assertEquals(mapOf(f(s(20)) to 1, f(t(20)) to 1), s.firstDart)
        assertTrue(s.transitionMatrix.keys.none { it == f(t(19)) || it == f(BULL) })
        assertEquals(60 + 20, s.byPosition[0].x01Points)

        val s2 = computeSequenceStats(listOf(l), p2)
        assertEquals(1, s2.visitsCounted)
        assertEquals(listOf(VisitPattern(List(3) { f(t(19)) }, 1)), s2.topCombinations)
    }

    @Test
    fun `Aufnahmen ohne Darts werden uebersprungen`() {
        val l = leg(visits = listOf(visit(p1, 0), visit(p1, 2, s(20))))

        val s = computeSequenceStats(listOf(l), p1)

        assertEquals(1, s.visitsCounted)
        assertEquals(1, s.incompleteVisits)
        assertEquals(mapOf(f(s(20)) to 1), s.firstDart)
    }

    @Test
    fun `leere Eingabe liefert leere Ergebnisse und null statt Division durch 0`() {
        val s = computeSequenceStats(emptyList(), p1)

        assertEquals(0, s.visitsCounted)
        assertTrue(s.firstDart.isEmpty())
        assertNull(s.mostFrequentFirstDart)
        assertNull(s.mostFrequentFirstDartShare)
        assertEquals(listOf(1, 2, 3), s.byPosition.map { it.position })
        for (pos in s.byPosition) {
            assertEquals(0, pos.darts)
            assertTrue(pos.byField.isEmpty())
            assertNull(pos.averagePoints)
        }
        assertTrue(s.transitions.isEmpty())
        assertTrue(s.transitionMatrix.isEmpty())
        assertTrue(s.topOrderedVisits.isEmpty())
        assertTrue(s.topCombinations.isEmpty())
        assertEquals(0, s.completeVisits)
        assertEquals(0, s.incompleteVisits)
    }

    @Test
    fun `topN 0 liefert leere Top-Listen, negatives topN wirft`() {
        val l = leg(visits = listOf(visit(p1, 0, t(20), s(20), s(1))))

        val s = computeSequenceStats(listOf(l), p1, topN = 0)

        assertTrue(s.transitions.isEmpty())
        assertTrue(s.topOrderedVisits.isEmpty())
        assertTrue(s.topCombinations.isEmpty())
        assertEquals(2, s.transitionMatrix.values.sumOf { it.values.sum() })

        val thrown = runCatching { computeSequenceStats(listOf(l), p1, topN = -1) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `Default topN ist 10`() {
        val visits = (1..12).map { n -> visit(p1, n * 2, s(n), s(n), s(n)) }

        val s = computeSequenceStats(listOf(leg(visits = visits)), p1)

        assertEquals(DEFAULT_TOP_N, s.topOrderedVisits.size)
        assertEquals(10, s.transitions.size)
        assertEquals(f(s(1)), s.topOrderedVisits.first().fields.first())
    }
}
