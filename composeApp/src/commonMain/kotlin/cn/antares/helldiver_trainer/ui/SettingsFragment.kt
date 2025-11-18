package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.antares.helldiver_trainer.BuildKonfig
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.NavRoute
import cn.antares.helldiver_trainer.bridge.openWebPage
import cn.antares.helldiver_trainer.util.HellUtils
import cn.antares.helldiver_trainer.util.LinkStore
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.SuperDialog
import cn.antares.helldiver_trainer.util.ThemeState
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.viewmodel.AppViewModel
import dev.icerock.moko.resources.compose.painterResource
import org.koin.compose.koinInject

@Composable
fun SettingsFragment(
    kvManager: SharedKVManager = koinInject(),
    navController: NavHostController = LocalNavController.current,
    windowInfoManager: WindowInfoManager = koinInject(),
) {
    var infiniteModeState by remember { mutableStateOf(kvManager.isInfiniteMode()) }
    var stratagemSelectorState by remember { mutableStateOf(kvManager.isStratagemSelectMode()) }
    var showInfiniteModeInfo by remember { mutableStateOf(false) }
    var showCustomStratagemInfo by remember { mutableStateOf(false) }
    if (showInfiniteModeInfo) {
        SuperDialog(
            onDismissRequest = { showInfiniteModeInfo = false },
            confirmButtonText = "确认",
            confirmButtonCallback = { showInfiniteModeInfo = false },
            message = "无限模式下，没有回合与时限，所有战备将随机排列。也可与自选战备功能配合使用",
        )
    }
    if (showCustomStratagemInfo) {
        SuperDialog(
            onDismissRequest = { showCustomStratagemInfo = false },
            confirmButtonText = "确认",
            confirmButtonCallback = { showCustomStratagemInfo = false },
            message = "设置后的战备将应用于所有模式\n若更新后加入了新战备，需要进入设置手动勾选才能生效",
        )
    }

    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    val horizontalPadding = if (windowInfo.isTwoPaneCandidate() && windowInfo.isTabletLandscape()) {
        100.dp
    } else {
        20.dp
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            SettingSwitchItem(
                "自选战备",
                stratagemSelectorState,
                showInfoIcon = true,
                infoClickCallback = { showCustomStratagemInfo = true },
                showMoreEntry = true,
                moreEntryCallback = {
                    navController.navigate(NavRoute.RouteList.StratagemSelector) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(NavRoute.RouteList.Main.MainContainer) {
                            saveState = true
                        }
                    }
                },
                onSwitchChanged = {
                    stratagemSelectorState = it
                    kvManager.setStratagemSelectMode(it)
                },
            )
            SettingSwitchItem(
                "无限模式",
                infiniteModeState,
                showInfoIcon = true,
                infoClickCallback = { showInfiniteModeInfo = true },
                onSwitchChanged = {
                    infiniteModeState = it
                    kvManager.setInfiniteMode(it)
                },
            )
            Spacer(modifier = Modifier.size(20.dp))
            FactionSelector()
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("版本号: ${BuildKonfig.VERSION_NAME}", color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.size(5.dp))
            Row {
                RepositoryLink()
                Spacer(modifier = Modifier.width(20.dp))
                UpdateChecker()
            }
        }
    }
}

@Composable
private fun SettingSwitchItem(
    text: String,
    initialSwitchState: Boolean,
    showInfoIcon: Boolean = false,
    infoClickCallback: (() -> Unit)? = null,
    showMoreEntry: Boolean = false,
    moreEntryCallback: (() -> Unit)? = null,
    onSwitchChanged: (Boolean) -> Unit,
) {
    BaseSettingItemLayout(
        title = text,
        showInfoIcon = showInfoIcon,
        infoClickCallback = infoClickCallback,
        showMoreEntry = showMoreEntry,
        moreEntryCallback = moreEntryCallback,
    ) {
        Switch(
            checked = initialSwitchState,
            onCheckedChange = { onSwitchChanged.invoke(it) },
        )
    }
}

