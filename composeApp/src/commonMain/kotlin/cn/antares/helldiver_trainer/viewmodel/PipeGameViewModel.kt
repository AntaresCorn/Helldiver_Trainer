package cn.antares.helldiver_trainer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.antares.helldiver_trainer.bridge.SoundResource
import cn.antares.helldiver_trainer.bridge.playSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

enum class Direction { UP, DOWN, LEFT, RIGHT }

fun Direction.opposite(): Direction = when (this) {
    Direction.UP -> Direction.DOWN
    Direction.DOWN -> Direction.UP
    Direction.LEFT -> Direction.RIGHT
    Direction.RIGHT -> Direction.LEFT
}

enum class PipeShape(val connections: Set<Direction>) {
    HORIZONTAL(setOf(Direction.LEFT, Direction.RIGHT)),
    VERTICAL(setOf(Direction.UP, Direction.DOWN)),
    BEND_TL(setOf(Direction.UP, Direction.LEFT)),
    BEND_TR(setOf(Direction.UP, Direction.RIGHT)),
    BEND_BL(setOf(Direction.DOWN, Direction.LEFT)),
    BEND_BR(setOf(Direction.DOWN, Direction.RIGHT)),
    EMPTY(emptySet())
}

data class PipeCell(
    val id: Long = Random.nextLong(),
    val shape: PipeShape,
)

class PipeGameViewModel : ViewModel() {
    var startRow = 0 // 入口（初始连通）
    var targetRow = 1 // 中转站（胜利目标）
    var localRow = 2  // 本地出口（初始连通）
    var sourceName = "710元素"
    var lockedColumn = listOf(2, 3) // 锁定列索引（0~4）

    // 新增：PC端选中的列索引
    private val _selectedColumn = MutableStateFlow(0)
    val selectedColumn: StateFlow<Int> = _selectedColumn.asStateFlow()

    private val _grid = MutableStateFlow<List<List<PipeCell>>>(emptyList())
    val grid: StateFlow<List<List<PipeCell>>> = _grid.asStateFlow()
    val currentPath: StateFlow<List<Pair<Int, Int>>> = _grid.map { g ->
        if (g.isEmpty()) emptyList() else tracePath(g, startRow)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isVictory: StateFlow<Boolean> = currentPath.map { path ->
        path.lastOrNull()?.let { (c, r) ->
            c == 4 && r == targetRow && _grid.value[c][r].shape.connections.contains(Direction.RIGHT)
        } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        resetGame()
    }

    fun resetGame() {
        startRow = (0..4).random()
        targetRow = (0..4).random()
        localRow = (0..4).filter { it != targetRow }.random()
        sourceName = listOf("710元素", "711元素", "火箭燃料").random() + "外流"
        lockedColumn = listOf(0, 1, 2, 3, 4).shuffled().take(2).sorted() // 随机锁定两列
        _selectedColumn.value = (0..4).first { !lockedColumn.contains(it) }
        _grid.value = generateDualPathPlayableGrid(startRow, targetRow, localRow)
    }

    // 移动选中列，跳过锁定的列，并支持循环切换
    fun moveSelection(delta: Int) {
        playSound(SoundResource.PipeMove)
        var next = _selectedColumn.value
        do {
            next = (next + delta + 5) % 5
        } while (lockedColumn.contains(next))
        _selectedColumn.value = next
    }

    // 处理列移动逻辑，增加锁定检查
    fun shiftColumn(
        colIndex: Int,
        dir: Int,
    ) {
        playSound(SoundResource.PipeMove)
        if (lockedColumn.contains(colIndex)) return
        val currentGrid = _grid.value.toMutableList()
        val col = currentGrid[colIndex].toMutableList()
        if (dir == 1) {
            col.add(0, col.removeAt(col.lastIndex))
        } else {
            col.add(col.removeAt(0))
        }
        currentGrid[colIndex] = col
        _grid.value = currentGrid
    }

    /**
     * 追踪从入口开始的所有连通格子
     */
    fun tracePath(grid: List<List<PipeCell>>, startRow: Int): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var currCol = 0
        var currRow = startRow
        var incomingDir = Direction.LEFT

        while (currCol in 0..4 && currRow in 0..4) {
            val cell = grid[currCol][currRow]
            val pipe = cell.shape
            if (!pipe.connections.contains(incomingDir)) break

            path.add(currCol to currRow)
            val outgoingDir = pipe.connections.firstOrNull { it != incomingDir } ?: break

            when (outgoingDir) {
                Direction.UP -> currRow--
                Direction.DOWN -> currRow++
                Direction.LEFT -> currCol--
                Direction.RIGHT -> currCol++
            }
            incomingDir = outgoingDir.opposite()
        }
        return path
    }

