package cn.antares.helldiver_trainer.util

import cn.antares.helldiver_trainer.bridge.DevicePlatform
import cn.antares.helldiver_trainer.bridge.getCurrentPlatform

object HellUtils {
    var checkedUpdate = false

    fun isOnPC(): Boolean = getCurrentPlatform() == DevicePlatform.Windows
}