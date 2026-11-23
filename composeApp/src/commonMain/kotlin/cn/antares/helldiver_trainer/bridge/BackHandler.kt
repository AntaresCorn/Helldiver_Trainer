package cn.antares.helldiver_trainer.bridge

import androidx.compose.runtime.Composable

/**
 * 跨平台返回按键/手势拦截桥接组件，各平台按自身交互机制提供实现。
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
