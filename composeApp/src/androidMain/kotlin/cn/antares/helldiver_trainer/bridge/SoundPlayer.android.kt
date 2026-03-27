package cn.antares.helldiver_trainer.bridge

import cn.antares.helldiver_trainer.AndroidSoundPlayer

actual fun playSound(sound: SoundResource) {
    AndroidSoundPlayer.instance.play(sound)
}

actual fun stopSound(sound: SoundResource) {
    AndroidSoundPlayer.instance.stop(sound)
}

