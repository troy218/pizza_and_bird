// [P05] 백업 코드 검증 — 에뮬레이터 없이 JVM에서 왕복(export→초기화→import)과
// 손상/구버전/빈 코드 거부를 확인한다.  실행: bash tools/backup_test/run.sh
package com.pizzaandbird.game

import android.content.Context
import kotlin.system.exitProcess

private var failed = 0
private var passed = 0

private fun check(name: String, cond: Boolean, detail: String = "") {
    if (cond) {
        passed++
        println("  ✅ $name")
    } else {
        failed++
        println("  ❌ $name ${if (detail.isEmpty()) "" else "→ $detail"}")
    }
}

private fun section(t: String) = println("\n— $t")

/** 테스트용 세이브를 만든다 (피자·도감·지역·스킬·feat_* 키까지) */
private fun seedGame(ctx: FakeContext): Game {
    val game = Game(ctx)
    val s = game.state
    s.reset("seoul")
    s.money = 123456
    s.hunger = 77.5f
    s.luck = 33.25f
    s.region = "busan"
    s.visited.add("busan")
    s.photos = 42
    s.level = 7
    s.day = 12
    s.worldTime = 19.25f
    s.pizzas[3] = 2
    s.birdCounts["sparrow"] = 5
    s.bestStars["sparrow"] = 4
    s.sfxOn = false
    s.camShake = 3
    s.ownedGear.add(CameraGear.STARTER)
    SaveManager.save(ctx, s)
    // 다른 파트(P3/P6/P7/P8)가 쓸 법한 feat_* 확장 저장소 (규칙 3)
    val p = ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE).edit()
    p.putString("feat_album_v1", """{"photos":[{"id":1,"stars":5,"날짜":"봄 3일째"}]}""")
    p.putInt("feat_ach_count_v1", 13)
    p.putBoolean("feat_story_seen_v1", true)
    p.putFloat("feat_luck_bonus_v1", 1.25f)
    p.putLong("feat_last_play_v1", 1_700_000_000_000L)
    p.putStringSet("feat_tags_v1", LinkedHashSet(listOf("두루미", "올빼미")))
    p.putString("not_collected_v1", "이 키는 백업되면 안 돼요")
    p.commit()
    return game
}

private fun freshGame(ctx: FakeContext): Game = Game(ctx)

private fun msgOf(r: Result<*>): String {
    val e = r.exceptionOrNull()
    return e?.message ?: "(성공)"
}

