package app.mediacentaur.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceFitTest {
    @Test
    fun `a 16 by 9 picture fills a 16 by 9 panel`() {
        assertEquals(1920 to 1080, SurfaceFit.size(16.0 / 9.0, 1920, 1080))
    }

    @Test
    fun `a 4 by 3 picture keeps the panel's height with bars at the sides`() {
        assertEquals(1440 to 1080, SurfaceFit.size(4.0 / 3.0, 1920, 1080))
    }

    @Test
    fun `a picture wider than the panel keeps the panel's width with bars above and below`() {
        assertEquals(1920 to 803, SurfaceFit.size(2.39, 1920, 1080))
    }

    @Test
    fun `a square picture is a square`() {
        assertEquals(1080 to 1080, SurfaceFit.size(1.0, 1920, 1080))
    }

    @Test
    fun `an unusable aspect or an unlaid-out parent gives the parent's size`() {
        assertEquals(1920 to 1080, SurfaceFit.size(0.0, 1920, 1080))
        assertEquals(1920 to 1080, SurfaceFit.size(Double.NaN, 1920, 1080))
        assertEquals(0 to 0, SurfaceFit.size(4.0 / 3.0, 0, 0))
    }
}
