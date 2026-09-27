package com.pizzaandbird.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 텍스트 역할 — 화면마다 손으로 크기를 맞추지 말고 여기 있는 역할만 쓴다.
 * (sizeDp: 글자 크기 · bold: 굵기 · track: 자간(em) · lineDp: 줄 간격)
 *
 * 수치는 내장 글꼴(주아 · 고운돋움)에 맞춰 잡았다. 주아는 같은 크기에서 글자가
 * 조금 작고 좁게 보여서 제목 계열을 살짝 키우고 자간을 줄였고, 본문은 힐링 게임답게
 * 줄 간격을 한 숨 더 벌려 두었다.
 */
enum class Role(val sizeDp: Float, val bold: Boolean, val track: Float, val lineDp: Float) {
    HERO(40f, true, 0.045f, 48f),       // 타이틀 화면 로고
    DISPLAY(22f, true, 0.04f, 28f),     // 화면을 뒤덮는 큰 제목 (사진 결과, 배너)
    TITLE(16.8f, true, 0.025f, 22f),    // 패널 제목
    HEADING(14f, true, 0.02f, 19.5f),   // 카드/리스트 이름
    LABEL(12.6f, true, 0.015f, 17f),    // 버튼 · 칩 · 탭
    BODY(12.8f, false, 0.005f, 19.5f),  // 본문 (가독성 때문에 보통 두께)
    CAPTION(11.2f, false, 0.005f, 16.5f), // 보조 설명
    MICRO(8.6f, false, 0f, 12f),        // 아주 작은 보조 글자
    EMOJI(20f, false, 0f, 24f)          // 이모지 단독
}

/**
 * 게임 타이포그래피 — "글꼴은 제자리에, 감성은 살짝 귀엽게".
 *
 * 1. 한글/본문 → 앱에 내장한 둥근 한글 글꼴 두 벌(assets/font, 둘 다 SIL OFL 1.1).
 *      · 굵은 글씨(제목·버튼·이름)  = display_jua.ttf      — Jua(배달의민족 주아), 동글동글 손글씨풍
 *      · 보통 글씨(본문·설명)       = body_gowundodum.ttf  — Gowun Dodum(고운돋움), 부드럽고 담백
 *    파일 이름 앞이 display / body 면 자동으로 제 역할에 꽂힌다. 폴더가 비어 있으면
 *    예전처럼 시스템 sans-serif 로 조용히 내려간다(글꼴 없다고 죽지 않는다).
 *    글꼴에 없는 글자(이모지·한자·희귀 음절)는 안드로이드가 시스템 글꼴로 대체한다.
 * 2. 숫자·영문·기호 → 코드로 생성한 5x7 픽셀 디스플레이 폰트([PixelFont]).
 *                게임 아트가 전부 픽셀이니까 숫자도 픽셀이 자연스럽다.
 *                한글/이모지가 섞인 문장은 자동으로 한글 글꼴로 넘어간다.
 * 3. 크기·굵기·자간·줄간격은 [Role]이 정한다. 색은 아래 팔레트를 쓴다.
 */
object Type {

    // ----- 색 (기존 UI 팔레트를 그대로 승계) -----
    val INK = 0xFF4A3728.toInt()       // 본문
    val SOFT = 0xFF8A7360.toInt()      // 보조 설명
    val MUTED = 0xFF6B5A48.toInt()
    val BROWN = 0xFF6B4F35.toInt()     // 테두리 · 칩
    val CARAMEL = 0xFFB5651D.toInt()   // 강조 · 제목
    val CREAM = 0xFFF8EFDC.toInt()     // 패널 배경
    val PAPER = 0xFFFFF8E8.toInt()     // 어두운 배경 위 글자
    val LEAF = 0xFF6FAE6F.toInt()      // 성공 · 서식지
    val BERRY = 0xFFE2574C.toInt()     // 경고 · 현재 위치
    val SKY = 0xFF3F6FB0.toInt()       // 정보 · 의뢰
    val DROP = Color.argb(74, 74, 55, 40)  // 글자 그림자

