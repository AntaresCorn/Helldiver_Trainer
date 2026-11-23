package cn.antares.helldiver_trainer.bridge

import androidx.compose.runtime.Composable

/**
 * Desktop 端返回拦截空实现；桌面端返回交互由页面键盘监听统一处理。
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // Desktop 端直接在 WikiPage 中通过 Esc 键事件处理返回
}
