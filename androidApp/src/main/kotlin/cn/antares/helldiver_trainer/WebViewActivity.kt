package cn.antares.helldiver_trainer

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class WebViewActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_URL) ?: ""
        setContent {
            AndroidWebViewScreen(url = url, onClose = { finish() })
        }
    }

    @Composable
    fun AndroidWebViewScreen(
        url: String,
        modifier: Modifier = Modifier,
        onClose: () -> Unit = {}
    ) {
        val context = LocalContext.current
        // remember WebView to preserve state across recompositions
        val webView = remember {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                // 清理 WebView 避免内存泄露
                webView.stopLoading()
                webView.webChromeClient = null
                webView.destroy()
            }
        }

        // back handling: 如果 WebView 能回退则优先回退，否则调用 onClose()
        BackHandler {
            if (webView.canGoBack()) webView.goBack()
            else onClose()
        }

        AndroidView(factory = { webView }, update = { it.loadUrl(url) }, modifier = modifier)
    }
}