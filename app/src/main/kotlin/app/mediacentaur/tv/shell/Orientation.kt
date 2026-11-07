package app.mediacentaur.tv.shell

import android.content.pm.ActivityInfo
import app.mediacentaur.tv.bridge.DeviceDeclaration.FormFactor

/**
 * The orientation the activity requests, from the two facts the app
 * already owns: the device's form factor and whether a viewing is on
 * (`campaigns/adaptive-layout.md`, the Netflix/Plex model). While browsing
 * a phone is held upright, a tablet as its user holds it (honouring their
 * rotation lock), a TV is on its side; a viewing is landscape on every
 * device, either way up. The manifest fixes nothing; `configChanges` keeps
 * a turn from recreating the activity.
 */
object Orientation {
    fun requested(formFactor: FormFactor, viewing: Boolean): Int =
        when {
            viewing -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            formFactor == FormFactor.PHONE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            formFactor == FormFactor.TABLET -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
}