@Composable
private fun SettingButtonItem(
    text: String,
    showInfoIcon: Boolean = false,
    infoClickCallback: (() -> Unit)? = null,
    buttonText: String,
    onButtonClick: (Boolean) -> Unit,
) {
    BaseSettingItemLayout(
        title = text,
        showInfoIcon = showInfoIcon,
        infoClickCallback = infoClickCallback,
    ) {
        Button(
            onClick = { onButtonClick.invoke(true) },
            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(buttonText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BaseSettingItemLayout(
    title: String,
    showInfoIcon: Boolean = false,
    infoClickCallback: (() -> Unit)? = null,
    showMoreEntry: Boolean = false,
    moreEntryCallback: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, color = Color.White)
                if (showInfoIcon) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(start = 5.dp).size(20.dp).clickable {
                            infoClickCallback?.invoke()
                        },
                    )
                }
                if (showMoreEntry) {
                    Box(Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 10.dp)
                                .size(20.dp)
                                .align(Alignment.CenterEnd)
                                .clickable { moreEntryCallback?.invoke() },
                        )
                    }
                }
            }
            content()
        }
        HorizontalDivider()
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
            PrimaryTabRow(
                selectedIndex,
                modifier = Modifier.weight(1f)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50),
                    ),
                containerColor = Color.Transparent,
                divider = {},
                indicator = {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .tabIndicatorOffset(selectedIndex)
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
        }
        Spacer(Modifier.size(10.dp))
        Text(
            buildAnnotatedString {
                append("我是 ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
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
private fun RepositoryLink() {
    Button(onClick = { openWebPage(LinkStore.GITHUB_REPO, useSystemBrowser = true) }) {
        Text("项目仓库", fontSize = 14.sp)
        Image(
            painterResource(MR.images.ic_github_mark),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 5.dp)
                .size(16.dp),
        )
    }
}

@Composable
private fun UpdateChecker(vm: AppViewModel = koinInject()) {
    val updateState by vm.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var pendingShowDialog by remember { mutableStateOf(false) }

    @Composable
    fun UpdateDialog() {
        val titleText = when (updateState) {
            is AppViewModel.UpdateState.NewRelease -> "检测到有新版本"
            is AppViewModel.UpdateState.UpToDate -> "当前已是最新版本"
            is AppViewModel.UpdateState.Error -> "更新检查失败"
            else -> ""
        }
        val messageText = when (updateState) {
            is AppViewModel.UpdateState.NewRelease -> "是否跳转下载页"
            is AppViewModel.UpdateState.Error -> (updateState as AppViewModel.UpdateState.Error).message
            else -> ""
        }
        val confirmButtonText = when (updateState) {
            is AppViewModel.UpdateState.NewRelease -> "前往"
            is AppViewModel.UpdateState.UpToDate,
            is AppViewModel.UpdateState.Error,
                -> "确认"

            else -> ""
        }
        SuperDialog(
            onDismissRequest = { showDialog = false },
            confirmButtonText = confirmButtonText,
            confirmButtonCallback = {
                showDialog = false
                if (updateState is AppViewModel.UpdateState.NewRelease) {
                    openWebPage(
                        (updateState as? AppViewModel.UpdateState.NewRelease)?.release?.url
                            ?: LinkStore.GITHUB_REPO,
                        useSystemBrowser = true,
                    )
                }
            },
            dismissButtonText = if (updateState is AppViewModel.UpdateState.NewRelease) "取消" else null,
            dismissButtonCallback = if (updateState is AppViewModel.UpdateState.NewRelease) {
                {
                    showDialog = false
                }
            } else null,
            title = titleText,
            message = messageText,
        )
    }

    LaunchedEffect(updateState, pendingShowDialog) {
        when (updateState) {
            is AppViewModel.UpdateState.NewRelease,
            is AppViewModel.UpdateState.UpToDate,
            is AppViewModel.UpdateState.Error,
                -> {
                if (pendingShowDialog) {
                    showDialog = true
                    pendingShowDialog = false
                }
            }

            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        if (HellUtils.checkedUpdate.not()) {
            vm.checkForUpdates()
            HellUtils.checkedUpdate = true
        }
    }

    if (showDialog) {
        UpdateDialog()
    }

    Button(
        onClick = {
            if (updateState is AppViewModel.UpdateState.NewRelease) {
                showDialog = true
            } else {
                vm.checkForUpdates()
                pendingShowDialog = true
            }
        },
    ) {
        if (updateState is AppViewModel.UpdateState.NewRelease) {
            Box(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
                    .align(Alignment.Top),
            )
        }
        Text("检查更新", fontSize = 14.sp)
        Icon(
            Icons.Default.SystemUpdate,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 5.dp)
                .size(18.dp),
        )
    }
}