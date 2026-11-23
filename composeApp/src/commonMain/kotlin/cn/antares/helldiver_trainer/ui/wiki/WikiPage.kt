package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.bridge.PlatformBackHandler
import cn.antares.helldiver_trainer.ui.LocalNavController
import cn.antares.helldiver_trainer.util.widget.SuperScaffoldTopBar
import cn.antares.helldiver_trainer.viewmodel.WikiLoadState
import cn.antares.helldiver_trainer.viewmodel.WikiUiState
import cn.antares.helldiver_trainer.viewmodel.WikiViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 资料库主页面入口。
 * 根据屏幕宽度以 `840.dp` 为响应式断点在窄屏单栏与宽屏双栏间切换，
 * 并统一将 Android 返回手势与 Desktop `Esc` 键事件接入 `handleBack` 分层回退逻辑。
 */
@Composable
fun WikiPage(
    vm: WikiViewModel = koinViewModel(),
) {
    val navController = LocalNavController.current
    val uiState by vm.uiState.collectAsState()

    val handleBack: () -> Unit = {
        if (!vm.onBack()) {
            navController.navigateUp()
        }
    }

    PlatformBackHandler(enabled = true, onBack = handleBack)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    handleBack()
                    true
                } else {
                    false
                }
            },
    ) {
        val isWideScreen = maxWidth >= 840.dp
        val topTitle = when {
            !isWideScreen && uiState.currentDetailId != null -> "条目详情"
            uiState.query.selectedKind != null -> "资料库 · ${uiState.query.selectedKind!!.labelZh}"
            !uiState.query.isHomeMode -> "资料库 · 搜索"
            else -> "资料库"
        }

        Scaffold(
            modifier = Modifier.fillMaxSize().padding(top = 20.dp, start = 20.dp, end = 20.dp),
            topBar = {
                SuperScaffoldTopBar(
                    title = topTitle,
                    onBack = handleBack,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding() + 12.dp),
            ) {
                when (val idxState = uiState.indexState) {
                    is WikiLoadState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    is WikiLoadState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            WikiErrorBox(
                                message = idxState.message,
                                showRetry = !idxState.isUnsupportedVersion,
                                onRetry = vm::retryInit,
                            )
                        }
                    }

                    is WikiLoadState.Content -> {
                        if (isWideScreen) {
                            WideWikiContent(vm = vm, uiState = uiState)
                        } else {
                            NarrowWikiContent(vm = vm, uiState = uiState)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 窄屏单栏布局内容：当详情栈非空时全屏展示详情页，否则展示搜索栏与首页或结果列表。
 */
@Composable
private fun NarrowWikiContent(
    vm: WikiViewModel,
    uiState: WikiUiState,
) {
    if (uiState.detailStack.isNotEmpty()) {
        WikiDetailPane(
            detailState = uiState.currentDetailState,
            onRetryDetail = vm::retryCurrentDetail,
            onOpenRelated = vm::openRelatedItem,
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            WikiSearchBar(
                queryText = uiState.query.queryText,
                onQueryChange = { vm.updateQueryText(it, isWideScreen = false) },
            )
            Spacer(Modifier.height(12.dp))
            if (uiState.query.isHomeMode) {
                WikiHomePane(
                    kindCounts = uiState.kindCounts,
                    onSelectKind = { vm.selectKind(it) },
                )
            } else {
                WikiListPane(
                    query = uiState.query,
                    results = uiState.results,
                    isSearching = uiState.isSearching,
                    searchError = uiState.searchError,
                    selectedDetailId = null,
                    onSelectKind = vm::selectKind,
                    onSelectStratagemCategory = vm::updateStratagemCategory,
                    onSelectWeaponSlot = vm::updateWeaponSlot,
                    onSelectWeaponClass = vm::updateWeaponClass,
                    onSelectEnemyFaction = vm::updateEnemyFaction,
                    onSelectEnemySizeClass = vm::updateEnemySizeClass,
                    onClearFilters = vm::clearFilters,
                    onSaveScroll = vm::saveListScroll,
                    onOpenItem = { vm.openResultItem(it, isWideScreen = false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 宽屏双栏主从布局：左侧展示搜索与首页/结果列表（占 42%），右侧固定展示当前选中条目的详情（占 58%）。
 */
@Composable
private fun WideWikiContent(
    vm: WikiViewModel,
    uiState: WikiUiState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
        ) {
            WikiSearchBar(
                queryText = uiState.query.queryText,
                onQueryChange = { vm.updateQueryText(it, isWideScreen = true) },
            )
            Spacer(Modifier.height(12.dp))
            if (uiState.query.isHomeMode) {
                WikiHomePane(
                    kindCounts = uiState.kindCounts,
                    onSelectKind = { vm.selectKind(it) },
                )
            } else {
                WikiListPane(
                    query = uiState.query,
                    results = uiState.results,
                    isSearching = uiState.isSearching,
                    searchError = uiState.searchError,
                    selectedDetailId = uiState.currentDetailId,
                    onSelectKind = vm::selectKind,
                    onSelectStratagemCategory = vm::updateStratagemCategory,
                    onSelectWeaponSlot = vm::updateWeaponSlot,
                    onSelectWeaponClass = vm::updateWeaponClass,
                    onSelectEnemyFaction = vm::updateEnemyFaction,
                    onSelectEnemySizeClass = vm::updateEnemySizeClass,
                    onClearFilters = vm::clearFilters,
                    onSaveScroll = vm::saveListScroll,
                    onOpenItem = { vm.openResultItem(it, isWideScreen = true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        VerticalDivider(color = WikiBorderColor)
        Spacer(Modifier.width(12.dp))
        WikiDetailPane(
            detailState = uiState.currentDetailState,
            onRetryDetail = vm::retryCurrentDetail,
            onOpenRelated = vm::openRelatedItem,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
        )
    }
}
