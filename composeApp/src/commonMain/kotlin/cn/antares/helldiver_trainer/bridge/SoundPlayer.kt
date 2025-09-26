package cn.antares.helldiver_trainer.bridge

enum class SoundResource {
    Coin, Correct, Error, Fail, FailFull, Hit, Playing, Start, Ready, Success1, Success2, Success3
}

expect fun playSound(sound: SoundResource)
expect fun stopSound(sound: SoundResource)