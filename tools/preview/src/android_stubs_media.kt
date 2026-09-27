@file:Suppress("unused", "MemberVisibilityCanBePrivate")

/**
 * tools/preview — android.media 스텁 (프리뷰 파이프라인 전용).
 * 소리는 재생하지 않고 호출만 받아들인다.
 */
package android.media

import android.content.Context

class AudioAttributes private constructor() {
    companion object {
        const val USAGE_GAME = 14
        const val CONTENT_TYPE_SONIFICATION = 1
    }

    class Builder {
        fun setUsage(usage: Int): Builder = this
        fun setContentType(type: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }
}

class SoundPool private constructor() {
    private var nextId = 1
    private var listener: OnLoadCompleteListener? = null

    fun interface OnLoadCompleteListener {
        fun onLoadComplete(soundPool: SoundPool, sampleId: Int, status: Int)
    }

    fun setOnLoadCompleteListener(l: OnLoadCompleteListener?) {
        listener = l
    }

    fun load(context: Context, resId: Int, priority: Int): Int {
        val id = nextId++
        // 프리뷰에서는 즉시 로드 완료 처리 (게임 로직의 isLoaded 판정 통과용)
        listener?.onLoadComplete(this, id, 0)
        return id
    }

    fun play(soundID: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float): Int = 0
    fun stop(streamID: Int) {}
    fun autoPause() {}
    fun autoResume() {}
    fun release() {}

    class Builder {
        fun setMaxStreams(maxStreams: Int): Builder = this
        fun setAudioAttributes(attributes: AudioAttributes): Builder = this
        fun build(): SoundPool = SoundPool()
    }
}

class MediaPlayer {
    private var playing = false

    var isLooping: Boolean = false
    val isPlaying: Boolean get() = playing

    fun start() { playing = true }
    fun pause() { playing = false }
    fun stop() { playing = false }
    fun release() { playing = false }
    fun setVolume(leftVolume: Float, rightVolume: Float) {}

    companion object {
        fun create(context: Context, resid: Int): MediaPlayer? = MediaPlayer()
    }
}
