package cn.antares.helldiver_trainer.bridge

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

actual fun getSharedPreference(name: String): Settings {
    val delegate = Preferences.userRoot().node(name)
    return PreferencesSettings(delegate)
}