fun main() {
    println("=== [P05] 백업 코드 검증 (JVM) ===")

    section("1. 세이브가 없으면 코드를 만들지 않는다")
    run {
        val ctx = FakeContext()
        val r = Backup.export(ctx, null)
        check("내보내기 실패", r.isFailure, msgOf(r))
        check("사유 안내 포함", (msgOf(r)).contains("저장된 진행 상황"))
    }

    section("2. 왕복: export → 데이터 초기화 → import")
    val ctx = FakeContext()
    val code: String
    val summary: String
    run {
        val game = seedGame(ctx)
        val r = Backup.export(ctx, game.state)
        check("내보내기 성공", r.isSuccess, msgOf(r))
        code = r.getOrNull() ?: run { println("코드 없음 — 중단"); exitProcess(1) }

        check("머리글 PBSAVE1", code.startsWith("PBSAVE1."), code.take(20))
        val parts = code.split('.')
        check("구획 4개 (머리글·날짜·체크섬·본문)", parts.size == 4, "실제 ${parts.size}개")
        check("날짜 8자리 숫자", parts[1].length == 8 && parts[1].all { it in '0'..'9' }, parts[1])
        check("체크섬 8hex", parts[2].length == 8 && parts[2].all { it.isDigit() || it in 'a'..'f' }, parts[2])
        check("본문에 줄바꿈·공백 없음", !parts[3].any { it.isWhitespace() })

        // 화면 표시용 청크
        val formatted = Backup.formatCode(code)
        val lines = formatted.split('\n')
        check("청크 줄 길이 ≤ ${Backup.CHUNK_LINE}", lines.all { it.length <= Backup.CHUNK_LINE },
            "최대 ${lines.maxOf { it.length }}")
        check("청크 되돌리면 원본", Backup.normalize(formatted) == code)
        println("     코드 ${Backup.describe(code)} · ${lines.size}줄")

        // 검사(미리보기)
        val insp = Backup.inspect(code)
        check("inspect 성공", insp.isSuccess, msgOf(insp))
        val info = insp.getOrNull()!!
        check("본 세이브 포함", info.hasSave)
        check("항목 수 = state + feat_* 6개 = 7", info.keyCount == 7, "실제 ${info.keyCount}")
        check("경고 없음", info.warning.isEmpty(), info.warning)
        check("날짜 라벨", info.dateLabel.contains("년"), info.dateLabel)

        // ---- 데이터 초기화 (설정 › 처음부터 다시 시작과 동일 경로) ----
        SaveManager.clear(ctx)
        val p = ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE).edit()
        p.remove("feat_album_v1"); p.remove("feat_ach_count_v1"); p.remove("feat_story_seen_v1")
        p.remove("feat_luck_bonus_v1"); p.remove("feat_last_play_v1"); p.remove("feat_tags_v1")
        p.commit()
        val wiped = freshGame(ctx)
        check("초기화 확인 — 진행 없음", !wiped.state.started)

        // ---- 복원 ----
        val game2 = freshGame(ctx)
        val imp = Backup.import(ctx, code, game2)
        check("불러오기 성공", imp.isSuccess, msgOf(imp))
        summary = imp.getOrNull() ?: ""
        println("     요약: ${summary.replace('\n', ' ')}")

        val st = game2.state
        check("시작 상태로 복원 (reloadState 반영)", st.started)
        check("돈 123456", st.money == 123456, "${st.money}")
        check("배고픔 77.5", kotlin.math.abs(st.hunger - 77.5f) < 0.001f, "${st.hunger}")
        check("행운 33.25", kotlin.math.abs(st.luck - 33.25f) < 0.001f, "${st.luck}")
        check("지역 busan", st.region == "busan", st.region)
        check("방문 지역 포함", "busan" in st.visited)
        check("촬영 42장", st.photos == 42, "${st.photos}")
        check("레벨 7", st.level == 7, "${st.level}")
        check("날짜 12일 · 시각 19.25", st.day == 12 && kotlin.math.abs(st.worldTime - 19.25f) < 0.001f,
            "${st.day}/${st.worldTime}")
        check("피자 2판", st.pizzas[3] == 2, "${st.pizzas[3]}")
        check("도감 촬영 횟수", st.birdCounts["sparrow"] == 5, "${st.birdCounts["sparrow"]}")
        check("도감 최고 별점", st.bestStars["sparrow"] == 4, "${st.bestStars["sparrow"]}")
        check("카메라 장비 유지", CameraGear.STARTER in st.ownedGear)
        check("효과음 꺼짐 복원", !st.sfxOn)
        check("흔들림 3단계 복원", st.camShake == 3, "${st.camShake}")

        // feat_* 확장 저장소
        val prefs = ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
        @Suppress("UNCHECKED_CAST")
        val all = prefs.all as Map<String, Any?>
        val album = all["feat_album_v1"] as? String
        check("feat_album_v1 (JSON 문자열) 복원", album != null && album.contains("\"stars\":5") && album.length > 20,
            "길이 ${album?.length}")
        check("feat_ach_count_v1 = 13", all["feat_ach_count_v1"] == 13, "${all["feat_ach_count_v1"]}")
        check("feat_story_seen_v1 = true", all["feat_story_seen_v1"] == true, "${all["feat_story_seen_v1"]}")
        check("feat_luck_bonus_v1 = 1.25", kotlin.math.abs((all["feat_luck_bonus_v1"] as? Float ?: -1f) - 1.25f) < 0.0001f,
            "${all["feat_luck_bonus_v1"]}")
        check("feat_last_play_v1 (Long)", all["feat_last_play_v1"] == 1_700_000_000_000L, "${all["feat_last_play_v1"]}")
        @Suppress("UNCHECKED_CAST")
        val tags = all["feat_tags_v1"] as? Set<String>
        check("feat_tags_v1 (문자열 집합)", tags != null && "두루미" in tags && "올빼미" in tags, "$tags")
        check("백업 대상 아닌 키는 건드리지 않음(그대로 남음)", all["not_collected_v1"] == "이 키는 백업되면 안 돼요",
            "${all["not_collected_v1"]}")
    }

    section("3. 손상 코드는 거부하고 기존 세이브를 지킨다")
    run {
        val body = code.split('.')[3]
        // 본문 한 글자 변경 → 체크섬 불일치
        val flip = if (body[100] == 'A') 'B' else 'A'
        val tampered = code.replaceRange(code.indexOf(body) + 100, code.indexOf(body) + 101, flip.toString())
        val r = Backup.inspect(tampered)
        check("체크섬 불일치로 거부", r.isFailure, msgOf(r))
        check("사유 = 손상", msgOf(r).contains("손상"), msgOf(r))
        check("import 도 거부", Backup.import(ctx, tampered, null).isFailure)

        // 앞 글자 하나 잘림(코드가 깨짐) → 그래도 기존 세이브는 그대로
        val cut = code.drop(3)
        check("잘린 코드 거부", Backup.inspect(cut).isFailure)

        val prefs = ctx.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
        check("기존 세이브 무손상", prefs.getString(SaveManager.saveKey(), null) != null)
        check("복원된 feat_* 무손상", prefs.all.containsKey("feat_album_v1"))
    }

    section("4. 다른 형식 / 빈 코드 / 옛 버전 거부")
    run {
        check("PBSAVE0 옛 포맷 → 지원하지 않는 형식",
            msgOf(Backup.inspect("PBSAVE0.20260927.deadbeef.QUJD")).contains("지원하지 않는 형식"))
        check("PBSAVE9 미래 포맷 → 지원하지 않는 형식",
            msgOf(Backup.inspect("PBSAVE9.20260927.deadbeef.QUJD")).contains("지원하지 않는 형식"))
        check("아예 다른 텍스트 → 이 게임 코드가 아님",
            msgOf(Backup.inspect("그냥 메모장에 적어둔 글")).contains("이 게임의 백업 코드"))
        check("빈 문자열 → 비어 있음", msgOf(Backup.inspect("")).contains("비어"))
        check("공백만 → 비어 있음", msgOf(Backup.inspect("  \n \t ")).contains("비어"))
        check("짧은 코드 → 손상(구획 개수 안내)", msgOf(Backup.inspect("PBSAVE1.2")).contains("구획이 2개"), msgOf(Backup.inspect("PBSAVE1.2")))
        val shortParts = msgOf(Backup.inspect("PBSAVE1.20260927.deadbeef"))
        check("구획 부족 → 손상 + 구획 개수 안내", shortParts.contains("손상") && shortParts.contains("3개"), shortParts)
        check("날짜가 숫자가 아님 → 손상", msgOf(Backup.inspect("PBSAVE1.2026ab27.deadbeef.QUJD")).contains("손상"))
        check("빈 백업 코드(본문은 정상, 항목 0개) → 빈 코드",
            msgOf(Backup.inspect(emptyBackupCode())).contains("빈 백업"))
    }

    section("5. 버전 비교 / 청크 규칙")
    run {
        check("0.4.3 > 0.4.2", Backup.isNewerVersion("0.4.3-beta01", "0.4.2-beta01"))
        check("0.5.0 > 0.4.9", Backup.isNewerVersion("0.5.0", "0.4.9"))
        check("1.0.0 > 0.4.2", Backup.isNewerVersion("1.0.0", "0.4.2-beta01"))
        check("0.4.2 = 0.4.2 → 신버전 아님", !Backup.isNewerVersion("0.4.2-beta01", "0.4.2-beta01"))
        check("0.4.1 < 0.4.2 → 신버전 아님", !Backup.isNewerVersion("0.4.1", "0.4.2-beta01"))

        val c = "PBSAVE1.20260927.00000000." + "X".repeat(120)   // 머리글 포함 전체 146글자
        val lines = Backup.formatCode(c).split('\n')
        check("146글자 코드 → 48/48/48/2", lines.map { it.length } == listOf(48, 48, 48, 2),
            "${lines.map { it.length }}")
        check("한 줄 최대 청크 길이", lines.all { it.length <= Backup.CHUNK_LINE })
        check("되돌리면 원본", Backup.normalize(Backup.formatCode(c)) == c)
        check("공백 섞인 붙여넣기도 정리",
            Backup.normalize(" PBSAVE1.\n2026 0927.\t00000000.\r\nXXXX ") == "PBSAVE1.20260927.00000000.XXXX")
    }

    section("6. 큰 세이브(앨범 수백 장)도 코드 길이 감당")
    run {
        val ctx2 = FakeContext()
        val g2 = seedGame(ctx2)
        val big = StringBuilder("{\"photos\":[")
        for (i in 1..800) {
            if (i > 1) big.append(',')
            big.append("{\"id\":$i,\"stars\":${(i % 5) + 1},\"bird\":\"bird_$i\",\"region\":\"seoul\",\"day\":$i}")
        }
        big.append("]}")
        ctx2.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE).edit()
            .putString("feat_album_v1", big.toString()).commit()
        val r = Backup.export(ctx2, g2.state)
        check("내보내기 성공", r.isSuccess, msgOf(r))
        val c = r.getOrNull() ?: ""
        println("     앨범 800장(원본 ${big.length / 1024}KB) → 코드 ${Backup.describe(c)}")
        check("압축 후 60KB 이하", c.length < 60_000, "${c.length}글자")
        check("되돌아오는 항목 수 7개", (Backup.inspect(c).getOrNull()?.keyCount ?: -1) == 7)

        SaveManager.clear(ctx2)
        val g3 = freshGame(ctx2)
        val imp = Backup.import(ctx2, c, g3)
        check("큰 코드 복원 성공", imp.isSuccess, msgOf(imp))
        val restored = ctx2.getSharedPreferences(SaveManager.prefsName(), Context.MODE_PRIVATE)
            .getString("feat_album_v1", null)
        check("앨범 내용 동일", restored == big.toString(), "길이 ${restored?.length}")
    }

    println("\n=== 결과: 성공 $passed · 실패 $failed ===")
    System.out.flush()
    if (failed > 0) exitProcess(1)
}

/** 항목이 하나도 없는 정상 포맷 코드 (빈 백업 거부 확인용) */
private fun emptyBackupCode(): String {
    val ctx = FakeContext()
    // state 없이 feat_ 하나만 넣고 export 한 뒤, 그 feat_ 를 지운 문서를 다시 만든다.
    // 간단하게: Backup 내부 포맷과 같은 "keys가 빈 문서"를 직접 만든다.
    val doc = org.json.JSONObject()
        .put("meta", org.json.JSONObject().put("created", System.currentTimeMillis())
            .put("app", Backup.APP_VERSION).put("keys", 0).put("hasSave", false))
        .put("keys", org.json.JSONObject())
    val raw = doc.toString().toByteArray(Charsets.UTF_8)
    val def = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
    def.setInput(raw); def.finish()
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (!def.finished()) {
        val n = def.deflate(buf)
        if (n <= 0) break
        out.write(buf, 0, n)
    }
    def.end()
    val zipped = out.toByteArray()
    val sum = java.util.zip.Adler32().apply { update(zipped) }.value
    val payload = android.util.Base64.encodeToString(
        zipped, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
    )
    val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    return "PBSAVE1.$stamp.${String.format(java.util.Locale.US, "%08x", sum)}.$payload"
}
