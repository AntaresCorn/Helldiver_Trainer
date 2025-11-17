package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.StratagemStore
import cn.antares.helldiver_trainer.util.SuperCircleButton
import cn.antares.helldiver_trainer.util.SuperDialog
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import dev.icerock.moko.resources.compose.painterResource
import org.koin.compose.koinInject

@Composable
fun StratagemSelectorPage(
    windowInfoManager: WindowInfoManager = koinInject(),
    kvManager: SharedKVManager = koinInject(),
) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    val horizontalPadding = if (windowInfo.isTabletLandscape()) 100.dp else 0.dp
    val allStratagems = StratagemStore.getAllStratagems()
    val selectedStratagems = kvManager.getSelectedStratagemIDs()
    val stratagemStates = remember(allStratagems, selectedStratagems) {
        allStratagems.associate { stratagem ->
            stratagem.id to mutableStateOf(selectedStratagems.contains(stratagem.id))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, start = 20.dp, end = 20.dp),
        topBar = { TopBar(stratagemStates = stratagemStates) },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        var containerHeight by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    bottom = 10.dp,
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerHeight = coordinates.size.height
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                state = listState,
            ) {
                itemsIndexed(
                    items = allStratagems,
                    key = { _: Int, stratagem: GameViewModel.StratagemItem -> stratagem.id },
                ) { index, stratagem ->
                    StratagemCheckItem(
                        stratagem,
                        index == allStratagems.lastIndex,
                        checked = stratagemStates[stratagem.id]?.value == true,
                        onCheckedChange = { stratagemStates[stratagem.id]?.value = it },
                    )
                }
            }
            ScrollBar(
                modifier = Modifier.align(Alignment.TopEnd),
                state = listState,
                containerHeight = containerHeight,
            )
        }
    }
}

@Composable
private fun StratagemCheckItem(
    stratagem: GameViewModel.StratagemItem,
    isLast: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
    ) {
        Image(
            painter = painterResource(stratagem.icon),
            contentDescription = null,
            modifier = Modifier.size(45.dp),
        )
        Text(
            text = stratagem.name,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(uncheckedColor = Color.LightGray),
        )
    }
    if (isLast.not()) {
        Spacer(Modifier.size(5.dp))
        HorizontalDivider(
            modifier = Modifier.padding(end = 8.dp),
            color = Color.Gray,
        )
        Spacer(Modifier.size(5.dp))
    }
}

@Composable
private fun TopBar(
    navController: NavHostController = LocalNavController.current,
    kvManager: SharedKVManager = koinInject(),
    stratagemStates: Map<String, MutableState<Boolean>>,
) {
    var showWarningDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        SuperCircleButton(
            modifier = Modifier.align(Alignment.CenterStart),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = { navController.navigateUp() },
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            SuperCircleButton(
                icon = Icons.Default.SelectAll,
                contentDescription = "全选/全不选",
                onClick = {
                    val allSelected = stratagemStates.values.all { it.value }
                    stratagemStates.values.forEach { it.value = !allSelected }
                },
            )
            Spacer(Modifier.size(10.dp))
            SuperCircleButton(
                icon = Icons.Default.Save,
                contentDescription = "保存",
                onClick = {
                    val selectedIDs = stratagemStates.filter { it.value.value }.keys.toSet()
                    if (selectedIDs.isEmpty()) {
                        showWarningDialog = true
                    } else {
                        kvManager.setSelectedStratagemIDs(selectedIDs)
                        showConfirmDialog = true
                    }
                },
            )
        }
    }
    if (showWarningDialog) {
        SuperDialog(
            message = "至少选择1个战备",
            confirmButtonText = "确认",
            confirmButtonCallback = { showWarningDialog = false },
            onDismissRequest = { showWarningDialog = false },
        )
    }
    if (showConfirmDialog) {
        SuperDialog(
            message = "保存成功",
            confirmButtonText = "确认",
            confirmButtonCallback = { showConfirmDialog = false },
            onDismissRequest = { showConfirmDialog = false },
        )
    }
}

@Composable
private fun ScrollBar(modifier: Modifier = Modifier, state: LazyListState, containerHeight: Int) {
    val info = state.layoutInfo
    if (info.totalItemsCount > 0 && containerHeight > 0) {
        val visible = info.visibleItemsInfo.size
        val total = info.totalItemsCount
        val thumbH = (visible.toFloat() / total * containerHeight).coerceAtLeast(24f)
        val maxRange = (total - visible).coerceAtLeast(1)
        val offsetPx =
            (state.firstVisibleItemIndex.toFloat() / maxRange) * (containerHeight - thumbH)
        Spacer(
            modifier
                .width(4.dp)
                .height(with(LocalDensity.current) { thumbH.toDp() })
                .offset(y = with(LocalDensity.current) { offsetPx.toDp() })
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}