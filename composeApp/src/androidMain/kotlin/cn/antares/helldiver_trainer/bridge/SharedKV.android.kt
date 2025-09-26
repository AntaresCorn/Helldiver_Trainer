package cn.antares.helldiver_trainer.bridge

import android.app.Application
import android.content.ContextWrapper
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

actual fun getSharedPreference(name: String): Settings {
    val sharedPreferences = ContextWrapper(Application()).getSharedPreferences(
        name,
        android.content.Context.MODE_PRIVATE,
    )
    return SharedPreferencesSettings(sharedPreferences)
}