    /**
     * 生成带有“初始通路”和“目标通路”的双解网格
     */
    fun generateDualPathPlayableGrid(
        startRow: Int = 2,
        targetRow: Int = 1, // 中转站出口
        localRow: Int = 4,   // 本地存储出口
    ): List<List<PipeCell>> {
        val maxAttempts = 10000 // 最大重试次数
        // 提前准备好干扰项列表，避免在循环中重复过滤
        val decoys = PipeShape.entries.filter { it != PipeShape.EMPTY }

        repeat(maxAttempts) {
            // 1. 初始化一个全空的物理网格
            val physicalGrid = MutableList(5) { MutableList(5) { PipeShape.EMPTY } }

            // 2. 随机决定目标状态需要的滑动步数
            // 根据实时的 lockedColumn 计算位移：锁定列位移为 0，非锁定列位移 1~4
            val targetShifts = (0..4).map { colIndex ->
                if (lockedColumn.contains(colIndex)) 0 else (1..4).random()
            }

            // 3. 绘制【路径 A】：初始状态 -> 连通“本地存储” (不带任何滑动)
            // 如果绘制失败，return@repeat 相当于 loop 中的 continue，直接进入下一次尝试
            if (!drawPathOnGrid(
                    physicalGrid,
                    startRow,
                    localRow,
                    listOf(0, 0, 0, 0, 0),
                )
            ) return@repeat

            // 4. 绘制【路径 B】：目标状态 -> 连通“中转站” (代入目标滑动步数)
            // 如果与路径 A 发生管道形状冲突，跳过本次尝试
            if (!drawPathOnGrid(physicalGrid, startRow, targetRow, targetShifts)) return@repeat

            // 5. 填充干扰项 (Decoys)
            // 走到这一步说明两条核心路径完美共存！用随机管道填满剩下的空位
            for (c in 0..4) {
                for (r in 0..4) {
                    if (physicalGrid[c][r] == PipeShape.EMPTY) {
                        physicalGrid[c][r] = decoys.random()
                    }
                }
            }

            // 将 PipeShape 网格转换为带有唯一 ID 的 PipeCell 网格
            val cellGrid = physicalGrid.map { column ->
                column.map { shape -> PipeCell(shape = shape) }
            }

            // 成功找到一个合法网格，直接返回
            return cellGrid
        }

        // 极端情况：如果 10000 次都没随出来（概率极低），返回一个默认保底网格防崩溃
        return List(5) { List(5) { PipeCell(shape = PipeShape.HORIZONTAL) } }
    }

    private fun drawPathOnGrid(
        grid: MutableList<MutableList<PipeShape>>,
        startRow: Int,
        endRow: Int,
        shifts: List<Int>, // 该路径在各列的滑动偏移量
    ): Boolean {
        var currentVisualEntry = startRow
        val tempGrid = MutableList(5) { MutableList(5) { PipeShape.EMPTY } }

        // 挑选 1-2 个不重叠的列强制进行行跳转，确保路径不是直连
        val jumpCols = (0..3).shuffled().take((1..2).random()).toSet()

        for (col in 0..4) {
            // 确定视觉层面的出口行
            val visualExit = if (col == 4) {
                endRow
            } else {
                val possibleExits = (0..4).toMutableList()
                if (jumpCols.contains(col)) {
                    // 强制跳转：出口行不能等于入口行
                    possibleExits.remove(currentVisualEntry)
                    possibleExits.random()
                } else {
                    // 非强制列：有 40% 的概率尝试跳转，增加路径复杂性
                    if (Random.nextFloat() < 0.4f) {
                        possibleExits.remove(currentVisualEntry)
                        possibleExits.random()
                    } else {
                        currentVisualEntry
                    }
                }
            }

            val visualShapes = mutableMapOf<Int, PipeShape>()

            // 确定视觉层面的管道走向
            if (currentVisualEntry == visualExit) {
                visualShapes[currentVisualEntry] = PipeShape.HORIZONTAL
            } else if (currentVisualEntry < visualExit) { // 水流向下
                visualShapes[currentVisualEntry] = PipeShape.BEND_BL
                for (r in currentVisualEntry + 1 until visualExit) {
                    visualShapes[r] = PipeShape.VERTICAL
                }
                visualShapes[visualExit] = PipeShape.BEND_TR
            } else { // 水流向上
                visualShapes[currentVisualEntry] = PipeShape.BEND_TL
                for (r in currentVisualEntry - 1 downTo visualExit + 1) {
                    visualShapes[r] = PipeShape.VERTICAL
                }
                visualShapes[visualExit] = PipeShape.BEND_BR
            }

            // 将视觉坐标映射到物理网格，并进行碰撞检测
            for ((vRow, requiredShape) in visualShapes) {
                // 根据滑动步数，反推物理行号
                val pRow = (vRow - shifts[col] + 5) % 5

                // 检查：如果该位置已有管道，且与我现在需要的管道形状不一致，则发生冲突！
                if (grid[col][pRow] != PipeShape.EMPTY && grid[col][pRow] != requiredShape) {
                    return false
                }
                if (tempGrid[col][pRow] != PipeShape.EMPTY && tempGrid[col][pRow] != requiredShape) {
                    return false
                }
                tempGrid[col][pRow] = requiredShape
            }
            currentVisualEntry = visualExit
        }

        // 无冲突，将临时路径合并到主网格
        for (c in 0..4) {
            for (r in 0..4) {
                if (tempGrid[c][r] != PipeShape.EMPTY) {
                    grid[c][r] = tempGrid[c][r]
                }
            }
        }
        return true
    }
}