    /** dp 배율. [init] 에서 채운다. */
    var d = 1f
        private set

    /** 굵은 역할(제목·버튼·이름)에 쓰는 글꼴 — assets/font/display_*.ttf */
    private var display: Typeface? = null

    /** 보통 역할(본문·설명)에 쓰는 글꼴 — assets/font/body_*.ttf */
    private var body: Typeface? = null

    /** [init] 전에 만들어진 공유 페인트 — 글꼴이 준비되면 한 번 갈아 끼운다. */
    private val bound = ArrayList<Pair<Paint, Boolean>>()
    private var ready = false

    /** 앱 시작 시 한 번 호출. assets/font 의 글꼴을 물려 준다(없으면 시스템 글꼴). */
    fun init(ctx: Context) {
        d = ctx.resources.displayMetrics.density
        loadFromAssets(ctx)
        // 정적 초기화 때 만들어진 페인트(Overlays.textP 등)도 새 글꼴로 갈아 끼운다.
        for ((p, bold) in bound) p.typeface = face(bold)
        bound.clear()
        ready = true
        fills.clear()
        edges.clear()
    }

    /**
     * assets/font 안의 ttf/otf 를 이름으로 골라 담는다.
     *   "display..." 로 시작하면 제목용, "body..." 또는 "text..." 면 본문용,
     *   그 외는 남는 자리에 채운다. 한 벌만 넣어 두면 제목·본문이 같은 글꼴을 쓴다.
     */
    private fun loadFromAssets(ctx: Context) {
        display = null
        body = null
        try {
            val files = (ctx.assets.list("font") ?: emptyArray())
                .filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
                .sorted()
            for (f in files) {
                val tf = try {
                    Typeface.createFromAsset(ctx.assets, "font/$f")
                } catch (_: Exception) {
                    null
                } ?: continue
                val n = f.lowercase()
                when {
                    n.startsWith("display") || n.contains("title") ||
                        n.contains("bold") || n.contains("black") -> if (display == null) display = tf
                    n.startsWith("body") || n.startsWith("text") ||
                        n.contains("regular") -> if (body == null) body = tf
                    display == null -> display = tf
                    body == null -> body = tf
                }
            }
            if (display == null) display = body
            if (body == null) body = display
        } catch (_: Exception) {
            display = null
            body = null
        }
    }

    /** 이미 만들어 둔 공유 페인트에 글꼴을 건다(공용 페인트를 만들 때 호출). */
    fun bind(p: Paint, bold: Boolean): Paint {
        p.typeface = face(bold)
        // init 이 끝난 뒤(씬마다) 만들어지는 페인트는 이미 제 글꼴을 받았으니 붙잡아 두지 않는다.
        if (!ready) bound.add(p to bold)
        return p
    }

