@file:Suppress("unused")

/**
 * tools/preview — android.media 스텁 (프리뷰에서는 소리를 내지 않는다).
 * Audio.kt 컴파일에 필요한 최소 API만 제공.
 */
package android.media

import android.content.Context

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(@Suppress("UNUSED_PARAMETER") u: Int) = this
        fun setContentType(@Suppress("UNUSED_PARAMETER") t: Int) = this
        fun build() = AudioAttributes()
    }
    companion object {
        const val USAGE_GAME = 14
        const val CONTENT_TYPE_SONIFICATION = 4
    }
}

class SoundPool private constructor() {
    class Builder {
        fun setMaxStreams(@Suppress("UNUSED_PARAMETER") n: Int) = this
        fun setAudioAttributes(@Suppress("UNUSED_PARAMETER") a: AudioAttributes) = this
        fun build() = SoundPool()
    }

    fun interface OnLoadCompleteListener {
        fun onLoadComplete(soundPool: SoundPool, sampleId: Int, status: Int)
    }

    private val listeners = ArrayList<OnLoadCompleteListener>()

    fun setOnLoadCompleteListener(l: OnLoadCompleteListener) { listeners.add(l) }

    fun load(context: Context, resId: Int, priority: Int): Int = resId

    fun play(
        soundID: Int, leftVolume: Float, rightVolume: Float,
        priority: Int, loop: Int, rate: Float
    ): Int = soundID

    fun stop(streamID: Int) {}
    fun autoPause() {}
    fun autoResume() {}
    fun release() {}
}

class MediaPlayer private constructor() {
    var isLooping = false
    var isPlaying = false
        private set

    companion object {
        fun create(context: Context, resId: Int): MediaPlayer? = MediaPlayer()
    }

    fun start() { isPlaying = true }
    fun pause() { isPlaying = false }
    fun stop() { isPlaying = false }
    fun setVolume(@Suppress("UNUSED_PARAMETER") l: Float, @Suppress("UNUSED_PARAMETER") r: Float) {}
    fun release() {}
    fun reset() {}
}
