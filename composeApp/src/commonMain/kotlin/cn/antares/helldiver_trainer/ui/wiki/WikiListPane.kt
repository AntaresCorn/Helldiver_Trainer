package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.data.wiki.model.EnemyFaction
import cn.antares.helldiver_trainer.data.wiki.model.EnemySizeClass
import cn.antares.helldiver_trainer.data.wiki.model.StratagemCategory
import cn.antares.helldiver_trainer.data.wiki.model.WeaponClass
import cn.antares.helldiver_trainer.data.wiki.model.WeaponSlot
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.util.widget.SuperButton
import cn.antares.helldiver_trainer.viewmodel.WikiQueryState

/**
 * 资料库检索结果与筛选面板。
 * 支持列表滚动位置的保存与恢复、筛选条件或关键词变化时自动回顶，
 * 以及武器“槽位-子类”二级联动筛选和敌人“阵营-体型”双排筛选。
 */
@Composable
fun WikiListPane(
    query: WikiQueryState,
    results: List<WikiSearchHit>,
    isSearching: Boolean,
    searchError: String?,
    selectedDetailId: String?,
    onSelectKind: (WikiKind?) -> Unit,
    onSelectStratagemCategory: (StratagemCategory?) -> Unit,
    onSelectWeaponSlot: (WeaponSlot?) -> Unit,
    onSelectWeaponClass: (WeaponClass?) -> Unit,
    onSelectEnemyFaction: (EnemyFaction?) -> Unit,
    onSelectEnemySizeClass: (EnemySizeClass?) -> Unit,
    onClearFilters: () -> Unit,
    onSaveScroll: (Int, Int) -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = query.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = query.firstVisibleItemScrollOffset,
    )

    LaunchedEffect(
        query.queryText,
        query.selectedKind,
        query.stratagemCategory,
        query.weaponSlot,
        query.weaponClass,
        query.enemyFaction,
        query.enemySizeClass,
    ) {
        if (query.firstVisibleItemIndex == 0 && query.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0, 0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 大类切换条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WikiFilterChip(
                label = "全部",
                selected = query.selectedKind == null,
                onClick = { onSelectKind(null) },
            )
            WikiKind.entries.forEach { kind ->
                WikiFilterChip(
                    label = kind.labelZh,
                    selected = query.selectedKind == kind,
                    onClick = { onSelectKind(kind) },
                )
            }
        }

        // 细分筛选项
        when (query.selectedKind) {
            WikiKind.STRATAGEM -> {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WikiFilterChip(
                        label = "全部分类",
                        selected = query.stratagemCategory == null,
                        onClick = { onSelectStratagemCategory(null) },
                    )
                    StratagemCategory.entries.forEach { cat ->
                        WikiFilterChip(
                            label = cat.labelZh,
                            selected = query.stratagemCategory == cat,
                            onClick = { onSelectStratagemCategory(cat) },
                        )
                    }
                }
            }

            WikiKind.WEAPON -> {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WikiFilterChip(
                        label = "全部槽位",
                        selected = query.weaponSlot == null,
                        onClick = { onSelectWeaponSlot(null) },
                    )
                    WeaponSlot.entries.forEach { slot ->
                        WikiFilterChip(
                            label = slot.labelZh,
                            selected = query.weaponSlot == slot,
                            onClick = { onSelectWeaponSlot(slot) },
                        )
                    }
                }
                val selectedSlot = query.weaponSlot
                if (selectedSlot != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WikiFilterChip(
                            label = "全部子类",
                            selected = query.weaponClass == null,
                            onClick = { onSelectWeaponClass(null) },
                        )
                        selectedSlot.allowedClasses().forEach { wClass ->
                            WikiFilterChip(
                                label = wClass.labelZh,
                                selected = query.weaponClass == wClass,
                                onClick = { onSelectWeaponClass(wClass) },
                            )
                        }
                    }
                }
            }

            WikiKind.ENEMY -> {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WikiFilterChip(
                        label = "全部阵营",
                        selected = query.enemyFaction == null,
                        onClick = { onSelectEnemyFaction(null) },
                    )
                    EnemyFaction.entries.forEach { faction ->
                        WikiFilterChip(
                            label = faction.labelZh,
                            selected = query.enemyFaction == faction,
                            onClick = { onSelectEnemyFaction(faction) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WikiFilterChip(
                        label = "全部体型",
                        selected = query.enemySizeClass == null,
                        onClick = { onSelectEnemySizeClass(null) },
                    )
                    EnemySizeClass.entries.forEach { size ->
                        WikiFilterChip(
                            label = size.labelZh,
                            selected = query.enemySizeClass == size,
                            onClick = { onSelectEnemySizeClass(size) },
                        )
                    }
                }
            }

            null -> Unit
        }

        Spacer(Modifier.height(10.dp))

        when {
            searchError != null -> {
                WikiErrorBox(
                    message = searchError,
                    showRetry = false,
                    onRetry = {},
                )
            }

            isSearching && results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            results.isEmpty() -> {
                WikiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "没有找到匹配的条目",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        if (query.hasActiveFacet) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "当前筛选条件可能过窄，可清除筛选后重试",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            SuperButton(
                                text = "清除筛选",
                                icon = Icons.Rounded.Clear,
                                onClick = onClearFilters,
                            )
                        }
                    }
                }
            }

            else -> {
                Text(
                    text = "共 ${results.size} 条结果",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(
                        items = results,
                        key = { it.item.entryId },
                    ) { hit ->
                        WikiIndexItemCard(
                            item = hit.item,
                            selected = hit.item.entryId == selectedDetailId,
                            onClick = {
                                onSaveScroll(
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset,
                                )
                                onOpenItem(hit.item.entryId)
                            },
                        )
                    }
                }
            }
        }
    }
}