    /**
     * 역할에 맞는 글꼴. bold = 제목 글꼴(주아), 보통 = 본문 글꼴(고운돋움).
     * 두 글꼴 모두 한 가지 굵기라서 가짜 볼드를 씌우지 않는다 — 씌우면 획이 뭉개진다.
     */
    fun face(bold: Boolean): Typeface {
        val tf = if (bold) display else body
        if (tf != null) return tf
        return Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    // ----- 페인트 (캐시) -----

    private val fills = HashMap<Long, Paint>()
    private val edges = HashMap<Long, Paint>()

    private fun ckey(sizeDp: Float, bold: Boolean, track: Float, color: Int): Long {
        val a = (sizeDp * 100f).toLong()
        val b = (track * 10000f).toLong()
        // 알파는 16단계로 양자화 — 배너/토스트처럼 매 프레임 알파가 바뀌면
        // 캐시가 무한정 커진다(1/16 차이는 눈에 보이지 않는다).
        val alpha = ((color ushr 24) ushr 4).toLong()
        val rgb = (color and 0xFFFFFF).toLong()
        return ((a * 128L + b) * 2L + (if (bold) 1L else 0L)) * 4294967296L + (alpha shl 24) + rgb
    }

    fun paint(role: Role, color: Int): Paint = paintPx(role.sizeDp * d, role.bold, role.track, color)

    /** 크기를 직접 지정하는 페인트 (기존처럼 화면마다 dp 를 넘기는 곳에서 쓴다). */
    fun paintAt(sizeDp: Float, bold: Boolean, track: Float, color: Int): Paint =
        paintPx(sizeDp * d, bold, track, color)

    /** px 단위 지정 — 가상 해상도(960x540) 월드 캔버스에 그릴 때 쓴다. */
    fun paintPx(sizePx: Float, bold: Boolean, track: Float, color: Int): Paint {
        val k = ckey(sizePx, bold, track, color)
        return fills.getOrPut(k) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face(bold)
                textSize = sizePx
                letterSpacing = track
                this.color = color
            }
        }
    }

    private fun edge(role: Role, color: Int, w: Float): Paint {
        val k = ckey(role.sizeDp, role.bold, role.track, color) * 1024L + (w * 16f).toLong()
        return edges.getOrPut(k) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face(role.bold)
                textSize = role.sizeDp * d
                letterSpacing = role.track
                this.color = color
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                strokeWidth = w
            }
        }
    }

    fun size(role: Role): Float = role.sizeDp * d
    fun lineHeight(role: Role): Float = role.lineDp * d

    /**
     * 세로 가운데 정렬용 baseline — 픽셀 폰트면 대문자 높이로, 아니면 ascent/descent 로 계산
     */
    fun midBaseline(role: Role, centerY: Float, s: String, color: Int = INK): Float {
        if (usePixel(role, s)) return PixelFont.midY(centerY, scaleOf(role))
        val p = paint(role, color)
        return centerY - (p.descent() + p.ascent()) / 2f
    }

    /** 임의 크기 페인트의 세로 가운데 baseline (레거 밖 크기가 필요할 때). */
    fun midBaseline(p: Paint, centerY: Float): Float = centerY - (p.descent() + p.ascent()) / 2f

    /** [text] 를 상자 세로 가운데에 맞춰 그린다. */
    fun textCentered(
        c: Canvas, s: String, x: Float, centerY: Float, role: Role,
        color: Int = INK, align: Float = 0f
    ) = text(c, s, x, midBaseline(role, centerY, s, color), role, color, align)

    /** [sticker] 를 상자 세로 가운데에 맞춰 그린다(가로 가운데 정렬). */
    fun stickerCentered(
        c: Canvas, s: String, x: Float, centerY: Float, role: Role,
        color: Int = INK, edgeColor: Int = CREAM
    ) = sticker(c, s, x, midBaseline(role, centerY, s, color), role, color, 0.5f, edgeColor)

    /** 픽셀 폰트를 쓸지 (라틴만으로 된 진한 글자면 O) */
    private fun usePixel(role: Role, s: String): Boolean =
        role.bold && role.sizeDp >= 10f && PixelFont.supports(s)

    private fun scaleOf(role: Role): Int =
        Math.max(1, Math.round(role.sizeDp * d * 0.72f / PixelFont.GH))

    /** 문자열이 실제로 차지할 너비(px). [text] 와 반드시 같은 규칙을 쓴다. */
    fun width(role: Role, s: String, color: Int = INK): Float =
        if (usePixel(role, s)) PixelFont.width(s, scaleOf(role)).toFloat()
        else paint(role, color).measureText(s)

    /**
     * 글자 그리기. y 는 밑줄(baseline) — Canvas.drawText 과 같은 규약이라
     * 기존 코드와 섞어 써도 어긋나지 않는다.
     * align: 0 = x가 왼쪽, 0.5 = 가운데, 1 = 오른쪽.
     */
    fun text(
        c: Canvas, s: String, x: Float, y: Float, role: Role,
        color: Int = INK, align: Float = 0f
    ) {
        if (s.isEmpty()) return
        if (usePixel(role, s)) {
            PixelFont.draw(c, s, x, y, scaleOf(role), color, align)
            return
        }
        val p = paint(role, color)
        c.drawText(s, x - align * p.measureText(s), y, p)
    }

    /**
     * 스티커 텍스트 — 부드러운 그림자 + 크림색 테두리.
     * 화면을 덮는 큰 제목에만 쓴다(작은 글자에 쓰면 화면이 시끄러워진다).
     */
    fun sticker(
        c: Canvas, s: String, x: Float, y: Float, role: Role,
        color: Int = INK, align: Float = 0.5f, edgeColor: Int = CREAM
    ) {
        if (s.isEmpty()) return
        if (usePixel(role, s)) {
            PixelFont.draw(
                c, s, x, y, scaleOf(role), color, align,
                outline = edgeColor, shadow = DROP
            )
            return
        }
        val p = paint(role, color)
        val left = x - align * p.measureText(s)
        val off = role.sizeDp * d * 0.055f
        c.drawText(s, left + off, y + off, paint(role, DROP))
        // 테두리는 글자 크기의 11% — 더 두꺼우면 한글 획이 뭉개진다
        c.drawText(s, left, y, edge(role, edgeColor, minOf(role.sizeDp * d * 0.11f, 3.2f * d)))
        c.drawText(s, left, y, p)
    }

    // ----- 줄바꿈 -----

    /** 줄 맨 앞에 오면 안 되는 글자(받침·문장부호) — 한글 줄바꿈 규칙 */
    private val NO_START = "ㄱㄲㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎㄸㅃㅆㅉ.,:;!?)]}%…·"

    /**
     * 줄바꿈. 한글은 공백이 드물어 단어 단위로만 자르면 패널을 넘치므로,
     * 한 단어가 폭보다 길면 글자 단위로 자른다.
     */
    fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = ArrayList<String>()
        if (text.isEmpty()) return out
        if (width <= 0f) {
            out.add(text)
            return out
        }
        for (para in text.split('\n')) {
            var cur = ""
            for (word in para.split(' ')) {
                if (word.isEmpty()) continue
                val cand = if (cur.isEmpty()) word else "$cur $word"
                if (p.measureText(cand) <= width) {
                    cur = cand
                    continue
                }
                if (cur.isNotEmpty()) {
                    out.add(cur)
                    cur = ""
                }
                var piece = ""
                for (ch in word) {
                    val test = piece + ch
                    if (piece.isNotEmpty() && p.measureText(test) > width) {
                        if (NO_START.indexOf(ch) >= 0) {
                            piece = test          // 금칙 문자는 앞 글자에 붙인다
                        } else {
                            out.add(piece)
                            piece = ch.toString()
                        }
                    } else {
                        piece = test
                    }
                }
                cur = piece
            }
            if (cur.isNotEmpty()) out.add(cur)
        }
        return out
    }
}

