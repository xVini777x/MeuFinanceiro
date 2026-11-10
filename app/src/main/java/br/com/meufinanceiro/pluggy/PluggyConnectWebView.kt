package br.com.meufinanceiro.pluggy

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import br.com.meufinanceiro.BuildConfig
import org.json.JSONObject

/**
 * Faz a ponte do retorno OAuth (deep link) para o WebView que ainda está aberto.
 * O widget espera uma mensagem { source, itemId } para concluir a conexão.
 */
object PluggyOAuthRelay {
    private var deliverer: ((String) -> Boolean)? = null

    fun register(block: (String) -> Boolean) {
        deliverer = block
    }

    fun unregister() {
        deliverer = null
    }

    fun deliver(uri: String): Boolean = deliverer?.invoke(uri) ?: false
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
@Suppress("DEPRECATION")
fun PluggyConnectWebView(
    connectToken: String,
    updateItemId: String?,
    onSuccess: (String, String?) -> Unit,
    onItem: (String, String?) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    onDiagnostic: (String) -> Unit,
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val html = remember(connectToken, updateItemId) { buildConnectHtml(connectToken, updateItemId) }

    BackHandler(onBack = onClose)

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(true)
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.cacheMode = WebSettings.LOAD_DEFAULT

                val defaultUserAgent = settings.userAgentString
                if (defaultUserAgent != null && defaultUserAgent.contains("; wv")) {
                    settings.userAgentString = defaultUserAgent.replace("; wv", "")
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return handleUrl(view, request?.url?.toString().orEmpty(), context, onClose, onDiagnostic)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return handleUrl(view, url.orEmpty(), context, onClose, onDiagnostic)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onDiagnostic("Widget carregado.")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            onDiagnostic("Erro de rede: " + (error?.description?.toString() ?: "desconhecido"))
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?,
                    ): Boolean {
                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        val popup = WebView(context)
                        popup.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                openExternally(
                                    context,
                                    request?.url?.toString().orEmpty(),
                                    onClose,
                                    onDiagnostic,
                                )
                                return true
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                openExternally(context, url.orEmpty(), onClose, onDiagnostic)
                                return true
                            }
                        }
                        transport.webView = popup
                        resultMsg.sendToTarget()
                        return true
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val message = consoleMessage?.message()
                        if (!message.isNullOrBlank()) onDiagnostic("JS: " + message.take(180))
                        return true
                    }
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onSuccess(itemId: String, itemJson: String?) {
                            mainHandler.post { onSuccess(itemId, itemJson) }
                        }

                        @JavascriptInterface
                        fun onItem(itemId: String, itemJson: String?) {
                            mainHandler.post { onItem(itemId, itemJson) }
                        }

                        @JavascriptInterface
                        fun onError(message: String) {
                            mainHandler.post { onError(message) }
                        }

                        @JavascriptInterface
                        fun onClose() {
                            mainHandler.post { onClose() }
                        }

                        @JavascriptInterface
                        fun onDiagnostic(message: String) {
                            mainHandler.post { onDiagnostic(message) }
                        }

                        /**
                         * Recebe os eventos que o widget envia pelo shim ReactNativeWebView.
                         * Em especial OAUTH_OPEN, que precisa abrir o navegador do sistema.
                         */
                        @JavascriptInterface
                        fun onBridgeMessage(payload: String) {
                            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
                            val type = json.optString("type")
                            val message = json.optString("message")
                            when (type) {
                                "OAUTH_OPEN", "LINK_OPEN" -> mainHandler.post {
                                    onDiagnostic(
                                        if (type == "OAUTH_OPEN") {
                                            "Abrindo autorização no navegador do celular."
                                        } else {
                                            "Abrindo link no navegador do celular."
                                        },
                                    )
                                    openExternally(context, message, onClose, onDiagnostic)
                                }

                                else -> mainHandler.post {
                                    if (type.isNotBlank()) onDiagnostic("Evento do widget: " + type)
                                }
                            }
                        }
                    },
                    "AndroidPluggy",
                )

                PluggyOAuthRelay.register { uri -> deliverOAuthResult(this, uri) }

                loadDataWithBaseURL(
                    "https://connect.pluggy.ai/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            PluggyOAuthRelay.unregister()
            webView.destroy()
        },
    )
}

private fun handleUrl(
    webView: WebView?,
    url: String,
    context: Context,
    onClose: () -> Unit,
    onDiagnostic: (String) -> Unit,
): Boolean {
    if (url.isBlank()) return false
    if (url.startsWith("meufinanceiro://")) {
        onDiagnostic("Retorno da autorização recebido.")
        if (!deliverOAuthResult(webView, url)) {
            onClose()
        }
        return true
    }
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = uri.scheme.orEmpty()
    val host = uri.host.orEmpty()
    if (scheme == "http" || scheme == "https") {
        val trusted = host.endsWith("pluggy.ai") || host.endsWith("pluggy.com.br")
        if (!trusted) {
            openExternally(context, url, onClose, onDiagnostic)
            return true
        }
    }
    return false
}

