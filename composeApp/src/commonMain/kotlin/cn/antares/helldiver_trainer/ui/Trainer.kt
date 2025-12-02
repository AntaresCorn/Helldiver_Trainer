package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.util.widget.ArrowButtons
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import helldiver_trainer.composeapp.generated.resources.Res
import helldiver_trainer.composeapp.generated.resources.ic_super_earth
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Trainer(vm: GameViewModel = koinViewModel()) {
    var containerModifier: Modifier = Modifier
    if (HellUtils.isOnPC()) {
        // 注册键盘事件
        val focusRequester = remember { FocusRequester() }
        containerModifier = containerModifier.focusRequester(focusRequester)
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
    }

    Box(modifier = containerModifier) {
        GameAndButtonContainer()
    }
}

@Composable
fun GameAndButtonContainer(
    windowInfoManager: WindowInfoManager = koinInject(),
    adjustMode: Boolean = false,
) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    if (windowInfo.isTwoPaneCandidate() && windowInfo.isTabletPortrait().not()) {
        Box {
            GameContainer(adjustMode = adjustMode)
            if (HellUtils.isOnPC().not()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp),
                ) {
                    ArrowButtons(adjustMode = adjustMode)
                }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GameContainer(Modifier.weight(1f), adjustMode = adjustMode)
            if (HellUtils.isOnPC().not()) {
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