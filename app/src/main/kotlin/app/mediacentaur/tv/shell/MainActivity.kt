package app.mediacentaur.tv.shell

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import app.mediacentaur.tv.Log
import app.mediacentaur.tv.R
import app.mediacentaur.tv.bridge.Channel
import app.mediacentaur.tv.player.Player

/**
 * The app's one activity: the host-address screen until an address is
 * known, then the WebView shell over an always-attached player surface.
 * The page stays up during a viewing — it is the TV's OSD — and every
 * remote key goes to it; Back goes to the page as its BACK action, which
 * closes an open menu or stops the viewing through the host. Nothing is
 * sent to mpv from here. Back with nothing left to peel finishes the
 * activity (the shell's `onLeave`). Launch with `--es host <url>` to set the address
 * without typing (`mc-tv launch --host`).
 *
 * The activity owns the device's one channel and one mpv, so it is one
 * instance for as long as the app is in use. `singleTask` hands a second
 * launch (an icon tap after `am start`) to this instance's `onNewIntent`;
 * two instances would hold two channels under one player id, each evicting
 * the other on the host every second. `configChanges` keeps a dark-mode
 * switch, a window resize, a rotation (Android 16 ignores the fixed
 * orientation on large screens) or a keyboard from recreating it, which
 * would destroy mpv mid-film; the WebView and the surface resize themselves.
 */
class MainActivity : Activity() {
    private lateinit var hostAddress: HostAddress
    private lateinit var shell: Shell
    private lateinit var player: Player
    private lateinit var webView: WebView
    private lateinit var addressScreen: View
    private lateinit var addressField: EditText
    private var channel: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setContentView(R.layout.activity_main)
        hideSystemBars()
        // The screen stays on while the app is in front: a phone otherwise
        // sleeps mid-film, and a work profile can cap its timeout. A setting
        // for it can come later.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView = findViewById(R.id.web_view)
        addressScreen = findViewById(R.id.address_screen)
        addressField = findViewById(R.id.address_field)
        hostAddress = HostAddress(this)
        player = Player(this, findViewById<SurfaceView>(R.id.player_surface), onViewingChanged = ::onViewingChanged)
        shell = Shell(webView, onPaired = ::onPaired, onLeave = ::finish)
        findViewById<Button>(R.id.address_button).setOnClickListener { submitAddress() }
        addressField.setOnEditorActionListener { _, _, _ -> submitAddress(); true }
        takeHostFrom(intent)
        start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (takeHostFrom(intent)) start()
    }

    private fun takeHostFrom(intent: Intent?): Boolean {
        val url = intent?.getStringExtra(EXTRA_HOST)?.let(HostAddress::normalize) ?: return false
        Log.i("host address from intent: $url")
        hostAddress.url = url
        return true
    }

    private fun start() {
        val url = hostAddress.url
        if (url == null) {
            addressScreen.visibility = View.VISIBLE
            webView.visibility = View.GONE
            addressField.requestFocus()
            return
        }
        addressScreen.visibility = View.GONE
        webView.visibility = View.VISIBLE
        channel?.stop()
        channel = null
        shell.load(url)
    }

    private fun submitAddress() {
        val url = HostAddress.normalize(addressField.text.toString()) ?: return
        hostAddress.url = url
        start()
    }

    private fun onPaired() {
        val url = hostAddress.url ?: return
        if (channel == null) channel = Channel(url, player).also { it.start() }
    }

    // A phone's status and navigation bars take the screen from the picture
    // and the page; hidden, a swipe from the edge shows them for a moment.
    // A TV has neither, so this changes nothing there.
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // The page stays over the video: it is the TV's OSD (campaign M5). The
    // callback remains the shell's hook for a viewing's start and end.
    private fun onViewingChanged(viewing: Boolean) {
        Log.i(if (viewing) "shell: viewing started, page stays up" else "shell: viewing ended")
    }

    // Back and Menu never reach the WebView as key events, so the shell hands
    // them to the page itself; every other key the WebView delivers as is.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (webView.visibility != View.VISIBLE) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> { if (down) shell.back(); true }
            KeyEvent.KEYCODE_MENU -> { if (down) shell.menu(); true }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onDestroy() {
        channel?.stop()
        player.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOST = "host"
    }
}
