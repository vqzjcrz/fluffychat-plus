package dev.hush.client.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.hush.client.discord.DiscordHeaders
import dev.hush.client.ui.theme.LocalPalette
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGIN_URL = "https://discord.com/login"
private const val DISCORD_ORIGIN = "https://discord.com"
private val ALLOWED_HOSTS = listOf("discord.com", "discordapp.com", "discord.gg", "hcaptcha.com")

/**
 * Watches the Discord web app's own API requests and hands the Authorization header
 * to Hush. Runs before Discord's scripts so the very first authenticated call is seen.
 */
private const val HOOK_JS = """
(function () {
  if (window.__hushHooked) return;
  window.__hushHooked = true;
  function send(t) {
    try {
      if (typeof t === 'string' && t.length > 30 && t.indexOf('Bot ') !== 0 && window.HushBridge) {
        window.HushBridge.postMessage(t);
      }
    } catch (e) {}
  }
  var setHeader = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
    if (String(name).toLowerCase() === 'authorization') send(value);
    return setHeader.apply(this, arguments);
  };
  var realFetch = window.fetch;
  if (realFetch) {
    window.fetch = function (input, init) {
      try {
        var h = init && init.headers;
        if (h) send(typeof h.get === 'function' ? h.get('Authorization') : (h.Authorization || h.authorization));
        if (input && typeof input === 'object' && input.headers && typeof input.headers.get === 'function') {
          send(input.headers.get('Authorization'));
        }
      } catch (e) {}
      return realFetch.apply(this, arguments);
    };
  }
})();
"""

/** Fallback: read the stored session from a fresh frame's localStorage once the app has loaded. */
private const val STORAGE_JS = """
(function () {
  try {
    var f = document.createElement('iframe');
    f.style.display = 'none';
    document.body.appendChild(f);
    var t = f.contentWindow.localStorage.getItem('token');
    f.remove();
    return t;
  } catch (e) { return null; }
})();
"""

private fun hostAllowed(host: String?): Boolean =
    host != null && ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }

private class LegacyBridge(private val deliver: (String?) -> Unit) {
    @JavascriptInterface
    fun postMessage(token: String?) = deliver(token)
}

/** Full-screen Discord login. Calls [onToken] once with the session token. */
@Composable
fun WebLogin(onToken: (String) -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    val tokenCallback by rememberUpdatedState(onToken)
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onCancel()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = p.text)
            }
            Column(Modifier.weight(1f)) {
                Text("Log in to Discord", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Hush keeps your session, never your password.", color = p.muted, fontSize = 12.sp)
            }
            if (loading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.dp)
            }
        }
        HorizontalDivider(color = p.divider, thickness = 0.5.dp)
        AndroidView(
            factory = { ctx ->
                createLoginWebView(ctx, onLoading = { loading = it }) { tokenCallback(it) }
                    .also { webView = it }
            },
            onRelease = { destroyLoginWebView(it) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private fun createLoginWebView(
    context: Context,
    onLoading: (Boolean) -> Unit,
    onToken: (String) -> Unit,
): WebView {
    // Start from a clean slate every time.
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()

    val main = Handler(Looper.getMainLooper())
    val delivered = AtomicBoolean(false)
    val deliver: (String?) -> Unit = { raw ->
        val token = raw?.filter { it != '"' && it != '\\' }?.trim()
        if (token != null && token != "null" && token.length > 30 && !token.startsWith("Bot ") &&
            delivered.compareAndSet(false, true)
        ) {
            main.post { onToken(token) }
        }
    }

    val web = WebView(context)
    web.settings.javaScriptEnabled = true
    web.settings.domStorageEnabled = true
    web.settings.userAgentString = DiscordHeaders.USER_AGENT

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(web, "HushBridge", setOf(DISCORD_ORIGIN)) { _, message, _, _, _ ->
            deliver(message.data)
        }
    } else {
        web.addJavascriptInterface(LegacyBridge(deliver), "HushBridge")
    }

    val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    if (documentStart) WebViewCompat.addDocumentStartJavaScript(web, HOOK_JS, setOf(DISCORD_ORIGIN))

    fun readStorageSoon(view: WebView, attempt: Int = 0) {
        if (delivered.get() || attempt >= 8) return
        main.postDelayed({
            if (!delivered.get()) {
                view.evaluateJavascript(STORAGE_JS) { result -> deliver(result) }
                readStorageSoon(view, attempt + 1)
            }
        }, 900)
    }

    web.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val isWeb = uri.scheme == "https" || uri.scheme == "http"
            // Stay on Discord (and its captcha); ignore "open in app" links and outside sites.
            return !(isWeb && hostAllowed(uri.host))
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            onLoading(true)
            if (!documentStart) view.evaluateJavascript(HOOK_JS, null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onLoading(false)
            if (!documentStart) view.evaluateJavascript(HOOK_JS, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            // Discord moves to /channels/... after a successful login without a full page load.
            if (url != null && (url.contains("/channels") || url.contains("/app"))) readStorageSoon(view)
        }
    }

    web.loadUrl(LOGIN_URL)
    return web
}

/** Wipes the web session from the WebView. The token Hush saved stays valid. */
private fun destroyLoginWebView(web: WebView) {
    web.stopLoading()
    web.clearHistory()
    web.clearCache(true)
    web.clearFormData()
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
    web.destroy()
}