private fun openExternally(
    context: Context,
    url: String,
    onClose: () -> Unit,
    onDiagnostic: (String) -> Unit,
) {
    if (url.isBlank()) return
    if (url.startsWith("meufinanceiro://")) {
        onDiagnostic("Retorno da autorização recebido.")
        onClose()
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        onDiagnostic("Abrindo no navegador do celular.")
    }.onFailure {
        onDiagnostic("Não foi possível abrir o navegador.")
    }
}

/**
 * Converte o deep link de retorno em uma mensagem que o widget entende:
 * { source: 'connector-oauth-login', itemId: '...' } ou { source, error }.
 */
private fun deliverOAuthResult(webView: WebView?, uri: String): Boolean {
    if (webView == null) return false
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
    val itemId = parsed.getQueryParameter("itemId")
        ?: parsed.getQueryParameter("item_id")
        ?: parsed.getQueryParameter("id")
    val error = parsed.getQueryParameter("error")
        ?: parsed.getQueryParameter("error_description")
    if (itemId.isNullOrBlank() && error.isNullOrBlank()) return false

    val payload = JSONObject().apply {
        put("source", "connector-oauth-login")
        if (!itemId.isNullOrBlank()) put("itemId", itemId)
        if (!error.isNullOrBlank()) put("error", error)
    }
    val script = "window.dispatchEvent(new MessageEvent('message', { data: " +
        payload.toString() + " }));"
    webView.post {
        runCatching { webView.evaluateJavascript(script, null) }
    }
    return true
}

private fun buildConnectHtml(connectToken: String, updateItemId: String?): String {
    val token = JSONObject.quote(connectToken)
    val updateItemLine = if (updateItemId.isNullOrBlank()) {
        ""
    } else {
        "updateItem: " + JSONObject.quote(updateItemId) + ","
    }
    val includeSandbox = if (BuildConfig.DEBUG) "true" else "false"

    return """
        |<!DOCTYPE html>
        |<html lang="pt-BR">
        |<head>
        |  <meta charset="UTF-8" />
        |  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
        |  <style>
        |    html, body { margin: 0; padding: 0; height: 100%; background: #070B14; }
        |  </style>
        |  <script>
        |    // O widget usa esta ponte em WebViews nativos para o fluxo OAuth.
        |    window.ReactNativeWebView = {
        |      postMessage: function (payload) {
        |        if (window.AndroidPluggy && window.AndroidPluggy.onBridgeMessage) {
        |          window.AndroidPluggy.onBridgeMessage(String(payload));
        |        }
        |      }
        |    };
        |  </script>
        |</head>
        |<body>
        |  <script src="https://cdn.pluggy.ai/pluggy-connect/v2.8.2/pluggy-connect.js"></script>
        |  <script>
        |    (function () {
        |      function report(message) {
        |        if (window.AndroidPluggy) { window.AndroidPluggy.onError(message); }
        |      }
        |      function diagnose(message) {
        |        if (window.AndroidPluggy && window.AndroidPluggy.onDiagnostic) {
        |          window.AndroidPluggy.onDiagnostic(message);
        |        }
        |      }
        |      try {
        |        var pluggy = new PluggyConnect({
        |          connectToken: $token,
        |          $updateItemLine
        |          includeSandbox: $includeSandbox,
        |          theme: 'dark',
        |          language: 'pt',
        |          countries: ['BR'],
        |          allowFullscreen: true,
        |          allowConnectInBackground: true,
        |          onSuccess: function (data) {
        |            var item = (data && data.item) ? data.item : data;
        |            var id = (item && item.id) ? item.id : '';
        |            if (window.AndroidPluggy) {
        |              window.AndroidPluggy.onSuccess(id, JSON.stringify(item || {}));
        |            }
        |          },
        |          onError: function (error) {
        |            var item = error && error.data ? error.data.item : null;
        |            var status = item && item.executionStatus ? item.executionStatus : '';
        |            var base = (error && error.message) ? error.message : 'Falha ao conectar o banco.';
        |            report(status ? (base + ' (' + status + ')') : base);
        |          },
        |          onClose: function () {
        |            if (window.AndroidPluggy) { window.AndroidPluggy.onClose(); }
        |          },
        |          onEvent: function (payload) {
        |            if (!payload) { return; }
        |            var event = payload.event || '';
        |            var item = payload.item || null;
        |            if (event === 'ITEM_RESPONSE' && item && item.id && window.AndroidPluggy) {
        |              window.AndroidPluggy.onItem(item.id, JSON.stringify(item));
        |            }
        |            var suffix = (item && item.executionStatus) ? ' (' + item.executionStatus + ')' : '';
        |            diagnose('Evento: ' + event + suffix);
        |          }
        |        });
        |        pluggy.init();
        |      } catch (e) {
        |        report((e && e.message) ? e.message : String(e));
        |      }
        |    })();
        |  </script>
        |</body>
        |</html>
    """.trimMargin()
}