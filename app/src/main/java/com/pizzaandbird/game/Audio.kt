package com.pizzaandbird.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * 게임 오디오: 효과음(SoundPool) + BGM/환경음(MediaPlayer 루프).
 *
 * - 효과음: res/raw/sfx_* (짧은 소리, 동시 재생 가능)
 * - 환경음: res/raw/amb_* (한 채널 루프 — 낮 새소리 / 숲 / 바닷가 / 밤 / 바람 / 화덕 불 /
 *   매미(여름 낮) / 귀뚜라미(가을 밤) / 개구리(봄 밤) — 계절 루프는 tools/season_audio.py 합성)
 * - BGM   : res/raw/bgm_* (한 채널 루프 — 타이틀 / 월드 / 산 / 바다 / 집)
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
        OWL(R.raw.sfx_owl),                    // 밤 부엉이 울음
        CROW(R.raw.sfx_crow),                  // 밤/흐린 날 까마귀 (으스스한 한 소리)
        CUCKOO1(R.raw.sfx_cuckoo1),            // 뻐꾸기 1 (숲·산 지역 지저귐)
        CUCKOO2(R.raw.sfx_cuckoo2),            // 뻐꾸기 2 (짧은 한 마디)
        BOOK_OPEN(R.raw.sfx_book_open),        // 📚 도감 펼치기
        BAG_OPEN(R.raw.sfx_bag_open)           // 🎒 가방/장비 가방 열기
    }

    /** 발소리 종류 */
    enum class Steps { NONE, GRAVEL, WOOD, SNOW }

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
    private val stepSnow: Int      // 눈 위 뽀드득 (걷기/달리기 공용, 속도만 다르게)

    // BGM / 환경음 (MediaPlayer 루프, 페이드 인/아웃)
    private class Channel(val fadeIn: Float, val fadeOut: Float) {
        var mp: MediaPlayer? = null
        var res = 0            // 지금 재생 중인 트랙
        var target = 0         // 재생해야 할 트랙 (0 = 침묵)
        var vol = 0f           // 현재 볼륨 (페이드로 변함)
        var targetVol = 0f     // 목표 볼륨
        var pending: PendingLoad? = null   // 로더에서 디코딩 중인 트랙
        var failedRes = 0      // 로딩에 실패해 더 시도하지 않을 트랙
    }

    /** 디코딩은 MediaPlayer.create 가 끝날 때까지 수십~수백 ms 걸리므로 전용 스레드에서 한다. */
    private class PendingLoad(val res: Int) {
        @Volatile var mp: MediaPlayer? = null
        @Volatile var failed = false
    }

    private val audioThread = HandlerThread("PizzaAndBirdAudio").apply { start() }
    private val loader = Handler(audioThread.looper)

    private val bgmCh = Channel(fadeIn = 1.6f, fadeOut = 0.9f)
    private val ambCh = Channel(fadeIn = 1.3f, fadeOut = 0.7f)
    private var paused = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loaded) { loaded.add(sampleId) }
        }
        for (s in Sfx.values()) sfxIds[s] = pool.load(context, s.res, 1)
        stepGravel1 = pool.load(context, R.raw.sfx_step_gravel1, 1)
        stepGravel2 = pool.load(context, R.raw.sfx_step_gravel2, 1)
        stepWood = pool.load(context, R.raw.sfx_step_wood, 1)
        stepSnow = pool.load(context, R.raw.sfx_step_snow, 1)
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
            kind == Steps.SNOW -> stepSnow
            run -> stepGravel2
            else -> stepGravel1
        }
        if (!isLoaded(sample)) return
        try {
            val vol = if (kind == Steps.SNOW) 0.42f else 0.34f
            val stream = pool.play(sample, vol, vol, 0, -1, if (run) 1.15f else 1f)
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
    // BGM / 환경음 — 페이드 인/아웃
    //
    // playBgm/playAmb는 "목표 트랙"만 정하고, 실제 전환은 매 프레임
    // update(dt)에서 부드럽게 진행된다:
    //   다른 곡 요청 → 현재 곡 페이드아웃 → 교체 → 새 곡 페이드인
    // ------------------------------------------------------------------

    /** BGM 루프 재생 (페이드 전환). 같은 곡이면 그대로 이어 재생한다. */
    fun playBgm(res: Int, vol: Float = 0.45f) {
        bgmCh.target = res
        bgmCh.targetVol = vol
    }

    /** BGM 페이드아웃 정지 */
    fun stopBgm() {
        bgmCh.target = 0
    }

    /** 환경음 루프 재생 (페이드 전환) — 낮 새소리 / 밤 바람 / 화덕 불 */
    fun playAmb(res: Int, vol: Float = 0.3f) {
        ambCh.target = res
        ambCh.targetVol = vol
    }

    /** 환경음 페이드아웃 정지 */
    fun stopAmb() {
        ambCh.target = 0
    }

    /** 매 프레임 호출 (게임 스레드) — 페이드 진행 */
    fun update(dt: Float) {
        if (paused) return
        updateChannel(bgmCh, dt, musicOn)
        updateChannel(ambCh, dt, sfxOn)
    }

    private fun updateChannel(ch: Channel, dt: Float, on: Boolean) {
        // 침묵 목표(정지)가 되면 과거 실패 기록은 지운다 — 다음번 요청은 다시 시도할 수 있게
        if (ch.target == 0) ch.failedRes = 0
        val mp = ch.mp

        // 재생 중인 플레이어가 없으면: 백그라운드에서 디코딩한 트랙을 채택하거나 로딩을 시킨다.
        // (예전엔 여기서 MediaPlayer.create 를 게임 스레드에서 동기 호출해
        //   곡이 바뀔 때마다 화면이 수백 ms 얼었다)
        if (mp == null) {
            val p = ch.pending
            if (p != null) {
                when {
                    p.failed -> {
                        Log.w("PizzaAndBird", "BGM 로딩 실패: res=${p.res}")
                        ch.failedRes = p.res
                        ch.pending = null
                    }
                    p.mp != null -> {
                        ch.pending = null
                        if (on && ch.target == p.res) {
                            // 준비 완료 → 볼륨 0으로 채택, 페이드인은 아래 유지 분기가 맡는다
                            ch.mp = p.mp
                            ch.res = p.res
                            ch.vol = 0f
                        } else {
                            // 기다리는 동안 목표가 바뀌었다 → 버림
                            try { p.mp?.release() } catch (_: Exception) { }
                        }
                    }
                }
                return
            }
            if (on && ch.target != 0 && ch.failedRes != ch.target) {
                val pl = PendingLoad(ch.target)
                ch.pending = pl
                loader.post { loadTrack(pl) }
            }
            return
        }

        // 다른 곡으로 바꿔야 하거나 꺼야 하면: 페이드아웃
        if (ch.res != ch.target || !on) {
            ch.vol -= dt * (ch.targetVol.coerceAtLeast(0.1f) / ch.fadeOut)
            if (ch.vol <= 0f) {
                ch.vol = 0f
                if (ch.res != ch.target) {
                    // 트랙 교체: 해제 후 다음 프레임에 새 곡 시작
                    try { mp.stop(); mp.release() } catch (_: Exception) { }
                    ch.mp = null
                    ch.res = 0
                } else {
                    // 설정에서 꺼짐: 위치 유지한 채 일시정지
                    try { if (mp.isPlaying) mp.pause() } catch (_: Exception) { }
                }
            }
            setVol(mp, ch.vol)
            return
        }

        // 같은 곡 유지: 페이드인 (꺼짐에서 다시 켜졌으면 재개)
        try { if (!mp.isPlaying) mp.start() } catch (_: Exception) { }
        if (ch.vol < ch.targetVol) {
            ch.vol = (ch.vol + dt * (ch.targetVol / ch.fadeIn)).coerceAtMost(ch.targetVol)
            setVol(mp, ch.vol)
        } else if (ch.vol > ch.targetVol) {
            // 목표 볼륨이 낮아진 경우도 부드럽게
            ch.vol = (ch.vol - dt * (ch.targetVol.coerceAtLeast(0.1f) / ch.fadeOut)).coerceAtLeast(ch.targetVol)
            setVol(mp, ch.vol)
        }
    }

    private fun setVol(mp: MediaPlayer, v: Float) {
        try { mp.setVolume(v, v) } catch (_: Exception) { }
    }

    /** 로더 스레드: 트랙 디코딩 (prepare가 수백 ms 걸릴 수 있어 게임 스레드와 분리) */
    private fun loadTrack(p: PendingLoad) {
        val mp = try {
            MediaPlayer.create(context, p.res)?.apply {
                isLooping = true
                setVolume(0f, 0f)   // 재생 시작·페이드인은 updateChannel 이 맡는다
            }
        } catch (_: Exception) {
            null
        }
        if (mp != null) p.mp = mp else p.failed = true
    }

    // ------------------------------------------------------------------
    // 설정 토글 / 생명주기
    // ------------------------------------------------------------------

    /** 음악 켜기/끄기 — 전환은 update()가 페이드로 처리 */
    fun setMusic(on: Boolean) {
        musicOn = on
    }

    /** 효과음/환경음 켜기/끄기 — 환경음 페이드는 update()가 처리 */
    fun setSfx(on: Boolean) {
        sfxOn = on
        if (!on) stopSteps()
    }

    /** 앱이 백그라운드로 갈 때 */
    fun onPause() {
        paused = true
        stopSteps()
        try { pool.autoPause() } catch (_: Exception) { }
        bgmCh.mp?.let { try { if (it.isPlaying) it.pause() } catch (_: Exception) { } }
        ambCh.mp?.let { try { if (it.isPlaying) it.pause() } catch (_: Exception) { } }
    }

    /** 앱으로 돌아올 때 — 절반 볼륨에서 페이드인으로 복귀 */
    fun onResume() {
        paused = false
        try { pool.autoResume() } catch (_: Exception) { }
        bgmCh.vol = (bgmCh.vol * 0.5f)
        ambCh.vol = (ambCh.vol * 0.5f)
        if (musicOn) bgmCh.mp?.let { try { setVol(it, bgmCh.vol); it.start() } catch (_: Exception) { } }
        if (sfxOn) ambCh.mp?.let { try { setVol(it, ambCh.vol); it.start() } catch (_: Exception) { } }
    }
}
