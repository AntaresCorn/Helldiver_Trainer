package cn.antares.helldiver_trainer.bridge

import cn.antares.helldiver_trainer.MainActivity
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

actual fun getSharedPreference(name: String): Settings {
    val sharedPreferences = MainActivity.currentActivity!!.getSharedPreferences(
        name,
        android.content.Context.MODE_PRIVATE,
    )
    return SharedPreferencesSettings(sharedPreferences)
}