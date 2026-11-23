package cn.antares.helldiver_trainer.bridge

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android 端返回拦截实现，委托给 Compose `BackHandler` 处理系统返回键与预测性返回手势。
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
