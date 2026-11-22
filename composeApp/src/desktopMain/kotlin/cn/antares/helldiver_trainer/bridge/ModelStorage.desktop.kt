package cn.antares.helldiver_trainer.bridge

import java.io.File

actual fun resolveDownloadModelPath(fileName: String): String {
    val home = System.getProperty("user.home")
    val downloadsFile = File(home, "Downloads/$fileName")
    val downloadFile = File(home, "Download/$fileName")
    return if (!downloadsFile.exists() && downloadFile.exists()) {
        downloadFile.absolutePath
    } else {
        downloadsFile.absolutePath
    }
}

actual fun checkModelFileAccess(path: String): ModelFileAccessState {
    val file = File(path)
    return when {
        !file.exists() -> ModelFileAccessState.NotFound(path)
        !file.canRead() -> ModelFileAccessState.Unreadable(path)
        else -> ModelFileAccessState.Ready(path, file.length())
    }
}

actual fun requestStoragePermission() = Unit
