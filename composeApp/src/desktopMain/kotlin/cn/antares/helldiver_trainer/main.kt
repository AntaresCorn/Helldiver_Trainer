package cn.antares.helldiver_trainer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cn.antares.helldiver_trainer.ui.App

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
            title = "潜兵随身伴侣",
            state = rememberWindowState(width = 700.dp, height = 500.dp),
        ) {
            App()
        }
    }
}