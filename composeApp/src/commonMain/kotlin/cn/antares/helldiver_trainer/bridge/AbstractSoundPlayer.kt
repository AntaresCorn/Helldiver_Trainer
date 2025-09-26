package cn.antares.helldiver_trainer.bridge

interface AbstractSoundPlayer<T> {
    fun init(context: T)
    fun release()
    fun play(sound: SoundResource)
    fun stop(sound: SoundResource)
}