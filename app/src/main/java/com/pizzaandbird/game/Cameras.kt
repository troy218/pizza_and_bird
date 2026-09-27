package com.pizzaandbird.game

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 카메라 장비 시스템 (v0.4)
 *
 * 현실의 카메라 특성을 그대로 게임 수치로 옮긴다.
 *
 *  - **일체형(컴팩트)**: 렌즈를 바꿀 수 없다. 대신 가볍고 값이 싸며, 보급/중급/하이엔드로 나뉜다.
 *  - **렌즈교환식(ILC)**: 바디 + 렌즈(+텔레컨버터)를 따로 사서 조합한다. 마운트가 맞아야 한다.
 *
 * 핵심 규칙(현실 반영)
 *  - 센서가 클수록 화질·고감도에 유리하지만 무겁고 비싸다.
 *  - 촬영 반경은 **35mm 환산 초점거리**로 정해진다. (크롭 바디 = 환산 초점거리 ↑)
 *  - 초망원일수록 최단 촬영 거리가 길어져 **너무 가까우면 화각에 안 들어온다.**
 *  - 조리개가 밝을수록(F 숫자가 작을수록) 어두운 곳에서 유리하지만 무겁다.
 *  - 손떨림 보정(IBIS/OS)이 약하면 움직이면서 찍을 때 흔들린다.
 *  - AF 성능이 낮으면 움직이는 새를 놓친다. 연사가 빠르면 한 번 더 기회가 있다.
 *  - 텔레컨버터는 초점거리를 늘리는 대신 조리개가 어두워지고 AF/화질이 떨어진다.
 *  - 무거운 장비는 이동 속도를 떨어뜨리고 배가 빨리 고프다.
 */

// ---------------------------------------------------------------------------
// 센서 / 마운트
// ---------------------------------------------------------------------------

/** 센서 규격 — 크기가 클수록 화질·고감도에 유리하지만 시스템이 커진다. */
enum class Sensor(
    val label: String,
    val crop: Float,        // 35mm 환산 계수
    val iqBase: Float,      // 기본 화질 점수
    val lowBase: Float,     // 고감도(저조도) 점수
    val bulk: Float         // 시스템 부피 계수 (연출용)
) {
    T23("1/2.3\"", 5.6f, 1.1f, 1.0f, 0.5f),
    T17("1/1.7\"", 4.6f, 1.8f, 1.7f, 0.6f),
    ONE("1\"", 2.7f, 2.8f, 2.7f, 0.8f),
    M43("마이크로 포서드", 2.0f, 3.5f, 3.3f, 1.0f),
    APSC("APS-C", 1.5f, 4.3f, 4.2f, 1.2f),
    FF("풀프레임", 1.0f, 5.6f, 5.6f, 1.6f),
    MF("중형 44×33", 0.79f, 6.8f, 6.0f, 2.2f)
}

/** 마운트 규격 — 바디와 렌즈가 같은 마운트여야 결합할 수 있다. */
object Mounts {
    const val E = "HB-E"      // 미러리스 공용 마운트 (APS-C / 풀프레임)
    const val F = "HB-F"      // DSLR 마운트 (어댑터로 E 바디에 사용 가능)
    const val M43 = "M43"     // 마이크로 포서드
    const val MF = "MF-G"     // 중형 마운트

    fun label(id: String): String = when (id) {
        E -> "HB-E 미러리스 마운트"
        F -> "HB-F DSLR 마운트"
        M43 -> "마이크로 포서드 마운트"
        MF -> "MF-G 중형 마운트"
        else -> id
    }
}

/** 렌즈 이미지 서클(커버리지) */
object Coverage {
    const val APSC = "apsc"
    const val FF = "ff"
    const val M43 = "m43"
    const val MF = "mf"
}

// ---------------------------------------------------------------------------
// 겉모습 (아이콘 / 인게임 스프라이트에 쓰이는 형태 정보)
// ---------------------------------------------------------------------------

/**
 * 장비의 생김새. Assets가 이 값으로 아이콘과 인게임 카메라 스프라이트를 그린다.
 * data class 라서 그대로 캐시 키로 쓸 수 있다.
 */
data class CamLook(
    val style: Int,           // 0 컴팩트 / 1 브릿지 / 2 미러리스 / 3 DSLR / 4 플래그십 / 5 중형
    val bodyCol: Int,
    val bodyDark: Int,
    val accent: Int,
    val barrelLen: Float,     // 경통 길이 (0~10)
    val barrelDia: Float,     // 경통 지름 (2~8)
    val barrelCol: Int,
    val hood: Boolean,        // 렌즈 후드
    val evf: Boolean,         // 돌출된 뷰파인더(펜타프리즘)
    val flash: Boolean        // 팝업 플래시
)

private val COL_BLACK = 0xFF3A3A44.toInt()
private val COL_BLACK_D = 0xFF23232B.toInt()
private val COL_GRAPH = 0xFF4E4E5C.toInt()
private val COL_GRAPH_D = 0xFF32323C.toInt()
private val COL_SILVER = 0xFFB9BEC6.toInt()
private val COL_SILVER_D = 0xFF8A8F98.toInt()
private val COL_LEATHER = 0xFF6B5A48.toInt()
private val COL_LEATHER_D = 0xFF4A3C30.toInt()
private val COL_WHITE_LENS = 0xFFE8E4D8.toInt()
private val COL_DARK_LENS = 0xFF2E2E38.toInt()
private val COL_GRAY_LENS = 0xFF5A5A66.toInt()
private val ACC_RED = 0xFFE2574C.toInt()
private val ACC_GOLD = 0xFFF2D06B.toInt()
private val ACC_BLUE = 0xFF6FA8DC.toInt()
private val ACC_GREEN = 0xFF6FBA6B.toInt()

// ---------------------------------------------------------------------------
// 장비 정의
// ---------------------------------------------------------------------------

