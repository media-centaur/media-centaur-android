package app.mediacentaur.tv.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import app.mediacentaur.tv.Log
import app.mediacentaur.tv.player.Mpv
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The player channel: one WebSocket to the host's `/players/channel`,
 * opened with the WebView's cookies so the host admits the paired player,
 * carrying one IPC frame per text message each way. Reconnects with
 * backoff from 1 s to 30 s whenever it drops; the host's pings are answered
 * by OkHttp. Every frame is logged at debug level: `>>` host to mpv,
 * `<<` mpv to host.
 */
class Channel(hostUrl: String, mpv: Mpv) : WebSocketListener() {
    private val url = hostUrl.replaceFirst("http", "ws").trimEnd('/') + "/players/channel"
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val ipc = Ipc(mpv, schedule = { delay, action -> timer.schedule(action, delay, TimeUnit.MILLISECONDS) })

    @Volatile private var socket: WebSocket? = null
    @Volatile private var stopped = false
    private var backoffMs = 1000L

    fun start() {
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        socket?.close(1000, "app closing")
        socket = null
        ipc.sink = null
        timer.shutdownNow()
    }

    private fun connect() {
        if (stopped) return
        Log.i("channel: connecting $url")
        socket = client.newWebSocket(Request.Builder().url(url).build(), this)
    }

    private fun retry() {
        if (stopped) return
        val delay = backoffMs
        backoffMs = minOf(backoffMs * 2, 30_000L)
        Log.i("channel: retrying in ${delay}ms")
        main.postDelayed({ connect() }, delay)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        backoffMs = 1000L
        Log.i("channel: up")
        ipc.sink = { text ->
            Log.d("<< ${text.take(200)}")
            webSocket.send(text)
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(">> ${text.take(200)}")
        ipc.handle(text)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.i("channel: closed ($code $reason)")
        ipc.sink = null
        retry()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w("channel: failed (${response?.code ?: "no response"}) — ${t.message}")
        ipc.sink = null
        retry()
    }
}

/** OkHttp reads and writes the WebView's cookies: one jar, one identity. */
class WebViewCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        for (cookie in cookies) manager.setCookie(url.toString(), cookie.toString())
        manager.flush()
    }
}
