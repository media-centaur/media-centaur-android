package app.mediacentaur.tv.player

import android.view.KeyEvent

/**
 * The remote's keys as mpv key names, sent with `keypress` while a file
 * plays. What each name does is the TV's `input.conf` (assets/mpv), where
 * a visible pill's forced ENTER wins while it shows. Back is not here: it
 * stops the viewing.
 */
object RemoteKeys {
    private val names = mapOf(
        KeyEvent.KEYCODE_DPAD_CENTER to "ENTER",
        KeyEvent.KEYCODE_ENTER to "ENTER",
        KeyEvent.KEYCODE_DPAD_LEFT to "LEFT",
        KeyEvent.KEYCODE_DPAD_RIGHT to "RIGHT",
        KeyEvent.KEYCODE_DPAD_UP to "UP",
        KeyEvent.KEYCODE_DPAD_DOWN to "DOWN",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "PLAYPAUSE",
        KeyEvent.KEYCODE_MEDIA_PLAY to "PLAY",
        KeyEvent.KEYCODE_MEDIA_PAUSE to "PAUSE",
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "FORWARD",
        KeyEvent.KEYCODE_MEDIA_REWIND to "REWIND",
        KeyEvent.KEYCODE_MENU to "MENU",
    )

    fun name(keyCode: Int): String? = names[keyCode]
}
