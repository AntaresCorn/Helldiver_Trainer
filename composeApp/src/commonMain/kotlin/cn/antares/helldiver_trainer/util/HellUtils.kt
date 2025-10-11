package cn.antares.helldiver_trainer.util

import com.architect.kmpessentials.deviceInfo.DevicePlatform
import com.architect.kmpessentials.deviceInfo.KmpDeviceInfo

object HellUtils {
    fun isOnPC(): Boolean {
        return KmpDeviceInfo.getRunningPlatform() == DevicePlatform.Windows ||
                KmpDeviceInfo.getRunningPlatform() == DevicePlatform.Linux ||
                KmpDeviceInfo.getRunningPlatform() == DevicePlatform.MacOS
    }
}