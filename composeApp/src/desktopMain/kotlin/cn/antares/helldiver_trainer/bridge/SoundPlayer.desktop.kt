package cn.antares.helldiver_trainer.bridge

import cn.antares.helldiver_trainer.DesktopSoundPlayer

actual fun playSound(sound: SoundResource) {
    DesktopSoundPlayer.instance.play(sound)
}

actual fun stopSound(sound: SoundResource) {
    DesktopSoundPlayer.instance.stop(sound)
}