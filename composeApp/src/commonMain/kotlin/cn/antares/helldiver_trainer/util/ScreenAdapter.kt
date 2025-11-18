package cn.antares.helldiver_trainer.util

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class WindowType { Compact, Medium, Expanded }

/**
 * 手机竖屏：widthType=Compact, heightType=Medium
 * 手机横屏：widthType=Expanded, heightType=Compact
 *          widthType=Medium, heightType=Compact
 * 平板竖屏：widthType=Medium, heightType=Expanded
 * 平板横屏：widthType=Expanded, heightType=Medium
 */
data class WindowInfo(
    val widthType: WindowType,
    val heightType: WindowType,
) {
    fun isPhonePortrait() = widthType == WindowType.Compact && heightType != WindowType.Compact

    fun isPhoneLandscape() = widthType != WindowType.Compact && heightType == WindowType.Compact

    fun isTabletPortrait() = widthType != WindowType.Compact && heightType == WindowType.Expanded

    fun isTabletLandscape() = widthType == WindowType.Expanded && heightType != WindowType.Compact

    // 是否适合两栏/分栏布局
    fun isTwoPaneCandidate() = (widthType == WindowType.Expanded) ||
            (widthType == WindowType.Medium && heightType != WindowType.Compact)
}

interface WindowInfoManager {
    val windowInfoFlow: StateFlow<WindowInfo>

    @Composable
    fun Init()
}

class WindowInfoManagerImpl : WindowInfoManager {
    private val _flow = MutableStateFlow(WindowInfo(WindowType.Compact, WindowType.Compact))
    override val windowInfoFlow: StateFlow<WindowInfo> = _flow

    @Composable
    override fun Init() {
        BoxWithConstraints {
            val w = maxWidth
            val h = maxHeight
            val widthType = when {
                w < 600.dp -> WindowType.Compact
                w < 840.dp -> WindowType.Medium
                else -> WindowType.Expanded
            }
            val heightType = when {
                h < 480.dp -> WindowType.Compact
                h < 900.dp -> WindowType.Medium
                else -> WindowType.Expanded
            }
            val info = WindowInfo(widthType, heightType)
            LaunchedEffect(info) {
                _flow.value = info
            }
        }
    }
}