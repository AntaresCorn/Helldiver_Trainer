package cn.antares.helldiver_trainer.util

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.architect.kmpessentials.deviceInfo.DevicePlatform
import com.architect.kmpessentials.deviceInfo.KmpDeviceInfo

object MyColor {
    private val HelldiverColor = Color(0xFFFDE701)
    private val AutomatonColor = Color(0xFFFC6C72)
    private val IlluminateColor = Color(0xFF6A3ABA)
    private val TerminidColor = Color(0xFFFDB801)
    val PrimaryColor = HelldiverColor
}

val AppColors = lightColorScheme(
    primary = MyColor.PrimaryColor,
    onPrimary = Color.Black,
)

fun isOnPC(): Boolean {
    return KmpDeviceInfo.getRunningPlatform() == DevicePlatform.Windows ||
            KmpDeviceInfo.getRunningPlatform() == DevicePlatform.Linux ||
            KmpDeviceInfo.getRunningPlatform() == DevicePlatform.MacOS
}