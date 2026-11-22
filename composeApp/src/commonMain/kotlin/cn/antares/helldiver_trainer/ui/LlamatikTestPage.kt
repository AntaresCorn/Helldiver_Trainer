package cn.antares.helldiver_trainer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.bridge.DEFAULT_LLAMATIK_MODEL_FILE
import cn.antares.helldiver_trainer.util.widget.SuperCircleButton
import cn.antares.helldiver_trainer.util.widget.SuperScaffoldTopBar
import cn.antares.helldiver_trainer.viewmodel.ChatMessage
import cn.antares.helldiver_trainer.viewmodel.ChatRole
import cn.antares.helldiver_trainer.viewmodel.LlamatikTestUiState
import cn.antares.helldiver_trainer.viewmodel.LlamatikTestViewModel
import cn.antares.helldiver_trainer.viewmodel.ModelLoadStatus
import cn.antares.helldiver_trainer.viewmodel.parseAssistantOutput
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LlamatikTestPage(
    vm: LlamatikTestViewModel = koinViewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    var shouldAutoScroll by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    shouldAutoScroll = false
                } else if (available.y < 0f && !listState.canScrollForward) {
                    shouldAutoScroll = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && !listState.canScrollForward) {
            shouldAutoScroll = true
        }
    }

    val lastMessageLength = uiState.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            shouldAutoScroll = true
            listState.animateScrollToItem(uiState.messages.size)
        }
    }
    LaunchedEffect(lastMessageLength) {
        if (uiState.messages.isNotEmpty() && shouldAutoScroll && !listState.isScrollInProgress) {
            listState.scrollToItem(uiState.messages.size)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, start = 20.dp, end = 20.dp),
        topBar = {
            SuperScaffoldTopBar(title = "Llamatik 测试") {
                if (uiState.messages.isNotEmpty() && !uiState.isGenerating) {
                    SuperCircleButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "清空对话",
                        onClick = vm::clearMessages,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 12.dp),
        ) {
            ModelControlCard(
                uiState = uiState,
                onLoadClick = vm::loadModel,
                onUnloadClick = vm::unloadModel,
                onRequestPermissionClick = vm::requestPermission,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (uiState.messages.isEmpty()) {
                    Text(
                        text = "请先点击上方「加载模型」手动加载本地模型，加载完成后在下方输入文字开始对话。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, item -> item.id },
                        ) { index, message ->
                            val isStreamingLast =
                                uiState.isGenerating && index == uiState.messages.lastIndex
                            ChatMessageBubble(
                                message = message,
                                isStreaming = isStreamingLast,
                            )
                        }
                        item(key = "chat_bottom_anchor") {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = vm::updateInputText,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "输入对话内容...",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    enabled = uiState.modelStatus == ModelLoadStatus.LOADED && !uiState.isGenerating,
                    maxLines = 4,
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (uiState.isGenerating) {
                    OutlinedButton(onClick = vm::cancelGeneration) {
                        Text("停止")
                    }
                } else {
                    Button(
                        onClick = vm::sendMessage,
                        enabled = uiState.modelStatus == ModelLoadStatus.LOADED &&
                                uiState.inputText.isNotBlank(),
                    ) {
                        Text("发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelControlCard(
    uiState: LlamatikTestUiState,
    onLoadClick: () -> Unit,
    onUnloadClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "模型: $DEFAULT_LLAMATIK_MODEL_FILE",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "路径: ${uiState.modelPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "状态: ${uiState.statusMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.modelStatus == ModelLoadStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (uiState.modelStatus) {
                    ModelLoadStatus.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            text = "加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    ModelLoadStatus.LOADED -> {
                        OutlinedButton(
                            onClick = onUnloadClick,
                            enabled = !uiState.isGenerating,
                        ) {
                            Text("卸载模型")
                        }
                    }

                    ModelLoadStatus.IDLE,
                    ModelLoadStatus.ERROR,
                        -> {
                        Button(onClick = onLoadClick) {
                            Text("加载模型")
                        }
                    }
                }

                if (uiState.needsStoragePermission) {
                    OutlinedButton(onClick = onRequestPermissionClick) {
                        Text("授予文件访问权限")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isStreaming: Boolean,
) {
    val isUser = message.role == ChatRole.USER
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            if (isUser) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                AssistantMessageContent(
                    rawContent = message.content,
                    messageId = message.id,
                    isStreaming = isStreaming,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageContent(
    rawContent: String,
    messageId: Long,
    isStreaming: Boolean,
) {
    val parsed = remember(rawContent, isStreaming) {
        parseAssistantOutput(rawContent, isStreaming)
    }

    var isThinkingExpanded by remember(messageId) { mutableStateOf(false) }
    var wasThinkingInProgress by remember(messageId) { mutableStateOf(parsed.isThinkingInProgress) }

    LaunchedEffect(parsed.isThinkingInProgress) {
        if (wasThinkingInProgress && !parsed.isThinkingInProgress) {
            isThinkingExpanded = false
        }
        wasThinkingInProgress = parsed.isThinkingInProgress
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (parsed.hasThinking) {
            ThinkingHeader(
                isThinkingInProgress = parsed.isThinkingInProgress,
                isExpanded = isThinkingExpanded,
                onToggle = { isThinkingExpanded = !isThinkingExpanded },
            )

            AnimatedVisibility(
                visible = isThinkingExpanded && (parsed.thinkingContent.isNotEmpty() || !parsed.isThinkingInProgress),
            ) {
                ThinkingContentBlock(
                    text = parsed.thinkingContent.ifEmpty { "（无思考内容）" },
                )
            }
        }

        if (parsed.replyContent.isNotEmpty()) {
            if (parsed.hasThinking) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = parsed.replyContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ThinkingContentBlock(text: String) {
    Row(
        modifier = Modifier
            .padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(1.dp),
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun ThinkingHeader(
    isThinkingInProgress: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
    )
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(isThinkingInProgress) {
        if (isThinkingInProgress) {
            while (true) {
                delay(350L.milliseconds)
                dotCount = (dotCount % 3) + 1
            }
        }
    }

    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isThinkingInProgress) {
            Text(
                text = "思考中",
                style = MaterialTheme.typography.labelMedium,
                color = headerColor,
            )
            Text(
                text = ".".repeat(dotCount),
                style = MaterialTheme.typography.labelMedium,
                color = headerColor,
                modifier = Modifier.width(18.dp),
            )
        } else {
            Text(
                text = "思考完成",
                style = MaterialTheme.typography.labelMedium,
                color = headerColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (isExpanded) "收起思考过程" else "展开思考过程",
            tint = headerColor,
            modifier = Modifier
                .size(18.dp)
                .rotate(arrowRotation),
        )
    }
}
