package app.mediacentaur.tv

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView

/**
 * The app's one activity. For now it shows the app's name; the pairing
 * screen, the WebView shell, the libmpv surface and the bridge arrive in the
 * TV app skeleton milestone (see the host repository's
 * `campaigns/remote-players.md`).
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")
        setContentView(
            TextView(this).apply {
                text = getString(R.string.app_name)
                textSize = 48f
                gravity = Gravity.CENTER
            },
        )
    }

    companion object {
        /** The logcat tag every log line of the app carries; `mc-tv log` filters on it. */
        const val TAG = "MediaCentaur"
    }
}