enum class GearKind(val label: String, val emoji: String) {
    COMPACT("컴팩트(일체형)", "📷"),
    BODY("바디", "🔲"),
    LENS("렌즈", "🔭"),
    TELECONV("텔레컨버터", "➕"),
    ACCESSORY("액세서리", "🎒")
}

/** 제품 등급 — 상점 진열과 설명에 쓰인다. */
enum class GearGrade(val label: String, val color: Int) {
    ENTRY("보급", 0xFF7A8A93.toInt()),
    MID("중급", 0xFF5E934F.toInt()),
    HIGH("하이엔드", 0xFF3F6FB0.toInt()),
    PRO("프로/플래그십", 0xFFB65342.toInt())
}

/** 모든 카메라 장비의 공통 정보 */
sealed class CamGear(
    val id: String,
    val brand: String,
    val name: String,
    val kind: GearKind,
    val grade: GearGrade,
    val price: Int,
    val weightG: Int,
    val desc: String,
    val luck: Int = 0
) {
    val fullName: String get() = "$brand $name"
}

/** 일체형 컴팩트 카메라 (렌즈 교환 불가) */
class CompactCam(
    id: String, brand: String, name: String, grade: GearGrade, price: Int, weightG: Int,
    desc: String,
    val sensor: Sensor,
    val wideMm: Int,          // 35mm 환산 광각단
    val teleMm: Int,          // 35mm 환산 망원단
    val apWide: Float,        // 광각단 개방 조리개
    val apTele: Float,        // 망원단 개방 조리개
    val mp: Float,            // 유효 화소(MP)
    val afScore: Float,       // AF 성능 0~10
    val burst: Float,         // 연사(fps)
    val stab: Float,          // 손떨림 보정 0~4
    val weatherProof: Boolean,
    val sharp: Float,         // 렌즈 해상력 0~6
    val look: CamLook,
    luck: Int = 0
) : CamGear(id, brand, name, GearKind.COMPACT, grade, price, weightG, desc, luck) {
    val zoomX: Float get() = teleMm.toFloat() / wideMm.toFloat()
    val specLine: String
        get() = "${sensor.label} · ${mp.fmt1()}MP · ${wideMm}-${teleMm}mm 환산 · F${apWide.fmt1()}-${apTele.fmt1()}"
}

/** 렌즈교환식 바디 */
class CamBody(
    id: String, brand: String, name: String, grade: GearGrade, price: Int, weightG: Int,
    desc: String,
    val sensor: Sensor,
    val mount: String,
    val mp: Float,
    val afScore: Float,
    val burst: Float,
    val ibis: Float,          // 바디 손떨림 보정 0~4
    val weatherProof: Boolean,
    val birdAf: Boolean,      // 조류/동물 눈 인식 AF
    val look: CamLook,
    luck: Int = 0
) : CamGear(id, brand, name, GearKind.BODY, grade, price, weightG, desc, luck) {
    val specLine: String
        get() = "${sensor.label} · ${mp.fmt1()}MP · ${burst.fmt1()}fps · ${Mounts.label(mount)}"
}

/** 교환 렌즈 */
class CamLens(
    id: String, brand: String, name: String, grade: GearGrade, price: Int, weightG: Int,
    desc: String,
    val mounts: Set<String>,
    val coverage: String,     // Coverage.*
    val wideMm: Int,          // 실초점거리 (환산 아님)
    val teleMm: Int,
    val apWide: Float,
    val apTele: Float,
    val os: Float,            // 렌즈 손떨림 보정 0~4
    val sharp: Float,         // 해상력 0~6
    val afMod: Float,         // AF 가감 (-2 ~ +2)
    val tcOk: Boolean,        // 텔레컨버터 장착 가능
    val macro: Boolean,
    val barrelLen: Float,
    val barrelDia: Float,
    val barrelCol: Int,
    val hood: Boolean,
    luck: Int = 0
) : CamGear(id, brand, name, GearKind.LENS, grade, price, weightG, desc, luck) {
    val prime: Boolean get() = wideMm == teleMm
    val focalLabel: String
        get() = if (prime) "${wideMm}mm" else "${wideMm}-${teleMm}mm"
    val apLabel: String
        get() = if (apWide == apTele) "F${apWide.fmt1()}" else "F${apWide.fmt1()}-${apTele.fmt1()}"
    val specLine: String
        get() = "$focalLabel $apLabel · ${coverageLabel()} · ${weightG}g"

    fun coverageLabel(): String = when (coverage) {
        Coverage.APSC -> "APS-C 전용"
        Coverage.M43 -> "마이크로 포서드"
        Coverage.MF -> "중형"
        else -> "풀프레임 대응"
    }
}

/** 텔레컨버터 — 초점거리를 늘리는 대신 조리개·AF·화질을 깎는다. */
class TeleConv(
    id: String, brand: String, name: String, price: Int, weightG: Int, desc: String,
    val mounts: Set<String>,
    val mul: Float,           // 배율 (1.4 / 2.0)
    val afPenalty: Float,
    val sharpPenalty: Float
) : CamGear(id, brand, name, GearKind.TELECONV, GearGrade.MID, price, weightG, desc) {
    val stopsLost: Float get() = if (mul >= 1.9f) 2f else 1f
    val specLine: String get() = "×${mul.fmt1()} · 조리개 -${stopsLost.toInt()}스톱 · AF ↓"
}

/** 액세서리 — 사면 계속 효과가 적용된다. */
class CamAccessory(
    id: String, brand: String, name: String, price: Int, weightG: Int, desc: String,
    val effect: String,
    luck: Int = 0
) : CamGear(id, brand, name, GearKind.ACCESSORY, GearGrade.ENTRY, price, weightG, desc, luck)

/** 소수점 한 자리 (1.0 -> "1", 2.8 -> "2.8") */
fun Float.fmt1(): String {
    val r = (this * 10f).roundToInt()
    return if (r % 10 == 0) (r / 10).toString() else "${r / 10}.${r % 10}"
}

