package cn.antares.helldiver_trainer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.WindowInfo
import cn.antares.helldiver_trainer.util.WindowInfoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArrowButtonConfig(
    val standardKeyboardMode: Boolean = false,
    val fixedPositionMode: Boolean = true,
    val buttonSize: Int = 0,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val upOffsetX: Int = 0,
    val upOffsetY: Int = 0,
    val leftOffsetX: Int = 0,
    val leftOffsetY: Int = 0,
    val downOffsetX: Int = 0,
    val downOffsetY: Int = 0,
    val rightOffsetX: Int = 0,
    val rightOffsetY: Int = 0,
) {

    companion object {
        fun String.toArrowButtonConfig(): ArrowButtonConfig {
            this.split(",").let {
                return ArrowButtonConfig(
                    standardKeyboardMode = it[0].toBoolean(),
                    fixedPositionMode = it[1].toBoolean(),
                    buttonSize = it[2].toInt(),
                    offsetX = it[3].toInt(),
                    offsetY = it[4].toInt(),
                    upOffsetX = it[5].toInt(),
                    upOffsetY = it[6].toInt(),
                    leftOffsetX = it[7].toInt(),
                    leftOffsetY = it[8].toInt(),
                    downOffsetX = it[9].toInt(),
                    downOffsetY = it[10].toInt(),
                    rightOffsetX = it[11].toInt(),
                    rightOffsetY = it[12].toInt(),
                )
            }
        }
    }

    override fun toString(): String {
        return "$standardKeyboardMode,$fixedPositionMode,$buttonSize,$offsetX,$offsetY," +
                "$upOffsetX,$upOffsetY,$leftOffsetX,$leftOffsetY,$downOffsetX,$downOffsetY,$rightOffsetX,$rightOffsetY"
    }
}

class ArrowButtonViewModel(
    private val windowInfoManager: WindowInfoManager,
    private val sharedKVManager: SharedKVManager,
) : ViewModel() {

    private val _config = MutableStateFlow(ArrowButtonConfig())
    val config = _config.asStateFlow()
    private var lastWindowInfo: WindowInfo? = null

    init {
        viewModelScope.launch {
            windowInfoManager.windowInfoFlow.collect {
                lastWindowInfo = it
                updateConfigWhenScreenChanged(it)
            }
        }
    }

    override fun onCleared() {
        lastWindowInfo = null
        super.onCleared()
    }

    private fun updateConfigWhenScreenChanged(windowInfo: WindowInfo) {
        println("ArrowButtonViewModel updateConfigWhenScreenChanged")
        if (HellUtils.isOnPC().not()) {
            val savedConfig = if (windowInfo.isTabletPortrait() || windowInfo.isPhonePortrait()) {
                sharedKVManager.getButtonConfigPortrait()
            } else {
                sharedKVManager.getButtonConfigLandscape()
            }
            if (savedConfig != null) {
                setPositions(savedConfig)
            } else {
                reset()
            }
        }
    }

    fun setStandardKeyboardMode(enabled: Boolean) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(cur.copy(standardKeyboardMode = enabled))
        }
    }

    fun setFixedPositionMode(enabled: Boolean) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(cur.copy(fixedPositionMode = enabled))
        }
    }

    fun setButtonSize(size: Int) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(cur.copy(buttonSize = size))
        }
    }

    fun savePositions() {
        if (lastWindowInfo != null) {
            if (lastWindowInfo!!.isTabletPortrait() || lastWindowInfo!!.isPhonePortrait()) {
                sharedKVManager.setButtonConfigPortrait(_config.value)
            } else {
                sharedKVManager.setButtonConfigLandscape(_config.value)
            }
        }
    }

    fun setPositions(new: ArrowButtonConfig) {
        viewModelScope.launch {
            _config.emit(new)
        }
    }

    fun reset() {
        viewModelScope.launch {
            val defaultSize = if (lastWindowInfo?.isTwoPaneCandidate() == true) {
                if (lastWindowInfo!!.isPhoneLandscape()) {
                    85
                } else {
                    105
                }
            } else {
                95
            }
            _config.emit(ArrowButtonConfig(buttonSize = defaultSize))
        }
    }

    fun updateOffset(x: Int? = null, y: Int? = null) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(
                cur.copy(
                    offsetX = x ?: cur.offsetX,
                    offsetY = y ?: cur.offsetY,
                ),
            )
        }
    }

    fun updateUpOffset(x: Int? = null, y: Int? = null) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(
                cur.copy(
                    upOffsetX = x ?: cur.upOffsetX,
                    upOffsetY = y ?: cur.upOffsetY,
                ),
            )
        }
    }

    fun updateLeftOffset(x: Int? = null, y: Int? = null) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(
                cur.copy(
                    leftOffsetX = x ?: cur.leftOffsetX,
                    leftOffsetY = y ?: cur.leftOffsetY,
                ),
            )
        }
    }

    fun updateDownOffset(x: Int? = null, y: Int? = null) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(
                cur.copy(
                    downOffsetX = x ?: cur.downOffsetX,
                    downOffsetY = y ?: cur.downOffsetY,
                ),
            )
        }
    }

    fun updateRightOffset(x: Int? = null, y: Int? = null) {
        viewModelScope.launch {
            val cur = _config.value
            _config.emit(
                cur.copy(
                    rightOffsetX = x ?: cur.rightOffsetX,
                    rightOffsetY = y ?: cur.rightOffsetY,
                ),
            )
        }
    }
}