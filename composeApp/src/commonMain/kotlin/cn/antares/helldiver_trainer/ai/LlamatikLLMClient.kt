package cn.antares.helldiver_trainer.ai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import cn.antares.helldiver_trainer.bridge.DEFAULT_LLAMATIK_MODEL_FILE
import com.llamatik.core.platform.GenStream
import com.llamatik.core.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

object LlamatikLLMProvider : LLMProvider(
    id = "llamatik",
    display = "Llamatik Local",
)

object LlamatikModels {
    val Qwen35_2B: LLModel = LLModel(
        provider = LlamatikLLMProvider,
        id = DEFAULT_LLAMATIK_MODEL_FILE,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Schema.JSON.Basic,
        ),
        contextLength = 4096L,
        maxOutputTokens = 1024L,
    )
}

data class LocalModelConfig(
    val temperature: Float = 0.6f,
    val maxTokens: Int = 1024,
    val topP: Float = 0.95f,
    val topK: Int = 20,
    val repeatPenalty: Float = 1.0f,
    val contextLength: Int = 4096,
    val numThreads: Int = 4,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = true,
    val batchSize: Int = 512,
    val gpuLayers: Int = 0,
)

class LlamatikLLMClient(
    defaultConfig: LocalModelConfig = LocalModelConfig(),
) : LLMClient() {

    @Volatile
    var isModelLoaded: Boolean = false
        private set

    @Volatile
    private var activeConfig: LocalModelConfig = defaultConfig

    suspend fun loadModel(
        modelPath: String,
        config: LocalModelConfig = activeConfig,
    ): Boolean = withContext(Dispatchers.IO) {
        applyGenerateParams(config)
        val loaded = LlamaBridge.initGenerateModel(modelPath)
        activeConfig = config
        isModelLoaded = loaded
        loaded
    }

    fun cancelGeneration() {
        runCatching { LlamaBridge.nativeCancelGenerate() }
    }

    suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            cancelGeneration()
            if (isModelLoaded) {
                runCatching { LlamaBridge.shutdown() }
            }
            isModelLoaded = false
        }
    }

    override fun llmProvider(): LLMProvider = LlamatikLLMProvider

    override suspend fun models(): List<LLModel> = listOf(LlamatikModels.Qwen35_2B)

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        val sb = StringBuilder()
        executeStreaming(prompt, model, tools).collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> sb.append(frame.text)
                is StreamFrame.ReasoningDelta -> frame.text?.let(sb::append)
                else -> Unit
            }
        }
        return Message.Assistant(
            content = sb.toString(),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "stop",
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = callbackFlow {
        if (!isModelLoaded) {
            close(IllegalStateException("本地模型尚未加载"))
            return@callbackFlow
        }

        val fullSystemPrompt = buildSystemPrompt(prompt)
        val conversationPairs = prompt.messages.mapNotNull { msg ->
            val text = msg.textContent()
            if (text.isBlank()) return@mapNotNull null
            when (msg.role) {
                Message.Role.User -> "user" to text
                Message.Role.Assistant -> "assistant" to text
                else -> null
            }
        }

        val streamState = object {
            @Volatile
            var finished = false

            @Volatile
            var userCancelled = false

            @Volatile
            var promptOpensThinking = false

            @Volatile
            var emittedThinkPrefix = false

            @Volatile
            var loopDetected = false

            @Volatile
            var loopCutoffIndex = 0

            @Volatile
            var thinkingBufferText = ""
        }

        val pass1RawBuffer = StringBuilder()
        var inThinking = false
        var thinkingStartOffset = 0
        var thinkingClosed = false
        var lastLoopCheckLen = 0

        val genStream = object : GenStream {
            override fun onDelta(text: String) {
                if (text.isEmpty() || streamState.loopDetected || streamState.userCancelled) return
                if (streamState.promptOpensThinking && !streamState.emittedThinkPrefix) {
                    streamState.emittedThinkPrefix = true
                    inThinking = true
                    if (!text.trimStart().startsWith("<think>")) {
                        trySend(StreamFrame.TextDelta("<think>\n"))
                    }
                }
                trySend(StreamFrame.TextDelta(text))

                if (!thinkingClosed) {
                    pass1RawBuffer.append(text)
                    if (!inThinking) {
                        val openIdx = pass1RawBuffer.indexOf("<think>")
                        if (openIdx != -1) {
                            inThinking = true
                            thinkingStartOffset = openIdx + "<think>".length
                        }
                    }
                    if (inThinking) {
                        if (pass1RawBuffer.indexOf("</think>", thinkingStartOffset) != -1) {
                            thinkingClosed = true
                        } else {
                            val thinkingLen = pass1RawBuffer.length - thinkingStartOffset
                            if (thinkingLen >= 200 && thinkingLen - lastLoopCheckLen >= 24) {
                                lastLoopCheckLen = thinkingLen
                                val currentThinking = pass1RawBuffer.substring(thinkingStartOffset)
                                val cutoff = findThinkingLoopCutoff(currentThinking)
                                if (cutoff != null) {
                                    streamState.loopDetected = true
                                    streamState.loopCutoffIndex = cutoff
                                    streamState.thinkingBufferText = currentThinking
                                    cancelGeneration()
                                }
                            }
                        }
                    }
                }
            }

            override fun onComplete() {
                if (streamState.loopDetected && !streamState.userCancelled) return
                streamState.finished = true
                trySend(
                    StreamFrame.End(
                        finishReason = "stop",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
                close()
            }

            override fun onError(message: String) {
                if (streamState.loopDetected && !streamState.userCancelled) return
                streamState.finished = true
                close(IllegalStateException(message))
            }
        }

        launch(Dispatchers.IO) {
            try {
                val effectiveConfig = activeConfig.copy(
                    temperature = prompt.params.temperature?.toFloat() ?: activeConfig.temperature,
                    maxTokens = prompt.params.maxTokens ?: activeConfig.maxTokens,
                )
                if (effectiveConfig != activeConfig) {
                    applyGenerateParams(effectiveConfig)
                    activeConfig = effectiveConfig
                }

                val chatMessages = buildList {
                    if (fullSystemPrompt.isNotBlank()) {
                        add("system" to fullSystemPrompt)
                    }
                    addAll(conversationPairs)
                }

                val rendered = LlamaBridge.applyChatTemplate(
                    messages = chatMessages,
                    addAssistantPrefix = true,
                )
                if (rendered != null) {
                    val (finalPrompt, promptOpensThinking) = normalizeThinkingPrompt(rendered)
                    streamState.promptOpensThinking = promptOpensThinking
                    LlamaBridge.generateStream(finalPrompt, genStream)

                    if (streamState.loopDetected && !streamState.userCancelled) {
                        trySend(StreamFrame.TextDelta("\n（检测到重复思考，已自动收敛并输出结论）\n</think>\n"))
                        val cleanThinking = buildCleanThinkingPrefix(
                            thinkingText = streamState.thinkingBufferText,
                            cutoffIndex = streamState.loopCutoffIndex,
                        )
                        val continuationPrompt = if (streamState.promptOpensThinking) {
                            "$finalPrompt$cleanThinking\n</think>\n"
                        } else {
                            "$finalPrompt<think>\n$cleanThinking\n</think>\n"
                        }

                        val replyBuffer = StringBuilder()
                        var replyStoppedForLoop = false
                        val replyGenStream = object : GenStream {
                            override fun onDelta(text: String) {
                                if (text.isEmpty() || replyStoppedForLoop || streamState.userCancelled) return
                                trySend(StreamFrame.TextDelta(text))
                                replyBuffer.append(text)
                                if (replyBuffer.length >= 160 && hasTailExactRepetition(replyBuffer.toString())) {
                                    replyStoppedForLoop = true
                                    cancelGeneration()
                                }
                            }

                            override fun onComplete() {
                                streamState.finished = true
                                trySend(
                                    StreamFrame.End(
                                        finishReason = "stop",
                                        metaInfo = ResponseMetaInfo.Empty,
                                    ),
                                )
                                close()
                            }

                            override fun onError(message: String) {
                                if (replyStoppedForLoop && !streamState.userCancelled) {
                                    streamState.finished = true
                                    trySend(
                                        StreamFrame.End(
                                            finishReason = "stop",
                                            metaInfo = ResponseMetaInfo.Empty,
                                        ),
                                    )
                                    close()
                                } else {
                                    streamState.finished = true
                                    close(IllegalStateException(message))
                                }
                            }
                        }

                        LlamaBridge.generateStream(continuationPrompt, replyGenStream)
                    }
                } else {
                    val lastUserIdx = conversationPairs.indexOfLast { it.first == "user" }
                    val lastUserPrompt = if (lastUserIdx >= 0) {
                        conversationPairs[lastUserIdx].second
                    } else {
                        ""
                    }
                    val historyPairs = if (lastUserIdx > 0) {
                        conversationPairs.subList(0, lastUserIdx)
                    } else {
                        emptyList()
                    }
                    val historyContext = historyPairs.joinToString("\n") { (role, content) ->
                        val label = if (role == "user") "User" else "Assistant"
                        "$label: $content"
                    }
                    LlamaBridge.generateStreamWithContext(
                        systemPrompt = fullSystemPrompt,
                        contextBlock = historyContext,
                        userPrompt = lastUserPrompt,
                        callback = genStream,
                    )
                }
            } catch (e: Throwable) {
                if (!streamState.finished) {
                    streamState.finished = true
                    close(e)
                }
            } finally {
                if (!streamState.finished) {
                    streamState.finished = true
                    trySend(
                        StreamFrame.End(
                            finishReason = "stop",
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                    close()
                }
            }
        }

        awaitClose {
            streamState.userCancelled = true
            if (!streamState.finished) {
                cancelGeneration()
            }
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        ModerationResult(isHarmful = false, categories = emptyMap())

    override fun close() {
        cancelGeneration()
        if (isModelLoaded) {
            runCatching { LlamaBridge.shutdown() }
            isModelLoaded = false
        }
    }

    private fun findThinkingLoopCutoff(thinkingText: String): Int? {
        if (thinkingText.length < 200) return null
        val windowStart = (thinkingText.length - 700).coerceAtLeast(0)
        val window = thinkingText.substring(windowStart)

        val maxPeriod = minOf(180, window.length / 3)
        for (period in 16..maxPeriod) {
            val tailStart = window.length - period
            val midStart = tailStart - period
            val headStart = midStart - period
            val tail = window.substring(tailStart)
            if (window.regionMatches(midStart, tail, 0, period) &&
                window.regionMatches(headStart, tail, 0, period)
            ) {
                return windowStart + midStart
            }
        }

        val delimiters = charArrayOf('。', '？', '！', '，', '；', '\n', '.', '?', '!', ',', ';')
        val clauses = window.split(*delimiters)
            .map { it.trim() }
            .filter { it.length >= 12 }
        val counts = HashMap<String, Int>()
        for (clause in clauses) {
            val count = (counts[clause] ?: 0) + 1
            counts[clause] = count
            if (count >= 3) {
                val firstIdx = window.indexOf(clause)
                val secondIdx = if (firstIdx >= 0) {
                    window.indexOf(clause, firstIdx + clause.length)
                } else {
                    -1
                }
                return if (secondIdx >= 0) windowStart + secondIdx else thinkingText.length
            }
        }

        return null
    }

    private fun hasTailExactRepetition(text: String): Boolean {
        val maxPeriod = minOf(120, text.length / 3)
        for (period in 16..maxPeriod) {
            val tailStart = text.length - period
            val midStart = tailStart - period
            val headStart = midStart - period
            val tail = text.substring(tailStart)
            if (text.regionMatches(midStart, tail, 0, period) &&
                text.regionMatches(headStart, tail, 0, period)
            ) {
                return true
            }
        }
        return false
    }

    private fun buildCleanThinkingPrefix(thinkingText: String, cutoffIndex: Int): String {
        val stripped = thinkingText.trimStart().removePrefix("<think>").trimStart()
        val endIndex = cutoffIndex.coerceIn(0, stripped.length)
        return stripped.substring(0, endIndex).trim().take(500)
    }

    private fun normalizeThinkingPrompt(rendered: String): Pair<String, Boolean> {
        val trimmedEnd = rendered.trimEnd()
        return when {
            trimmedEnd.endsWith("<think>") -> rendered to true
            trimmedEnd.endsWith("<|im_start|>assistant") -> {
                val withThink = if (rendered.endsWith("\n")) {
                    "${rendered}<think>\n"
                } else {
                    "${rendered}\n<think>\n"
                }
                withThink to true
            }

            else -> rendered to false
        }
    }

    private fun buildSystemPrompt(prompt: Prompt): String {
        val baseSystem = prompt.messages
            .filter { it.role == Message.Role.System }
            .mapNotNull { msg -> msg.textContent().takeIf { it.isNotBlank() } }
            .joinToString("\n\n")

        val jsonSchema = (prompt.params.schema as? LLMParams.Schema.JSON)?.schema
        return buildString {
            if (baseSystem.isNotBlank()) {
                append(baseSystem)
            }
            if (jsonSchema != null) {
                if (isNotEmpty()) append("\n\n")
                append("请严格按照以下 JSON Schema 输出单个合法 JSON 对象，不要包含 Markdown 代码块（如 ```json）或额外解释文字：\n")
                append(jsonSchema.toString())
            }
        }
    }

    private fun applyGenerateParams(config: LocalModelConfig) {
        LlamaBridge.updateGenerateParams(
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            topK = config.topK,
            repeatPenalty = config.repeatPenalty,
            contextLength = config.contextLength,
            numThreads = config.numThreads,
            useMmap = config.useMmap,
            flashAttention = config.flashAttention,
            batchSize = config.batchSize,
            gpuLayers = config.gpuLayers,
        )
    }
}
