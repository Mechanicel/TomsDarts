package com.mechanicel.tomsdarts.ui.delight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests der puren Konfetti-Logik ([generateConfetti], [particlePosition],
 * [confettiCount]) - Grundlage fuer ein deterministisches, ruckelfreies
 * Konfetti ohne Animation je Partikel (ADR-0039).
 */
class ConfettiParticlesTest {

    @Test
    fun gleicherSeedLiefertGleichePartikel() {
        assertEquals(generateConfetti(seed = 42L, count = 60), generateConfetti(seed = 42L, count = 60))
    }

    @Test
    fun andererSeedLiefertAnderePartikel() {
        assertNotEquals(generateConfetti(seed = 1L, count = 60), generateConfetti(seed = 2L, count = 60))
    }

    @Test
    fun anzahlEntsprichtCount() {
        assertEquals(60, generateConfetti(seed = 7L, count = 60).size)
        assertEquals(CONFETTI_STATIC_COUNT, generateConfetti(seed = 7L, count = CONFETTI_STATIC_COUNT).size)
    }

    @Test
    fun anzahlWirdAufGrenzenBegrenzt() {
        assertEquals(CONFETTI_MAX_COUNT, generateConfetti(seed = 7L, count = 10_000).size)
        assertEquals(0, generateConfetti(seed = 7L, count = 0).size)
        assertEquals(0, generateConfetti(seed = 7L, count = -5).size)
    }

    @Test
    fun alleWerteLiegenInDenVorgegebenenBereichen() {
        // Mehrere Seeds und die Maximalzahl, damit Ausreisser auffallen.
        listOf(0L, 1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE).forEach { seed ->
            generateConfetti(seed, CONFETTI_MAX_COUNT).forEach { p ->
                assertTrue("x0 $p", p.x0 in 0.05f..0.95f)
                assertTrue("y0 $p", p.y0 in -0.1f..0f)
                assertTrue("vx $p", p.vx in -0.15f..0.15f)
                assertTrue("vy $p", p.vy in 0f..0.2f)
                assertTrue("rotation $p", p.rotationDeg in 0f..360f)
                assertTrue("spin $p", p.spin in -2f..2f)
                assertTrue("sway $p", p.sway in 0.5f..2f)
                assertTrue("color $p", p.colorIndex in 0 until CONFETTI_COLOR_COUNT)
            }
        }
    }

    @Test
    fun beideFormenKommenVorUeberwiegendRechtecke() {
        val particles = generateConfetti(seed = 3L, count = CONFETTI_MAX_COUNT)
        val rects = particles.count { it.shape == ConfettiShape.RECT }
        val circles = particles.count { it.shape == ConfettiShape.CIRCLE }
        assertTrue(circles > 0)
        assertTrue(rects > circles)
    }

    @Test
    fun beiTNullStehtDasPartikelAmStart() {
        val p = generateConfetti(seed = 5L, count = 1).single()
        val pos = particlePosition(p, t = 0f)
        assertEquals(p.x0, pos.x, 1e-6f)
        assertEquals(p.y0, pos.y, 1e-6f)
        assertEquals(p.rotationDeg, pos.rotationDeg, 1e-6f)
    }

    @Test
    fun yFaelltMonotonUeberDieZeit() {
        generateConfetti(seed = 9L, count = 30).forEach { p ->
            var previous = particlePosition(p, 0f).y
            for (step in 1..20) {
                val y = particlePosition(p, step / 20f).y
                assertTrue("y nicht monoton fuer $p", y > previous)
                previous = y
            }
        }
    }

    @Test
    fun positionFolgtDerFormel() {
        val p = ConfettiParticle(
            x0 = 0.5f, y0 = 0f, vx = 0.1f, vy = 0.2f, rotationDeg = 10f, spin = 1f,
            sway = 1f, colorIndex = 0, shape = ConfettiShape.RECT,
        )
        val pos = particlePosition(p, t = 1f, gravity = 1f)
        // sin(2*pi) ~ 0 -> nur Drift.
        assertEquals(0.6f, pos.x, 1e-4f)
        assertEquals(0.2f + 0.5f, pos.y, 1e-6f)
        assertEquals(370f, pos.rotationDeg, 1e-4f)
    }

    @Test
    fun partikelzahlRichtetSichNachDerBreite() {
        assertEquals(60, confettiCount(320f))
        assertEquals(60, confettiCount(599.9f))
        assertEquals(100, confettiCount(600f))
        assertTrue(confettiCount(2000f) <= CONFETTI_MAX_COUNT)
    }
}
