package cn.antares.helldiver_trainer.bridge

import cn.antares.helldiver_trainer.DesktopSoundPlayer

actual fun playSound(sound: SoundResource) {
    DesktopSoundPlayer.Companion.instance.play(sound)
}

actual fun stopSound(sound: SoundResource) {
    DesktopSoundPlayer.Companion.instance.stop(sound)
}