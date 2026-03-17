package cn.antares.helldiver_trainer

import android.os.Bundle
import androidx.activity.compose.setContent
import cn.antares.helldiver_trainer.ui.App

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initResource()
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        releaseResource()
        super.onDestroy()
    }

    private fun initResource() {
        currentActivity = this
        AndroidSoundPlayer.instance.init(this)
    }

    private fun releaseResource() {
        currentActivity = null
        AndroidSoundPlayer.instance.release()
    }

    companion object {
        var currentActivity: MainActivity? = null
            private set
    }
}
