package cn.antares.helldiver_trainer.bridge

import android.content.Intent
import cn.antares.helldiver_trainer.MainActivity
import cn.antares.helldiver_trainer.WebViewActivity

actual fun openWebPage(url: String) {
    val ctx = MainActivity.currentActivity ?: return
    val intent = Intent(ctx, WebViewActivity::class.java).apply {
        putExtra(WebViewActivity.EXTRA_URL, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}