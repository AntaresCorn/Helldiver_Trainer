package cn.antares.helldiver_trainer.util

import cn.antares.helldiver_trainer.bridge.getSharedPreference
import com.russhwolf.settings.Settings

class SharedKVManager {

    companion object {
        private const val DEFAULT_NAMESPACE = "helldiver_trainer"
    }

    private fun getSharedKVInstance(): Settings = getSharedPreference(DEFAULT_NAMESPACE)

    fun getUserFaction(): String = getSharedKVInstance().getString("user_faction", "helldiver")
    fun setUserFaction(faction: String) = getSharedKVInstance().putString("user_faction", faction)
}