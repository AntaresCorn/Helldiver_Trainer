package cn.antares.helldiver_trainer.util

import cn.antares.helldiver_trainer.bridge.getSharedPreference
import cn.antares.helldiver_trainer.viewmodel.ArrowButtonConfig
import cn.antares.helldiver_trainer.viewmodel.ArrowButtonConfig.Companion.toArrowButtonConfig
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
        private const val STRATAGEM_SELECT_MODE = "stratagem_select_mode"
        private const val SELECTED_STRATAGEM_IDS = "selected_stratagem_ids"
        private const val BUTTON_CONFIG_PORTRAIT = "button_config_portrait"
        private const val BUTTON_CONFIG_LANDSCAPE = "button_config_landscape"
    }

    private fun getSharedKVInstance(): Settings = getSharedPreference(DEFAULT_NAMESPACE)

    fun getUserFaction(): String =
        getSharedKVInstance().getString(USER_FACTION, UserFaction.HELLDIVER)

    fun setUserFaction(faction: String) = getSharedKVInstance().putString(USER_FACTION, faction)

    fun isInfiniteMode(): Boolean = getSharedKVInstance().getBoolean(INFINITE_MODE, false)

    fun setInfiniteMode(enabled: Boolean) = getSharedKVInstance().putBoolean(INFINITE_MODE, enabled)

    fun isStratagemSelectMode(): Boolean =
        getSharedKVInstance().getBoolean(STRATAGEM_SELECT_MODE, false)

    fun setStratagemSelectMode(enabled: Boolean) =
        getSharedKVInstance().putBoolean(STRATAGEM_SELECT_MODE, enabled)

    fun getSelectedStratagemIDs(): Set<String> {
        getSharedKVInstance().getStringOrNull(SELECTED_STRATAGEM_IDS).let {
            return if (it.isNullOrEmpty()) {
                emptySet()
            } else {
                it.split(",").toSet()
            }
        }
    }

    fun setSelectedStratagemIDs(ids: Set<String>) {
        getSharedKVInstance().putString(SELECTED_STRATAGEM_IDS, ids.joinToString(","))
    }

    fun getButtonConfigPortrait(): ArrowButtonConfig? =
        getSharedKVInstance().getStringOrNull(BUTTON_CONFIG_PORTRAIT)?.toArrowButtonConfig()

    fun setButtonConfigPortrait(config: ArrowButtonConfig) {
        getSharedKVInstance().putString(BUTTON_CONFIG_PORTRAIT, config.toString())
    }

    fun getButtonConfigLandscape(): ArrowButtonConfig? =
        getSharedKVInstance().getStringOrNull(BUTTON_CONFIG_LANDSCAPE)?.toArrowButtonConfig()

    fun setButtonConfigLandscape(config: ArrowButtonConfig) {
        getSharedKVInstance().putString(BUTTON_CONFIG_LANDSCAPE, config.toString())
    }
}