/**
 * 코드로 생성한 5x7 픽셀 디스플레이 폰트 (숫자 · 영문 · 기호).
 *
 * 게임 아트가 전부 픽셀이니 숫자도 픽셀이 제일 어울린다.
 * 한글/이모지가 하나라도 섞이면 [supports] 가 false 가 되어
 * 호출한 쪽이 시스템 폰트로 자연스럽게 넘어간다.
 *
 * 글리프는 위아래가 뒤집히지 않도록 문자열 그대로 위에서 아래로 그린다.
 */
object PixelFont {

    const val GW = 5        // 글리프 폭
    const val GH = 7        // 대문자 높이 (8번째 행은 아래로 늘어진 글자용)
    const val ADVANCE = 6   // 글리프 폭 + 여백 1칸
    private const val CELL_H = 8

    private val TABLE: Array<Pair<Char, String>> = arrayOf(
        ' ' to "...../...../...../...../...../...../...../.....",
        '0' to ".###./#...#/#..##/#.#.#/##..#/#...#/.###./.....",
        '1' to "..#../.##../..#../..#../..#../..#../.###./.....",
        '2' to ".###./#...#/....#/...#./..#../.#.../#####/.....",
        '3' to "#####/...#./..#../...#./....#/#...#/.###./.....",
        '4' to "...#./..##./.#.#./#..#./#####/...#./...#./.....",
        '5' to "#####/#..../####./....#/....#/#...#/.###./.....",
        '6' to "..##./.#.../#..../####./#...#/#...#/.###./.....",
        '7' to "#####/....#/...#./..#../.#.../.#.../.#.../.....",
        '8' to ".###./#...#/#...#/.###./#...#/#...#/.###./.....",
        '9' to ".###./#...#/#...#/.####/....#/...#./.##../.....",
        'A' to "..#../.#.#./#...#/#...#/#####/#...#/#...#/.....",
        'B' to "####./#...#/#...#/####./#...#/#...#/####./.....",
        'C' to ".###./#...#/#..../#..../#..../#...#/.###./.....",
        'D' to "####./#...#/#...#/#...#/#...#/#...#/####./.....",
        'E' to "#####/#..../#..../####./#..../#..../#####/.....",
        'F' to "#####/#..../#..../####./#..../#..../#..../.....",
        'G' to ".###./#...#/#..../#..##/#...#/#...#/.###./.....",
        'H' to "#...#/#...#/#...#/#####/#...#/#...#/#...#/.....",
        'I' to ".###./..#../..#../..#../..#../..#../.###./.....",
        'J' to "..###/...#./...#./...#./...#./#..#./.##../.....",
        'K' to "#...#/#..#./#.#../##.../#.#../#..#./#...#/.....",
        'L' to "#..../#..../#..../#..../#..../#..../#####/.....",
        'M' to "#...#/##.##/#.#.#/#...#/#...#/#...#/#...#/.....",
        'N' to "#...#/##..#/#.#.#/#..##/#...#/#...#/#...#/.....",
        'O' to ".###./#...#/#...#/#...#/#...#/#...#/.###./.....",
        'P' to "####./#...#/#...#/####./#..../#..../#..../.....",
        'Q' to ".###./#...#/#...#/#...#/#.#.#/#..#./.##.#/.....",
        'R' to "####./#...#/#...#/####./#.#../#..#./#...#/.....",
        'S' to ".####/#..../#..../.###./....#/....#/####./.....",
        'T' to "#####/..#../..#../..#../..#../..#../..#../.....",
        'U' to "#...#/#...#/#...#/#...#/#...#/#...#/.###./.....",
        'V' to "#...#/#...#/#...#/#...#/#...#/.#.#./..#../.....",
        'W' to "#...#/#...#/#...#/#.#.#/#.#.#/##.##/#...#/.....",
        'X' to "#...#/#...#/.#.#./..#../.#.#./#...#/#...#/.....",
        'Y' to "#...#/#...#/.#.#./..#../..#../..#../..#../.....",
        'Z' to "#####/....#/...#./..#../.#.../#..../#####/.....",
        'a' to "...../...../.###./....#/.####/#...#/.####/.....",
        'b' to "#..../#..../####./#...#/#...#/#...#/####./.....",
        'c' to "...../...../.###./#...#/#..../#...#/.###./.....",
        'd' to "....#/....#/.####/#...#/#...#/#...#/.####/.....",
        'e' to "...../...../.###./#...#/#####/#..../.###./.....",
        'f' to "..##./.#..#/.#.../####./.#.../.#.../.#.../.....",
        'g' to "...../...../.####/#...#/#...#/.####/....#/.###.",
        'h' to "#..../#..../####./#...#/#...#/#...#/#...#/.....",
        'i' to "..#../...../.##../..#../..#../..#../.###./.....",
        'j' to "...#./...../..##./...#./...#./...#./#..#./.##..",
        'k' to "#..../#..../#..#./#.#../##.../#.#../#..#./.....",
        'l' to ".##../..#../..#../..#../..#../..#../.###./.....",
        'm' to "...../...../##.#./#.#.#/#.#.#/#...#/#...#/.....",
        'n' to "...../...../####./#...#/#...#/#...#/#...#/.....",
        'o' to "...../...../.###./#...#/#...#/#...#/.###./.....",
        'p' to "...../...../####./#...#/#...#/####./#..../#....",
        'q' to "...../...../.####/#...#/#...#/.####/....#/....#",
        'r' to "...../...../#.##./##..#/#..../#..../#..../.....",
        's' to "...../...../.####/#..../.###./....#/####./.....",
        't' to ".#.../.#.../####./.#.../.#.../.#..#/..##./.....",
        'u' to "...../...../#...#/#...#/#...#/#..##/.##.#/.....",
        'v' to "...../...../#...#/#...#/#...#/.#.#./..#../.....",
        'w' to "...../...../#...#/#...#/#.#.#/#.#.#/.#.#./.....",
        'x' to "...../...../#...#/.#.#./..#../.#.#./#...#/.....",
        'y' to "...../...../#...#/#...#/#...#/.####/....#/.###.",
        'z' to "...../...../#####/...#./..#../.#.../#####/.....",
        '.' to "...../...../...../...../...../...../.##../.##..",
        ',' to "...../...../...../...../...../.##../.##../.#...",
        ':' to "...../..#../..#../...../..#../..#../...../.....",
        ';' to "...../..#../..#../...../..#../..#../.#.../.....",
        '!' to "..#../..#../..#../..#../..#../...../..#../.....",
        '?' to ".###./#...#/....#/...#./..#../...../..#../.....",
        '\'' to "..#../..#../...../...../...../...../...../.....",
        '"' to ".#.#./.#.#./...../...../...../...../...../.....",
        '-' to "...../...../...../.###./...../...../...../.....",
        '+' to "...../..#../..#../#####/..#../..#../...../.....",
        '=' to "...../...../.###./...../.###./...../...../.....",
        '/' to "....#/....#/...#./..#../.#.../#..../#..../.....",
        '\\' to "#..../#..../.#.../..#../...#./....#/....#/.....",
        '(' to "...#./..#../.#.../.#.../.#.../..#../...#./.....",
        ')' to ".#.../..#../...#./...#./...#./..#../.#.../.....",
        '[' to ".###./..#../..#../..#../..#../..#../.###./.....",
        ']' to ".###./..#../..#../..#../..#../..#../.###./.....",
        '<' to "...#./..#../.#.../.#.../.#.../..#../...#./.....",
        '>' to ".#.../..#../...#./...#./...#./..#../.#.../.....",
        '#' to ".#.#./.#.#./#####/.#.#./#####/.#.#./.#.#./.....",
        '*' to "...../..#../#.#.#/.###./#.#.#/..#../...../.....",
        '%' to "##..#/##.#./...#./..#../.#.../#..##/...##/.....",
        '&' to ".##../#..#./#..#./.##../#.#.#/#..#./.##.#/.....",
        '@' to ".###./#...#/#.###/#.#.#/#.###/#..../.###./.....",
        '$' to "..#../.####/#.#../.###./..#.#/####./..#../.....",
        '|' to "..#../..#../..#../..#../..#../..#../..#../.....",
        '^' to "..#../.#.#./#...#/...../...../...../...../.....",
        '~' to "...../...../...../.#..#/#.##./...../...../.....",
        '_' to "...../...../...../...../...../...../#####/.....",
        '°' to ".###./.#.#./.###./...../...../...../...../.....",
        '×' to "...../...../#...#/.#.#./..#../.#.#./#...#/.....",
        '·' to "...../...../...../..#../...../...../...../.....",
        '•' to "...../...../.###./.###./.###./...../...../.....",
        '★' to "..#../..#../#####/.###./.###./#.#.#/#...#/.....",
        '☆' to "..#../..#../#...#/.#.#./.#.#./#...#/...../.....",
        '₩' to "#.#.#/#.#.#/#.#.#/#####/#.#.#/#####/..#../.....",
        '←' to "...../..#../.#.../#####/.#.../..#../...../.....",
        '→' to "...../..#../...#./#####/...#./..#../...../.....",
        '↑' to "..#../.###./#.#.#/..#../..#../..#../...../.....",
        '↓' to "...../..#../..#../..#../#.#.#/.###./..#../.....",
        '»' to "...../#.#../.#.#./..#../.#.#./#.#../...../.....",
        '«' to "...../..#.#/.#.#./..#../.#.#./..#.#/...../.....",
    )