// ---------------------------------------------------------------------------
// 제품군
// ---------------------------------------------------------------------------

object CameraGear {

    // 액세서리 id (효과 판정용)
    const val ACC_ADAPTER = "acc_adapter"
    const val ACC_TRIPOD = "acc_tripod"
    const val ACC_BLIND = "acc_blind"
    const val ACC_RAINCOVER = "acc_raincover"
    const val ACC_STRAP = "acc_strap"

    /** 새 게임에서 들고 시작하는 컴팩트 카메라 */
    const val STARTER = "c_start"

    // ------------------------------------------------------------------
    // 컴팩트 (일체형) — 보급 / 중급 / 하이엔드
    // ------------------------------------------------------------------
    val COMPACTS: List<CompactCam> = listOf(
        CompactCam(
            "c_start", "하늘광학", "픽스 P1", GearGrade.ENTRY, 0, 145,
            "물려받은 첫 카메라. 3배 줌에 화질도 수수하지만 가볍고 튼튼하다.",
            Sensor.T23, 28, 84, 3.5f, 6.5f, 16f, 3.0f, 4f, 1.0f, false, 1.2f,
            CamLook(0, COL_SILVER, COL_SILVER_D, ACC_BLUE, 1.6f, 3.0f, COL_DARK_LENS, false, false, true)
        ),
        CompactCam(
            "c_pocket", "한빛이미징", "포켓 Z8", GearGrade.ENTRY, 180000, 183,
            "주머니에 쏙 들어가는 8배 줌. 여행용으로 딱이지만 망원단이 어둡다.",
            Sensor.T23, 24, 200, 3.3f, 6.9f, 20f, 4.0f, 6f, 2.5f, false, 1.5f,
            CamLook(0, COL_BLACK, COL_BLACK_D, ACC_BLUE, 2.0f, 3.2f, COL_DARK_LENS, false, false, true)
        ),
        CompactCam(
            "c_zoom20", "하늘광학", "줌샷 Z20", GearGrade.ENTRY, 90000, 300,
            "1/2.3\" 센서로 480mm까지 당기는 보급 슈퍼줌. 화질은 양보하고 사거리를 얻었다.",
            Sensor.T23, 24, 480, 3.3f, 6.4f, 20f, 3.5f, 5f, 2.5f, false, 1.3f,
            CamLook(0, COL_BLACK, COL_BLACK_D, ACC_RED, 2.6f, 3.4f, COL_DARK_LENS, false, false, true)
        ),
        CompactCam(
            "c_tough", "참새정밀", "아쿠아 W3", GearGrade.ENTRY, 150000, 250,
            "수심 15m 방수·내충격. 비바람 치는 갯벌에서도 겁 없이 꺼낼 수 있다.",
            Sensor.T23, 25, 100, 2.0f, 4.9f, 12f, 4.0f, 10f, 2.0f, true, 1.4f,
            CamLook(0, 0xFF3E8FA8.toInt(), 0xFF2C6B80.toInt(), ACC_GOLD, 1.4f, 3.0f, COL_DARK_LENS, false, false, true)
        ),
        CompactCam(
            "c_retro", "까치광학", "레트로 F2", GearGrade.ENTRY, 320000, 290,
            "필름 카메라를 닮은 감성 컴팩트. 찍는 맛이 좋아 기분(행운)이 올라간다.",
            Sensor.T17, 28, 112, 2.0f, 4.9f, 12f, 3.5f, 3f, 1.0f, false, 2.2f,
            CamLook(0, COL_LEATHER, COL_LEATHER_D, COL_SILVER, 1.8f, 3.2f, COL_DARK_LENS, false, true, false),
            luck = 4
        ),
        CompactCam(
            "c_bridge60", "솔개옵틱", "브리지 B60", GearGrade.MID, 280000, 650,
            "60배 줌 브릿지. 환산 1200mm의 압도적 사거리지만 작은 센서라 어두우면 힘들다.",
            Sensor.T23, 20, 1200, 2.8f, 5.9f, 16f, 4.0f, 7f, 3.5f, false, 1.6f,
            CamLook(1, COL_BLACK, COL_BLACK_D, ACC_RED, 5.0f, 4.6f, COL_DARK_LENS, true, true, true)
        ),
        CompactCam(
            "c_one_fast", "하늘광학", "프로 X1", GearGrade.MID, 420000, 300,
            "1인치 센서 + F1.8 밝은 렌즈. 망원은 짧아도 어두운 숲과 새벽에 강하다.",
            Sensor.ONE, 24, 70, 1.8f, 2.8f, 20f, 6.0f, 24f, 2.0f, false, 3.4f,
            CamLook(0, COL_BLACK, COL_BLACK_D, ACC_GOLD, 2.2f, 3.6f, COL_DARK_LENS, false, true, true)
        ),
        CompactCam(
            "c_travel", "한빛이미징", "트래블 T20", GearGrade.MID, 560000, 302,
            "1인치에 환산 200mm 줌을 담은 만능 여행기. 무게 대비 성능이 훌륭하다.",
            Sensor.ONE, 24, 200, 2.8f, 4.5f, 20f, 6.0f, 20f, 2.5f, false, 3.0f,
            CamLook(0, COL_BLACK, COL_BLACK_D, ACC_BLUE, 2.8f, 3.6f, COL_DARK_LENS, false, true, true)
        ),
        CompactCam(
            "c_apsc_prime", "까치광학", "스트리트 R3", GearGrade.HIGH, 700000, 257,
            "APS-C 센서에 28mm 단렌즈. 새보다 풍경·근접용이지만 화질만큼은 진심이다.",
            Sensor.APSC, 28, 28, 2.8f, 2.8f, 24f, 5.0f, 4f, 1.5f, false, 5.0f,
            CamLook(0, COL_BLACK, COL_BLACK_D, COL_SILVER, 1.2f, 3.4f, COL_DARK_LENS, false, false, false),
            luck = 2
        ),
        CompactCam(
            "c_bridge_pro", "솔개옵틱", "브리지 프로 B24", GearGrade.HIGH, 1050000, 1095,
            "1인치 센서에 환산 24-600mm F2.4-4 고정 렌즈. 렌즈 교환 없이 다 되는 만능 브릿지.",
            Sensor.ONE, 24, 600, 2.4f, 4.0f, 20f, 7.0f, 24f, 3.5f, true, 4.2f,
            CamLook(1, COL_BLACK, COL_BLACK_D, ACC_GOLD, 6.0f, 5.2f, COL_DARK_LENS, true, true, true)
        ),
        CompactCam(
            "c_ff", "루멘", "FX 컴팩트", GearGrade.HIGH, 1800000, 507,
            "주머니에 들어가는 풀프레임. 35mm F2 단렌즈의 화질은 최상급, 사거리는 최하급.",
            Sensor.FF, 35, 35, 2.0f, 2.0f, 42f, 5.5f, 5f, 0.0f, false, 5.6f,
            CamLook(0, COL_GRAPH, COL_GRAPH_D, COL_SILVER, 1.6f, 3.8f, COL_DARK_LENS, true, true, false),
            luck = 3
        )
    )

