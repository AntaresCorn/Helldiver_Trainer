package cn.antares.helldiver_trainer.viewmodel

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.antares.helldiver_trainer.ai.LlamatikLLMClient
import cn.antares.helldiver_trainer.ai.LlamatikModels
import cn.antares.helldiver_trainer.bridge.ModelFileAccessState
import cn.antares.helldiver_trainer.bridge.checkModelFileAccess
import cn.antares.helldiver_trainer.bridge.requestStoragePermission
import cn.antares.helldiver_trainer.bridge.resolveDownloadModelPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

enum class ChatRole {
    USER,
    ASSISTANT,
}

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
)

data class ParsedAssistantOutput(
    val hasThinking: Boolean,
    val isThinkingInProgress: Boolean,
    val thinkingContent: String,
    val replyContent: String,
)

private const val THINK_OPEN_TAG = "<think>"
private const val THINK_CLOSE_TAG = "</think>"

private const val WIKI_SYSTEM_PROMPT =
    "你是一个小型 Wiki 知识库问答助手。请严格依据提供的上下文回答用户问题，并以合法的 JSON 格式输出结果。如果上下文中没有相关信息，请在 answer 中说明未找到相关记录，将 found 设为 false，keywords 设为 []。思考过程请简明扼要，不要反复重复确认同一规则，得出结论后立即结束思考并输出 JSON。"

private const val WIKI_JSON_SCHEMA = """
{
  "type": "object",
  "properties": {
    "answer": { "type": "string" },
    "keywords": {
      "type": "array",
      "items": { "type": "string" }
    },
    "found": { "type": "boolean" }
  },
  "required": ["answer", "keywords", "found"]
}
"""

fun parseAssistantOutput(rawContent: String, isStreaming: Boolean): ParsedAssistantOutput {
    if (rawContent.isBlank()) {
        return ParsedAssistantOutput(
            hasThinking = isStreaming,
            isThinkingInProgress = isStreaming,
            thinkingContent = "",
            replyContent = "",
        )
    }

    val openIdx = rawContent.indexOf(THINK_OPEN_TAG)
    val closeIdx = rawContent.indexOf(THINK_CLOSE_TAG)

    if (openIdx != -1) {
        val rawAfterOpen = rawContent.substring(openIdx + THINK_OPEN_TAG.length)
        val afterOpen = rawAfterOpen.trimStart().let { trimmed ->
            if (trimmed.startsWith(THINK_OPEN_TAG)) {
                trimmed.removePrefix(THINK_OPEN_TAG)
            } else {
                rawAfterOpen
            }
        }
        val closeAfterOpen = afterOpen.indexOf(THINK_CLOSE_TAG)
        return if (closeAfterOpen != -1) {
            ParsedAssistantOutput(
                hasThinking = true,
                isThinkingInProgress = false,
                thinkingContent = afterOpen.substring(0, closeAfterOpen).trim(),
                replyContent = afterOpen.substring(closeAfterOpen + THINK_CLOSE_TAG.length)
                    .trimStart(),
            )
        } else {
            val cleanedThinking = stripPartialSuffix(afterOpen, THINK_CLOSE_TAG, isStreaming)
                .trimStart('\n', '\r')
            ParsedAssistantOutput(
                hasThinking = true,
                isThinkingInProgress = isStreaming,
                thinkingContent = cleanedThinking,
                replyContent = "",
            )
        }
    }

    if (closeIdx != -1) {
        return ParsedAssistantOutput(
            hasThinking = true,
            isThinkingInProgress = false,
            thinkingContent = rawContent.substring(0, closeIdx).trim(),
            replyContent = rawContent.substring(closeIdx + THINK_CLOSE_TAG.length).trimStart(),
        )
    }

    val trimmedStart = rawContent.trimStart()
    if (isStreaming && THINK_OPEN_TAG.startsWith(trimmedStart)) {
        return ParsedAssistantOutput(
            hasThinking = true,
            isThinkingInProgress = true,
            thinkingContent = "",
            replyContent = "",
        )
    }

    return ParsedAssistantOutput(
        hasThinking = false,
        isThinkingInProgress = false,
        thinkingContent = "",
        replyContent = rawContent,
    )
}

private fun stripPartialSuffix(text: String, tag: String, isStreaming: Boolean): String {
    if (!isStreaming || text.isEmpty()) return text
    val maxOverlap = minOf(text.length, tag.length - 1)
    for (len in maxOverlap downTo 1) {
        if (text.endsWith(tag.substring(0, len))) {
            return text.dropLast(len)
        }
    }
    return text
}

enum class ModelLoadStatus {
    IDLE,
    LOADING,
    LOADED,
    ERROR,
}

data class LlamatikTestUiState(
    val modelPath: String = resolveDownloadModelPath(),
    val modelStatus: ModelLoadStatus = ModelLoadStatus.IDLE,
    val statusMessage: String = "未加载模型，请点击下方按钮手动加载",
    val needsStoragePermission: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
)

