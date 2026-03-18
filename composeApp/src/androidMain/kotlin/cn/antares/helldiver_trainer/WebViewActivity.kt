package cn.antares.helldiver_trainer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun AndroidWebViewScreen(
        url: String,
        modifier: Modifier = Modifier,
        onClose: () -> Unit = {},
    ) {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(true) }
        var progress by remember { mutableIntStateOf(0) }
        var hasError by remember { mutableStateOf(false) }
        var pageErrorCode by remember { mutableIntStateOf(0) }

        val webView = remember {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.DKGRAY)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        hasError = false
                        isLoading = true
                        progress = 0
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.postVisualStateCallback(
                            System.currentTimeMillis(),
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(requestId: Long) {
                                    isLoading = false
                                }
                            },
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            hasError = true
                            pageErrorCode = error?.errorCode ?: 0
                            isLoading = false
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?,
                    ) {
                        hasError = true
                        pageErrorCode = errorCode
                        isLoading = false
                    }
                }
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

        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { webView },
                update = { it.loadUrl(url) },
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                modifier = Modifier.align(Alignment.Center),
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(durationMillis = 800)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Text(
                        text = "$progress%",
                        modifier = Modifier.padding(top = 4.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Text(
                        text = "请民主地等待...\n（境外网站，访问速度较慢）",
                        modifier = Modifier.padding(top = 10.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (hasError) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "加载失败 (错误码: $pageErrorCode)")
                    Button(onClick = { webView.loadUrl(url) }) {
                        Text(text = "重试")
                    }
                }
            }
        }
    }
}