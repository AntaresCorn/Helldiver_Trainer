package cn.antares.helldiver_trainer.bridge

const val DEFAULT_LLAMATIK_MODEL_FILE = "Qwen3.5-2B-UD-Q4_K_XL.gguf"

sealed interface ModelFileAccessState {
    data class Ready(val path: String, val sizeBytes: Long) : ModelFileAccessState
    data class PermissionRequired(val path: String) : ModelFileAccessState
    data class NotFound(val path: String) : ModelFileAccessState
    data class Unreadable(val path: String) : ModelFileAccessState
}

expect fun resolveDownloadModelPath(fileName: String = DEFAULT_LLAMATIK_MODEL_FILE): String

expect fun checkModelFileAccess(path: String): ModelFileAccessState

expect fun requestStoragePermission()
