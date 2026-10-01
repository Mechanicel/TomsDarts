package com.mechanicel.tomsdarts.ui.delight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.hypot

/**
 * Tests der puren Layout-Helfer des Feier-Overlays
 * ([delightIllustrationSizeDp], [triangleCornerFractions]).
 */
class DelightLayoutTest {

    @Test
    fun hochformatSkaliertMitDerBreite() {
        assertEquals(160f, delightIllustrationSizeDp(320f, 640f, fontScale = 1f)!!, 1e-4f)
        assertEquals(180f, delightIllustrationSizeDp(360f, 760f, fontScale = 1f)!!, 1e-4f)
    }

    @Test
    fun illustrationIstAuf200Begrenzt() {
        assertEquals(200f, delightIllustrationSizeDp(800f, 1280f, fontScale = 1f)!!, 1e-4f)
    }

    @Test
    fun hochformatBeachtetDieHoehe() {
        // 0.3 * 400 = 120 < 0.5 * 360.
        assertEquals(120f, delightIllustrationSizeDp(360f, 400f, fontScale = 1f)!!, 1e-4f)
    }

    @Test
    fun querformatNutztDieHoehe() {
        assertEquals(200f, delightIllustrationSizeDp(760f, 380f, fontScale = 1f)!!, 1e-4f)
        assertEquals(180f, delightIllustrationSizeDp(700f, 300f, fontScale = 1f)!!, 1e-4f)
    }

    @Test
    fun zuKleineIllustrationWirdWeggelassen() {
        assertNull(delightIllustrationSizeDp(180f, 400f, fontScale = 1f))
        assertNull(delightIllustrationSizeDp(500f, 150f, fontScale = 1f))
    }

    @Test
    fun grosseSchriftImQuerformatLaesstDieIllustrationWeg() {
        assertNull(delightIllustrationSizeDp(760f, 380f, fontScale = 1.5f))
        // Im Hochformat bleibt sie.
        assertEquals(180f, delightIllustrationSizeDp(360f, 760f, fontScale = 2f)!!, 1e-4f)
    }

    @Test
    fun dreieckIstGleichseitigMitSpitzeOben() {
        val (top, left, right) = triangleCornerFractions(inset = 0.13f)
        val a = hypot(top.first - left.first, top.second - left.second)
        val b = hypot(top.first - right.first, top.second - right.second)
        val c = hypot(left.first - right.first, left.second - right.second)
        assertEquals(a, b, 1e-5f)
        assertEquals(a, c, 1e-5f)
        assertEquals(0.5f, top.first, 1e-6f)
        assertEquals(left.second, right.second, 1e-6f)
        // Vertikal zentriert, innerhalb der Flaeche.
        assertEquals(1f - left.second, top.second, 1e-5f)
        listOf(top, left, right).forEach { (x, y) ->
            assertEquals(true, x in 0f..1f && y in 0f..1f)
        }
    }
}
