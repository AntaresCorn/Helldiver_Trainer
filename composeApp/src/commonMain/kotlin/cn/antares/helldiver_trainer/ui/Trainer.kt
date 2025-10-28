package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import helldiver_trainer.composeapp.generated.resources.Res
import helldiver_trainer.composeapp.generated.resources.ic_input_down
import helldiver_trainer.composeapp.generated.resources.ic_input_left
import helldiver_trainer.composeapp.generated.resources.ic_input_right
import helldiver_trainer.composeapp.generated.resources.ic_input_up
import helldiver_trainer.composeapp.generated.resources.ic_super_earth
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Trainer(
    windowInfoManager: WindowInfoManager = koinInject(),
    vm: GameViewModel = koinViewModel(),
) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()

    var containerModifier = Modifier.background(Color.DarkGray)
    if (HellUtils.isOnPC()) {
        // 注册键盘事件
        val focusRequester = remember { FocusRequester() }
        containerModifier = containerModifier.focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> vm.onButtonClicked(GameViewModel.StratagemInput.UP)
                        Key.DirectionDown -> vm.onButtonClicked(GameViewModel.StratagemInput.DOWN)
                        Key.DirectionLeft -> vm.onButtonClicked(GameViewModel.StratagemInput.LEFT)
                        Key.DirectionRight -> vm.onButtonClicked(GameViewModel.StratagemInput.RIGHT)
                        else -> {}
                    }
                    true
                }
                false
            }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = containerModifier) {
        Image(
            painter = painterResource(Res.drawable.ic_super_earth),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.3f),
        )
        if (windowInfo.isWidthLargerThanCompact() && windowInfo.isHeightExpanded().not()) {
            Box {
                GameContainer()
                if (HellUtils.isOnPC().not()) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).alpha(0.2f)
                            .padding(end = 20.dp),
                    ) {
                        Arrows()
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GameContainer(Modifier.weight(1f))
                if (HellUtils.isOnPC().not()) {
                    Arrows()
                }
            }
        }
    }
}

@Composable
private fun GameContainer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(vertical = 30.dp),
    ) {
        val lineModifier = Modifier.fillMaxWidth()
            .height(5.dp)
            .background(Color.White.copy(alpha = 0.7f))
        Box(modifier = lineModifier.align(Alignment.TopCenter))
        Box(modifier = lineModifier.align(Alignment.BottomCenter))
        Box(modifier = Modifier.align(Alignment.Center)) {
            Game()
        }
    }
}

@Composable
private fun Arrows(
    vm: GameViewModel = koinViewModel(),
    windowInfoManager: WindowInfoManager = koinInject(),
) {
    @Composable
    fun CustomRelativeLayout(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        Layout(
            modifier = modifier,
            content = content,
        ) { measurables, constraints ->
            // 1. 清除 minWidth/minHeight，按子项自身需求测量
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            // 2. 测量所有子项（会“吃掉”它们的 size()/padding()/border() 等）
            val placeables = measurables.map { it.measure(childConstraints) }

            // 3. 计算网格行高列宽
            val colWidth = placeables[0].width
            val rowHeight = placeables[0].height

            // 4. 最终布局尺寸要受上级 constraints 限制
            val margin = 5.dp.roundToPx()
            val layoutWidth =
                (colWidth * 3 + margin * 2).coerceIn(constraints.minWidth, constraints.maxWidth)
            val layoutHeight =
                (rowHeight * 2 + margin).coerceIn(constraints.minHeight, constraints.maxHeight)

            // 5. 布局并放置
            layout(layoutWidth, layoutHeight) {
                placeables[0].placeRelative(colWidth + margin, 0)
                placeables[1].placeRelative(0, (rowHeight + margin) / 2)
                placeables[2].placeRelative(colWidth + margin, rowHeight + margin)
                placeables[3].placeRelative((colWidth + margin) * 2, (rowHeight + margin) / 2)
            }
        }
    }

    @Composable
    fun ArrowItem(
        modifier: Modifier,
        colorFilter: ColorFilter,
        type: GameViewModel.StratagemInput,
    ) {
        Box(modifier = Modifier.clickable { vm.onButtonClicked(type) }) {
            Image(
                painter = painterResource(
                    when (type) {
                        GameViewModel.StratagemInput.UP -> Res.drawable.ic_input_up
                        GameViewModel.StratagemInput.LEFT -> Res.drawable.ic_input_left
                        GameViewModel.StratagemInput.DOWN -> Res.drawable.ic_input_down
                        GameViewModel.StratagemInput.RIGHT -> Res.drawable.ic_input_right
                    },
                ),
                contentDescription = null,
                colorFilter = colorFilter,
                modifier = modifier,
            )
        }
    }

    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    val arrowSize =
        (if (windowInfo.isWidthLargerThanCompact() && windowInfo.isHeightExpanded().not()) {
            if (windowInfo.isHeightLargerThanCompact()) 105 else 85
        } else 95).dp
    val arrowModifier = Modifier.size(arrowSize).background(Color.LightGray).padding(15.dp)
    val colorFilter = ColorFilter.tint(Color.Black)
    val bottomPadding =
        (if (windowInfo.isWidthLargerThanCompact() && windowInfo.isHeightExpanded().not()) {
            if (windowInfo.isHeightLargerThanCompact()) 70 else 30
        } else 50).dp
    CustomRelativeLayout(modifier = Modifier.padding(bottom = bottomPadding)) {
        ArrowItem(arrowModifier, colorFilter, GameViewModel.StratagemInput.UP)
        ArrowItem(arrowModifier, colorFilter, GameViewModel.StratagemInput.LEFT)
        ArrowItem(arrowModifier, colorFilter, GameViewModel.StratagemInput.DOWN)
        ArrowItem(arrowModifier, colorFilter, GameViewModel.StratagemInput.RIGHT)
    }
}