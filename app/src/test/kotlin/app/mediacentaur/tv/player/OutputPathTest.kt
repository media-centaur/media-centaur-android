package app.mediacentaur.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputPathTest {
    @Test
    fun `a video chain that fails on the direct path moves the file to the GPU path`() {
        assertEquals(OutputPath.GPU, OutputPath.DIRECT.afterVideoChainFailure())
    }

    @Test
    fun `the GPU path is the last resort`() {
        assertNull(OutputPath.GPU.afterVideoChainFailure())
    }

    @Test
    fun `only mpv's fatal video chain report is the failure`() {
        assertTrue(OutputPath.isVideoChainFailure("cplayer", fatal = true, text = "Could not initialize video chain.\n"))
        assertFalse(OutputPath.isVideoChainFailure("cplayer", fatal = false, text = "Could not initialize video chain."))
        assertFalse(OutputPath.isVideoChainFailure("vd", fatal = true, text = "Could not open codec."))
    }
}
