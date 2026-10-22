package app.mediacentaur.tv.shell

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.mediacentaur.tv.BuildConfig
import app.mediacentaur.tv.Log

/**
 * The WebView showing the host's UI. An unpaired device is sent by the host
 * to its pairing page; a page that is not the pairing page means the
 * device's session holds a player, and [onPaired] fires once per load so
 * the channel can open. Back on the page is a synthetic Escape, which the
 * host's input system treats as its BACK action.
 */
class Shell(private val webView: WebView, private val onPaired: () -> Unit) {
    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
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
        }
    }

    fun load(url: String) {
        Log.i("loading $url")
        webView.loadUrl(url)
    }

    fun back() {
        webView.evaluateJavascript(
            "document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true, cancelable: true}))",
            null,
        )
    }
}
