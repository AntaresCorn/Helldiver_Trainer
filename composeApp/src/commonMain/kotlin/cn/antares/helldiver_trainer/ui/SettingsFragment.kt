package cn.antares.helldiver_trainer.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.util.MyColor
import dev.icerock.moko.resources.compose.painterResource

@Composable
fun SettingsFragment() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val imageSize = 25.dp
    val imagePadding = 3.dp
    val tabs = listOf(
        MR.images.ic_launcher,
        MR.images.ic_automaton,
        MR.images.ic_terminid,
        MR.images.ic_illuminate,
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row {
            Box(modifier = Modifier.weight(0.25f))
            TabRow(
                selectedIndex,
                modifier = Modifier.weight(0.5f)
                    .border(2.dp, MyColor.PrimaryColor, RoundedCornerShape(50)),
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
                    tabs.forEachIndexed { index, image ->
                        Image(
                            painterResource(image),
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = imagePadding).height(imageSize)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    selectedIndex = index
                                },
                        )
                    }
                },
            )
            Box(modifier = Modifier.weight(0.25f))
        }
    }
}