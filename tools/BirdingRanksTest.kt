import com.pizzaandbird.game.*

/**
 * 라이퍼 이정표·도장 깨기 컬렉션 검증.
 * (#91로 복구된 BirdingRanks/BirdingCollections 정의가 문서·스토리와 어긋나지 않는지 지킨다)
 * 실행: 컴파일된 게임 클래스 + android.jar + kotlin-stdlib 을 클래스패스에 두고 JVM으로 돌린다.
 */
fun main() {
    // 라이퍼 이정표 경계
    check(BirdingRanks.of(0).name == "입문")
    check(BirdingRanks.of(29).name == "입문")
    check(BirdingRanks.of(30).name == "초보")
    check(BirdingRanks.of(99).name == "초보")
    check(BirdingRanks.of(100).name == "중수")
    check(BirdingRanks.of(200).name == "고수")
    check(BirdingRanks.of(300).name == "초고수")
    check(BirdingRanks.of(399).name == "초고수")
    check(BirdingRanks.of(400).name == "종새꾼")
    check(BirdingRanks.of(598).name == "종새꾼")
    check(BirdingRanks.next(0)?.name == "초보")
    check(BirdingRanks.next(29)?.min == 30)
    check(BirdingRanks.next(399)?.name == "종새꾼")
    check(BirdingRanks.next(400) == null)

    // 도장 깨기 컬렉션
    check(BirdingCollections.ALL.size == 70) { "컬렉션 70개여야 한다: ${BirdingCollections.ALL.size}" }
    check(BirdingCollections.ALL.map { it.name }.toSet().size == 70) { "컬렉션 이름은 유일해야 한다" }

    // 메인 스토리가 참조하는 컬렉션 이름이 모두 존재한다
    for (ch in MainStory.CHAPTERS) {
        val name = ch.collection ?: continue
        check(BirdingCollections.ALL.any { it.name == name }) { "스토리 컬렉션 없음: $name" }
    }

    // 컬렉션이 참조하는 종명이 모두 실제 도감에 있다 (없으면 영원히 못 깨는 도장이 된다)
    val unknown = BirdingCollections.ALL.flatMap { col ->
        col.species.filterNot { Birds.byName.containsKey(it) }.map { "${col.name}:$it" }
    }
    check(unknown.isEmpty()) { "도감에 없는 종: $unknown" }

    // 완료·진행도 계산
    val s = GameState()
    val first = BirdingCollections.ALL.first()
    check(!first.complete(s))
    check(first.progress(s) == "0/${first.species.size}")
    for (name in first.species) {
        val id = Birds.byName.getValue(name).id
        s.birdCounts[id] = (s.birdCounts[id] ?: 0) + 1
    }
    check(first.complete(s))
    check(first.progress(s) == "${first.species.size}/${first.species.size}")

    println("Birding ranks & collections: boundaries, story refs, species validity, progress — passed")
}
