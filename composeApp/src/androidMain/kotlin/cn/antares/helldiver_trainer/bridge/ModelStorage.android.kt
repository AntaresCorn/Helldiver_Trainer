package cn.antares.helldiver_trainer.bridge

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cn.antares.helldiver_trainer.MainActivity
import java.io.File

actual fun resolveDownloadModelPath(fileName: String): String {
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File(downloadsDir, fileName).absolutePath
}

actual fun checkModelFileAccess(path: String): ModelFileAccessState {
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val ctx = MainActivity.currentActivity
        ctx != null && ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
    if (!hasPermission) {
        return ModelFileAccessState.PermissionRequired(path)
    }

    val file = File(path)
    return when {
        !file.exists() -> ModelFileAccessState.NotFound(path)
        !file.canRead() -> ModelFileAccessState.Unreadable(path)
        else -> ModelFileAccessState.Ready(path, file.length())
    }
}

actual fun requestStoragePermission() {
    val ctx = MainActivity.currentActivity ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${ctx.packageName}".toUri(),
            )
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else {
        ActivityCompat.requestPermissions(
            ctx,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            1001,
        )
    }
}
