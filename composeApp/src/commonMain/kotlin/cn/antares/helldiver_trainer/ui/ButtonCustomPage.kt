package cn.antares.helldiver_trainer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.util.WindowInfo
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.util.widget.SuperButton
import cn.antares.helldiver_trainer.util.widget.SuperCircleButton
import cn.antares.helldiver_trainer.util.widget.SuperDialog
import cn.antares.helldiver_trainer.util.widget.SuperScaffoldTopBar
import cn.antares.helldiver_trainer.viewmodel.ArrowButtonViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ButtonCustomPage(
    windowInfoManager: WindowInfoManager = koinInject(),
    keyVM: ArrowButtonViewModel = koinViewModel(),
) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        SuperDialog(
            message = "保存成功",
            confirmButtonText = "确认",
            confirmButtonCallback = { showConfirmDialog = false },
            onDismissRequest = { showConfirmDialog = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, start = 20.dp, end = 20.dp),
        topBar = {
            SuperScaffoldTopBar(title = "按键设置") {
                SuperCircleButton(
                    icon = Icons.Rounded.Save,
                    contentDescription = "保存",
                    onClick = {
                        keyVM.savePositions()
                        showConfirmDialog = true
                    },
                )
            }
        },
    ) { innerPadding ->
        GameAndButtonContainer(showButton = true, adjustMode = true)

        InfoAndSetting(
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding() + 10.dp),
            windowInfo = windowInfo,
        )
    }
}

@Composable
private fun InfoAndSetting(
    keyVM: ArrowButtonViewModel = koinViewModel(),
    modifier: Modifier,
    windowInfo: WindowInfo,
) {
    val buttonConfig by keyVM.config.collectAsState()
    var showSettings by remember { mutableStateOf(true) }
    val flexLayout: @Composable (@Composable () -> Unit) -> Unit =
        if (windowInfo.isTwoPaneCandidate()) {
            { content -> Row(verticalAlignment = Alignment.CenterVertically) { content() } }
        } else {
            { content -> Column { content() } }
        }
    val splitter: @Composable () -> Unit = {
        if (windowInfo.isTwoPaneCandidate()) {
            Spacer(Modifier.width(7.dp))
            VerticalDivider(Modifier.height(30.dp))
            Spacer(Modifier.width(7.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }

    Column(modifier = modifier) {
        SuperCircleButton(
            icon = if (showSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = "显示/隐藏说明",
            onClick = { showSettings = !showSettings },
        )
        AnimatedVisibility(visible = showSettings) {
            Column {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "想调整横/竖屏的按键就旋转到对应方向，调整完毕后点击右上角保存即可\n" +
                            "固定布局模式可整体拖动，自由移动模式可单独拖动每个按键",
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                flexLayout {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (buttonConfig.fixedPositionMode) "固定布局模式" else "自由移动模式",
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.size(5.dp))
                        Switch(
                            checked = buttonConfig.fixedPositionMode,
                            onCheckedChange = { keyVM.setFixedPositionMode(it) },
                        )
                    }
                    splitter()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "按键大小",
                            fontSize = 16.sp,
                        )
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.padding(start = 10.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(10),
                                )
                                .clickable { keyVM.setButtonSize(buttonConfig.buttonSize - 5) },
                        )
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.padding(start = 10.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(10),
                                )
                                .clickable { keyVM.setButtonSize(buttonConfig.buttonSize + 5) },
                        )
                    }
                    splitter()
                    SuperButton(
                        text = "重置",
                        icon = Icons.Rounded.Refresh,
                        onClick = { keyVM.reset() },
                    )
                    splitter()
                    SuperButton(
                        text = "默认排列",
                        icon = Icons.Rounded.Restore,
                        enable = buttonConfig.fixedPositionMode,
                        onClick = { keyVM.setStandardKeyboardMode(false) },
                    )
                    splitter()
                    SuperButton(
                        text = "键盘排列",
                        icon = Icons.Rounded.Keyboard,
                        enable = buttonConfig.fixedPositionMode,
                        onClick = { keyVM.setStandardKeyboardMode(true) },
                    )
                }
            }
        }
    }
}