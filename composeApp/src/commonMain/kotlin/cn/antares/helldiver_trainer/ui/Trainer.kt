package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.util.widget.ArrowButtons
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import helldiver_trainer.composeapp.generated.resources.Res
import helldiver_trainer.composeapp.generated.resources.ic_super_earth
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

@Composable
fun Trainer(sharedKVManager: SharedKVManager = koinInject()) {
    if (HellUtils.isOnPC()) {
        DesktopContainer {
            GameAndButtonContainer()
        }
    } else {
        if (sharedKVManager.isSwipeMode()) {
            MobileSwipeContainer {
                GameAndButtonContainer()
            }
        } else {
            Box {
                GameAndButtonContainer(showButton = true)
            }
        }
    }
}

@Composable
fun GameAndButtonContainer(
    windowInfoManager: WindowInfoManager = koinInject(),
    showButton: Boolean = false,
    adjustMode: Boolean = false,
) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    if (windowInfo.isTwoPaneCandidate() && windowInfo.isTabletPortrait().not()) {
        Box {
            GameContainer(adjustMode = adjustMode)
            if (showButton) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp)) {
                    ArrowButtons(adjustMode = adjustMode)
                }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GameContainer(Modifier.weight(1f), adjustMode = adjustMode)
            if (showButton) {
                ArrowButtons(
                    modifier = Modifier.weight(if (windowInfo.isTabletPortrait()) 0.3f else 0.4f),
                    adjustMode = adjustMode,
                )
            }
        }
    }
}

@Composable
fun GameContainer(modifier: Modifier = Modifier, adjustMode: Boolean = false) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
            .padding(vertical = 30.dp),
    ) {
        if (adjustMode.not()) {
            Image(
                painter = painterResource(Res.drawable.ic_super_earth),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
            )
        }
        val lineModifier = Modifier.fillMaxWidth()
            .height(5.dp)
            .background(Color.White.copy(alpha = 0.7f))
        Box(modifier = lineModifier.align(Alignment.TopCenter))
        Box(modifier = lineModifier.align(Alignment.BottomCenter))
        Box(modifier = Modifier.align(Alignment.Center).alpha(if (adjustMode) 0f else 1f)) {
            Game()
        }
    }
}

@Composable
private fun MobileSwipeContainer(
    vm: GameViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val onTrigger: (GameViewModel.StratagemInput) -> Unit = { direction ->
        vm.onButtonClicked(direction)
    }

    // 累计位移
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    val clearTrigger = {
        accX = 0f
        accY = 0f
        triggered = false
    }

    val modifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { clearTrigger() },
            onDragEnd = { clearTrigger() },
            onDragCancel = { clearTrigger() },
        ) { change, dragAmount ->
            // 累加增量
            accX += dragAmount.x
            accY += dragAmount.y

            if (triggered.not()) {
                // 判断主方向：水平主导或垂直主导
                if (abs(accX) > abs(accY)) {
                    // 水平方向
                    if (abs(accX) >= thresholdPx) {
                        val dir =
                            if (accX > 0) GameViewModel.StratagemInput.RIGHT else GameViewModel.StratagemInput.LEFT
                        onTrigger(dir)
                        triggered = true
                    }
                } else {
                    // 垂直方向
                    if (abs(accY) >= thresholdPx) {
                        val dir =
                            if (accY > 0) GameViewModel.StratagemInput.DOWN else GameViewModel.StratagemInput.UP
                        onTrigger(dir)
                        triggered = true
                    }
                }
            }
            // 防止父容器继续处理（按需）
            change.consume()
        }
    }
    Box(modifier = modifier) {
        content()
    }
}

@Composable
private fun DesktopContainer(
    vm: GameViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val modifier = Modifier.focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.DirectionUp -> {
                        vm.onButtonClicked(GameViewModel.StratagemInput.UP)
                        return@onPreviewKeyEvent true
                    }

                    Key.DirectionDown -> {
                        vm.onButtonClicked(GameViewModel.StratagemInput.DOWN)
                        return@onPreviewKeyEvent true
                    }

                    Key.DirectionLeft -> {
                        vm.onButtonClicked(GameViewModel.StratagemInput.LEFT)
                        return@onPreviewKeyEvent true
                    }

                    Key.DirectionRight -> {
                        vm.onButtonClicked(GameViewModel.StratagemInput.RIGHT)
                        return@onPreviewKeyEvent true
                    }
                }
            }
            return@onPreviewKeyEvent false
        }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Box(modifier = modifier) {
        content()
    }
}