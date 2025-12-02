package cn.antares.helldiver_trainer.util.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.util.WindowInfo
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.viewmodel.ArrowButtonViewModel
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import helldiver_trainer.composeapp.generated.resources.Res
import helldiver_trainer.composeapp.generated.resources.ic_input_down
import helldiver_trainer.composeapp.generated.resources.ic_input_left
import helldiver_trainer.composeapp.generated.resources.ic_input_right
import helldiver_trainer.composeapp.generated.resources.ic_input_up
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ArrowButtons(
    modifier: Modifier = Modifier,
    vm: GameViewModel = koinViewModel(),
    buttonVM: ArrowButtonViewModel = koinViewModel(),
    windowInfoManager: WindowInfoManager = koinInject(),
    adjustMode: Boolean = false,
) {
    val buttonConfig by buttonVM.config.collectAsState()
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    val arrowSize = buttonConfig.buttonSize.dp
    val arrowModifier = Modifier.size(arrowSize)
    val bottomPadding =
        if (windowInfo.isTwoPaneCandidate()) {
            if (windowInfo.isPhoneLandscape()) {
                30.dp
            } else {
                70.dp
            }
        } else {
            50.dp
        }
    CustomRelativeLayout(
        modifier = modifier
            .padding(bottom = bottomPadding)
            .then(
                if (buttonConfig.fixedPositionMode)
                    Modifier.offset { IntOffset(buttonConfig.offsetX, buttonConfig.offsetY) }
                        .pointerInput(Unit) {
                            if (adjustMode) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    buttonVM.updateOffset(
                                        buttonConfig.offsetX + dragAmount.x.roundToInt(),
                                        buttonConfig.offsetY + dragAmount.y.roundToInt(),
                                    )
                                }
                            }
                        }
                else Modifier,
            ),
        standardKeyboardMode = buttonConfig.standardKeyboardMode,
    ) {
        ArrowItem(
            modifier = arrowModifier
                .then(
                    if (buttonConfig.fixedPositionMode.not())
                        Modifier.offset {
                            IntOffset(buttonConfig.upOffsetX, buttonConfig.upOffsetY)
                        }.pointerInput(Unit) {
                            if (adjustMode) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    buttonVM.updateUpOffset(
                                        buttonConfig.upOffsetX + dragAmount.x.roundToInt(),
                                        buttonConfig.upOffsetY + dragAmount.y.roundToInt(),
                                    )
                                }
                            }
                        } else Modifier,
                ),
            type = GameViewModel.StratagemInput.UP,
            adjustMode = adjustMode,
            windowInfo = windowInfo,
        ) {
            if (adjustMode.not()) vm.onButtonClicked(GameViewModel.StratagemInput.UP)
        }
        ArrowItem(
            modifier = arrowModifier.then(
                if (buttonConfig.fixedPositionMode.not())
                    Modifier.offset {
                        IntOffset(buttonConfig.leftOffsetX, buttonConfig.leftOffsetY)
                    }.pointerInput(Unit) {
                        if (adjustMode) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                buttonVM.updateLeftOffset(
                                    buttonConfig.leftOffsetX + dragAmount.x.roundToInt(),
                                    buttonConfig.leftOffsetY + dragAmount.y.roundToInt(),
                                )
                            }
                        }
                    } else Modifier,
            ),
            type = GameViewModel.StratagemInput.LEFT,
            adjustMode = adjustMode,
            windowInfo = windowInfo,
        ) {
            if (adjustMode.not()) vm.onButtonClicked(GameViewModel.StratagemInput.LEFT)
        }
        ArrowItem(
            modifier = arrowModifier.then(
                if (buttonConfig.fixedPositionMode.not())
                    Modifier.offset {
                        IntOffset(buttonConfig.downOffsetX, buttonConfig.downOffsetY)
                    }.pointerInput(Unit) {
                        if (adjustMode) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                buttonVM.updateDownOffset(
                                    buttonConfig.downOffsetX + dragAmount.x.roundToInt(),
                                    buttonConfig.downOffsetY + dragAmount.y.roundToInt(),
                                )
                            }
                        }
                    } else Modifier,
            ),
            type = GameViewModel.StratagemInput.DOWN,
            adjustMode = adjustMode,
            windowInfo = windowInfo,
        ) {
            if (adjustMode.not()) vm.onButtonClicked(GameViewModel.StratagemInput.DOWN)
        }
        ArrowItem(
            modifier = arrowModifier.then(
                if (buttonConfig.fixedPositionMode.not())
                    Modifier.offset {
                        IntOffset(buttonConfig.rightOffsetX, buttonConfig.rightOffsetY)
                    }.pointerInput(Unit) {
                        if (adjustMode) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                buttonVM.updateRightOffset(
                                    buttonConfig.rightOffsetX + dragAmount.x.roundToInt(),
                                    buttonConfig.rightOffsetY + dragAmount.y.roundToInt(),
                                )
                            }
                        }
                    } else Modifier,
            ),
            type = GameViewModel.StratagemInput.RIGHT,
            adjustMode = adjustMode,
            windowInfo = windowInfo,
        ) {
            if (adjustMode.not()) vm.onButtonClicked(GameViewModel.StratagemInput.RIGHT)
        }
    }
}

@Composable
private fun ArrowItem(
    modifier: Modifier,
    type: GameViewModel.StratagemInput,
    adjustMode: Boolean,
    windowInfo: WindowInfo,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .then(
                if (adjustMode.not() && windowInfo.isTwoPaneCandidate() &&
                    windowInfo.isTabletPortrait().not()
                ) Modifier.alpha(0.2f) else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
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
            colorFilter = ColorFilter.tint(Color.Black),
            modifier = Modifier.background(Color.LightGray).fillMaxSize().padding(15.dp),
        )
    }
}

@Composable
private fun CustomRelativeLayout(
    modifier: Modifier = Modifier,
    standardKeyboardMode: Boolean,
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

        val offset = if (standardKeyboardMode) 1 else 2
        // 5. 布局并放置
        layout(layoutWidth, layoutHeight) {
            placeables[0].placeRelative(colWidth + margin, 0)
            placeables[1].placeRelative(0, (rowHeight + margin) / offset)
            placeables[2].placeRelative(colWidth + margin, rowHeight + margin)
            placeables[3].placeRelative((colWidth + margin) * 2, (rowHeight + margin) / offset)
        }
    }
}