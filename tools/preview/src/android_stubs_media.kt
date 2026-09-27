@file:Suppress("unused")

/**
 * tools/preview — android.media 스텁.
 * Audio.kt 컴파일/런타임용이며 아무 소리도 내지 않는다.
 */
package android.media

import android.content.Context

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(usage: Int): Builder = this
        fun setContentType(contentType: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }

    companion object {
        const val USAGE_GAME = 14
        const val CONTENT_TYPE_SONIFICATION = 4
    }
}

open class SoundPool private constructor() {
    class Builder {
        fun setMaxStreams(maxStreams: Int): Builder = this
        fun setAudioAttributes(attributes: AudioAttributes): Builder = this
        fun build(): SoundPool = SoundPool()
    }

    fun interface OnLoadCompleteListener {
        fun onLoadComplete(soundPool: SoundPool, sampleId: Int, status: Int)
    }

    private var next = 1
    fun load(context: Context, resId: Int, priority: Int): Int = next++
    fun setOnLoadCompleteListener(listener: OnLoadCompleteListener?) {}
    fun play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float): Int = 0
    fun stop(streamId: Int) {}
    fun pause(streamId: Int) {}
    fun resume(streamId: Int) {}
    fun autoPause() {}
    fun autoResume() {}
    fun setRate(streamId: Int, rate: Float) {}
    fun setVolume(streamId: Int, leftVolume: Float, rightVolume: Float) {}
    fun release() {}
}

open class MediaPlayer {
    companion object {
        @JvmStatic
        fun create(context: Context, resId: Int): MediaPlayer? = MediaPlayer()
    }

    var isLooping: Boolean = false
    var isPlaying: Boolean = false
    var volume: Float = 1f

    fun setVolume(leftVolume: Float, rightVolume: Float) { volume = (leftVolume + rightVolume) / 2f }
    fun setDataSource(path: String) {}
    fun setAudioAttributes(attributes: AudioAttributes) {}
    fun prepare() {}
    fun prepareAsync() {}
    fun start() { isPlaying = true }
    fun pause() { isPlaying = false }
    fun stop() { isPlaying = false }
    fun seekTo(msec: Int) {}
    fun release() {}
    fun setOnCompletionListener(listener: Any?) {}
    fun setOnErrorListener(listener: Any?) {}
}
