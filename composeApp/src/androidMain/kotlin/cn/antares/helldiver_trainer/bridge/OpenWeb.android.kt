package cn.antares.helldiver_trainer.bridge

import android.content.Intent
import androidx.core.net.toUri
import cn.antares.helldiver_trainer.MainActivity
import cn.antares.helldiver_trainer.WebViewActivity

actual fun openWebPage(url: String, useSystemBrowser: Boolean) {
    val ctx = MainActivity.currentActivity ?: return
    val intent = if (useSystemBrowser) {
        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(ctx, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    ctx.startActivity(intent)
}