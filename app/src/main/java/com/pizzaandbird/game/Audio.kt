package com.pizzaandbird.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * 게임 오디오: 효과음(SoundPool) + BGM/환경음(MediaPlayer 루프).
 *
 * - 효과음: res/raw/sfx_* (짧은 소리, 동시 재생 가능)
 * - 환경음: res/raw/amb_* (한 채널 루프 — 낮 새소리 / 밤 바람 / 화덕 불)
 * - BGM   : res/raw/bgm_* (한 채널 루프 — 타이틀 / 월드 / 집)
 *
 * 곡 배치를 바꾸고 싶으면 씬에서 부르는 R.raw.bgm_* 만 바꾸면 된다.
 */
class Audio(private val context: Context) {

    /** 효과음 종류 → res/raw 리소스 매핑 */
    enum class Sfx(val res: Int) {
        TAP(R.raw.sfx_tap),                    // 버튼/UI 탭
        SHUTTER(R.raw.sfx_shutter),            // 📷 촬영
        BIRD_FLEE(R.raw.sfx_bird_flee),        // 새 도망 (날개 푸드덕)
        BIRD_CHIRP1(R.raw.sfx_bird_chirp1),    // 새 지저귐 1
        BIRD_CHIRP2(R.raw.sfx_bird_chirp2),    // 새 지저귐 2
        SUCCESS(R.raw.sfx_success),            // 성공 (사진 별점, 피자 맛있음)
        SPARKLE(R.raw.sfx_sparkle),            // 반짝 (도감 신규, 걸작, 행운 상승)
        EAT(R.raw.sfx_eat),                    // 🍕 냠냠
        BIKE_BELL(R.raw.sfx_bike_bell),        // 자전거 타기 (따르릉)
        BIKE_BRAKE(R.raw.sfx_bike_brake),      // 자전거 내리기 (끼익)
        BUY(R.raw.sfx_buy),                    // 구매 성공
        REWARD(R.raw.sfx_reward),              // 의뢰 보수 획득
        FAIL(R.raw.sfx_fail),                  // 실패/돈 부족/피자 탐
        WHOOSH(R.raw.sfx_whoosh),              // 터널 이동
        NOTIFY(R.raw.sfx_notify),              // 알림 (의뢰 접수, 희귀새 등장)
        OWL(R.raw.sfx_owl)                     // 밤 부엉이 울음
    }

    /** 발소리 종류 */
    enum class Steps { NONE, GRAVEL, WOOD }

    var sfxOn = true
    var musicOn = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sfxIds = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()

    // 발소리 루프 (SoundPool 스트림)
    private var stepSample = 0
    private var stepStream = 0
    private var stepKind = Steps.NONE
    private var stepRun = false
    private val stepGravel1: Int   // 걷기
    private val stepGravel2: Int   // 달리기
    private val stepWood: Int

