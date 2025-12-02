package cn.antares.helldiver_trainer.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.antares.helldiver_trainer.bridge.SoundResource
import cn.antares.helldiver_trainer.bridge.playSound
import cn.antares.helldiver_trainer.bridge.stopSound
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.StratagemStore
import dev.icerock.moko.resources.ImageResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextInt

class GameViewModel(private val kvManager: SharedKVManager) : ViewModel() {

    companion object {
        const val INIT_ROUND_STRATAGEM_SIZE = 4 // 初始战备数量
        const val MAX_STRATAGEM_SIZE = 15 // 最大战备数量
        const val ROUND_TIME = 10000L // 每轮时间，单位毫秒
        const val EXTRA_TIME = 1200L // 每次延长时间，单位毫秒
        const val ROUND_BONUS_SCORE_MULTIPLIER = 25 // 每轮奖励乘数
        const val TIME_BONUS_SCORE_MULTIPLIER = 1 // 时间奖励乘数
        const val PERFECT_BONUS_SCORE = 100 // 完美奖励分数
        const val TIME_WARNING_THRESHOLD = 0.3f // 时间警告阈值，剩余时间比例
    }

    // 不用enum，会影响live edit
    sealed interface Screen {
        object Idle : Screen
        object Ready : Screen
        object Play : Screen
        object RoundOver : Screen
        object GameOver : Screen
    }

    sealed interface StratagemInput {
        object UP : StratagemInput
        object DOWN : StratagemInput
        object LEFT : StratagemInput
        object RIGHT : StratagemInput
    }

    data class RoundInfo(
        val roundNumber: Int = 0,
        val roundBonusScore: Int = 0,
        val timeBonusScore: Int = 0,
        val perfectBonusScore: Int = 0,
        val totalScore: Int = 0,
    )

    data class StratagemItem(
        val id: String,
        val name: String,
        val icon: ImageResource,
        val inputs: List<StratagemInput>,
    )

    // 倒计时相关
    var totalDuration by mutableLongStateOf(ROUND_TIME) // 总时长
        private set
    private val _remainingTime = MutableStateFlow(ROUND_TIME) // 剩余时长
    val remainingTime: StateFlow<Long> = _remainingTime.asStateFlow()
    private var tickerJob: Job? = null

    var currentScreen: Screen by mutableStateOf(Screen.Idle)
        private set
    var roundInfo by mutableStateOf(RoundInfo())
    private val allStratagems = mutableListOf<StratagemItem>()
    private val _stratagemList =
        MutableStateFlow<SnapshotStateList<StratagemItem>>(SnapshotStateList())
    val stratagemList = _stratagemList.asStateFlow()
    var currentStratagem by mutableStateOf<StratagemItem?>(null)
        private set
    var currentInputIndex by mutableIntStateOf(0)
        private set
    var wrongInputIndex by mutableIntStateOf(-1)
        private set
    private var roundIsPerfect = true
    private var gameOverClickLimited = false
    private val isInfiniteMode = kvManager.isInfiniteMode()

    init {
        initStratagems()
    }

    override fun onCleared() {
        stopCountdown()
        stopAllSounds()
        super.onCleared()
    }

    fun goToReady() {
        currentScreen = Screen.Ready
        roundInfo = roundInfo.copy(roundNumber = roundInfo.roundNumber + 1)
        updateStratagemList()
        if (roundInfo.roundNumber == 1) {
            playSound(SoundResource.Coin)
            playSound(SoundResource.Start)
        } else {
            playSound(SoundResource.Ready)
        }
        viewModelScope.launch {
            delay(2000)
            goToPlay()
        }
    }

    fun goToPlay() {
        currentScreen = Screen.Play
        playSound(SoundResource.Playing)
        if (isInfiniteMode.not()) startCountdown()
        roundIsPerfect = true
    }

    fun goToRoundOver() {
        currentScreen = Screen.RoundOver
        calculateTotalScores()
        stopCountdown()
        stopSound(SoundResource.Playing)
        playSound(
            Random.nextInt(1..3).let {
                when (it) {
                    1 -> SoundResource.Success1
                    2 -> SoundResource.Success2
                    else -> SoundResource.Success3
                }
            },
        )
        viewModelScope.launch {
            delay(5000)
            goToReady()
        }
    }

    fun goToGameOver() {
        currentScreen = Screen.GameOver
        stopSound(SoundResource.Playing)
        playSound(SoundResource.FailFull)
        stopCountdown()
        viewModelScope.launch { // 防止用户疯狂点击
            gameOverClickLimited = true
            delay(1000)
            gameOverClickLimited = false
        }
    }

