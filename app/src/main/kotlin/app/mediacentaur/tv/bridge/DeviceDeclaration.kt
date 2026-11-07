package app.mediacentaur.tv.bridge

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import app.mediacentaur.tv.BuildConfig

/**
 * What the device says of itself when it opens the player channel: its
 * maker and model, its Android version, this app's version and its form
 * factor, one header each. The host records them on the player's row and
 * shows them in Settings → Players and Status → Playback, so two players
 * can be told apart. Header values carry printable ASCII only; anything
 * else is dropped.
 */
object DeviceDeclaration {
    enum class FormFactor(val wire: String) { TV("tv"), PHONE("phone"), TABLET("tablet") }

    fun of(context: Context): Map<String, String> =
        headers(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, BuildConfig.VERSION_NAME, formFactor(context))

    fun headers(manufacturer: String, model: String, release: String, appVersion: String, formFactor: FormFactor): Map<String, String> {
        val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return mapOf(
            "x-media-centaur-device-model" to ascii(name),
            "x-media-centaur-device-os" to ascii("Android $release"),
            "x-media-centaur-app-version" to ascii(appVersion),
            "x-media-centaur-form-factor" to formFactor.wire,
        )
    }

    /** The device's form factor: a TV by its UI mode, a tablet by a 600 dp smallest width, else a phone. */
    fun formFactor(context: Context): FormFactor {
        val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType
        return when {
            uiMode == Configuration.UI_MODE_TYPE_TELEVISION -> FormFactor.TV
            context.resources.configuration.smallestScreenWidthDp >= 600 -> FormFactor.TABLET
            else -> FormFactor.PHONE
        }
    }

    private fun ascii(value: String): String = value.filter { it in ' '..'~' }.trim()
}
