package com.pizzaandbird.game

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Adler32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * [P05] 클라우드 없는 백업 — "백업 코드" 텍스트 하나로 세이브 전체를 다른 기기로 옮긴다.
 *
 * 이 게임은 네트워크 권한이 0개다(완전 오프라인 원칙). 그래서 클라우드 대신
 * **사람이 옮겨 적거나 앱으로 공유할 수 있는 텍스트 코드**를 세이브 통째의 백업 매체로 쓴다.
 *
 * ## 수집 범위 (투명 덤프)
 * 백업은 세이브 스키마를 **모른다**. [SaveManager]가 쓰는 prefs의
 *  - 기존 세이브 키(`state`)
 *  - 기능별 확장 키 `feat_*` (계획 규칙 3 — 앨범/업적/도우/스토리 등 각 파트가 자기 키로 저장)
 *
 * 를 있는 그대로 모아서 덤프한다. 그래서 다른 파트가 `feat_` 접두사 규칙만 지키면
 * 이 파일을 고치지 않아도 새 기능이 자동으로 백업에 포함된다.
 *
 * ## 코드 포맷
 * ```
 * PBSAVE1.yyyyMMdd.adler32(8hex).<payload>
 *   payload = Base64(URL_SAFE|NO_WRAP|NO_PADDING) ← Deflate ← UTF-8 JSON
 *   JSON    = { meta:{created, app, keys, hasSave}, keys:{ "키이름":{t,v} … } }
 *   t = s(문자열) · b(불린) · i(정수) · f(실수) · l(롱) · S(문자열 집합)
 * ```
 * 화면에 보여줄 때는 [formatCode]로 **48글자씩 줄바꿈**해 나눠 읽을 수 있게 한다.
 *
 * ## 안전 규칙
 * - 외부 라이브러리 없음: `java.util.zip` + `android.util.Base64` + `org.json`만 사용
 * - 복원은 **덤프에 있는 키만 덮어쓴다** — 현재 기기에만 있는 데이터를 지우지 않는다
 * - 검증(머리글/날짜/체크섬/압축/JSON)을 전부 통과하기 전에는 prefs를 건드리지 않는다
 *   → 잘못된 코드를 넣어도 기존 세이브는 손상되지 않는다
 */
object Backup {

    /** 코드 머리글 — 포맷 식별. 뒤의 숫자가 포맷 버전이다. */
    const val MAGIC = "PBSAVE1"

    /** 기능별 확장 저장소의 접두사 (계획 규칙 3). 이 접두사 키는 전부 자동으로 백업된다. */
    const val FEAT_PREFIX = "feat_"

    /** 코드를 만들 때 이 앱의 버전으로 기록한다 (구버전/신버전 구분용). */
    const val APP_VERSION = "0.4.2-beta01"

    /** 화면 표시용 한 줄 길이 (48 = 8글자 × 6덩어리, 눈으로 짚어 읽기 좋은 폭) */
    const val CHUNK_LINE = 48

    /** 이보다 긴 코드는 "공유 앱에 따라 잘릴 수 있다"는 안내를 띄운다 (진행은 허용) */
    const val LONG_CODE_CHARS = 24_000

    /** 압축 해제 후 크기 상한 (폭탄 방지 — 정상 세이브는 아무리 커도 수백 KB) */
    private const val MAX_INFLATED_BYTES = 32 * 1024 * 1024

