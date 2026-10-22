package app.mediacentaur.tv

import android.util.Log as AndroidLog

/**
 * The one tag the app's own log lines carry. libmpv's lines go under [MPV_TAG].
 * `mc-tv log` filters logcat on both.
 */
object Log {
    const val TAG = "MediaCentaur"
    const val MPV_TAG = "mpv"

    fun d(message: String) { AndroidLog.d(TAG, message) }
    fun i(message: String) { AndroidLog.i(TAG, message) }
    fun w(message: String, error: Throwable? = null) { AndroidLog.w(TAG, message, error) }
    fun e(message: String, error: Throwable? = null) { AndroidLog.e(TAG, message, error) }
}
