package cn.antares.helldiver_trainer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.bridge.SoundResource
import cn.antares.helldiver_trainer.bridge.playSound
import cn.antares.helldiver_trainer.bridge.stopSound
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.WindowInfo
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.viewmodel.Direction
import cn.antares.helldiver_trainer.viewmodel.PipeCell
import cn.antares.helldiver_trainer.viewmodel.PipeGameViewModel
import cn.antares.helldiver_trainer.viewmodel.PipeShape
import dev.icerock.moko.resources.compose.painterResource
import helldiver_trainer.composeapp.generated.resources.Res
import helldiver_trainer.composeapp.generated.resources.ic_super_earth
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PipeGameContainer() {
    @Composable
    fun DesktopContainer(
        vm: PipeGameViewModel = koinViewModel(),
        content: @Composable () -> Unit,
    ) {
        val focusRequester = remember { FocusRequester() }
        val selectedCol by vm.selectedColumn.collectAsState()
        val isVictory by vm.isVictory.collectAsState()

        val modifier = Modifier.focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (isVictory) {
                        when (event.key) {
                            Key.Enter -> {
                                vm.resetGame()
                                return@onPreviewKeyEvent true
                            }
                        }
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionUp -> {
                            vm.shiftColumn(selectedCol, -1)
                            return@onPreviewKeyEvent true
                        }

                        Key.DirectionDown -> {
                            vm.shiftColumn(selectedCol, 1)
                            return@onPreviewKeyEvent true
                        }

                        Key.DirectionLeft -> {
                            vm.moveSelection(-1)
                            return@onPreviewKeyEvent true
                        }

                        Key.DirectionRight -> {
                            vm.moveSelection(1)
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

    if (HellUtils.isOnPC()) {
        DesktopContainer {
            PipeGame()
        }
    } else {
        PipeGame()
    }
}

@Composable
private fun PipeGame(
    vm: PipeGameViewModel = koinViewModel(),
    navController: NavHostController = LocalNavController.current,
    windowInfoManager: WindowInfoManager = koinInject(),
) {
    val grid by vm.grid.collectAsState()
    val currentPath by vm.currentPath.collectAsState()
    val isVictory by vm.isVictory.collectAsState()
    val selectedCol by vm.selectedColumn.collectAsState()
    val sourceName = vm.sourceName
    val isOnPC = HellUtils.isOnPC()
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    val cellSize =
        if (windowInfo.isPhonePortrait() || windowInfo.isPhoneLandscape()) 40.dp else 60.dp
    val textSize = if (windowInfo.isPhonePortrait()) 15.sp else 18.sp

    // 动画放完的标志
    var animationFinished by remember { mutableStateOf(false) }

    // 判断当前路径是否通向“本地”（初始状态）
    val isToLocal = remember(currentPath) {
        currentPath.lastOrNull()?.let { (c, r) ->
            c == 4 && r == vm.localRow && grid[c][r].shape.connections.contains(Direction.RIGHT)
        } ?: false
    }

    // 液体流动的动画进度 (0f 到 path.size)
    val flowStep = remember { Animatable(0f) }

    // 每次网格变化时，重新计算水流
    LaunchedEffect(currentPath, isVictory, isToLocal) {
        if (isVictory) {
            animationFinished = false
            playSound(SoundResource.PipeLoading)
            // 确保动画从开头 (0f) 开始执行
            flowStep.snapTo(0f)
            // 如果胜利，顺着路径播放“灌满”动画
            flowStep.animateTo(
                targetValue = currentPath.size.toFloat(),
                animationSpec = tween(
                    durationMillis = currentPath.size * 150,
                    easing = LinearEasing,
                ),
            )
            stopSound(SoundResource.PipeLoading)
            playSound(SoundResource.PipeComplete)
            animationFinished = true
        } else {
            flowStep.snapTo(currentPath.size.toFloat())
        }
    }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Image(
                painter = painterResource(Res.drawable.ic_super_earth),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.3f),
            )

            PipeGameHeader(windowInfo, sourceName)

            Row(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧入口
                PipeEntryColumn(cellSize, vm.startRow, sourceName, windowInfo)

                // 5x5 管道网格
                Row {
                    grid.forEachIndexed { colIndex, column ->
                        PipeColumn(
                            vm = vm,
                            colIndex = colIndex,
                            column = column,
                            cellSize = cellSize,
                            isSelected = isOnPC && selectedCol == colIndex,
                            isVictory = isVictory,
                            currentPath = currentPath,
                            flowStepValue = flowStep.value,
                            isToLocal = isToLocal,
                        )
                    }
                }

                // 右侧出口
                PipeExitColumn(cellSize, vm.targetRow, vm.localRow, windowInfo, textSize)
            }

            if (isVictory && animationFinished) {
                VictoryOverlay(
                    isOnPC,
                    onExit = {
                        navController.navigateUp()
                        animationFinished = false
                    },
                    onNext = {
                        vm.resetGame()
                        animationFinished = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PipeGameHeader(windowInfo: WindowInfo, sourceName: String) {
    if (windowInfo.isPhonePortrait()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "传输源: $sourceName",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TargetIcon()
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("中转站", color = Color.White, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocalIcon()
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("本地存储", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PipeEntryColumn(
    cellSize: Dp,
    startRow: Int,
    sourceName: String,
    windowInfo: WindowInfo,
) {
    Column(horizontalAlignment = Alignment.End) {
        repeat(5) { rowIndex ->
            Box(
                modifier = Modifier.height(cellSize),
                contentAlignment = Alignment.Center,
            ) {
                if (rowIndex == startRow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!windowInfo.isPhonePortrait()) {
                            Text(
                                text = sourceName,
                                color = Color.White,
                                fontSize = 18.sp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        TargetIcon()
                    }
                }
            }
        }
    }
}

@Composable
private fun PipeExitColumn(
    cellSize: Dp,
    targetRow: Int,
    localRow: Int,
    windowInfo: WindowInfo,
    textSize: androidx.compose.ui.unit.TextUnit,
) {
    Column(horizontalAlignment = Alignment.Start) {
        repeat(5) { rowIndex ->
            Box(
                Modifier.height(cellSize),
                contentAlignment = Alignment.Center,
            ) {
                if (rowIndex == targetRow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TargetIcon()
                        if (!windowInfo.isPhonePortrait()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("中转站", color = Color.White, fontSize = textSize)
                        }
                    }
                }
                if (rowIndex == localRow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalIcon()
                        if (!windowInfo.isPhonePortrait()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("本地存储", color = Color.White, fontSize = textSize)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipeColumn(
    vm: PipeGameViewModel,
    colIndex: Int,
    column: List<PipeCell>,
    cellSize: Dp,
    isSelected: Boolean,
    isVictory: Boolean,
    currentPath: List<Pair<Int, Int>>,
    flowStepValue: Float,
    isToLocal: Boolean,
) {
    val isLocked = vm.lockedColumn.contains(colIndex)
    val isOnPC = HellUtils.isOnPC()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlArrow(
            isLocked = isLocked,
            direction = Direction.UP,
            isHidden = (isOnPC && !isSelected) || isVictory,
            isInteractive = !isOnPC && !isVictory,
        ) {
            vm.shiftColumn(colIndex, -1)
        }
        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.height(cellSize * 5)) {
            column.forEachIndexed { rowIndex, cell ->
                PipeCellWithAnimation(
                    cell = cell,
                    colIndex = colIndex,
                    rowIndex = rowIndex,
                    cellSize = cellSize,
                    currentPath = currentPath,
                    flowStepValue = flowStepValue,
                    isSelected = isSelected,
                    isLocked = isLocked,
                    isVictory = isVictory,
                    isToLocal = isToLocal,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        ControlArrow(
            isLocked = isLocked,
            direction = Direction.DOWN,
            isHidden = (isOnPC && !isSelected) || isVictory,
            isInteractive = !isOnPC && !isVictory,
        ) {
            vm.shiftColumn(colIndex, 1)
        }
    }
}

@Composable
private fun PipeCellWithAnimation(
    cell: PipeCell,
    colIndex: Int,
    rowIndex: Int,
    cellSize: Dp,
    currentPath: List<Pair<Int, Int>>,
    flowStepValue: Float,
    isSelected: Boolean,
    isLocked: Boolean,
    isVictory: Boolean,
    isToLocal: Boolean,
) {
    key(cell.id) {
        var previousRowIndex by remember { mutableIntStateOf(rowIndex) }
        val isWraparound = remember(rowIndex) {
            val diff = kotlin.math.abs(rowIndex - previousRowIndex)
            val wrap = diff > 1
            previousRowIndex = rowIndex
            wrap
        }

        val animatedY by animateDpAsState(
            targetValue = cellSize * rowIndex,
            animationSpec = if (isWraparound) tween(0) else tween(100),
        )

        val pathIndex = currentPath.indexOf(colIndex to rowIndex)
        val fillProgress = when {
            pathIndex == -1 -> 0f
            flowStepValue > pathIndex + 1 -> 1f
            flowStepValue > pathIndex -> flowStepValue - pathIndex
            else -> 0f
        }

        val incomingDir = remember(pathIndex, currentPath) {
            when (pathIndex) {
                -1 -> null
                0 -> Direction.LEFT
                else -> {
                    val prevCell = currentPath[pathIndex - 1]
                    val currCell = currentPath[pathIndex]
                    when {
                        prevCell.first < currCell.first -> Direction.LEFT
                        prevCell.first > currCell.first -> Direction.RIGHT
                        prevCell.second < currCell.second -> Direction.UP
                        prevCell.second > currCell.second -> Direction.DOWN
                        else -> Direction.LEFT
                    }
                }
            }
        }

        Box(modifier = Modifier.graphicsLayer { translationY = animatedY.toPx() }) {
            PipeCell(
                cellSize = cellSize,
                shape = cell.shape,
                fillProgress = fillProgress,
                incomingDir = incomingDir,
                isLocked = isLocked,
                isPicked = isSelected,
                isVictory = isVictory,
                isToLocal = isToLocal,
            )
        }
    }
}

@Composable
private fun VictoryOverlay(
    isOnPC: Boolean,
    onExit: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "终端已解锁",
                color = Color.Green,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            if (isOnPC) {
                Text("退出按 ESC", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text("下一局按 ENTER", color = Color.White, fontSize = 20.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onExit) { Text("退出") }
                    Button(onClick = onNext) { Text("下一局") }
                }
            }
        }
    }
}

@Composable
private fun PipeCell(
    cellSize: Dp = 60.dp,
    shape: PipeShape,
    fillProgress: Float, // 0.0f 到 1.0f
    incomingDir: Direction?, // 水流是从哪个方向流进这个格子的？
    isLocked: Boolean = false, // 这个格子是否被锁定（无法旋转）？
    isPicked: Boolean = false, // 这个格子是否被选中（用于旋转时的高亮）？
    isVictory: Boolean = false, // 是否胜利状态（可以用来改变颜色）？
    isToLocal: Boolean = false, // 是否连接到本地状态？
) {
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = if (isPicked) primary else Color.Gray
    val lockedEmptyColor = Color.LightGray

    // 颜色逻辑：胜利为主色，连接到本地为浅灰色（被选中时变为高亮），其他为背景灰色
    val flowColor = when {
        isVictory -> primary
        isToLocal -> if (isPicked) primary else Color.LightGray
        else -> if (isPicked) primary else Color.LightGray
    }

    val strokeWidth = 40f
    val innerLineWidth = 2f
    val innerLineColor = Color.Black

    Canvas(modifier = Modifier.size(cellSize)) {
        val center = Offset(size.width / 2, size.height / 2)

        // 辅助函数：获取某个方向在格子边缘的坐标
        fun getEdgeOffset(dir: Direction): Offset = when (dir) {
            Direction.UP -> Offset(size.width / 2, 0f)
            Direction.DOWN -> Offset(size.width / 2, size.height)
            Direction.LEFT -> Offset(0f, size.height / 2)
            Direction.RIGHT -> Offset(size.width, size.height / 2)
        }

        // 1. 绘制静态的背景管道
        shape.connections.forEach { dir ->
            drawLine(
                color = if (isPicked) primary else (if (isLocked) lockedEmptyColor else emptyColor),
                start = center,
                end = getEdgeOffset(dir),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
            // 绘制中间的细黑线
            drawLine(
                color = innerLineColor,
                start = center,
                end = getEdgeOffset(dir),
                strokeWidth = innerLineWidth,
                cap = StrokeCap.Butt,
            )
        }

        // 2. 绘制中心连接点 (为了让转角圆滑，在中心画一个圆)
        if (shape != PipeShape.EMPTY) {
            drawCircle(
                color = if (isPicked) primary else (if (isLocked) lockedEmptyColor else emptyColor),
                radius = strokeWidth / 2f,
                center = center,
            )
        }

        // 3. 使用 PathMeasure 绘制流动的液体
        if (fillProgress > 0f && incomingDir != null && shape.connections.contains(incomingDir)) {
            // 构建一条完整的流动路径：入口 -> 中心 -> 出口
            val flowPath = Path().apply {
                val startOffset = getEdgeOffset(incomingDir)
                moveTo(startOffset.x, startOffset.y) // 移动到入口边缘
                lineTo(center.x, center.y)           // 连线到中心

                // 找到出口方向（形状中除了入口以外的另一个方向）
                val outgoingDir = shape.connections.firstOrNull { it != incomingDir }
                if (outgoingDir != null) {
                    val endOffset = getEdgeOffset(outgoingDir)
                    lineTo(endOffset.x, endOffset.y) // 连线到出口边缘
                }
            }

            // 使用 PathMeasure 测量总长度并截取当前进度的子路径
            val measure = PathMeasure()
            measure.setPath(flowPath, forceClosed = false)

            val segmentPath = Path()
            measure.getSegment(
                startDistance = 0f,
                stopDistance = measure.length * fillProgress,
                destination = segmentPath,
                startWithMoveTo = true,
            )

            // 画出被截取出来的、正在流动的那一段液体
            drawPath(
                path = segmentPath,
                color = flowColor,
                style = Stroke(
                    strokeWidth,
                    cap = StrokeCap.Butt,   // 流动液体的头尾保持矩形
                    join = StrokeJoin.Round,  // 转角处保持圆润
                ),
            )

            // 绘制流动状态下的中间细黑线
            drawPath(
                path = segmentPath,
                color = innerLineColor,
                style = Stroke(
                    innerLineWidth,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Round,
                ),
            )

            // 绘制中心圆点
            if (fillProgress > (measure.length / 2f / measure.length)) {
                drawCircle(
                    color = flowColor,
                    radius = strokeWidth / 2f,
                    center = center,
                )
            }
        }
    }
}

@Composable
private fun ControlArrow(
    isLocked: Boolean,
    direction: Direction,
    isHidden: Boolean = false,
    isInteractive: Boolean = true,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        if (isLocked) {
            // 锁定图标持续显示，不受 isHidden 影响
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        } else if (!isHidden) {
            // 只有在未锁定且不隐藏时才显示箭头
            val modifier = if (isInteractive) {
                Modifier.rotate(if (direction == Direction.UP) -90f else 90f)
                    .size(30.dp)
                    .clickable { onClick() }
            } else {
                Modifier.rotate(if (direction == Direction.UP) -90f else 90f)
                    .size(30.dp)
            }
            Image(
                painter = painterResource(MR.images.ic_arrow),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun LocalIcon() {
    Canvas(modifier = Modifier.size(30.dp)) {
        val inset = 2.dp.toPx()
        drawRect(
            color = Color.LightGray,
            size = size,
        )
        drawRect(
            color = Color.Black,
            size = size.copy(width = size.width - inset, height = size.height - inset),
            style = Stroke(4f),
            topLeft = Offset(inset / 2, inset / 2),
        )
        drawLine(
            color = Color.Black,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = 4f,
        )
        drawLine(
            color = Color.Black,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = 4f,
        )
    }
}

@Composable
private fun TargetIcon() {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(30.dp)) {
        val inset = 2.dp.toPx()
        drawRect(
            color = primary,
            size = size,
        )
        drawRect(
            color = Color.Black,
            size = size.copy(width = size.width - inset, height = size.height - inset),
            style = Stroke(4f),
            topLeft = Offset(inset / 2, inset / 2),
        )
        drawCircle(
            color = Color.Black,
            radius = size.minDimension / 4,
            style = Stroke(4f),
        )
    }
}