    /** 코드 검사 결과 — prefs는 건드리지 않고 내용만 확인한다. */
    class Info(
        /** 생성 날짜 (코드에 박힌 yyyyMMdd) */
        val date: String,
        /** 코드를 만든 앱 버전 */
        val app: String,
        /** 담긴 저장 항목 수 */
        val keyCount: Int,
        /** 본 세이브(진행 상황)가 들어 있는지 */
        val hasSave: Boolean,
        /** 날짜를 사람이 읽는形で (예: 2026년 9월 27일) */
        val dateLabel: String,
        /** 신버전 코드 등 주의 문구 (없으면 빈 문자열) */
        val warning: String
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // 내보내기
    // ------------------------------------------------------------------

    /**
     * [P05] 현재 기기의 세이브 전체를 백업 코드 텍스트로 만든다.
     *
     * @param flush 백업 직전의 메모리 상태. 넘기면 prefs에 먼저 저장해서
     *              "방금 찍은 사진"까지 코드에 들어가게 한다 (기본값 null = 저장된 것만).
     * @return 코드 문자열. 저장된 것이 아예 없으면 `Result.failure`.
     */
    fun export(ctx: Context, flush: GameState? = null): Result<String> {
      return try {
        val p = prefs(ctx)
        if (flush != null) SaveManager.save(ctx, flush)
        val all = p.all
        val keys = JSONObject()
        var count = 0
        for ((k, v) in all) {
            if (k != SaveManager.saveKey() && !k.startsWith(FEAT_PREFIX)) continue
            val rec = encodeValue(v) ?: continue
            keys.put(k, rec)
            count++
        }
        if (count == 0) {
            return Result.failure(Exception("아직 저장된 진행 상황이 없어요. 게임을 조금 플레이한 뒤 다시 시도해 주세요."))
        }
        val now = System.currentTimeMillis()
        val meta = JSONObject()
            .put("created", now)
            .put("app", APP_VERSION)
            .put("keys", count)
            .put("hasSave", keys.has(SaveManager.saveKey()))
        val doc = JSONObject().put("meta", meta).put("keys", keys)

        val raw = doc.toString().toByteArray(Charsets.UTF_8)
        val zipped = deflate(raw)
        val payload = Base64.encodeToString(zipped, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val sum = String.format(Locale.US, "%08x", adler32(zipped))
        Result.success("$MAGIC.$stamp.$sum.$payload")
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

    /** 화면 표시용 — 코드를 [CHUNK_LINE]글자씩 잘라 줄바꿈한 텍스트. */
    fun formatCode(code: String): String {
        val sb = StringBuilder(code.length + code.length / CHUNK_LINE + 8)
        var i = 0
        while (i < code.length) {
            val end = minOf(i + CHUNK_LINE, code.length)
            sb.append(code, i, end).append('\n')
            i = end
        }
        return sb.toString().trimEnd()
    }

    /** 사람이 읽는 정보 카드용 요약 (길이·항목 수). */
    fun describe(code: String): String {
        val kb = code.length / 1024.0
        return String.format(Locale.KOREA, "%.1fKB · %d글자", kb, code.length)
    }

    // ------------------------------------------------------------------
    // 불러오기
    // ------------------------------------------------------------------

    /**
     * [P05] 코드를 **검사만** 한다 (prefs 무변경). 복원 전에 플레이어에게 확인을 받기 위한 단계.
     * 성공하면 [Info], 실패하면 거부 사유를 message로 돌려준다.
     */
    fun inspect(code: String): Result<Info> {
        val clean = normalize(code)
        if (clean.isEmpty()) {
            return Result.failure(Exception("코드가 비어 있어요. 클립보드에 백업 코드를 복사한 뒤 다시 시도해 주세요."))
        }
        if (!clean.startsWith("PBSAVE")) {
            return Result.failure(Exception("이 게임의 백업 코드가 아니에요. (PBSAVE…로 시작해야 해요)"))
        }
        if (!clean.startsWith("$MAGIC.")) {
            return Result.failure(Exception("지원하지 않는 형식이에요. 다른 버전에서 만든 코드라 이 기기에서는 열 수 없어요."))
        }
        val parts = clean.split('.')
        if (parts.size != 4) {
            return Result.failure(
                Exception("코드가 손상됐어요. (구획이 ${parts.size}개 — 'PBSAVE1.날짜.체크섬.본문' 4개여야 해요. 처음부터 끝까지 전부 복사해 주세요)")
            )
        }
        if (clean.length < MAGIC.length + 22 || parts[1].length != 8 || parts[2].length != 8) {
            return Result.failure(Exception("코드가 손상됐어요. (형식이 맞지 않아요 — 잘리거나 중간이 빠졌어요)"))
        }
        val stamp = parts[1]
        if (stamp.any { it !in '0'..'9' }) {
            return Result.failure(Exception("코드가 손상됐어요. (날짜 부분을 읽을 수 없어요)"))
        }
        val createdMs = try {
            SimpleDateFormat("yyyyMMdd", Locale.US).parse(stamp)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (createdMs <= 0L) {
            return Result.failure(Exception("코드가 손상됐어요. (날짜가 올바르지 않아요)"))
        }
        // 오늘보다 뒤의 날짜 = 시계 오류거나 조작된 코드. 복원은 막지 않고 알린다.
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val future = stamp > today

        val zipped = try {
            Base64.decode(parts[3], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: Exception) {
            return Result.failure(Exception("코드가 손상됐어요. (Base64 해석 실패 — 중간에 글자가 빠졌거나 바뀌었어요)"))
        }
        if (String.format(Locale.US, "%08x", adler32(zipped)) != parts[2].lowercase(Locale.US)) {
            return Result.failure(Exception("코드가 손상됐어요. (체크섬 불일치 — 한 글자만 바뀌어도 이렇게 돼요)"))
        }
        val raw = try {
            inflate(zipped)
        } catch (_: Exception) {
            return Result.failure(Exception("코드가 손상됐어요. (압축 해제 실패)"))
        }
        val doc = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (_: Exception) {
            return Result.failure(Exception("코드가 손상됐어요. (내용을 읽을 수 없어요)"))
        }
        val keys = doc.optJSONObject("keys")
            ?: return Result.failure(Exception("코드가 손상됐어요. (저장 항목이 들어 있지 않아요)"))
        if (keys.length() == 0) {
            return Result.failure(Exception("빈 백업 코드예요. 담겨 있는 저장 항목이 하나도 없어요."))
        }
        val meta = doc.optJSONObject("meta")
        val app = meta?.optString("app", "") ?: ""
        val warning = buildString {
            if (app.isNotEmpty() && isNewerVersion(app, APP_VERSION)) {
                append("더 최신 버전($app)에서 만든 코드예요. ")
                append("이 기기(${APP_VERSION})에서는 일부 내용이 반영되지 않을 수 있어요.")
            }
            if (future) {
                if (isNotEmpty()) append(" · ")
                append("만든 날짜가 오늘보다 뒤예요 ($stamp). 기기 시계를 확인해 주세요.")
            }
        }
        return Result.success(
            Info(
                date = stamp,
                app = if (app.isEmpty()) "알 수 없음" else app,
                keyCount = keys.length(),
                hasSave = keys.has(SaveManager.saveKey()),
                dateLabel = humanDate(createdMs),
                warning = warning
            )
        )
    }

    /**
     * [P05] 코드를 검증하고 prefs에 복원한다. 성공 시 요약 문구를 돌려준다.
     *
     * @param game 현재 [Game]. 넘기면 복원 직후 [Game.reloadState]로 메모리 상태를 다시 읽어
     *             월드·도감·설정까지 그 자리에 바로 살아난다 (null이면 다음 기동 때 반영).
     */
    fun import(ctx: Context, code: String, game: Game? = null): Result<String> {
        val info = inspect(code).getOrElse { return Result.failure(it) }
        val keys = try {
            JSONObject(String(inflate(Base64.decode(normalize(code).split('.')[3],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)), Charsets.UTF_8))
                .getJSONObject("keys")
        } catch (e: Exception) {
            return Result.failure(Exception("코드를 읽는 중 문제가 생겼어요. (${e.javaClass.simpleName})"))
        }
        return try {
            val p = prefs(ctx)
            val ed = p.edit()
            var written = 0
            val it = keys.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (k != SaveManager.saveKey() && !k.startsWith(FEAT_PREFIX)) continue   // 모르는 키는 무시
                val rec = keys.optJSONObject(k) ?: continue
                if (decodeValue(ed, k, rec)) written++
            }
            if (written == 0) {
                return Result.failure(Exception("복원할 항목을 찾지 못했어요. (코드는 유효하지만 비어 있어요)"))
            }
            // apply()는 비동기라 곧바로 reloadState() 하면 옛값을 읽을 수 있다 → commit()
            ed.commit()
            // 복원 직후 메모리 상태를 다시 읽는다 (game이 없으면 다음 기동 때 로드된다)
            val ok = if (game != null) game.reloadState() else SaveManager.load(ctx).started
            val summary = buildString {
                append("${info.dateLabel} 백업 · 항목 ${written}개 복원 완료")
                if (info.warning.isNotEmpty()) append("\n⚠ ${info.warning}")
                if (!ok) append("\n본 세이브가 없어 타이틀로 돌아가요 — 「새로 시작하기」를 눌러 주세요.")
            }
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(Exception("복원 중 문제가 생겼어요. (${e.javaClass.simpleName}) 기존 세이브는 그대로 있어요."))
        }
    }

    // ------------------------------------------------------------------
    // 내부 유틸
    // ------------------------------------------------------------------

    /** 붙여넣기 텍스트는 줄바꿈·공백·BOM이 섞여 온다 — 코드 외의 문자를 전부 걷어낸다. */
    fun normalize(code: String): String {
        val sb = StringBuilder(code.length)
        for (ch in code) {
            if (ch.isWhitespace() || ch == '\uFEFF') continue
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun encodeValue(v: Any?): JSONObject? = when (v) {
        is String -> JSONObject().put("t", "s").put("v", v)
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Long -> JSONObject().put("t", "l").put("v", v)
        is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
        is Set<*> -> {
            val arr = org.json.JSONArray()
            for (e in v) if (e is String) arr.put(e)
            JSONObject().put("t", "S").put("v", arr)
        }
        else -> null      // 모르는 타입은 백업하지 않는다 (prefs에는 위 타입만 들어간다)
    }

    private fun decodeValue(ed: SharedPreferences.Editor, k: String, rec: JSONObject): Boolean {
        return try {
            when (rec.optString("t", "s")) {
                "s" -> ed.putString(k, rec.optString("v", ""))
                "b" -> ed.putBoolean(k, rec.optBoolean("v", false))
                "i" -> ed.putInt(k, rec.optInt("v", 0))
                "l" -> ed.putLong(k, rec.optLong("v", 0L))
                "f" -> ed.putFloat(k, rec.optDouble("v", 0.0).toFloat())
                "S" -> {
                    val arr = rec.optJSONArray("v") ?: return false
                    val set = LinkedHashSet<String>(arr.length())
                    for (i in 0 until arr.length()) set.add(arr.optString(i, ""))
                    ed.putStringSet(k, set)
                }
                else -> return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream(raw.size / 4 + 1024)
        val buf = ByteArray(16 * 1024)
        while (!d.finished()) {
            val n = d.deflate(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        d.end()
        out.close()
        return out.toByteArray()
    }

    private fun inflate(zipped: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(zipped)
        val out = ByteArrayOutputStream(zipped.size * 4 + 1024)
        val buf = ByteArray(16 * 1024)
        try {
            var total = 0
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n <= 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break
                    continue
                }
                total += n
                if (total > MAX_INFLATED_BYTES) throw IllegalStateException("코드가 너무 커요")
                out.write(buf, 0, n)
            }
        } finally {
            inf.end()
            out.close()
        }
        return out.toByteArray()
    }

    private fun adler32(b: ByteArray): Long {
        val a = Adler32()
        a.update(b)
        return a.value
    }

    private fun humanDate(ms: Long): String =
        SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(ms))

    /** "0.4.2-beta01" 같은 버전 라벨 비교 — 숫자 부만 비교한다 (선행 0 무시). */
    fun isNewerVersion(candidate: String, base: String): Boolean {
        fun nums(s: String): List<Int> {
            val out = ArrayList<Int>()
            for (tok in s.split('.', '-')) {
                val n = tok.takeWhile { it.isDigit() }
                if (n.isNotEmpty()) out.add(n.toIntOrNull() ?: 0)
            }
            return out
        }
        val a = nums(candidate)
        val b = nums(base)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
