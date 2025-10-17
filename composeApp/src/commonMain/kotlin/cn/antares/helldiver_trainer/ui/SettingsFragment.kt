package cn.antares.helldiver_trainer.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.bridge.openWebPage
import cn.antares.helldiver_trainer.util.HellColors
import cn.antares.helldiver_trainer.util.LinkStore
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.ThemeState
import cn.antares.helldiver_trainer.util.ThemeState.MyTheme.getPrimaryColor
import dev.icerock.moko.resources.compose.painterResource
import org.koin.compose.koinInject

@Composable
fun SettingsFragment() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FactionSelector()
        RepositoryLink(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

@Composable
private fun FactionSelector(
    sharedKVManager: SharedKVManager = koinInject(),
    themeState: ThemeState = koinInject(),
) {
    val imageSize = 25.dp
    val imagePadding = 5.dp
    val tabs = listOf(
        SharedKVManager.Companion.UserFaction.HELLDIVER to MR.images.ic_launcher,
        SharedKVManager.Companion.UserFaction.AUTOMATON to MR.images.ic_automaton,
        SharedKVManager.Companion.UserFaction.TERMINID to MR.images.ic_terminid,
        SharedKVManager.Companion.UserFaction.ILLUMINATE to MR.images.ic_illuminate,
    )
    var selectedIndex by remember {
        mutableIntStateOf(
            tabs.indexOfFirst { it.first == sharedKVManager.getUserFaction() }
                .let { if (it == -1) 0 else it },
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Box(modifier = Modifier.weight(0.25f))
            TabRow(
                selectedIndex,
                modifier = Modifier.weight(0.5f)
                    .border(
                        2.dp,
                        themeState.currentTheme.getPrimaryColor(),
                        RoundedCornerShape(50),
                    ),
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    val current = tabPositions[selectedIndex]
                    val targetWidth = current.width
                    val targetOffset = current.left

                    val indicatorWidth by animateDpAsState(targetValue = targetWidth)
                    val indicatorOffset by animateDpAsState(targetValue = targetOffset)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth() // 父容器占满 TabRow
                            .wrapContentSize(Alignment.BottomStart) // 使内部可按 offset 定位
                            .offset(x = indicatorOffset) // 将指示条移动到目标 tab 的 left
                            .width(indicatorWidth) // 限定指示条宽度为 tab 宽度
                            .height(imageSize + imagePadding * 2) // 指示条高度
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                    )
                },
                tabs = {
                    tabs.forEachIndexed { index, (factionName, factionImage) ->
                        Image(
                            painterResource(factionImage),
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = imagePadding).height(imageSize)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    selectedIndex = index
                                    sharedKVManager.setUserFaction(factionName)
                                    themeState.currentTheme = when (factionName) {
                                        SharedKVManager.Companion.UserFaction.HELLDIVER -> ThemeState.AppTheme.HELLDIVER
                                        SharedKVManager.Companion.UserFaction.AUTOMATON -> ThemeState.AppTheme.AUTOMATON
                                        SharedKVManager.Companion.UserFaction.TERMINID -> ThemeState.AppTheme.TERMINID
                                        SharedKVManager.Companion.UserFaction.ILLUMINATE -> ThemeState.AppTheme.ILLUMINATE
                                        else -> ThemeState.AppTheme.HELLDIVER
                                    }
                                },
                        )
                    }
                },
            )
            Box(modifier = Modifier.weight(0.25f))
        }
        Spacer(Modifier.size(20.dp))
        Text(
            buildAnnotatedString {
                append("我是 ")
                withStyle(
                    SpanStyle(
                        color = when (tabs[selectedIndex].first) {
                            SharedKVManager.Companion.UserFaction.HELLDIVER -> HellColors.HelldiverColor
                            SharedKVManager.Companion.UserFaction.AUTOMATON -> HellColors.AutomatonColor
                            SharedKVManager.Companion.UserFaction.TERMINID -> HellColors.TerminidColor
                            SharedKVManager.Companion.UserFaction.ILLUMINATE -> HellColors.IlluminateColor
                            else -> HellColors.HelldiverColor
                        },
                    ),
                ) {
                    append(
                        when (tabs[selectedIndex].first) {
                            SharedKVManager.Companion.UserFaction.HELLDIVER -> "绝地潜兵"
                            SharedKVManager.Companion.UserFaction.AUTOMATON -> "机器人"
                            SharedKVManager.Companion.UserFaction.TERMINID -> "终结族"
                            SharedKVManager.Companion.UserFaction.ILLUMINATE -> "光能者"
                            else -> "绝地潜兵"
                        },
                    )
                }
                append(" !")
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun RepositoryLink(modifier: Modifier, themeState: ThemeState = koinInject()) {
    Box(modifier) {
        Button(onClick = { openWebPage(LinkStore.GITHUB_REPO, useSystemBrowser = true) }) {
            Text("项目仓库", fontSize = 14.sp)
            Image(
                painterResource(
                    if (themeState.currentTheme == ThemeState.AppTheme.ILLUMINATE)
                        MR.images.ic_github_mark_white
                    else
                        MR.images.ic_github_mark,
                ),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(16.dp),
            )
        }
    }
}