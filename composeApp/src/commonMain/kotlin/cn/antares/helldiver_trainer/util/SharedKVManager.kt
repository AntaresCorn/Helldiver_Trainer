package cn.antares.helldiver_trainer.util

import cn.antares.helldiver_trainer.bridge.getSharedPreference
import com.russhwolf.settings.Settings

class SharedKVManager {

    companion object {
        object UserFaction {
            const val HELLDIVER = "helldiver"
            const val AUTOMATON = "automaton"
            const val TERMINID = "terminid"
            const val ILLUMINATE = "illuminate"
        }

        private const val DEFAULT_NAMESPACE = "helldiver_trainer"
        private const val USER_FACTION = "user_faction"
        private const val INFINITE_MODE = "infinite_mode"
    }

    private fun getSharedKVInstance(): Settings = getSharedPreference(DEFAULT_NAMESPACE)

    fun getUserFaction(): String =
        getSharedKVInstance().getString(USER_FACTION, UserFaction.HELLDIVER)

    fun setUserFaction(faction: String) = getSharedKVInstance().putString(USER_FACTION, faction)

    fun isInfiniteMode(): Boolean = getSharedKVInstance().getBoolean(INFINITE_MODE, false)
    fun setInfiniteMode(enabled: Boolean) = getSharedKVInstance().putBoolean(INFINITE_MODE, enabled)
}