package cn.antares.helldiver_trainer

import cn.antares.helldiver_trainer.bridge.AbstractSoundPlayer
import cn.antares.helldiver_trainer.bridge.SoundResource
import korlibs.audio.sound.Sound
import korlibs.audio.sound.SoundChannel
import korlibs.audio.sound.readSound
import korlibs.io.file.std.resourcesVfs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DesktopSoundPlayer : AbstractSoundPlayer<Unit> {

    companion object {
        val instance: DesktopSoundPlayer = DesktopSoundPlayer()
    }

    private val soundCache = ConcurrentHashMap<SoundResource, Sound>()
    private var loopSoundChannel: SoundChannel? = null

    override fun init(context: Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            SoundResource.entries.forEach { sound ->
                val fileName = sound.map()
                try {
                    val loadedSound = resourcesVfs[fileName].readSound()
                    soundCache[sound] = loadedSound
                    println("Desktop load sound success: $fileName")
                } catch (e: Exception) {
                    println("Desktop load sound failed: $fileName -> ${e.message}")
                }
            }
        }
    }

    override fun release() {
        soundCache.clear()
        println("Desktop sound cache cleared")
    }

    override fun play(sound: SoundResource) {
        CoroutineScope(Dispatchers.Default).launch {
            if (sound == SoundResource.Playing) {
                loopSoundChannel = soundCache[sound]?.playForever()
            } else {
                soundCache[sound]?.play()
            }
        }
    }

    override fun stop(sound: SoundResource) {
        if (sound == SoundResource.Playing) {
            loopSoundChannel?.stop()
            loopSoundChannel = null
        }
    }

    private fun SoundResource.map(): String {
        return when (this) {
            SoundResource.FailFull -> "failurefull.wav"
            SoundResource.Fail -> "failure.wav"
            SoundResource.Correct -> "correct.wav"
            SoundResource.Error -> "error.wav"
            SoundResource.Coin -> "coin.wav"
            SoundResource.Playing -> "playing.wav"
            SoundResource.Start -> "start.wav"
            SoundResource.Ready -> "ready.wav"
            SoundResource.Hit -> "hit.wav"
            SoundResource.Success1 -> "success1.wav"
            SoundResource.Success2 -> "success2.wav"
            SoundResource.Success3 -> "success3.wav"
        }
    }
}