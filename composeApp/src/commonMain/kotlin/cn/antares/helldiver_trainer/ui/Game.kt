package cn.antares.helldiver_trainer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.bridge.SoundResource
import cn.antares.helldiver_trainer.bridge.playSound
import cn.antares.helldiver_trainer.util.HellColors
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Game(vm: GameViewModel = koinViewModel()) {
    when (vm.currentScreen) {
        GameViewModel.Screen.Idle -> Idle()
        GameViewModel.Screen.Ready -> Ready()
        GameViewModel.Screen.Play -> Play()
        GameViewModel.Screen.RoundOver -> RoundOver()
        GameViewModel.Screen.GameOver -> GameOver()
    }
}

@Composable
fun Idle() {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "战备英雄",
            color = Color.White,
            fontSize = 50.sp,
            fontWeight = FontWeight.W900,
        )
        Spacer(modifier = Modifier.size(50.dp))
        Text(
            "按下任意方向键开始",
            color = HellColors.PrimaryColor,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
        )
        if (HellUtils.isOnPC()) {
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                "按ESC退出",
                color = Color.White,
                fontSize = 15.sp,
                fontStyle = FontStyle.Normal,
            )
        }
    }
}

@Composable
fun Ready(vm: GameViewModel = koinViewModel()) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "准备好",
            color = Color.White,
            fontSize = 50.sp,
            fontWeight = FontWeight.W900,
        )
        Spacer(modifier = Modifier.size(50.dp))
        Text(
            "回合",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.W900,
        )
        Text(
            vm.roundInfo.roundNumber.toString(),
            color = HellColors.PrimaryColor,
            fontSize = 40.sp,
            fontWeight = FontWeight.W900,
        )
    }
}

@Composable
fun Play(vm: GameViewModel = koinViewModel(), windowInfoManager: WindowInfoManager = koinInject()) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()

    @Composable
    fun CountdownBar(
        progress: Float,
        progressColor: Color,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            // 背景
            drawRect(Color.LightGray, size = size)
            // 进度条
            drawRect(progressColor, size = Size(size.width * progress, size.height))
        }
    }

    @Composable
    fun RoundPanel() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "回合",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.W900,
            )
            Text(
                vm.roundInfo.roundNumber.toString(),
                color = HellColors.PrimaryColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.W900,
            )
        }
    }

    @Composable
    fun MainPanel() {
        val stratagemList by vm.stratagemList.collectAsState()
        val firstStratagem = vm.currentStratagem

        val remaining by vm.remainingTime.collectAsState()
        val progress = (remaining / vm.totalDuration.toFloat()).coerceIn(0f, 1f)
        val reachThreshold = progress <= GameViewModel.TIME_WARNING_THRESHOLD
        val progressColor by animateColorAsState(
            targetValue = if (reachThreshold) Color.Red else HellColors.PrimaryColor,
            animationSpec = tween(durationMillis = 500),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = (if (windowInfo.isHeightExpanded()) 100 else 20).dp),
        ) {
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start),
            ) {
                itemsIndexed(
                    stratagemList,
                    key = { _, stratagem -> stratagem.id },
                ) { index, stratagem ->
                    Image(
                        painter = painterResource(stratagem.icon),
                        contentDescription = null,
                        modifier = Modifier.height((if (index == 0) 70 else 55).dp)
                            .then(
                                if (index == 0) Modifier.border(2.dp, progressColor).padding(5.dp)
                                else Modifier.padding(horizontal = 5.dp),
                            ),
                    )
                }
            }
            Text(
                firstStratagem?.name.toString(),
                color = if (reachThreshold) Color.White else Color.Black,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth().background(progressColor),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(10.dp))
            LazyRow {
                itemsIndexed(
                    vm.currentStratagem?.inputs ?: emptyList(),
                    key = { index, _ -> index },
                ) { index, input ->
                    Image(
                        painter = painterResource(MR.images.ic_arrow),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (vm.wrongInputIndex != -1) {
                                if (index < vm.wrongInputIndex) Color.Red else Color.LightGray
                            } else {
                                if (index < vm.currentInputIndex) HellColors.PrimaryColor else Color.LightGray
                            },
                        ),
                        modifier = Modifier.rotate(
                            when (input) {
                                GameViewModel.StratagemInput.UP -> -90f
                                GameViewModel.StratagemInput.DOWN -> 90f
                                GameViewModel.StratagemInput.LEFT -> 180f
                                GameViewModel.StratagemInput.RIGHT -> 0f
                            },
                        ).size(40.dp).padding(horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.size(20.dp))
            CountdownBar(progress, progressColor)
        }
    }

    @Composable
    fun ScorePanel() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "分数",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.W900,
            )
            Text(
                vm.roundInfo.totalScore.toString(),
                color = HellColors.PrimaryColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.W900,
            )
        }
    }

    if (windowInfo.isWidthLargerThanCompact() && windowInfo.isHeightExpanded().not()) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight(),
        ) {
            val infoModifier = Modifier.weight(0.4f)
                .padding(top = (if (windowInfo.isHeightLargerThanCompact()) 80 else 20).dp)
            Box(modifier = infoModifier) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    RoundPanel()
                }
            }
            Box(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                MainPanel()
            }
            Box(modifier = infoModifier) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    ScorePanel()
                }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundPanel()
            Spacer(modifier = Modifier.size(30.dp))
            MainPanel()
            Spacer(modifier = Modifier.size(30.dp))
            ScorePanel()
        }
    }
}

