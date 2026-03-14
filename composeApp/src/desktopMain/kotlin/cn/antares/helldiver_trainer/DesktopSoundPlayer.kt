package cn.antares.helldiver_trainer

import cn.antares.helldiver_trainer.bridge.AbstractSoundPlayer
import cn.antares.helldiver_trainer.bridge.SoundResource
import korlibs.audio.sound.Sound
import korlibs.audio.sound.SoundChannel
import korlibs.audio.sound.await
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
    private val activeChannels = ConcurrentHashMap<SoundResource, MutableList<SoundChannel>>()

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
        activeChannels.values.forEach { channels ->
            channels.forEach { it.stop() }
        }
        activeChannels.clear()
        soundCache.clear()
        println("Desktop sound cache cleared")
    }

    override fun play(sound: SoundResource) {
        CoroutineScope(Dispatchers.Default).launch {
            val loadedSound = soundCache[sound] ?: return@launch
            val channel = if (sound == SoundResource.Playing) {
                loadedSound.playForever()
            } else {
                loadedSound.play()
            }

            // 将当前播放的频道加入管理列表
            val channels = activeChannels.getOrPut(sound) { mutableListOf() }
            synchronized(channels) {
                channels.add(channel)
            }

            // 当频道停止或播放结束时，从列表中移除，防止内存泄漏
            // playForever 的声音只能通过 stop() 手动触发移除
            if (sound != SoundResource.Playing) {
                launch {
                    // 等待音频播放完毕
                    channel.await()
                    synchronized(channels) {
                        channels.remove(channel)
                    }
                }
            }
        }
    }

    override fun stop(sound: SoundResource) {
        activeChannels[sound]?.let { channels ->
            channels.forEach { it.stop() }
            channels.clear()
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
            SoundResource.PipeMove -> "pipe_move.wav"
            SoundResource.PipeLoading -> "pipe_loading.wav"
            SoundResource.PipeComplete -> "pipe_complete.wav"
        }
    }
}