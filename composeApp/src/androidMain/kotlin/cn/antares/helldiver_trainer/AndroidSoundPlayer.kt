package cn.antares.helldiver_trainer

import android.content.Context
import android.media.SoundPool
import cn.antares.helldiver_trainer.bridge.AbstractSoundPlayer
import cn.antares.helldiver_trainer.bridge.SoundResource

class AndroidSoundPlayer : AbstractSoundPlayer<Context> {

    companion object {
        val instance: AndroidSoundPlayer = AndroidSoundPlayer()
    }

    private val soundCache = HashMap<Int, Int>()
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(15).build().apply {
        setOnLoadCompleteListener { _, sampleId, status ->
            println("Android Sound loaded: $sampleId, status: ${if (status == 0) "Success" else "Failed"}")
        }
    }

    override fun init(context: Context) {
        SoundResource.entries.forEach { sound ->
            val resId = sound.map()
            soundCache[resId] = soundPool.load(context, resId, 1)
        }
    }

    override fun release() {
        soundCache.clear()
        soundPool.release()
    }

    override fun play(sound: SoundResource) {
        soundCache[sound.map()]?.let {
            soundPool.play(it, 1f, 1f, 1, if (sound == SoundResource.Playing) 1 else 0, 1f)
        }
    }

    override fun stop(sound: SoundResource) {
        soundCache[sound.map()]?.let {
            soundPool.stop(it)
        }
    }

    private fun SoundResource.map(): Int {
        return when (this) {
            SoundResource.FailFull -> R.raw.failurefull
            SoundResource.Fail -> R.raw.failure
            SoundResource.Correct -> R.raw.correct
            SoundResource.Error -> R.raw.error
            SoundResource.Coin -> R.raw.coin
            SoundResource.Playing -> R.raw.playing
            SoundResource.Start -> R.raw.start
            SoundResource.Ready -> R.raw.ready
            SoundResource.Hit -> R.raw.hit
            SoundResource.Success1 -> R.raw.success1
            SoundResource.Success2 -> R.raw.success2
            SoundResource.Success3 -> R.raw.success3
        }
    }
}