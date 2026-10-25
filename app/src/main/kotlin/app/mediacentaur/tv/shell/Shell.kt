package app.mediacentaur.tv.shell

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.mediacentaur.tv.BuildConfig
import app.mediacentaur.tv.Log

/**
 * The WebView showing the host's UI. An unpaired device is sent by the host
 * to its pairing page; a page that is not the pairing page means the
 * device's session holds a player, and [onPaired] fires once per load so
 * the channel can open. Two keys the WebView swallows reach the page as
 * synthetic keydowns the host's input system knows: Back as Escape (its
 * BACK action) and Menu as ContextMenu (the track menu during a viewing).
 *
 * Back leaves the app, as the platform's Back does, when the page has
 * nothing left to peel: the page calls `MediaCentaurTV.leave()` (a
 * JavaScript interface) from its main menu, and a page without the input
 * system (the pairing page, an error) leaves on the first Back. Google
 * Play's TV review requires it (TV-DB).
 */
class Shell(
    private val webView: WebView,
    private val onPaired: () -> Unit,
    private val onLeave: () -> Unit,
) {
    init {
        webView.addJavascriptInterface(ShellInterface(), "MediaCentaurTV")
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        // Chromium learns transparency only from this call, never from the XML background;
        // without it the WebView paints its own base colour over the player's surface.
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // A debug build loads against a dev host whose script has no
            // digest in its path; the WebView's cache would keep a rebuilt
            // script from landing until the entry expired. A release host
            // serves digested assets, which cache safely.
            if (BuildConfig.DEBUG) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.i("page: $url")
                CookieManager.getInstance().flush()
                if (Uri.parse(url).path != "/pair") onPaired()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Log.w("page failed: ${request.url} — ${error.description}")
            }

            // A dead renderer must not take the app (and a viewing) down: reload the page instead.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w("page renderer gone (crashed: ${detail.didCrash()}); reloading")
                view.post { view.reload() }
                return true
            }
        }
    }

    fun load(url: String) {
        Log.i("loading $url")
        webView.loadUrl(url)
    }

    fun back() {
        webView.evaluateJavascript(
            "document.querySelector('[phx-hook=\"InputSystem\"]') ? (${keyScript("Escape")}, 'page') : 'leave'",
        ) { result -> if (result == "\"leave\"") onLeave() }
    }

    fun menu() = key("ContextMenu")

    // Dispatched on what a real key press targets — the focused element, or
    // <body> when nothing is focused — never on the document itself: the
    // page's keyboard source reads the event's target as an element and a
    // document-targeted keydown threw before the key was seen (the Sony,
    // 2026-10-22).
    private fun key(name: String) {
        webView.evaluateJavascript(keyScript(name), null)
    }

    private fun keyScript(name: String) =
        "(document.activeElement || document.body).dispatchEvent(new KeyboardEvent('keydown', {key: '$name', bubbles: true, cancelable: true}))"

    // Called on a WebView binder thread; the activity is left on the UI thread.
    private inner class ShellInterface {
        @JavascriptInterface
        fun leave() {
            webView.post { onLeave() }
        }
    }
}