    // BGM / 환경음 (MediaPlayer 루프)
    private var bgm: MediaPlayer? = null
    private var bgmRes = 0
    private var amb: MediaPlayer? = null
    private var ambRes = 0
    private var ambVol = 0.3f
    private var paused = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loaded) { loaded.add(sampleId) }
        }
        for (s in Sfx.values()) sfxIds[s] = pool.load(context, s.res, 1)
        stepGravel1 = pool.load(context, R.raw.sfx_step_gravel1, 1)
        stepGravel2 = pool.load(context, R.raw.sfx_step_gravel2, 1)
        stepWood = pool.load(context, R.raw.sfx_step_wood, 1)
    }

    private fun isLoaded(id: Int) = synchronized(loaded) { id in loaded }

    // ------------------------------------------------------------------
    // 효과음
    // ------------------------------------------------------------------

    fun play(s: Sfx, vol: Float = 1f, rate: Float = 1f) {
        if (!sfxOn || paused) return
        val id = sfxIds[s] ?: return
        if (!isLoaded(id)) return
        try {
            pool.play(id, vol, vol, 1, 0, rate.coerceIn(0.5f, 2f))
        } catch (_: Exception) {
        }
    }

    /** 발소리 루프 시작/변경. kind=NONE이면 정지. run=true면 달리기 발소리. */
    fun steps(kind: Steps, run: Boolean = false) {
        if (kind == stepKind && run == stepRun) return
        stopSteps()
        if (!sfxOn || paused || kind == Steps.NONE) return
        val sample = when {
            kind == Steps.WOOD -> stepWood
            run -> stepGravel2
            else -> stepGravel1
        }
        if (!isLoaded(sample)) return
        try {
            val stream = pool.play(sample, 0.34f, 0.34f, 0, -1, if (run) 1.15f else 1f)
            if (stream != 0) {
                stepStream = stream
                stepSample = sample
                stepKind = kind
                stepRun = run
            }
        } catch (_: Exception) {
        }
    }

    fun stopSteps() {
        if (stepStream != 0) {
            try { pool.stop(stepStream) } catch (_: Exception) { }
        }
        stepStream = 0
        stepKind = Steps.NONE
        stepRun = false
    }

    // ------------------------------------------------------------------
    // BGM
    // ------------------------------------------------------------------

    /** BGM 루프 재생. 같은 곡이면 그대로 이어 재생한다. */
    fun playBgm(res: Int, vol: Float = 0.45f) {
        if (res == bgmRes && bgm != null) return
        stopBgm()
        bgmRes = res
        if (!musicOn) return
        bgm = start(res, vol) ?: return
    }

    fun stopBgm() {
        bgm?.let { try { it.stop(); it.release() } catch (_: Exception) { } }
        bgm = null
        bgmRes = 0
    }

    // ------------------------------------------------------------------
    // 환경음 (낮 새소리 / 밤 바람 / 화덕 불)
    // ------------------------------------------------------------------

    fun playAmb(res: Int, vol: Float = 0.3f) {
        if (res == ambRes && amb != null) return
        stopAmb()
        ambRes = res
        ambVol = vol
        if (!sfxOn) return
        amb = start(res, vol)
    }

    fun stopAmb() {
        amb?.let { try { it.stop(); it.release() } catch (_: Exception) { } }
        amb = null
        ambRes = 0
    }

    private fun start(res: Int, vol: Float): MediaPlayer? = try {
        MediaPlayer.create(context, res)?.apply {
            isLooping = true
            setVolume(vol, vol)
            if (!paused) start()
        }
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------------
    // 설정 토글 / 생명주기
    // ------------------------------------------------------------------

    fun setMusic(on: Boolean) {
        musicOn = on
        if (!on) {
            bgm?.let { try { it.pause() } catch (_: Exception) { } }
        } else {
            val res = bgmRes
            if (bgm != null) {
                try { bgm?.start() } catch (_: Exception) { }
            } else if (res != 0) {
                bgmRes = 0
                playBgm(res)
            }
        }
    }

    fun setSfx(on: Boolean) {
        sfxOn = on
        if (!on) {
            stopSteps()
            amb?.let { try { it.pause() } catch (_: Exception) { } }
        } else {
            val res = ambRes
            if (amb != null) {
                try { amb?.start() } catch (_: Exception) { }
            } else if (res != 0) {
                ambRes = 0
                playAmb(res, ambVol)
            }
        }
    }

    /** 앱이 백그라운드로 갈 때 */
    fun onPause() {
        paused = true
        stopSteps()
        try { pool.autoPause() } catch (_: Exception) { }
        bgm?.let { try { if (it.isPlaying) it.pause() } catch (_: Exception) { } }
        amb?.let { try { if (it.isPlaying) it.pause() } catch (_: Exception) { } }
    }

    /** 앱으로 돌아올 때 */
    fun onResume() {
        paused = false
        try { pool.autoResume() } catch (_: Exception) { }
        if (musicOn) bgm?.let { try { it.start() } catch (_: Exception) { } }
        if (sfxOn) amb?.let { try { it.start() } catch (_: Exception) { } }
    }
}