    private val glyphs: Map<Char, Array<String>> = HashMap<Char, Array<String>>().apply {
        for ((ch, spec) in TABLE) put(ch, spec.split('/').toTypedArray())
    }

    private var atlas: Bitmap? = null
    private val cellIndex = HashMap<Char, Int>()
    private val tints = HashMap<Int, PorterDuffColorFilter>()
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val src = Rect()
    private val dst = RectF()

    /** 라틴/숫자/기호만 지원한다. 한글이나 이모지가 섞이면 false. */
    fun supports(s: String): Boolean {
        for (ch in s) if (!glyphs.containsKey(ch)) return false
        return true
    }

    fun width(s: String, scale: Int): Int =
        if (s.isEmpty()) 0 else (s.length * ADVANCE - 1) * scale

    fun capHeight(scale: Int): Int = GH * scale

    /** 세로 가운데 정렬용: 줄 가운데를 baseline 으로 바꾼다. */
    fun midY(centerY: Float, scale: Int): Float = centerY + GH * scale / 2f

    /**
     * 그리기. y 는 밑줄(baseline) — Type.text 와 같은 규약.
     * align: 0 = x가 왼쪽, 0.5 = 가운데, 1 = 오른쪽.
     * outline / shadow 는 색(0 이면 그리지 않음).
     */
    fun draw(
        c: Canvas, s: String, x: Float, y: Float, scale: Int, color: Int,
        align: Float = 0f, outline: Int = 0, shadow: Int = 0
    ) {
        if (s.isEmpty() || scale <= 0) return
        val bmp = buildAtlas()
        val w = width(s, scale)
        val left = when (align) {
            0.5f -> x - w / 2f
            1f -> x - w
            else -> x
        }
        val top = y - GH * scale
        if (shadow != 0) pass(c, bmp, s, left + scale, top + scale, scale, shadow)
        if (outline != 0) {
            pass(c, bmp, s, left - scale, top, scale, outline)
            pass(c, bmp, s, left + scale, top, scale, outline)
            pass(c, bmp, s, left, top - scale, scale, outline)
            pass(c, bmp, s, left, top + scale, scale, outline)
        }
        pass(c, bmp, s, left, top, scale, color)
    }