    fun initStratagems() {
        allStratagems.clear()
        if (kvManager.isStratagemSelectMode()) {
            val selectedIDs = kvManager.getSelectedStratagemIDs()
            val storedStratagems = StratagemStore.getAllStratagems().filter {
                selectedIDs.contains(it.id)
            }
            if (storedStratagems.size < 10) {
                val multiplier = ceil(10.0 / storedStratagems.size).toInt()
                repeat(multiplier) {
                    allStratagems.addAll(storedStratagems)
                }
            } else {
                allStratagems.addAll(storedStratagems)
            }
        } else {
            allStratagems.addAll(StratagemStore.getAllStratagems())
        }
    }

    private fun updateStratagemList() {
        if (isInfiniteMode.not()) {
            _stratagemList.value.clear()
        }
        val count = if (isInfiniteMode) {
            allStratagems.size
        } else {
            min(
                INIT_ROUND_STRATAGEM_SIZE + roundInfo.roundNumber,
                MAX_STRATAGEM_SIZE,
            )
        }
        _stratagemList.value.addAll(allStratagems.shuffled().take(count))
        setFirstStratagem()
    }

    fun onButtonClicked(input: StratagemInput) {
        when (currentScreen) {
            Screen.Idle -> {
                // 从空闲状态开始
                if (isInfiniteMode) {
                    updateStratagemList()
                    goToPlay()
                } else {
                    goToReady()
                }
            }

            Screen.Ready -> {
                // 准备状态，无需处理输入
            }

            Screen.Play -> {
                // 游戏状态，处理输入
                handleInput(input)
            }

            Screen.RoundOver -> {
                // 战备完成，准备下一轮
            }

            Screen.GameOver -> {
                // 游戏结束，重置状态
                if (!gameOverClickLimited) {
                    roundInfo = RoundInfo()
                    goToReady()
                }
            }
        }
    }

    private fun handleInput(input: StratagemInput) {
        currentStratagem?.inputs?.let { expected ->
            if (currentInputIndex < expected.size && input == expected[currentInputIndex]) {
                currentInputIndex += 1
                if (currentInputIndex == expected.size) {
                    // 全部输入正确
                    _stratagemList.value.removeAt(0)
                    setFirstStratagem()
                    if (_stratagemList.value.isEmpty()) {
                        // 全部战备完成
                        goToRoundOver()
                    } else {
                        // 完成一个战备
                        playSound(SoundResource.Correct)
                        if (isInfiniteMode) {
                            if (_stratagemList.value.size < 10) {
                                updateStratagemList()
                            }
                        } else {
                            extendTime()
                            roundInfo =
                                roundInfo.copy(totalScore = roundInfo.totalScore + expected.size)
                        }
                    }
                } else {
                    playSound(SoundResource.Hit)
                }
            } else {
                // 输入错误：全部重置
                playSound(SoundResource.Error)
                roundIsPerfect = false
                wrongInputIndex = currentInputIndex + 1
                currentInputIndex = 0
                viewModelScope.launch {
                    delay(100)
                    clearWrongInputIndex()
                }
            }
        }
    }

    private fun setFirstStratagem() {
        _stratagemList.value.firstOrNull()?.let { stratagem ->
            currentStratagem = stratagem
            currentInputIndex = 0
        } ?: run {
            currentStratagem = null
            currentInputIndex = 0
        }
    }

    fun clearWrongInputIndex() {
        wrongInputIndex = -1
    }

    private fun startCountdown() {
        tickerJob = viewModelScope.launch {
            var lastTimestamp = System.currentTimeMillis()
            while (_remainingTime.value > 0L) {
                delay(16L) // ~60fps
                val now = System.currentTimeMillis()
                val delta = now - lastTimestamp
                lastTimestamp = now
                _remainingTime.update { current ->
                    max(0L, current - delta)
                }
            }
            // 倒计时结束
            goToGameOver()
        }
    }

    private fun stopCountdown() {
        tickerJob?.cancel()
        tickerJob = null
        _remainingTime.value = ROUND_TIME
        totalDuration = ROUND_TIME
    }

    private fun extendTime() {
        _remainingTime.update { it + EXTRA_TIME }
        totalDuration += EXTRA_TIME
    }

    private fun calculateTotalScores() {
        val roundBonusScore = roundInfo.roundNumber * ROUND_BONUS_SCORE_MULTIPLIER
        val timeBonusScore =
            (_remainingTime.value / totalDuration.toFloat() * 100).toInt() * TIME_BONUS_SCORE_MULTIPLIER
        val perfectBonusScore = if (roundIsPerfect) PERFECT_BONUS_SCORE else 0
        val totalScore = roundInfo.totalScore + roundBonusScore + timeBonusScore + perfectBonusScore
        roundInfo = roundInfo.copy(
            roundBonusScore = roundBonusScore,
            timeBonusScore = timeBonusScore,
            perfectBonusScore = perfectBonusScore,
            totalScore = totalScore,
        )
    }

    private fun stopAllSounds() {
        SoundResource.entries.forEach {
            stopSound(it)
        }
    }
}