    // ------------------------------------------------------------------
    // 바디 (렌즈교환식)
    // ------------------------------------------------------------------
    val BODIES: List<CamBody> = listOf(
        CamBody(
            "b_dslr_entry", "한빛이미징", "D3000", GearGrade.ENTRY, 200000, 465,
            "광학 뷰파인더의 입문 DSLR. 느리지만 배터리가 오래가고 손에 착 붙는다.",
            Sensor.APSC, Mounts.F, 24f, 4.5f, 5f, 0f, false, false,
            CamLook(3, COL_BLACK, COL_BLACK_D, ACC_RED, 0f, 0f, COL_DARK_LENS, false, true, true)
        ),
        CamBody(
            "b_apsc_entry", "하늘광학", "M10", GearGrade.ENTRY, 260000, 375,
            "가볍고 다루기 쉬운 APS-C 미러리스 입문기. 첫 렌즈교환식으로 가장 무난하다.",
            Sensor.APSC, Mounts.E, 24f, 5.0f, 8f, 0f, false, false,
            CamLook(2, COL_BLACK, COL_BLACK_D, ACC_BLUE, 0f, 0f, COL_DARK_LENS, false, false, true)
        ),
        CamBody(
            "b_apsc_mid", "하늘광학", "M50", GearGrade.MID, 480000, 507,
            "동물 눈 인식 AF와 바디 손떨림 보정을 갖춘 APS-C 중급기. 크롭 1.5배가 망원에 유리하다.",
            Sensor.APSC, Mounts.E, 26f, 7.0f, 11f, 2.5f, true, true,
            CamLook(2, COL_BLACK, COL_BLACK_D, ACC_GOLD, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_m43", "참새정밀", "G9 마이크로", GearGrade.MID, 560000, 658,
            "2배 크롭이라 같은 렌즈로 두 배 당긴다. 강력한 IBIS와 20연사, 가벼운 시스템이 강점.",
            Sensor.M43, Mounts.M43, 20f, 6.5f, 20f, 4.0f, true, true,
            CamLook(2, COL_BLACK, COL_BLACK_D, ACC_GREEN, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_dslr_mid", "한빛이미징", "D700", GearGrade.MID, 620000, 700,
            "32MP APS-C DSLR. 튼튼한 방진방적 바디에 10연사, 망원 화각도 1.5배로 벌어준다.",
            Sensor.APSC, Mounts.F, 32f, 6.5f, 10f, 0f, true, false,
            CamLook(3, COL_BLACK, COL_BLACK_D, ACC_GOLD, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_ff_entry", "루멘", "Z6", GearGrade.MID, 850000, 675,
            "입문 풀프레임 미러리스. 고감도와 계조가 넉넉해 새벽·해질녘에 강하다.",
            Sensor.FF, Mounts.E, 24f, 7.0f, 10f, 3.0f, true, false,
            CamLook(2, COL_GRAPH, COL_GRAPH_D, COL_SILVER, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_ff_hires", "루멘", "Z7R", GearGrade.HIGH, 1400000, 737,
            "61MP 고화소 풀프레임. 크롭해도 디테일이 남아 작은 새를 크게 뽑을 수 있다.",
            Sensor.FF, Mounts.E, 61f, 7.0f, 10f, 3.0f, true, true,
            CamLook(2, COL_GRAPH, COL_GRAPH_D, ACC_GOLD, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_ff_bird", "루멘", "Z9 버드", GearGrade.HIGH, 1900000, 900,
            "조류 인식 AF 탑재. 30연사로 날아오르는 순간을 붙잡는 탐조 전용기.",
            Sensor.FF, Mounts.E, 24f, 9.5f, 30f, 3.5f, true, true,
            CamLook(4, COL_BLACK, COL_BLACK_D, ACC_GOLD, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_flagship", "오리온", "F1 플래그십", GearGrade.PRO, 2600000, 1160,
            "세로그립 일체형 플래그십. 120연사와 절대 놓치지 않는 AF, 그리고 묵직한 무게.",
            Sensor.FF, Mounts.E, 24f, 10f, 120f, 3.5f, true, true,
            CamLook(4, COL_BLACK, COL_BLACK_D, ACC_RED, 0f, 0f, COL_DARK_LENS, false, true, false)
        ),
        CamBody(
            "b_mf", "오리온", "MF100 중형", GearGrade.PRO, 3200000, 1400,
            "102MP 중형 센서. 화질은 다른 차원이지만 AF가 느리고 망원 화각이 0.79배로 좁아진다.",
            Sensor.MF, Mounts.MF, 102f, 3.5f, 5f, 2.0f, true, false,
            CamLook(5, COL_GRAPH, COL_GRAPH_D, COL_SILVER, 0f, 0f, COL_DARK_LENS, false, true, false),
            luck = 2
        )
    )

    // ------------------------------------------------------------------
    // 렌즈
    // ------------------------------------------------------------------
    val LENSES: List<CamLens> = listOf(
        CamLens(
            "l_kit1855", "하늘광학", "18-55mm F3.5-5.6 번들", GearGrade.ENTRY, 40000, 205,
            "바디와 함께 주는 기본 줌. 가볍고 무난하지만 새를 찍기엔 너무 짧다.",
            setOf(Mounts.E, Mounts.F), Coverage.APSC, 18, 55, 3.5f, 5.6f,
            2.0f, 2.0f, 0f, false, false, 2.0f, 3.2f, COL_DARK_LENS, false
        ),
        CamLens(
            "l_55210", "하늘광학", "55-210mm F4.5-6.3", GearGrade.ENTRY, 90000, 345,
            "APS-C 전용 보급 망원. 가볍고 싸서 첫 망원으로 좋다.",
            setOf(Mounts.E, Mounts.F), Coverage.APSC, 55, 210, 4.5f, 6.3f,
            2.5f, 2.4f, -0.3f, false, false, 3.4f, 3.4f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_35f18", "까치광학", "35mm F1.8 팬케이크", GearGrade.ENTRY, 70000, 145,
            "주머니에 넣고 다니는 밝은 단렌즈. 어두운 골목과 실내에 강하다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 35, 35, 1.8f, 1.8f,
            0f, 4.0f, 0.3f, false, false, 1.4f, 3.0f, COL_DARK_LENS, false,
            luck = 1
        ),
        CamLens(
            "l_1635f4", "루멘", "16-35mm F4 광각 줌", GearGrade.MID, 190000, 540,
            "풍경과 서식지를 넓게 담는 광각. 새 촬영에는 거의 쓸모가 없다.",
            setOf(Mounts.E), Coverage.FF, 16, 35, 4.0f, 4.0f,
            1.5f, 4.2f, 0.2f, false, false, 2.2f, 3.8f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_2470f28", "루멘", "24-70mm F2.8 표준 줌", GearGrade.MID, 330000, 805,
            "가장 쓸모 많은 표준 줌. 밝고 선명하지만 망원은 부족하다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 24, 70, 2.8f, 2.8f,
            2.0f, 4.6f, 0.4f, false, false, 2.8f, 4.2f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_90macro", "까치광학", "90mm F2.8 매크로", GearGrade.MID, 170000, 600,
            "코앞까지 다가가 찍는 접사 렌즈. 최단 촬영 거리가 아주 짧다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 90, 90, 2.8f, 2.8f,
            2.0f, 5.0f, -0.5f, false, true, 3.0f, 3.4f, COL_DARK_LENS, false
        ),
        CamLens(
            "l_70300", "솔개옵틱", "70-300mm F4-5.6", GearGrade.ENTRY, 140000, 680,
            "첫 망원 줌의 정석. 가격 대비 사거리가 훌륭하다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 70, 300, 4.0f, 5.6f,
            2.5f, 3.2f, -0.2f, false, false, 4.2f, 3.8f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_100400", "솔개옵틱", "100-400mm F4.5-5.6", GearGrade.MID, 470000, 1135,
            "탐조인의 실전 줌. 화질과 휴대성의 균형이 좋고 텔레컨버터도 물린다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 100, 400, 4.5f, 5.6f,
            3.0f, 4.4f, 0.2f, true, false, 5.6f, 4.6f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_150600c", "참새정밀", "150-600mm F5-6.3 컨템", GearGrade.MID, 300000, 1930,
            "가성비 초망원. 무겁고 AF가 굼뜨지만 600mm를 이 값에 살 수 있다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 150, 600, 5.0f, 6.3f,
            2.5f, 3.4f, -1.5f, true, false, 7.0f, 5.0f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_200600", "루멘", "200-600mm F5.6-6.3 G", GearGrade.HIGH, 620000, 2115,
            "인너 줌 방식의 초망원. 조용하고 빠른 AF로 새 사진의 표준이 된 렌즈.",
            setOf(Mounts.E), Coverage.FF, 200, 600, 5.6f, 6.3f,
            3.0f, 4.8f, 0.3f, true, false, 7.6f, 5.2f, COL_GRAY_LENS, true
        ),
        CamLens(
            "l_300f28", "오리온", "300mm F2.8 단렌즈", GearGrade.HIGH, 1300000, 1470,
            "밝은 대구경 망원 단렌즈. 어두운 숲에서도 셔터가 살고 텔레컨버터 궁합이 최고다.",
            setOf(Mounts.E, Mounts.F), Coverage.FF, 300, 300, 2.8f, 2.8f,
            3.0f, 5.4f, 1.0f, true, false, 6.4f, 6.4f, COL_WHITE_LENS, true
        ),
        CamLens(
            "l_500f4", "오리온", "500mm F4 단렌즈", GearGrade.PRO, 2100000, 3050,
            "프로 초망원. 압도적인 해상력과 AF, 그리고 팔이 떨어질 듯한 무게.",
            setOf(Mounts.E), Coverage.FF, 500, 500, 4.0f, 4.0f,
            3.5f, 5.8f, 1.5f, true, false, 8.4f, 7.0f, COL_WHITE_LENS, true
        ),
        CamLens(
            "l_600f4", "오리온", "600mm F4 단렌즈", GearGrade.PRO, 2900000, 3810,
            "탐조 장비의 끝판왕. 삼각대 없이는 하루를 버티기 어렵다.",
            setOf(Mounts.E), Coverage.FF, 600, 600, 4.0f, 4.0f,
            3.5f, 6.0f, 1.5f, true, false, 9.4f, 7.4f, COL_WHITE_LENS, true
        ),
        CamLens(
            "l_800f63", "루멘", "800mm F6.3 단렌즈", GearGrade.HIGH, 1500000, 2475,
            "가벼운 초망원 단렌즈. 어둡지만 손에 들고 800mm를 쓸 수 있다.",
            setOf(Mounts.E), Coverage.FF, 800, 800, 6.3f, 6.3f,
            3.0f, 5.2f, 0.6f, true, false, 9.0f, 5.8f, COL_WHITE_LENS, true
        ),
        CamLens(
            "l_m43_1260", "참새정밀", "12-60mm F3.5-5.6", GearGrade.ENTRY, 140000, 210,
            "마이크로 포서드 표준 줌. 환산 24-120mm를 아주 가볍게 소화한다.",
            setOf(Mounts.M43), Coverage.M43, 12, 60, 3.5f, 5.6f,
            2.5f, 3.4f, 0.2f, false, false, 2.2f, 3.2f, COL_DARK_LENS, false
        ),
        CamLens(
            "l_m43_100400", "참새정밀", "100-400mm F4-6.3", GearGrade.MID, 380000, 985,
            "환산 800mm를 1kg이 안 되게 담았다. 가벼운 초망원 시스템의 매력.",
            setOf(Mounts.M43), Coverage.M43, 100, 400, 4.0f, 6.3f,
            3.0f, 3.8f, 0f, true, false, 6.0f, 4.4f, COL_DARK_LENS, true
        ),
        CamLens(
            "l_m43_300f4", "참새정밀", "300mm F4 단렌즈", GearGrade.HIGH, 950000, 1270,
            "환산 600mm F4. 초망원 단렌즈를 배낭에 넣고 산을 오를 수 있게 해준다.",
            setOf(Mounts.M43), Coverage.M43, 300, 300, 4.0f, 4.0f,
            3.5f, 5.4f, 0.8f, true, false, 6.6f, 5.2f, COL_WHITE_LENS, true
        ),
        CamLens(
            "l_mf_55", "오리온", "55mm F1.7 표준", GearGrade.HIGH, 700000, 780,
            "중형 표준 렌즈. 환산 44mm의 담백한 화각과 믿기 힘든 묘사력.",
            setOf(Mounts.MF), Coverage.MF, 55, 55, 1.7f, 1.7f,
            0f, 6.0f, -0.3f, false, false, 2.6f, 4.6f, COL_DARK_LENS, false,
            luck = 2
        ),
        CamLens(
            "l_mf_100200", "오리온", "100-200mm F5.6", GearGrade.HIGH, 900000, 1050,
            "중형 망원 줌. 환산 79-158mm로 초상과 풍경에 좋지만 새에겐 여전히 짧다.",
            setOf(Mounts.MF), Coverage.MF, 100, 200, 5.6f, 5.6f,
            2.0f, 5.8f, -0.4f, false, false, 4.4f, 4.6f, COL_DARK_LENS, true
        )
    )

    // ------------------------------------------------------------------
    // 텔레컨버터 / 액세서리
    // ------------------------------------------------------------------
    val TELECONVS: List<TeleConv> = listOf(
        TeleConv(
            "tc_14", "오리온", "1.4× 텔레컨버터", 120000, 170,
            "초점거리를 1.4배로. 조리개 1스톱 손해, AF는 조금 느려진다.",
            setOf(Mounts.E, Mounts.F, Mounts.M43), 1.4f, 1.0f, 0.5f
        ),
        TeleConv(
            "tc_20", "오리온", "2.0× 텔레컨버터", 150000, 210,
            "초점거리를 두 배로. 대신 2스톱이 어두워지고 화질·AF 손실이 크다.",
            setOf(Mounts.E, Mounts.F, Mounts.M43), 2.0f, 2.5f, 1.2f
        )
    )

    val ACCESSORIES: List<CamAccessory> = listOf(
        CamAccessory(
            ACC_STRAP, "까치광학", "속사 스트랩", 40000, 60,
            "장비를 몸에 밀착시켜 들고 다니기 편하다.",
            "무게로 인한 피로 -20%"
        ),
        CamAccessory(
            ACC_RAINCOVER, "참새정밀", "레인 커버", 60000, 120,
            "비와 눈으로부터 장비를 지킨다. 방진방적이 없는 장비에 특히 요긴하다.",
            "비·눈 촬영 감점 방지"
        ),
        CamAccessory(
            ACC_ADAPTER, "하늘광학", "마운트 어댑터 (F→E)", 120000, 130,
            "HB-F DSLR 렌즈를 HB-E 미러리스 바디에 물릴 수 있다. AF는 조금 느려진다.",
            "F 렌즈 ↔ E 바디 결합 (AF -1)"
        ),
        CamAccessory(
            ACC_TRIPOD, "솔개옵틱", "카본 삼각대 + 짐벌", 190000, 1800,
            "초망원의 흔들림을 잡아 준다. 멈춰 서서 찍을 때 진가를 발휘한다.",
            "흔들림 보정 대폭 ↑ (무게 +1.8kg)"
        ),
        CamAccessory(
            ACC_BLIND, "솔개옵틱", "위장 블라인드 텐트", 220000, 1300,
            "새의 경계심을 낮춰 더 가까이 접근할 수 있다.",
            "새 도망 반경 -18%",
            luck = 2
        )
    )

    val ALL: List<CamGear> = COMPACTS + BODIES + LENSES + TELECONVS + ACCESSORIES
    val byId: Map<String, CamGear> = ALL.associateBy { it.id }

    fun compact(id: String?): CompactCam? = byId[id] as? CompactCam
    fun body(id: String?): CamBody? = byId[id] as? CamBody
    fun lens(id: String?): CamLens? = byId[id] as? CamLens
    fun tc(id: String?): TeleConv? = byId[id] as? TeleConv
    fun accessory(id: String?): CamAccessory? = byId[id] as? CamAccessory

    /** 바디 + 렌즈 결합 가능 여부 (어댑터 보유 시 F 렌즈를 E 바디에) */
    fun canMount(body: CamBody, lens: CamLens, hasAdapter: Boolean): Boolean {
        if (body.mount in lens.mounts) return true
        if (hasAdapter && body.mount == Mounts.E && Mounts.F in lens.mounts) return true
        return false
    }

    /** 결합할 수 없는 이유 (없으면 null) */
    fun mountProblem(body: CamBody, lens: CamLens, hasAdapter: Boolean): String? {
        if (canMount(body, lens, hasAdapter)) return null
        if (body.mount == Mounts.E && Mounts.F in lens.mounts) {
            return "마운트 어댑터(F→E)가 있어야 결합할 수 있어요"
        }
        return "${Mounts.label(body.mount)}에 맞지 않는 렌즈예요"
    }

    /** 예전 세이브(카메라 Lv.1~5)를 새 장비 시스템으로 옮긴다. */
    fun migrateLegacy(level: Int): List<String> = when (level.coerceIn(1, 5)) {
        1 -> listOf("c_start")
        2 -> listOf("c_start", "c_zoom20")
        3 -> listOf("c_start", "c_zoom20", "b_apsc_entry", "l_70300")
        4 -> listOf("c_start", "c_zoom20", "b_apsc_mid", "l_100400")
        else -> listOf("c_start", "c_zoom20", "b_ff_entry", "l_200600", "tc_14")
    }
}

// ---------------------------------------------------------------------------
// 조합 결과 (실제 게임에 적용되는 성능)
// ---------------------------------------------------------------------------

/**
 * 현재 장착한 장비의 종합 성능.
 * 모든 게임 판정(촬영 반경·별점·AF·흔들림·무게)은 이 값만 본다.
 */
class CameraRig(
    val ilc: Boolean,
    val title: String,
    val short: String,
    val sensor: Sensor,
    val mp: Float,
    val wideMm: Int,          // 35mm 환산
    val teleMm: Int,          // 35mm 환산
    val apWide: Float,
    val apTele: Float,
    val reach: Float,         // 최대 촬영 거리(타일)
    val minDist: Float,       // 최단 촬영 거리(타일)
    val iq: Float,            // 화질 0~10
    val lowLight: Float,      // 저조도 0~10
    val af: Float,            // AF 0~10
    val steady: Float,        // 흔들림 억제 0~10
    val burst: Float,
    val weightG: Int,
    val weatherProof: Boolean,
    val luck: Int,
    val look: CamLook,
    val tags: List<String>
) {
    /** 성능 요약 (상태창/상점) */
    fun specLine(): String =
        "${sensor.label} · ${mp.fmt1()}MP · 환산 ${if (wideMm == teleMm) "${teleMm}mm" else "${wideMm}-${teleMm}mm"} · F${apWide.fmt1()}" +
                (if (apWide != apTele) "-${apTele.fmt1()}" else "")

    fun reachLabel(): String = "촬영 반경 ${reach.fmt1()}칸 · 최단 ${minDist.fmt1()}칸"

    /** 화질 덕분에 별 하나를 더 받을 확률 */
    fun iqChance(): Float = ((iq - 3.4f) / 15f).coerceIn(0f, 0.38f)

    /** 어두울 때 노이즈로 별을 잃을 확률 (darkness 0~1) */
    fun noiseRisk(darkness: Float): Float =
        (darkness * (0.8f - lowLight * 0.085f)).coerceIn(0f, 0.62f)

    /** 움직이며 찍을 때 흔들릴 확률 */
    fun shakeRisk(moving: Boolean, fast: Boolean): Float {
        if (!moving) return 0f
        val base = if (fast) 0.44f else 0.26f
        val tele = (teleMm / 900f).coerceAtMost(0.5f)
        return (base + tele - steady * 0.075f).coerceIn(0f, 0.62f)
    }

    /** 움직이는 새에 초점을 놓칠 확률 */
    fun afMissChance(tierStar: Int): Float =
        (0.30f + tierStar * 0.035f - af * 0.045f).coerceIn(0f, 0.34f)

    /** 연사로 한 번 더 기회를 얻는지 */
    fun burstRetry(): Boolean = burst >= 10f

    /** 촬영 정보(EXIF 느낌) 한 줄 */
    fun exifLine(darkness: Float): String {
        val f = teleMm
        val ap = apTele
        val shutter = when {
            darkness > 0.7f -> 250
            darkness > 0.4f -> 500
            else -> 1000
        }
        var iso = (100f * Math.pow(2.0, (darkness * 5.2f).toDouble()).toFloat() * (ap / 2.8f)).roundToInt()
        iso = (iso / 50) * 50
        iso = iso.coerceIn(100, 25600)
        return "${f}mm · F${ap.fmt1()} · 1/${shutter}s · ISO $iso"
    }
}

object CameraRigs {

    /** 환산 초점거리 -> 촬영 반경(타일). 길수록 멀리서 찍을 수 있지만 수확 체감. */
    fun reachFor(equivTele: Int): Float =
        (2.3f * ln(equivTele.coerceAtLeast(8).toFloat()) - 4.4f).coerceIn(3.2f, 13.0f)

    /** 환산 광각 -> 최단 촬영 거리(타일). 초망원은 너무 가까우면 화각에 안 들어온다. */
    fun minDistFor(equivWide: Int, macro: Boolean): Float =
        if (macro) 0.3f else (equivWide / 260f).coerceIn(0.4f, 5.2f)

    /** 조리개 -> 저조도 보너스 */
    private fun apertureBonus(ap: Float): Float {
        val stops = (Math.log(ap.toDouble()) / Math.log(2.0)).toFloat()   // log2(F)
        return ((3.0f - stops) * 1.15f).coerceIn(-0.6f, 3.2f)
    }

    /** 화소 보너스 (크롭 여유) */
    private fun mpBonus(mp: Float): Float = ((mp - 16f) / 26f).coerceIn(-0.4f, 2.2f)

    /** 일체형 컴팩트 조합 */
    fun fromCompact(cam: CompactCam, acc: Set<String>): CameraRig {
        val tags = ArrayList<String>()
        if (cam.zoomX >= 15f) tags.add("슈퍼줌")
        if (cam.apWide <= 2.0f) tags.add("밝은 렌즈")
        if (cam.sensor.crop <= 1.6f) tags.add("대형 센서")
        if (cam.weatherProof) tags.add("방진방적")
        if (cam.teleMm <= 60) tags.add("광각 특화")
        tags.add("일체형")

        val steadyBase = cam.stab * 1.7f + if (CameraGear.ACC_TRIPOD in acc) 2.6f else 0f
        val iq = (cam.sensor.iqBase + mpBonus(cam.mp) + cam.sharp * 0.42f).coerceIn(0f, 10f)
        val low = (cam.sensor.lowBase + apertureBonus(cam.apTele) + cam.stab * 0.25f).coerceIn(0f, 10f)
        var weight = cam.weightG
        if (CameraGear.ACC_TRIPOD in acc) weight += 1800
        return CameraRig(
            ilc = false,
            title = cam.fullName,
            short = cam.name,
            sensor = cam.sensor,
            mp = cam.mp,
            wideMm = cam.wideMm,
            teleMm = cam.teleMm,
            apWide = cam.apWide,
            apTele = cam.apTele,
            reach = reachFor(cam.teleMm),
            minDist = minDistFor(cam.wideMm, false),
            iq = iq,
            lowLight = low,
            af = cam.afScore,
            steady = steadyBase.coerceIn(0f, 10f),
            burst = cam.burst,
            weightG = weight,
            weatherProof = cam.weatherProof || CameraGear.ACC_RAINCOVER in acc,
            luck = cam.luck + accessoryLuck(acc),
            look = cam.look,
            tags = tags
        )
    }

    /** 바디 + 렌즈 (+ 텔레컨버터) 조합 */
    fun fromIlc(body: CamBody, lens: CamLens, tc: TeleConv?, acc: Set<String>): CameraRig {
        val adapter = CameraGear.ACC_ADAPTER in acc
        val adapted = body.mount !in lens.mounts && adapter

        // 크롭: APS-C 전용 렌즈를 풀프레임 바디에 쓰면 크롭 모드로 동작한다.
        val cropMode = body.sensor == Sensor.FF && lens.coverage == Coverage.APSC
        val crop = if (cropMode) 1.5f else body.sensor.crop
        val effMp = if (cropMode) body.mp * 0.44f else body.mp

        val mul = tc?.mul ?: 1f
        val equivWide = (lens.wideMm * crop * mul).roundToInt()
        val equivTele = (lens.teleMm * crop * mul).roundToInt()
        val apW = lens.apWide * mul
        val apT = lens.apTele * mul

        val tags = ArrayList<String>()
        if (equivTele >= 560) tags.add("초망원")
        else if (equivTele >= 280) tags.add("망원")
        if (lens.macro) tags.add("접사")
        if (lens.prime) tags.add("단렌즈")
        if (apT <= 2.9f) tags.add("대구경")
        if (body.birdAf) tags.add("조류 인식 AF")
        if (body.weatherProof) tags.add("방진방적")
        if (cropMode) tags.add("크롭 모드")
        if (adapted) tags.add("어댑터 결합")
        if (tc != null) tags.add("TC ×${tc.mul.fmt1()}")

        val iq = (
            body.sensor.iqBase + mpBonus(effMp) + lens.sharp * 0.45f - (tc?.sharpPenalty ?: 0f)
        ).coerceIn(0f, 10f)

        val low = (
            body.sensor.lowBase + apertureBonus(apT) + (body.ibis + lens.os) * 0.16f
        ).coerceIn(0f, 10f)

        var af = body.afScore + lens.afMod - (tc?.afPenalty ?: 0f)
        if (adapted) af -= 1f
        if (body.birdAf) af += 0.5f
        af = af.coerceIn(0.5f, 10f)

        var steady = (body.ibis + lens.os) * 1.25f
        if (CameraGear.ACC_TRIPOD in acc) steady += 2.6f
        steady = steady.coerceIn(0f, 10f)

        var weight = body.weightG + lens.weightG + (tc?.weightG ?: 0) + (if (adapted) 130 else 0)
        if (CameraGear.ACC_TRIPOD in acc) weight += 1800

        val lensShort = if (lens.prime) "${lens.wideMm}mm" else "${lens.wideMm}-${lens.teleMm}mm"
        val look = CamLook(
            style = body.look.style,
            bodyCol = body.look.bodyCol,
            bodyDark = body.look.bodyDark,
            accent = body.look.accent,
            barrelLen = lens.barrelLen,
            barrelDia = lens.barrelDia,
            barrelCol = lens.barrelCol,
            hood = lens.hood,
            evf = body.look.evf,
            flash = body.look.flash
        )

        return CameraRig(
            ilc = true,
            title = "${body.name} + ${lens.name}" + (if (tc != null) " + ${tc.name}" else ""),
            short = "${body.name}+$lensShort",
            sensor = body.sensor,
            mp = effMp,
            wideMm = equivWide,
            teleMm = equivTele,
            apWide = apW,
            apTele = apT,
            reach = reachFor(equivTele),
            minDist = minDistFor(equivWide, lens.macro),
            iq = iq,
            lowLight = low,
            af = af,
            steady = steady,
            burst = body.burst,
            weightG = weight,
            weatherProof = (body.weatherProof && lens.grade != GearGrade.ENTRY) || CameraGear.ACC_RAINCOVER in acc,
            luck = body.luck + lens.luck + accessoryLuck(acc),
            look = look,
            tags = tags
        )
    }

    private fun accessoryLuck(acc: Set<String>): Int {
        var n = 0
        for (id in acc) n += CameraGear.accessory(id)?.luck ?: 0
        return n
    }
}
