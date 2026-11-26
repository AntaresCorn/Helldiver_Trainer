package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.bridge.decodeImageBitmap
import cn.antares.helldiver_trainer.data.wiki.anatomy.AnatomyGeometry
import cn.antares.helldiver_trainer.data.wiki.anatomy.Vec2
import cn.antares.helldiver_trainer.data.wiki.model.AnatomyView
import cn.antares.helldiver_trainer.data.wiki.model.EnemyAnatomyBundle
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.formatDisplay
import cn.antares.helldiver_trainer.util.widget.SuperButton

/**
 * 敌人解剖图鉴展示器：根据所选角度与当前部位直出对应渲染图，
 * 并通过透明绿色区域映射层承接手势缩放、平移与点击部位识别（无任何 Canvas 多边形高亮覆盖）。
 */
@Composable
fun EnemyAnatomyViewer(
    anatomy: EnemyAnatomyBundle?,
    anatomyError: String?,
    selectedViewId: String?,
    selectedPartId: String?,
    previewState: EnemyPartState,
    onSelectView: (String) -> Unit,
    onTapPartFromImage: (String) -> Unit,
    onLoadImageBytes: suspend (String) -> WikiResult<ByteArray>,
    onRetryAnatomy: () -> Unit,
    modifier: Modifier = Modifier,
    maxImageHeight: Dp = Dp.Unspecified,
) {
    val views = anatomy?.views.orEmpty()
    val soleViewIdForSelectedPart = remember(views, selectedPartId) {
        if (selectedPartId == null) {
            null
        } else {
            val matching = views.filter { selectedPartId in it.partAssets }
            if (matching.size == 1) matching.first().viewId else null
        }
    }
    val viewSelectionEnabled = soleViewIdForSelectedPart == null
    val currentView: AnatomyView? = soleViewIdForSelectedPart?.let { soleId ->
        views.firstOrNull { it.viewId == soleId }
    } ?: views.firstOrNull { it.viewId == selectedViewId }
    ?: views.firstOrNull { it.viewId == anatomy?.defaultViewId }
    ?: views.firstOrNull()

    val decodedHitMap = remember(currentView?.viewId, currentView?.assetRevision, previewState) {
        AnatomyGeometry.decodeHitMap(currentView?.hitMap, previewState)
    }

    val targetAssetKey = currentView?.resolveAssetKey(selectedPartId)

    var zoom by remember(currentView?.viewId) { mutableFloatStateOf(1f) }
    var panX by remember(currentView?.viewId) { mutableFloatStateOf(0f) }
    var panY by remember(currentView?.viewId) { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageReloadNonce by remember { mutableIntStateOf(0) }

    // 按当前敌人缓存已解码的部位图，实现同敌人内点击切换零闪烁秒切
    val bitmapCache = remember(anatomy?.enemyId) { mutableStateMapOf<String, ImageBitmap>() }
    var displayedBitmap by remember(anatomy?.enemyId) { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingImage by remember(targetAssetKey, imageReloadNonce) {
        mutableStateOf(targetAssetKey != null && bitmapCache[targetAssetKey] == null)
    }
    var imageLoadError by remember(targetAssetKey, imageReloadNonce) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(targetAssetKey, currentView?.assetRevision, imageReloadNonce) {
        val key = targetAssetKey
        if (key == null) {
            displayedBitmap = null
            isLoadingImage = false
            imageLoadError = null
            return@LaunchedEffect
        }
        val cached = bitmapCache[key]
        if (cached != null) {
            displayedBitmap = cached
            isLoadingImage = false
            imageLoadError = null
            return@LaunchedEffect
        }

        isLoadingImage = true
        imageLoadError = null
        when (val res = onLoadImageBytes(key)) {
            is WikiResult.Success -> {
                val decoded = decodeImageBitmap(res.data)
                if (decoded != null) {
                    bitmapCache[key] = decoded
                    displayedBitmap = decoded
                    imageLoadError = null
                } else {
                    imageLoadError = "部位图片解码失败"
                }
            }

            is WikiResult.Missing -> {
                imageLoadError = "未找到部位图片资源"
            }

            is WikiResult.ReadError -> {
                imageLoadError = res.message
            }

            is WikiResult.UnsupportedVersion -> {
                imageLoadError = "图片格式版本不兼容"
            }
        }
        isLoadingImage = false
    }

    val primary = MaterialTheme.colorScheme.primary

    WikiSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "部位解剖图鉴",
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = {
                        zoom = 1f
                        panX = 0f
                        panY = 0f
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOutMap,
                        contentDescription = "重置缩放",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (views.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    views.forEach { view ->
                        WikiFilterChip(
                            label = view.labelZh,
                            selected = view.viewId == currentView?.viewId,
                            enabled = viewSelectionEnabled,
                            onClick = { onSelectView(view.viewId) },
                        )
                    }
                }
            }

            if (anatomyError != null && currentView == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(WikiPanelBg)
                        .border(1.dp, WikiBorderColor, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "图鉴加载受限：$anatomyError",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        SuperButton(
                            text = "重试",
                            icon = Icons.Rounded.Refresh,
                            onClick = onRetryAnatomy,
                        )
                    }
                }
            } else if (currentView != null) {
                val aspectRatio = remember(currentView) {
                    if (currentView.width > 0 && currentView.height > 0) {
                        (currentView.width.toFloat() / currentView.height.toFloat()).coerceIn(
                            0.6f,
                            2.2f,
                        )
                    } else {
                        1.5f
                    }
                }

                val heightMod = if (maxImageHeight != Dp.Unspecified) {
                    Modifier.heightIn(max = maxImageHeight)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(heightMod)
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WikiPanelBg)
                        .border(1.dp, WikiBorderColor, RoundedCornerShape(8.dp))
                        .clipToBounds()
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = displayedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "${currentView.labelZh}解剖图",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX
                                    translationY = panY
                                    transformOrigin = TransformOrigin.Center
                                },
                        )
                    }

                    // 透明点击区域映射层：覆盖于图片容器之上，负责缩放、平移及绿色映射表查表切换部位
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentView.viewId) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                    if (zoom > 1f) {
                                        panX += pan.x
                                        panY += pan.y
                                    } else {
                                        panX = 0f
                                        panY = 0f
                                    }
                                }
                            }
                            .pointerInput(
                                currentView.viewId,
                                decodedHitMap,
                                selectedPartId,
                                containerSize,
                            ) {
                                detectTapGestures { offset ->
                                    val width = containerSize.width.toFloat()
                                    val height = containerSize.height.toFloat()
                                    val fitRect = AnatomyGeometry.computeFitRect(
                                        containerWidth = width,
                                        containerHeight = height,
                                        imageWidth = currentView.width,
                                        imageHeight = currentView.height,
                                    ) ?: return@detectTapGestures

                                    val norm = AnatomyGeometry.screenToNormalized(
                                        tap = Vec2(offset.x, offset.y),
                                        fitRect = fitRect,
                                        containerWidth = width,
                                        containerHeight = height,
                                        zoom = zoom,
                                        pan = Vec2(panX, panY),
                                    ) ?: return@detectTapGestures

                                    val hitPartId = AnatomyGeometry.hitTestGrid(
                                        u = norm.x,
                                        v = norm.y,
                                        decoded = decodedHitMap,
                                    )
                                    val nextPartId = AnatomyGeometry.resolveSelectionOnTap(
                                        hitPartId = hitPartId,
                                        currentSelectedPartId = selectedPartId,
                                    )
                                    if (nextPartId != null && nextPartId != selectedPartId) {
                                        onTapPartFromImage(nextPartId)
                                    }
                                }
                            },
                    )

                    if (isLoadingImage && bmp == null) {
                        CircularProgressIndicator(color = primary)
                    } else if (imageLoadError != null && bmp == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = imageLoadError ?: "图片加载失败",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            SuperButton(
                                text = "重新加载图片",
                                icon = Icons.Rounded.Refresh,
                                onClick = { imageReloadNonce++ },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个敌人部位无障碍行卡片：可独立嵌入 `LazyColumn`，支持键盘聚焦与点击切换对应部位图片。
 */
@Composable
fun EnemyPartRow(
    part: EnemyPartDetail,
    isSelected: Boolean,
    partsById: Map<String, EnemyPartDetail>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    val bg = if (isSelected) primary.copy(alpha = 0.16f) else WikiPanelBg
    val borderColor = if (isSelected) primary else WikiBorderColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .focusable()
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = "${part.nameZh}，护甲 ${part.armor.formatDisplay()}"
            }
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = part.nameZh,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "护甲 ${part.armor.formatDisplay()}",
                color = primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "耐久度: ${part.durability.formatDisplay()}",
                color = Color.LightGray,
                fontSize = 11.sp,
            )
            Text(
                text = "传伤比: ${part.toMainRatio.formatDisplay()}",
                color = Color.LightGray,
                fontSize = 11.sp,
            )
            Text(
                text = "爆炸抗性: ${part.explosionResistance.formatDisplay()}",
                color = Color.LightGray,
                fontSize = 11.sp,
            )
        }
        if (part.protectedByPartIds.isNotEmpty()) {
            val names = part.protectedByPartIds.map { partsById[it]?.nameZh ?: it }
            Text(
                text = "受外甲保护: ${names.joinToString("、")}",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
            )
        }
        if (part.onBreak.isNotEmpty()) {
            HorizontalDivider(color = WikiBorderColor)
            part.onBreak.forEach { effect ->
                Text(
                    text = "破甲效果 · ${effect.effectType.labelZh}：${effect.descriptionZh}",
                    color = Color(0xFF81C784),
                    fontSize = 11.sp,
                )
            }
        }
        if (!part.notes.isNullOrBlank()) {
            Text(
                text = part.notes,
                color = Color.LightGray,
                fontSize = 11.sp,
            )
        }
    }
}
