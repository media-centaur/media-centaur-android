package app.mediacentaur.tv.shell

import android.content.Context

/** The host's base URL: with the cookie jar, the only state the app keeps. */
class HostAddress(context: Context) {
    private val prefs = context.getSharedPreferences("host", Context.MODE_PRIVATE)

    var url: String?
        get() = prefs.getString("url", null)
        set(value) { prefs.edit().putString("url", value).apply() }

    companion object {
        /** `192.168.1.5:4003` → `http://192.168.1.5:4003`; trailing slashes dropped; null when blank. */
        fun normalize(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            return withScheme
        }
    }
}