@Composable
fun RoundOver(
    vm: GameViewModel = koinViewModel(),
    windowInfoManager: WindowInfoManager = koinInject(),
) {
    @Composable
    fun TitleText(text: String) {
        Text(
            text,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.W900,
        )
    }

    @Composable
    fun ScoreText(text: String) {
        Text(
            text,
            color = HellColors.PrimaryColor,
            fontSize = 30.sp,
            fontWeight = FontWeight.W900,
        )
    }

    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    var visibleIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        repeat(4) {
            delay(700)
            visibleIndex += 1
            playSound(SoundResource.Hit)
        }
    }

    Column(
        modifier = Modifier.fillMaxHeight()
            .padding(
                horizontal = (if (windowInfo.isWidthLargerThanCompact() &&
                    windowInfo.isHeightExpanded().not()
                ) {
                    if (windowInfo.isHeightLargerThanCompact()) 360 else 240
                } else
                    if (windowInfo.isHeightExpanded()) 120 else 40).dp,
            ),
        verticalArrangement = Arrangement.Center,
    ) {
        val entries = listOf(
            "本轮加分" to vm.roundInfo.roundBonusScore.toString(),
            "时间加分" to vm.roundInfo.timeBonusScore.toString(),
            "完美加分" to vm.roundInfo.perfectBonusScore.toString(),
            "总分" to vm.roundInfo.totalScore.toString(),
        )

        entries.forEachIndexed { index, (title, score) ->
            Box(
                modifier = Modifier
                    .animateContentSize()
                    .then(if (visibleIndex < index) Modifier.alpha(0f) else Modifier),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleText(title)
                    Spacer(modifier = Modifier.weight(1f))
                    ScoreText(score)
                }
            }
            if (index != entries.lastIndex) {
                Spacer(modifier = Modifier.size(5.dp))
            }
        }
    }
}

@Composable
fun GameOver(vm: GameViewModel = koinViewModel()) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        repeat(5) {
            delay(500)
            showHint = !showHint
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "游戏结束",
            color = Color.White,
            fontSize = 50.sp,
            fontWeight = FontWeight.W900,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            "你的得分",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.W900,
        )
        Text(
            vm.roundInfo.totalScore.toString(),
            color = HellColors.PrimaryColor,
            fontSize = 40.sp,
            fontWeight = FontWeight.W900,
        )
        Spacer(modifier = Modifier.size(20.dp))
        Text(
            "按下任意方向键重新开始",
            color = HellColors.PrimaryColor,
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.alpha(if (showHint) 1f else 0f),
        )
    }
}

