package cn.antares.helldiver_trainer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cn.antares.helldiver_trainer.ui.App
import dev.icerock.moko.resources.compose.stringResource

fun main() {
    application {
        LaunchedEffect(Unit) {
            DesktopSoundPlayer.instance.init(Unit)
        }
        Window(
            onCloseRequest = {
                DesktopSoundPlayer.instance.release()
                exitApplication()
            },
            title = stringResource(MR.strings.my_app_name),
            state = rememberWindowState(width = 750.dp, height = 530.dp),
        ) {
            App()
        }
    }
}