class LlamatikTestViewModel(
    private val llamatikClient: LlamatikLLMClient,
    private val promptExecutor: PromptExecutor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        if (llamatikClient.isModelLoaded) {
            LlamatikTestUiState(
                modelStatus = ModelLoadStatus.LOADED,
                statusMessage = "模型已加载 (Koog)",
            )
        } else {
            LlamatikTestUiState()
        },
    )
    val uiState: StateFlow<LlamatikTestUiState> = _uiState.asStateFlow()

    private var nextMessageId = 1L
    private var generationJob: Job? = null

    private val wikiJsonSchemaParam: LLMParams.Schema.JSON.Basic by lazy {
        LLMParams.Schema.JSON.Basic(
            name = "wiki_query_response",
            schema = Json.parseToJsonElement(WIKI_JSON_SCHEMA).jsonObject,
        )
    }

    fun loadModel() {
        val current = _uiState.value
        if (current.modelStatus == ModelLoadStatus.LOADING || current.isGenerating) return

        val resolvedPath = resolveDownloadModelPath()
        _uiState.update {
            it.copy(
                modelPath = resolvedPath,
                modelStatus = ModelLoadStatus.LOADING,
                needsStoragePermission = false,
                statusMessage = "正在加载模型...",
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            when (val access = checkModelFileAccess(resolvedPath)) {
                is ModelFileAccessState.PermissionRequired -> {
                    _uiState.update {
                        it.copy(
                            modelStatus = ModelLoadStatus.ERROR,
                            needsStoragePermission = true,
                            statusMessage = "需要文件访问权限才能读取 Download 目录中的模型文件",
                        )
                    }
                }

                is ModelFileAccessState.NotFound -> {
                    _uiState.update {
                        it.copy(
                            modelStatus = ModelLoadStatus.ERROR,
                            needsStoragePermission = false,
                            statusMessage = "未找到模型文件: ${access.path}",
                        )
                    }
                }

                is ModelFileAccessState.Unreadable -> {
                    _uiState.update {
                        it.copy(
                            modelStatus = ModelLoadStatus.ERROR,
                            needsStoragePermission = false,
                            statusMessage = "模型文件不可读: ${access.path}",
                        )
                    }
                }

                is ModelFileAccessState.Ready -> {
                    val result = runCatching {
                        llamatikClient.loadModel(access.path)
                    }
                    result.fold(
                        onSuccess = { loaded ->
                            if (loaded) {
                                val sizeMb = access.sizeBytes / (1024 * 1024)
                                _uiState.update {
                                    it.copy(
                                        modelStatus = ModelLoadStatus.LOADED,
                                        needsStoragePermission = false,
                                        statusMessage = "模型已加载 (Koog / $sizeMb MB)",
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        modelStatus = ModelLoadStatus.ERROR,
                                        needsStoragePermission = false,
                                        statusMessage = "模型加载失败: initGenerateModel 返回 false",
                                    )
                                }
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    modelStatus = ModelLoadStatus.ERROR,
                                    needsStoragePermission = false,
                                    statusMessage = "模型加载失败: ${error.message ?: error::class.simpleName}",
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    fun unloadModel() {
        val current = _uiState.value
        if (current.modelStatus == ModelLoadStatus.LOADING) return

        viewModelScope.launch(Dispatchers.IO) {
            generationJob?.cancel()
            llamatikClient.unloadModel()
            _uiState.update {
                it.copy(
                    modelStatus = ModelLoadStatus.IDLE,
                    isGenerating = false,
                    needsStoragePermission = false,
                    statusMessage = "模型已卸载",
                )
            }
        }
    }

    fun requestPermission() {
        requestStoragePermission()
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearMessages() {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun sendMessage() {
        val current = _uiState.value
        val trimmed = current.inputText.trim()
        if (current.modelStatus != ModelLoadStatus.LOADED || current.isGenerating || trimmed.isEmpty()) {
            return
        }

        val userMsg = ChatMessage(
            id = nextMessageId++,
            role = ChatRole.USER,
            content = trimmed,
        )
        val assistantMsg = ChatMessage(
            id = nextMessageId++,
            role = ChatRole.ASSISTANT,
            content = "",
        )

        val koogPrompt = prompt(
            id = "wiki-chat",
            params = LLMParams(
                temperature = 0.6,
                maxTokens = 1024,
                schema = wikiJsonSchemaParam,
            ),
        ) {
            system(WIKI_SYSTEM_PROMPT)
            current.messages.forEach { msg ->
                when (msg.role) {
                    ChatRole.USER -> user(msg.content)
                    ChatRole.ASSISTANT -> {
                        val replyOnly = parseAssistantOutput(msg.content, isStreaming = false)
                            .replyContent
                            .ifBlank { msg.content }
                        assistant(replyOnly)
                    }
                }
            }
            user(trimmed)
        }

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                inputText = "",
                isGenerating = true,
            )
        }

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                promptExecutor.executeStreaming(
                    prompt = koogPrompt,
                    model = LlamatikModels.Qwen35_2B,
                    tools = emptyList(),
                ).collect { frame ->
                    when (frame) {
                        is StreamFrame.TextDelta -> appendDeltaToAssistant(
                            assistantMsg.id,
                            frame.text,
                        )

                        is StreamFrame.ReasoningDelta -> {
                            frame.text?.let { appendDeltaToAssistant(assistantMsg.id, it) }
                        }

                        is StreamFrame.End -> {
                            _uiState.update { it.copy(isGenerating = false) }
                        }

                        else -> Unit
                    }
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isGenerating = false) }
            } catch (e: Throwable) {
                appendErrorToAssistant(
                    assistantMsg.id,
                    e.message ?: e::class.simpleName ?: "未知错误",
                )
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        llamatikClient.cancelGeneration()
    }

    override fun onCleared() {
        generationJob?.cancel()
        llamatikClient.cancelGeneration()
    }

    private fun appendDeltaToAssistant(assistantId: Long, delta: String) {
        if (delta.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == assistantId) {
                        msg.copy(content = msg.content + delta)
                    } else {
                        msg
                    }
                },
            )
        }
    }

    private fun appendErrorToAssistant(assistantId: Long, errorMessage: String) {
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map { msg ->
                    if (msg.id == assistantId) {
                        val prefix = if (msg.content.isEmpty()) "" else "${msg.content}\n"
                        msg.copy(content = "$prefix[错误: $errorMessage]")
                    } else {
                        msg
                    }
                },
            )
        }
    }
}