    private fun pass(c: Canvas, bmp: Bitmap, s: String, left: Float, top: Float, scale: Int, color: Int) {
        // 색은 RGB 로만 캐시하고 알파는 페인트에 실어 둔다(페이드할 때 캐시가 안 불어난다)
        val rgb = color and 0xFFFFFF
        bmpPaint.colorFilter = tints.getOrPut(rgb) {
            PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_IN)
        }
        bmpPaint.alpha = (color ushr 24) and 0xFF
        var x = left
        for (ch in s) {
            val i = cellIndex[ch] ?: continue
            src.set(i * ADVANCE, 0, i * ADVANCE + GW, CELL_H)
            dst.set(x, top, x + GW * scale, top + CELL_H * scale)
            c.drawBitmap(bmp, src, dst, bmpPaint)
            x += ADVANCE * scale
        }
    }

    private fun buildAtlas(): Bitmap {
        atlas?.let { return it }
        val bmp = Bitmap.createBitmap(TABLE.size * ADVANCE, CELL_H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        p.color = Color.WHITE
        for ((i, e) in TABLE.withIndex()) {
            cellIndex[e.first] = i
            val rows = glyphs[e.first] ?: continue
            for (ry in rows.indices) {
                if (ry >= CELL_H) break
                val row = rows[ry]
                for (rx in 0 until minOf(GW, row.length)) {
                    if (row[rx] != '#') continue
                    val x = i * ADVANCE + rx
                    cv.drawRect(x.toFloat(), ry.toFloat(), (x + 1).toFloat(), (ry + 1).toFloat(), p)
                }
            }
        }
        atlas = bmp
        return bmp
    }
}
