package app.mediacentaur.tv.shell

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import app.mediacentaur.tv.Log
import app.mediacentaur.tv.R
import app.mediacentaur.tv.bridge.Channel
import app.mediacentaur.tv.player.Player
import app.mediacentaur.tv.player.RemoteKeys

/**
 * The app's one activity: the host-address screen until an address is
 * known, then the WebView shell over an always-attached player surface.
 * While a file plays the WebView hides and the remote's keys go to mpv;
 * otherwise they go to the page. Launch with `--es host <url>` to set the
 * address without typing (`mc-tv launch --host`).
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
        webView = findViewById(R.id.web_view)
        addressScreen = findViewById(R.id.address_screen)
        addressField = findViewById(R.id.address_field)
        hostAddress = HostAddress(this)
        player = Player(this, findViewById<SurfaceView>(R.id.player_surface), onViewingChanged = ::onViewingChanged)
        shell = Shell(webView, onPaired = ::onPaired)
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

    // The page stays over the video: it is the TV's OSD (campaign M5). The
    // callback remains the shell's hook for a viewing's start and end.
    private fun onViewingChanged(viewing: Boolean) {
        Log.i(if (viewing) "shell: viewing started, page stays up" else "shell: viewing ended")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (player.viewing) {
            if (down) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) player.stop()
                else RemoteKeys.name(event.keyCode)?.let(player::keypress)
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && webView.visibility == View.VISIBLE) {
            if (down) shell.back()
            return true
        }
        return super.dispatchKeyEvent(event)
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
