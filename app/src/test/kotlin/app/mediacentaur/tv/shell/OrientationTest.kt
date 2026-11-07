package app.mediacentaur.tv.shell

import android.content.pm.ActivityInfo
import app.mediacentaur.tv.bridge.DeviceDeclaration.FormFactor
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {
    @Test
    fun aPhoneBrowsesUprightATabletAsHeldATvOnItsSide() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Orientation.requested(FormFactor.PHONE, viewing = false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, Orientation.requested(FormFactor.TABLET, viewing = false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Orientation.requested(FormFactor.TV, viewing = false))
    }

    @Test
    fun aViewingIsLandscapeEitherWayUpOnEveryDevice() {
        for (formFactor in FormFactor.values()) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, Orientation.requested(formFactor, viewing = true))
        }
    }
}
