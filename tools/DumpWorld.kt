package com.pizzaandbird.game

import java.io.File

/**
 * 지역 맵 레이어 JSON 덤프 — 미리보기/디자인 검증용 (Android 런타임 불필요).
 *
 * MapBuilder 가 만든 실제 게임 맵의 논리 타일·지면·포장·데칼을 JSON으로 출력한다.
 * `tools/preview/render_world.py` 가 이 JSON을 읽어 실제 타일 아트로 PNG를 그린다.
 *
 * 사용:
 *   kotlinc -cp <android.jar>:<게임 클래스 출력> -d out tools/DumpWorld.kt
 *   java  -cp <android.jar>:<게임 클래스>:out:<kotlin-stdlib.jar> com.pizzaandbird.game.DumpWorldKt <출력폴터> [regionId ...]
 *
 * 인자를 생략하면 32개 전 지역을 덤프한다. 그래픽/오디오 객체는 만들지 않으므로
 * android.jar 스텁 위에서 그대로 동작한다.
 */

object TNames {
    val byOrdinal: Array<String> = T.ALL.map { it.name }.toTypedArray()
}

fun main(args: Array<String>) {
    val outDir = File(args.getOrElse(0) { "build/world_dump" })
    val ids = if (args.size > 1) args.drop(1) else Regions.ALL.map { it.id }
    outDir.mkdirs()

    // 새 게임 기준: 서울 집만 소유
    val homeId = "seoul"
    val owned = setOf(homeId)

    for (id in ids) {
        val region = Regions.byId[id] ?: error("알 수 없는 지역 $id")
        val map = MapBuilder.build(region, homeId, owned)
        val sb = StringBuilder(1 shl 20)
        sb.append("{\n")
        sb.append("\"id\": \"").append(id).append("\",\n")
        sb.append("\"name\": \"").append(region.name).append("\",\n")
        sb.append("\"city\": ").append(region.city).append(",\n")
        sb.append("\"kind\": \"").append(region.kind.name).append("\",\n")
        sb.append("\"w\": ").append(map.w).append(", \"h\": ").append(map.h).append(",\n")
        sb.append("\"hasHouse\": ").append(map.hasHouse).append(",\n")
        sb.append("\"houseDoorX\": ").append(map.houseDoorX).append(", \"houseDoorY\": ").append(map.houseDoorY).append(",\n")
        sb.append("\"hasLandmark\": ").append(map.hasLandmark).append(",\n")
        sb.append("\"landmarkDoorX\": ").append(map.landmarkDoorX).append(", \"landmarkDoorY\": ").append(map.landmarkDoorY).append(",\n")

        fun dumpLayer(key: String, f: (Int, Int) -> Int) {
            sb.append("\"").append(key).append("\": [")
            for (y in 0 until map.h) {
                sb.append(if (y == 0) "[" else ",[")
                for (x in 0 until map.w) {
                    if (x > 0) sb.append(',')
                    sb.append(f(x, y))
                }
                sb.append("]")
            }
            sb.append("],\n")
        }
        // GameMap 의 난슬 배열은 private — 의도된 접근자만으로 동일한 값을 읽는다
        dumpLayer("tiles") { x, y -> map.t(x, y).ordinal }
        dumpLayer("base") { x, y -> map.groundAt(x, y).ordinal }
        dumpLayer("pave") { x, y -> map.paveAt(x, y) }
        dumpLayer("deco") { x, y -> map.decalAt(x, y) }

        sb.append("\"tileNames\": [")
        TNames.byOrdinal.forEachIndexed { i, n ->
            if (i > 0) sb.append(',')
            sb.append('"').append(n).append('"')
        }
        sb.append("],\n")

        sb.append("\"tunnels\": [")
        map.tunnels.forEachIndexed { i, t0 ->
            if (i > 0) sb.append(',')
            sb.append("{\"dir\":\"").append(t0.dir.name).append("\",\"x\":").append(t0.tileX)
                .append(",\"y\":").append(t0.tileY).append("}")
        }
        sb.append("],\n")

        sb.append("\"npcs\": [")
        map.npcs.forEachIndexed { i, n ->
            if (i > 0) sb.append(',')
            sb.append("{\"x\":").append(n.tileX).append(",\"y\":").append(n.tileY).append("}")
        }
        sb.append("]\n")
        sb.append("}\n")

        File(outDir, "$id.json").writeText(sb.toString())
        println("dumped $id (${map.w}x${map.h})")
    }

    // 지역 팔레트/자연물 아트 선택표 — 렌더러가 지역 색을 그대로 입힐 수 있게 함께 낸다
    run {
        val sb = StringBuilder(1 shl 14)
        sb.append("{\n")
        Regions.ALL.forEachIndexed { idx, region ->
            val s = RegionMapStyles.forRegion(region)
            fun Int.hex(): String = String.format("#%08X", this)
            sb.append(if (idx == 0) "\"" else ",\"").append(region.id).append("\": {")
            sb.append("\"foliage\": \"").append(s.foliageFilter.hex()).append("\", ")
            sb.append("\"water\": \"").append(s.waterFilter.hex()).append("\", ")
            sb.append("\"shore\": \"").append(s.shoreFilter.hex()).append("\", ")
            sb.append("\"stone\": \"").append(s.stoneFilter.hex()).append("\", ")
            fun ints(name: String, list: List<Int>) {
                sb.append("\"").append(name).append("\": [")
                list.forEachIndexed { i, v -> if (i > 0) sb.append(','); sb.append(v) }
                sb.append("], ")
            }
            ints("grass", s.natureArt.grass)
            ints("tallGrass", s.natureArt.tallGrass)
            ints("flowers", s.natureArt.flowers)
            ints("reeds", s.natureArt.reeds)
            ints("rocks", s.natureArt.rocks)
            ints("mountains", s.natureArt.mountains)
            ints("trees", s.natureArt.trees)
            sb.setLength(sb.length - 2)
            sb.append("}\n")
        }
        sb.append("}\n")
        File(outDir, "styles.json").writeText(sb.toString())
        println("dumped styles.json")
    }
    println("OK -> ${outDir.absolutePath}")
}
