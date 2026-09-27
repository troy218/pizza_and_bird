package com.pizzaandbird.game

/**
 * 한국조류학회 「한국조류목록 개정판 2025 v2.1」 기반 공식 조류 체크리스트.
 *
 * 원본: 한반도_조류_전체목록_2025.txt
 * 기준: 2025년 5월 말까지 한반도에서 기록된 조류, IOC World Bird List v15.1.
 * 공식 기록종 24목 85과 598종, 아종 383개. 보류종 제외.
 *
 * 이 파일은 원본 텍스트 목록에서 생성한 정적 데이터입니다.
 */
class BirdChecklistEntry(
    val orderName: String,
    val familyName: String,
    val koreanName: String,
    val scientificName: String,
    val englishName: String,
    val category: String,
    val subspecies: List<String> = emptyList()
)

object OfficialBirdChecklist {
    const val SOURCE_TITLE = "한국조류학회 조류목록 개정판 2025 v2.1"
    const val SPECIES_COUNT = 598
    const val SUBSPECIES_COUNT = 383

    val ALL: List<BirdChecklistEntry> = listOf(
        BirdChecklistEntry(
            "기러기목", "오리과", "흑기러기",
            "Branta bernicla", "Brant Goose", "가-1",
            listOf("Branta bernicla nigricans")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "붉은가슴기러기",
            "Branta ruficollis", "Red-breasted Goose", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰얼굴기러기",
            "Branta leucopsis", "Barnacle Goose", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "캐나다기러기",
            "Branta hutchinsii", "Cackling Goose", "가-1",
            listOf("Branta hutchinsii leucopareia", "Branta hutchinsii minima", "Branta hutchinsii taverneri")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "줄기러기",
            "Anser indicus", "Bar-headed Goose", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰머리기러기",
            "Anser canagicus", "Emperor Goose", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰기러기",
            "Anser caerulescens", "Snow Goose", "가-1",
            listOf("Anser caerulescens caerulescens")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "회색기러기",
            "Anser anser", "Greylag Goose", "가-1",
            listOf("Anser anser rubrirostris")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "개리",
            "Anser cygnoides", "Swan Goose", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "큰부리큰기러기",
            "Anser fabalis", "Taiga Bean Goose", "가-1",
            listOf("Anser fabalis middendorffii")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "큰기러기",
            "Anser serrirostris", "Tundra Bean Goose", "가-1",
            listOf("Anser serrirostris serrirostris")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "쇠기러기",
            "Anser albifrons", "Greater White-fronted Goose", "가-1",
            listOf("Anser albifrons albifrons")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰이마기러기",
            "Anser erythropus", "Lesser White-fronted Goose", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "혹고니",
            "Cygnus olor", "Mute Swan", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "고니",
            "Cygnus columbianus", "Tundra Swan", "가-1",
            listOf("Cygnus columbianus columbianus")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "큰고니",
            "Cygnus cygnus", "Whooper Swan", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "혹부리오리",
            "Tadorna tadorna", "Common Shelduck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "황오리",
            "Tadorna ferruginea", "Ruddy Shelduck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "원앙사촌",
            "Tadorna cristata", "Crested Shelduck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "원앙",
            "Aix galericulata", "Mandarin Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰얼굴아기오리",
            "Nettapus coromandelianus", "Cotton Pygmy Goose", "가-1",
            listOf("Nettapus coromandelianus coromandelianus")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "가창오리",
            "Sibirionetta formosa", "Baikal Teal", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "발구지",
            "Spatula querquedula", "Garganey", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "넓적부리",
            "Spatula clypeata", "Northern Shoveler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "알락오리",
            "Mareca strepera", "Gadwall", "가-1",
            listOf("Mareca strepera strepera")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "청머리오리",
            "Mareca falcata", "Falcated Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "홍머리오리",
            "Mareca penelope", "Eurasian Wigeon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "아메리카홍머리오리",
            "Mareca americana", "American Wigeon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰뺨검둥오리",
            "Anas zonorhyncha", "Eastern Spot-billed Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "청둥오리",
            "Anas platyrhynchos", "Mallard", "가-1",
            listOf("Anas platyrhynchos platyrhynchos")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "미국오리",
            "Anas rubripes", "American Black Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "고방오리",
            "Anas acuta", "Northern Pintail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "쇠오리",
            "Anas crecca", "Eurasian Teal", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "미국쇠오리",
            "Anas carolinensis", "Green-winged Teal", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "붉은부리흰죽지",
            "Netta rufina", "Red-crested Pochard", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "큰흰죽지",
            "Aythya valisineria", "Canvasback", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰죽지",
            "Aythya ferina", "Common Pochard", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "붉은가슴흰죽지",
            "Aythya baeri", "Baer's Pochard", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "적갈색흰죽지",
            "Aythya nyroca", "Ferruginous Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "북미댕기흰죽지",
            "Aythya collaris", "Ring-necked Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "댕기흰죽지",
            "Aythya fuligula", "Tufted Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "검은머리흰죽지",
            "Aythya marila", "Greater Scaup", "가-1",
            listOf("Aythya marila nearctica")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "쇠검은머리흰죽지",
            "Aythya affinis", "Lesser Scaup", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "호사북방오리",
            "Somateria spectabilis", "King Eider", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰줄박이오리",
            "Histrionicus histrionicus", "Harlequin Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰이마검둥오리",
            "Melanitta perspicillata", "Surf Scoter", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "노랑부리검둥오리사촌",
            "Melanitta fusca", "Velvet Scoter", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "검둥오리사촌",
            "Melanitta stejnegeri", "Stejneger's Scoter", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "검둥오리",
            "Melanitta americana", "Black Scoter", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "바다꿩",
            "Clangula hyemalis", "Long-tailed Duck", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "꼬마오리",
            "Bucephala albeola", "Bufflehead", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰뺨오리",
            "Bucephala clangula", "Common Goldeneye", "가-1",
            listOf("Bucephala clangula clangula")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "북방흰뺨오리",
            "Bucephala islandica", "Barrow's Goldeneye", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "흰비오리",
            "Mergellus albellus", "Smew", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "비오리",
            "Mergus merganser", "Common Merganser", "가-1",
            listOf("Mergus merganser merganser")
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "바다비오리",
            "Mergus serrator", "Red-breasted Merganser", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "기러기목", "오리과", "호사비오리",
            "Mergus squamatus", "Scaly-sided Merganser", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "닭목", "꿩과", "들꿩",
            "Tetrastes bonasia", "Hazel Grouse", "가-1",
            listOf("Tetrastes bonasia amurensis")
        ),
        BirdChecklistEntry(
            "닭목", "꿩과", "멧닭",
            "Lyrurus tetrix", "Black Grouse", "가-2",
            listOf("Lyrurus tetrix ussuriensis")
        ),
        BirdChecklistEntry(
            "닭목", "꿩과", "꿩",
            "Phasianus colchicus", "Common Pheasant", "가-1",
            listOf("Phasianus colchicus karpowi", "Phasianus colchicus pallasi")
        ),
        BirdChecklistEntry(
            "닭목", "꿩과", "메추라기",
            "Coturnix japonica", "Japanese Quail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "쏙독새목", "쏙독새과", "쏙독새",
            "Caprimulgus jotaka", "Grey Nightjar", "가-1",
            listOf("Caprimulgus jotaka jotaka")
        ),
        BirdChecklistEntry(
            "칼새목", "칼새과", "작은칼새",
            "Aerodramus brevirostris", "Himalayan Swiftlet", "가-1",
            listOf("Aerodramus brevirostris innominatus")
        ),
        BirdChecklistEntry(
            "칼새목", "칼새과", "바늘꼬리칼새",
            "Hirundapus caudacutus", "White-throated Needletail", "가-1",
            listOf("Hirundapus caudacutus caudacutus")
        ),
        BirdChecklistEntry(
            "칼새목", "칼새과", "흰배칼새",
            "Tachymarptis melba", "Alpine Swift", "가-1",
            listOf("Tachymarptis melba nubifugus")
        ),
        BirdChecklistEntry(
            "칼새목", "칼새과", "검은등칼새",
            "Apus apus", "Common Swift", "가-1",
            listOf("Apus apus pekinensis")
        ),
        BirdChecklistEntry(
            "칼새목", "칼새과", "칼새",
            "Apus pacificus", "Pacific Swift", "가-1",
            listOf("Apus pacificus pacificus")
        ),
        BirdChecklistEntry(
            "칼새목", "칼새과", "쇠칼새",
            "Apus nipalensis", "House Swift", "가-1",
            listOf("Apus nipalensis nipalensis")
        ),
        BirdChecklistEntry(
            "느시목", "느시과", "느시",
            "Otis tarda", "Great Bustard", "가-1",
            listOf("Otis tarda dybowskii")
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "작은뻐꾸기사촌",
            "Centropus bengalensis", "Lesser Coucal", "가-1",
            listOf("Centropus bengalensis lignator")
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "밤색날개뻐꾸기",
            "Clamator coromandus", "Chestnut-winged Cuckoo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "검은뻐꾸기",
            "Eudynamys scolopaceus", "Asian Koel", "가-1",
            listOf("Eudynamys scolopaceus chinensis")
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "우는뻐꾸기",
            "Cacomantis merulinus", "Plaintive Cuckoo", "가-1",
            listOf("Cacomantis merulinus querulus")
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "검은두견이",
            "Surniculus lugubris", "Square-tailed Drongo-Cuckoo", "가-1",
            listOf("Surniculus lugubris barussarum")
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "큰매사촌",
            "Hierococcyx sparverioides", "Large Hawk-Cuckoo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "매사촌",
            "Hierococcyx hyperythrus", "Northern Hawk-Cuckoo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "작은매사촌",
            "Hierococcyx nisicolor", "Hodgson's Hawk-Cuckoo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "두견이",
            "Cuculus poliocephalus", "Lesser Cuckoo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "검은등뻐꾸기",
            "Cuculus micropterus", "Indian Cuckoo", "가-1",
            listOf("Cuculus micropterus micropterus")
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "벙어리뻐꾸기",
            "Cuculus optatus", "Oriental Cuckoo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두견이목", "두견이과", "뻐꾸기",
            "Cuculus canorus", "Common Cuckoo", "가-1",
            listOf("Cuculus canorus canorus")
        ),
        BirdChecklistEntry(
            "사막꿩목", "사막꿩과", "사막꿩",
            "Syrrhaptes paradoxus", "Pallas's Sandgrouse", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "낭비둘기",
            "Columba rupestris", "Hill Pigeon", "가-1",
            listOf("Columba rupestris rupestris")
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "분홍가슴비둘기",
            "Columba oenas", "Stock Dove", "가-1",
            listOf("Columba oenas yarkandensis")
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "흑비둘기",
            "Columba janthina", "Black Wood Pigeon", "가-1",
            listOf("Columba janthina janthina")
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "멧비둘기",
            "Streptopelia orientalis", "Oriental Turtle Dove", "가-1",
            listOf("Streptopelia orientalis orientalis")
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "염주비둘기",
            "Streptopelia decaocto", "Eurasian Collared Dove", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "홍비둘기",
            "Streptopelia tranquebarica", "Red Collared Dove", "가-1",
            listOf("Streptopelia tranquebarica humilis")
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "목점박이비둘기",
            "Spilopelia chinensis", "Spotted Dove", "가-1",
            listOf("Spilopelia chinensis chinensis")
        ),
        BirdChecklistEntry(
            "비둘기목", "비둘기과", "녹색비둘기",
            "Treron sieboldii", "White-bellied Green Pigeon", "가-1",
            listOf("Treron sieboldii sieboldii")
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "회색가슴뜸부기",
            "Rallus aquaticus", "Water Rail", "가-1",
            listOf("Rallus aquaticus korejewi")
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "흰눈썹뜸부기",
            "Rallus indicus", "Brown-cheeked Rail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "쇠물닭",
            "Gallinula chloropus", "Common Moorhen", "가-1",
            listOf("Gallinula chloropus chloropus")
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "물닭",
            "Fulica atra", "Eurasian Coot", "가-1",
            listOf("Fulica atra atra")
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "알락뜸부기",
            "Coturnicops exquisitus", "Swinhoe's Rail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "쇠뜸부기사촌",
            "Zapornia fusca", "Ruddy-breasted Crake", "가-1",
            listOf("Zapornia fusca erythrothorax")
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "한국뜸부기",
            "Zapornia paykullii", "Band-bellied Crake", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "쇠뜸부기",
            "Zapornia pusilla", "Baillon's Crake", "가-1",
            listOf("Zapornia pusilla pusilla")
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "뜸부기",
            "Gallicrex cinerea", "Watercock", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "뜸부기과", "흰배뜸부기",
            "Amaurornis phoenicurus", "White-breasted Waterhen", "가-1",
            listOf("Amaurornis phoenicurus phoenicurus")
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "시베리아흰두루미",
            "Leucogeranus leucogeranus", "Siberian Crane", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "캐나다두루미",
            "Antigone canadensis", "Sandhill Crane", "가-1",
            listOf("Antigone canadensis canadensis")
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "재두루미",
            "Antigone vipio", "White-naped Crane", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "쇠재두루미",
            "Grus virgo", "Demoiselle Crane", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "두루미",
            "Grus japonensis", "Red-crowned Crane", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "검은목두루미",
            "Grus grus", "Common Crane", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "두루미목", "두루미과", "흑두루미",
            "Grus monacha", "Hooded Crane", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "논병아리목", "논병아리과", "논병아리",
            "Tachybaptus ruficollis", "Little Grebe", "가-1",
            listOf("Tachybaptus ruficollis poggei")
        ),
        BirdChecklistEntry(
            "논병아리목", "논병아리과", "큰논병아리",
            "Podiceps grisegena", "Red-necked Grebe", "가-1",
            listOf("Podiceps grisegena holbollii")
        ),
        BirdChecklistEntry(
            "논병아리목", "논병아리과", "뿔논병아리",
            "Podiceps cristatus", "Great Crested Grebe", "가-1",
            listOf("Podiceps cristatus cristatus")
        ),
        BirdChecklistEntry(
            "논병아리목", "논병아리과", "귀뿔논병아리",
            "Podiceps auritus", "Horned Grebe", "가-1",
            listOf("Podiceps auritus auritus")
        ),
        BirdChecklistEntry(
            "논병아리목", "논병아리과", "검은목논병아리",
            "Podiceps nigricollis", "Black-necked Grebe", "가-1",
            listOf("Podiceps nigricollis nigricollis")
        ),
        BirdChecklistEntry(
            "홍학목", "홍학과", "큰홍학",
            "Phoenicopterus roseus", "Greater Flamingo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "세가락메추라기과", "세가락메추라기",
            "Turnix tanki", "Yellow-legged Buttonquail", "가-1",
            listOf("Turnix tanki blanfordii")
        ),
        BirdChecklistEntry(
            "도요목", "검은머리물떼새과", "검은머리물떼새",
            "Haematopus ostralegus", "Eurasian Oystercatcher", "가-1",
            listOf("Haematopus ostralegus osculans")
        ),
        BirdChecklistEntry(
            "도요목", "장다리물떼새과", "장다리물떼새",
            "Himantopus himantopus", "Black-winged Stilt", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "장다리물떼새과", "뒷부리장다리물떼새",
            "Recurvirostra avosetta", "Pied Avocet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "개꿩",
            "Pluvialis squatarola", "Grey Plover", "가-1",
            listOf("Pluvialis squatarola squatarola")
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "검은가슴물떼새",
            "Pluvialis fulva", "Pacific Golden Plover", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "흰눈썹물떼새",
            "Eudromias morinellus", "Eurasian Dotterel", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "흰죽지꼬마물떼새",
            "Charadrius hiaticula", "Common Ringed Plover", "가-1",
            listOf("Charadrius hiaticula tundrae")
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "꼬마물떼새",
            "Charadrius dubius", "Little Ringed Plover", "가-1",
            listOf("Charadrius dubius curonicus")
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "흰목물떼새",
            "Charadrius placidus", "Long-billed Plover", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "댕기물떼새",
            "Vanellus vanellus", "Northern Lapwing", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "민댕기물떼새",
            "Vanellus cinereus", "Grey-headed Lapwing", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "큰물떼새",
            "Anarhynchus veredus", "Oriental Plover", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "검은이마왕눈물떼새",
            "Anarhynchus atrifrons", "Tibetan Sand Plover", "가-1",
            listOf("Anarhynchus atrifrons atrifrons")
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "왕눈물떼새",
            "Anarhynchus mongolus", "Siberian Sand Plover", "가-1",
            listOf("Anarhynchus mongolus mongolus", "Anarhynchus mongolus stegmanni")
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "큰왕눈물떼새",
            "Anarhynchus leschenaultii", "Greater Sand Plover", "가-1",
            listOf("Anarhynchus leschenaultii leschenaultii")
        ),
        BirdChecklistEntry(
            "도요목", "물떼새과", "흰물떼새",
            "Anarhynchus alexandrinus", "Kentish Plover", "가-1",
            listOf("Anarhynchus alexandrinus alexandrinus", "Anarhynchus alexandrinus nihonensis")
        ),
        BirdChecklistEntry(
            "도요목", "호사도요과", "호사도요",
            "Rostratula benghalensis", "Greater Painted-snipe", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "물꿩과", "물꿩",
            "Hydrophasianus chirurgus", "Pheasant-tailed Jacana", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "중부리도요",
            "Numenius phaeopus", "Eurasian Whimbrel", "가-1",
            listOf("Numenius phaeopus variegatus")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "쇠부리도요",
            "Numenius minutus", "Little Curlew", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "알락꼬리마도요",
            "Numenius madagascariensis", "Far Eastern Curlew", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "마도요",
            "Numenius arquata", "Eurasian Curlew", "가-1",
            listOf("Numenius arquata orientalis")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "큰뒷부리도요",
            "Limosa lapponica", "Bar-tailed Godwit", "가-1",
            listOf("Limosa lapponica menzbieri", "Limosa lapponica baueri")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "흑꼬리도요",
            "Limosa limosa", "Black-tailed Godwit", "가-1",
            listOf("Limosa limosa melanuroides", "Limosa limosa bohaii")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "큰부리도요",
            "Limnodromus semipalmatus", "Asian Dowitcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "긴부리도요",
            "Limnodromus scolopaceus", "Long-billed Dowitcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "꼬마도요",
            "Lymnocryptes minimus", "Jack Snipe", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "멧도요",
            "Scolopax rusticola", "Eurasian Woodcock", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "청도요",
            "Gallinago solitaria", "Solitary Snipe", "가-1",
            listOf("Gallinago solitaria japonica")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "꺅도요사촌",
            "Gallinago megala", "Swinhoe's Snipe", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "바늘꼬리도요",
            "Gallinago stenura", "Pin-tailed Snipe", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "큰꺅도요",
            "Gallinago hardwickii", "Latham's Snipe", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "꺅도요",
            "Gallinago gallinago", "Common Snipe", "가-1",
            listOf("Gallinago gallinago gallinago")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "큰지느러미발도요",
            "Phalaropus tricolor", "Wilson's Phalarope", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "붉은배지느러미발도요",
            "Phalaropus fulicarius", "Red Phalarope", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "지느러미발도요",
            "Phalaropus lobatus", "Red-necked Phalarope", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "뒷부리도요",
            "Xenus cinereus", "Terek Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "깝작도요",
            "Actitis hypoleucos", "Common Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "삑삑도요",
            "Tringa ochropus", "Green Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "노랑발도요",
            "Tringa brevipes", "Grey-tailed Tattler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "쇠청다리도요",
            "Tringa stagnatilis", "Marsh Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "알락도요",
            "Tringa glareola", "Wood Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "붉은발도요",
            "Tringa totanus", "Common Redshank", "가-1",
            listOf("Tringa totanus ussuriensis")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "청다리도요사촌",
            "Tringa guttifer", "Nordmann's Greenshank", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "학도요",
            "Tringa erythropus", "Spotted Redshank", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "청다리도요",
            "Tringa nebularia", "Common Greenshank", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "꼬까도요",
            "Arenaria interpres", "Ruddy Turnstone", "가-1",
            listOf("Arenaria interpres interpres")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "붉은어깨도요",
            "Calidris tenuirostris", "Great Knot", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "붉은가슴도요",
            "Calidris canutus", "Red Knot", "가-1",
            listOf("Calidris canutus piersmai", "Calidris canutus rogersi")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "목도리도요",
            "Calidris pugnax", "Ruff", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "송곳부리도요",
            "Calidris falcinellus", "Broad-billed Sandpiper", "가-1",
            listOf("Calidris falcinellus sibirica")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "메추라기도요",
            "Calidris acuminata", "Sharp-tailed Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "붉은갯도요",
            "Calidris ferruginea", "Curlew Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "흰꼬리좀도요",
            "Calidris temminckii", "Temminck's Stint", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "종달도요",
            "Calidris subminuta", "Long-toed Stint", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "좀도요",
            "Calidris ruficollis", "Red-necked Stint", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "넓적부리도요",
            "Calidris pygmaea", "Spoon-billed Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "누른도요",
            "Calidris subruficollis", "Buff-breasted Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "세가락도요",
            "Calidris alba", "Sanderling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "민물도요",
            "Calidris alpina", "Dunlin", "가-1",
            listOf("Calidris alpina sakhalina", "Calidris alpina actites", "Calidris alpina kistchinski", "Calidris alpina arcticola", "Calidris alpina pacifica")
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "작은도요",
            "Calidris minuta", "Little Stint", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도요과", "아메리카메추라기도요",
            "Calidris melanotos", "Pectoral Sandpiper", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "제비물떼새과", "제비물떼새",
            "Glareola maldivarum", "Oriental Pratincole", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "흰제비갈매기",
            "Gygis alba", "White Tern", "가-1",
            listOf("Gygis alba candida")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "알류샨제비갈매기",
            "Onychoprion aleuticus", "Aleutian Tern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "검은등제비갈매기",
            "Onychoprion fuscatus", "Sooty Tern", "가-1",
            listOf("Onychoprion fuscatus nubilosus")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "에위니아제비갈매기",
            "Onychoprion anaethetus", "Bridled Tern", "가-1",
            listOf("Onychoprion anaethetus anaethetus")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "쇠제비갈매기",
            "Sternula albifrons", "Little Tern", "가-1",
            listOf("Sternula albifrons sinensis")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "큰부리제비갈매기",
            "Gelochelidon nilotica", "Gull-billed Tern", "가-1",
            listOf("Gelochelidon nilotica affinis")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "붉은부리큰제비갈매기",
            "Hydroprogne caspia", "Caspian Tern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "구레나룻제비갈매기",
            "Chlidonias hybrida", "Whiskered Tern", "가-1",
            listOf("Chlidonias hybrida hybrida")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "검은제비갈매기",
            "Chlidonias niger", "Black Tern", "가-1",
            listOf("Chlidonias niger niger")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "흰죽지제비갈매기",
            "Chlidonias leucopterus", "White-winged Tern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "제비갈매기",
            "Sterna hirundo", "Common Tern", "가-1",
            listOf("Sterna hirundo minussensis", "Sterna hirundo longipennis")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "긴꼬리제비갈매기",
            "Sterna dougallii", "Roseate Tern", "가-1",
            listOf("Sterna dougallii bangsi")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "큰제비갈매기",
            "Thalasseus bergii", "Greater Crested Tern", "가-1",
            listOf("Thalasseus bergii cristatus")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "뿔제비갈매기",
            "Thalasseus bernsteini", "Chinese Crested Tern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "꼬마갈매기",
            "Hydrocoloeus minutus", "Little Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "세가락갈매기",
            "Rissa tridactyla", "Black-legged Kittiwake", "가-1",
            listOf("Rissa tridactyla pollicaris")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "북극흰갈매기",
            "Pagophila eburnea", "Ivory Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "목테갈매기",
            "Xema sabini", "Sabine's Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "검은머리갈매기",
            "Saundersilarus saundersi", "Saunders's Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "긴목갈매기",
            "Chroicocephalus genei", "Slender-billed Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "붉은부리갈매기",
            "Chroicocephalus ridibundus", "Black-headed Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "갈색머리갈매기",
            "Chroicocephalus brunnicephalus", "Brown-headed Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "큰검은머리갈매기",
            "Ichthyaetus ichthyaetus", "Pallas's Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "고대갈매기",
            "Ichthyaetus relictus", "Relict Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "괭이갈매기",
            "Larus crassirostris", "Black-tailed Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "갈매기",
            "Larus canus", "Common Gull", "가-1",
            listOf("Larus canus heinei", "Larus canus kamtschatschensis")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "재갈매기",
            "Larus vegae", "Vega Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "한국재갈매기",
            "Larus mongolicus", "Mongolian Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "흰갈매기",
            "Larus hyperboreus", "Glaucous Gull", "가-1",
            listOf("Larus hyperboreus pallidissimus", "Larus hyperboreus barrovianus")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "작은노랑발갈매기",
            "Larus fuscus", "Lesser Black-backed Gull", "가-1",
            listOf("Larus fuscus heuglini")
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "옅은재갈매기",
            "Larus smithsonianus", "American Herring Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "수리갈매기",
            "Larus glaucescens", "Glaucous-winged Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "큰재갈매기",
            "Larus schistisagus", "Slaty-backed Gull", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "갈매기과", "작은흰갈매기",
            "Larus glaucoides", "Iceland Gull", "가-1",
            listOf("Larus glaucoides glaucoides", "Larus glaucoides thayeri")
        ),
        BirdChecklistEntry(
            "도요목", "도둑갈매기과", "긴꼬리도둑갈매기",
            "Stercorarius longicaudus", "Long-tailed Jaeger", "가-1",
            listOf("Stercorarius longicaudus pallescens")
        ),
        BirdChecklistEntry(
            "도요목", "도둑갈매기과", "북극도둑갈매기",
            "Stercorarius parasiticus", "Parasitic Jaeger", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도둑갈매기과", "넓적꼬리도둑갈매기",
            "Stercorarius pomarinus", "Pomarine Jaeger", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "도둑갈매기과", "큰도둑갈매기",
            "Stercorarius maccormicki", "South Polar Skua", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "흰수염바다오리",
            "Cerorhinca monocerata", "Rhinoceros Auklet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "댕기바다오리",
            "Fratercula cirrhata", "Tufted Puffin", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "작은바다오리",
            "Aethia pusilla", "Least Auklet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "뿔바다오리",
            "Aethia cristatella", "Crested Auklet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "알락쇠오리",
            "Brachyramphus perdix", "Long-billed Murrelet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "흰눈썹바다오리",
            "Cepphus carbo", "Spectacled Guillemot", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "큰부리바다오리",
            "Uria lomvia", "Thick-billed Murre", "가-1",
            listOf("Uria lomvia arra")
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "바다오리",
            "Uria aalge", "Common Murre", "가-1",
            listOf("Uria aalge inornata")
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "바다쇠오리",
            "Synthliboramphus antiquus", "Ancient Murrelet", "가-1",
            listOf("Synthliboramphus antiquus antiquus")
        ),
        BirdChecklistEntry(
            "도요목", "바다오리과", "뿔쇠오리",
            "Synthliboramphus wumizusume", "Japanese Murrelet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "아비목", "아비과", "아비",
            "Gavia stellata", "Red-throated Loon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "아비목", "아비과", "큰회색머리아비",
            "Gavia arctica", "Black-throated Loon", "가-1",
            listOf("Gavia arctica viridigularis")
        ),
        BirdChecklistEntry(
            "아비목", "아비과", "회색머리아비",
            "Gavia pacifica", "Pacific Loon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "아비목", "아비과", "흰부리아비",
            "Gavia adamsii", "Yellow-billed Loon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "알바트로스과", "검은등알바트로스",
            "Phoebastria immutabilis", "Laysan Albatross", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "알바트로스과", "검은발알바트로스",
            "Phoebastria nigripes", "Black-footed Albatross", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "알바트로스과", "알바트로스",
            "Phoebastria albatrus", "Short-tailed Albatross", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "바다제비과", "바다제비",
            "Hydrobates monorhis", "Swinhoe's Storm Petrel", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "슴새과", "흰배슴새",
            "Pterodroma hypoleuca", "Bonin Petrel", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "슴새과", "슴새",
            "Calonectris leucomelas", "Streaked Shearwater", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "슴새과", "쇠부리슴새",
            "Ardenna tenuirostris", "Short-tailed Shearwater", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "슴새과", "붉은발슴새",
            "Ardenna carneipes", "Flesh-footed Shearwater", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "슴새목", "슴새과", "검은슴새",
            "Bulweria bulwerii", "Bulwer's Petrel", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "황새목", "황새과", "먹황새",
            "Ciconia nigra", "Black Stork", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "황새목", "황새과", "황새",
            "Ciconia boyciana", "Oriental Stork", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "얼가니새목", "군함조과", "군함조",
            "Fregata ariel", "Lesser Frigatebird", "가-1",
            listOf("Fregata ariel ariel")
        ),
        BirdChecklistEntry(
            "얼가니새목", "군함조과", "큰군함조",
            "Fregata minor", "Great Frigatebird", "가-1",
            listOf("Fregata minor minor")
        ),
        BirdChecklistEntry(
            "얼가니새목", "얼가니새과", "붉은발얼가니새",
            "Sula sula", "Red-footed Booby", "가-1",
            listOf("Sula sula rubripes")
        ),
        BirdChecklistEntry(
            "얼가니새목", "얼가니새과", "갈색얼가니새",
            "Sula leucogaster", "Brown Booby", "가-1",
            listOf("Sula leucogaster plotus")
        ),
        BirdChecklistEntry(
            "얼가니새목", "얼가니새과", "푸른얼굴얼가니새",
            "Sula dactylatra", "Masked Booby", "가-1",
            listOf("Sula dactylatra personata")
        ),
        BirdChecklistEntry(
            "얼가니새목", "가마우지과", "쇠가마우지",
            "Urile pelagicus", "Pelagic Cormorant", "가-1",
            listOf("Urile pelagicus pelagicus")
        ),
        BirdChecklistEntry(
            "얼가니새목", "가마우지과", "가마우지",
            "Phalacrocorax capillatus", "Japanese Cormorant", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "얼가니새목", "가마우지과", "민물가마우지",
            "Phalacrocorax carbo", "Great Cormorant", "가-1",
            listOf("Phalacrocorax carbo sinensis")
        ),
        BirdChecklistEntry(
            "사다새목", "저어새과", "검은머리흰따오기",
            "Threskiornis melanocephalus", "Black-headed Ibis", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "저어새과", "따오기",
            "Nipponia nippon", "Crested Ibis", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "저어새과", "적갈색따오기",
            "Plegadis falcinellus", "Glossy Ibis", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "저어새과", "노랑부리저어새",
            "Platalea leucorodia", "Eurasian Spoonbill", "가-1",
            listOf("Platalea leucorodia leucorodia")
        ),
        BirdChecklistEntry(
            "사다새목", "저어새과", "저어새",
            "Platalea minor", "Black-faced Spoonbill", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "알락해오라기",
            "Botaurus stellaris", "Eurasian Bittern", "가-1",
            listOf("Botaurus stellaris stellaris")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "검은해오라기",
            "Botaurus flavicollis", "Black Bittern", "가-1",
            listOf("Botaurus flavicollis flavicollis")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "열대붉은해오라기",
            "Botaurus cinnamomeus", "Cinnamon Bittern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "큰덤불해오라기",
            "Botaurus eurhythmus", "Von Schrenck's Bittern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "덤불해오라기",
            "Botaurus sinensis", "Yellow Bittern", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "해오라기",
            "Nycticorax nycticorax", "Black-crowned Night Heron", "가-1",
            listOf("Nycticorax nycticorax nycticorax")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "푸른눈테해오라기",
            "Gorsachius melanolophus", "Malayan Night Heron", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "붉은해오라기",
            "Gorsachius goisagi", "Japanese Night Heron", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "흑로",
            "Egretta sacra", "Pacific Reef Heron", "가-1",
            listOf("Egretta sacra sacra")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "노랑부리백로",
            "Egretta eulophotes", "Chinese Egret", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "쇠백로",
            "Egretta garzetta", "Little Egret", "가-1",
            listOf("Egretta garzetta garzetta")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "검은댕기해오라기",
            "Butorides atricapilla", "Little Heron", "가-1",
            listOf("Butorides atricapilla amurensis")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "흰날개해오라기",
            "Ardeola bacchus", "Chinese Pond Heron", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "중대백로",
            "Ardea alba", "Great Egret", "가-1",
            listOf("Ardea alba alba", "Ardea alba modesta")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "중백로",
            "Ardea intermedia", "Medium Egret", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "황로",
            "Ardea coromanda", "Eastern Cattle Egret", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "왜가리",
            "Ardea cinerea", "Grey Heron", "가-1",
            listOf("Ardea cinerea jouyi")
        ),
        BirdChecklistEntry(
            "사다새목", "백로과", "붉은왜가리",
            "Ardea purpurea", "Purple Heron", "가-1",
            listOf("Ardea purpurea manilensis")
        ),
        BirdChecklistEntry(
            "사다새목", "사다새과", "큰사다새",
            "Pelecanus onocrotalus", "Great White Pelican", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "사다새목", "사다새과", "사다새",
            "Pelecanus crispus", "Dalmatian Pelican", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "물수리과", "물수리",
            "Pandion haliaetus", "Osprey", "가-1",
            listOf("Pandion haliaetus haliaetus")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "검은어깨매",
            "Elanus caeruleus", "Black-winged Kite", "가-1",
            listOf("Elanus caeruleus vociferus")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "수염수리",
            "Gypaetus barbatus", "Bearded Vulture", "가-1",
            listOf("Gypaetus barbatus barbatus")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "벌매",
            "Pernis ptilorhynchus", "Crested Honey Buzzard", "가-1",
            listOf("Pernis ptilorhynchus orientalis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "검은댕기수리",
            "Aviceda leuphotes", "Black Baza", "가-1",
            listOf("Aviceda leuphotes syama")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "고산대머리수리",
            "Gyps himalayensis", "Himalayan Vulture", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "독수리",
            "Aegypius monachus", "Cinereous Vulture", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "관수리",
            "Spilornis cheela", "Crested Serpent Eagle", "가-1",
            listOf("Spilornis cheela ricketti")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "뿔매",
            "Nisaetus nipalensis", "Mountain Hawk-Eagle", "가-1",
            listOf("Nisaetus nipalensis orientalis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "항라머리검독수리",
            "Clanga clanga", "Greater Spotted Eagle", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "흰점어깨수리",
            "Hieraaetus pennatus", "Booted Eagle", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "초원수리",
            "Aquila nipalensis", "Steppe Eagle", "가-1",
            listOf("Aquila nipalensis nipalensis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "흰죽지수리",
            "Aquila heliaca", "Eastern Imperial Eagle", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "검독수리",
            "Aquila chrysaetos", "Golden Eagle", "가-1",
            listOf("Aquila chrysaetos japonica")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "흰배줄무늬수리",
            "Aquila fasciata", "Bonelli's Eagle", "가-1",
            listOf("Aquila fasciata fasciata")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "붉은배새매",
            "Tachyspiza soloensis", "Chinese Sparrowhawk", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "조롱이",
            "Tachyspiza gularis", "Japanese Sparrowhawk", "가-1",
            listOf("Tachyspiza gularis gularis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "작은새매",
            "Tachyspiza virgata", "Besra", "가-1",
            listOf("Tachyspiza virgata affinis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "새매",
            "Accipiter nisus", "Eurasian Sparrowhawk", "가-1",
            listOf("Accipiter nisus nisosimilis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "참매",
            "Astur gentilis", "Eurasian Goshawk", "가-1",
            listOf("Astur gentilis albidus", "Astur gentilis schvedowi")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "개구리매",
            "Circus spilonotus", "Eastern Marsh Harrier", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "잿빛개구리매",
            "Circus cyaneus", "Hen Harrier", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "알락개구리매",
            "Circus melanoleucos", "Pied Harrier", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "솔개",
            "Milvus migrans", "Black Kite", "가-1",
            listOf("Milvus migrans lineatus")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "참수리",
            "Haliaeetus pelagicus", "Steller's Sea Eagle", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "흰꼬리수리",
            "Haliaeetus albicilla", "White-tailed Eagle", "가-1",
            listOf("Haliaeetus albicilla albicilla")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "왕새매",
            "Butastur indicus", "Grey-faced Buzzard", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "털발말똥가리",
            "Buteo lagopus", "Rough-legged Buzzard", "가-1",
            listOf("Buteo lagopus menzbieri", "Buteo lagopus kamtschatkensis")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "큰말똥가리",
            "Buteo hemilasius", "Upland Buzzard", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "말똥가리",
            "Buteo japonicus", "Eastern Buzzard", "가-1",
            listOf("Buteo japonicus burmanicus", "Buteo japonicus japonicus")
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "히말라야말똥가리",
            "Buteo refectus", "Himalayan Buzzard", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "수리목", "수리과", "대륙말똥가리",
            "Buteo buteo", "Common Buzzard", "가-1",
            listOf("Buteo buteo buteo", "Buteo buteo vulpinus")
        ),
        BirdChecklistEntry(
            "올빼미목", "가면올빼미과", "가면올빼미",
            "Tyto longimembris", "Eastern Grass Owl", "가-1",
            listOf("Tyto longimembris chinensis")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "솔부엉이",
            "Ninox japonica", "Northern Boobook", "가-1",
            listOf("Ninox japonica florensis", "Ninox japonica japonica")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "금눈쇠올빼미",
            "Athene noctua", "Little Owl", "가-1",
            listOf("Athene noctua plumipes")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "긴꼬리올빼미",
            "Surnia ulula", "Northern Hawk-Owl", "가-2",
            listOf("Surnia ulula ulula")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "소쩍새",
            "Otus sunia", "Oriental Scops Owl", "가-1",
            listOf("Otus sunia japonicus", "Otus sunia stictonotus")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "큰소쩍새",
            "Otus semitorques", "Japanese Scops Owl", "가-1",
            listOf("Otus semitorques ussuriensis", "Otus semitorques semitorques")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "칡부엉이",
            "Asio otus", "Long-eared Owl", "가-1",
            listOf("Asio otus otus")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "쇠부엉이",
            "Asio flammeus", "Short-eared Owl", "가-1",
            listOf("Asio flammeus flammeus")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "흰올빼미",
            "Bubo scandiacus", "Snowy Owl", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "수리부엉이",
            "Bubo bubo", "Eurasian Eagle-Owl", "가-1",
            listOf("Bubo bubo kiautschensis")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "올빼미",
            "Strix nivicolum", "Himalayan Owl", "가-1",
            listOf("Strix nivicolum ma")
        ),
        BirdChecklistEntry(
            "올빼미목", "올빼미과", "긴점박이올빼미",
            "Strix uralensis", "Ural Owl", "가-1",
            listOf("Strix uralensis nikolskii")
        ),
        BirdChecklistEntry(
            "코뿔새목", "후투티과", "후투티",
            "Upupa epops", "Eurasian Hoopoe", "가-1",
            listOf("Upupa epops saturata")
        ),
        BirdChecklistEntry(
            "파랑새목", "파랑새과", "파랑새",
            "Eurystomus orientalis", "Oriental Dollarbird", "가-1",
            listOf("Eurystomus orientalis cyanocollis")
        ),
        BirdChecklistEntry(
            "파랑새목", "물총새과", "호반새",
            "Halcyon coromanda", "Ruddy Kingfisher", "가-1",
            listOf("Halcyon coromanda major")
        ),
        BirdChecklistEntry(
            "파랑새목", "물총새과", "청호반새",
            "Halcyon pileata", "Black-capped Kingfisher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "파랑새목", "물총새과", "물총새",
            "Alcedo atthis", "Common Kingfisher", "가-1",
            listOf("Alcedo atthis bengalensis")
        ),
        BirdChecklistEntry(
            "파랑새목", "물총새과", "뿔호반새",
            "Megaceryle lugubris", "Crested Kingfisher", "가-1",
            listOf("Megaceryle lugubris lugubris")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "개미잡이",
            "Jynx torquilla", "Eurasian Wryneck", "가-1",
            listOf("Jynx torquilla chinensis")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "아물쇠딱다구리",
            "Yungipicus canicapillus", "Grey-capped Pygmy Woodpecker", "가-1",
            listOf("Yungipicus canicapillus doerriesi")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "쇠딱다구리",
            "Yungipicus kizuki", "Japanese Pygmy Woodpecker", "가-1",
            listOf("Yungipicus kizuki permutatus", "Yungipicus kizuki nippon")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "세가락딱다구리",
            "Picoides tridactylus", "Eurasian Three-toed Woodpecker", "가-2",
            listOf("Picoides tridactylus kurodai")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "쇠오색딱다구리",
            "Dryobates minor", "Lesser Spotted Woodpecker", "가-2",
            listOf("Dryobates minor amurensis")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "붉은배오색딱다구리",
            "Dendrocopos hyperythrus", "Rufous-bellied Woodpecker", "가-1",
            listOf("Dendrocopos hyperythrus subrufinus")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "오색딱다구리",
            "Dendrocopos major", "Great Spotted Woodpecker", "가-1",
            listOf("Dendrocopos major brevirostris", "Dendrocopos major japonicus")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "큰오색딱다구리",
            "Dendrocopos leucotos", "White-backed Woodpecker", "가-1",
            listOf("Dendrocopos leucotos takahashii", "Dendrocopos leucotos quelpartensis", "Dendrocopos leucotos leucotos")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "크낙새",
            "Dryocopus javensis", "White-bellied Woodpecker", "가-1",
            listOf("Dryocopus javensis richardsi")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "까막딱다구리",
            "Dryocopus martius", "Black Woodpecker", "가-1",
            listOf("Dryocopus martius martius")
        ),
        BirdChecklistEntry(
            "딱다구리목", "딱다구리과", "청딱다구리",
            "Picus canus", "Grey-headed Woodpecker", "가-1",
            listOf("Picus canus jessoensis")
        ),
        BirdChecklistEntry(
            "매목", "매과", "황조롱이",
            "Falco tinnunculus", "Common Kestrel", "가-1",
            listOf("Falco tinnunculus tinnunculus", "Falco tinnunculus interstinctus")
        ),
        BirdChecklistEntry(
            "매목", "매과", "비둘기조롱이",
            "Falco amurensis", "Amur Falcon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "매목", "매과", "쇠황조롱이",
            "Falco columbarius", "Merlin", "가-1",
            listOf("Falco columbarius insignis")
        ),
        BirdChecklistEntry(
            "매목", "매과", "새호리기",
            "Falco subbuteo", "Eurasian Hobby", "가-1",
            listOf("Falco subbuteo subbuteo")
        ),
        BirdChecklistEntry(
            "매목", "매과", "헨다손매",
            "Falco cherrug", "Saker Falcon", "가-1",
            listOf("Falco cherrug milvipes")
        ),
        BirdChecklistEntry(
            "매목", "매과", "흰매",
            "Falco rusticolus", "Gyrfalcon", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "매목", "매과", "매",
            "Falco peregrinus", "Peregrine Falcon", "가-1",
            listOf("Falco peregrinus pealei", "Falco peregrinus calidus", "Falco peregrinus japonensis")
        ),
        BirdChecklistEntry(
            "참새목", "팔색조과", "푸른날개팔색조",
            "Pitta moluccensis", "Blue-winged Pitta", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "팔색조과", "팔색조",
            "Pitta nympha", "Fairy Pitta", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "숲제비과", "회색숲제비",
            "Artamus fuscus", "Ashy Woodswallow", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새사촌과", "할미새사촌",
            "Pericrocotus divaricatus", "Ashy Minivet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새사촌과", "검은가슴할미새사촌",
            "Pericrocotus tegimae", "Ryukyu Minivet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새사촌과", "갈색할미새사촌",
            "Pericrocotus cantonensis", "Swinhoe's Minivet", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새사촌과", "검은할미새사촌",
            "Lalage melaschistos", "Black-winged Cuckooshrike", "가-1",
            listOf("Lalage melaschistos intermedia")
        ),
        BirdChecklistEntry(
            "참새목", "꾀꼬리과", "꾀꼬리",
            "Oriolus chinensis", "Black-naped Oriole", "가-1",
            listOf("Oriolus chinensis diffusus")
        ),
        BirdChecklistEntry(
            "참새목", "바람까마귀과", "큰부리바람까마귀",
            "Dicrurus annectens", "Crow-billed Drongo", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "바람까마귀과", "바람까마귀",
            "Dicrurus hottentottus", "Hair-crested Drongo", "가-1",
            listOf("Dicrurus hottentottus brevirostris")
        ),
        BirdChecklistEntry(
            "참새목", "바람까마귀과", "회색바람까마귀",
            "Dicrurus leucophaeus", "Ashy Drongo", "가-1",
            listOf("Dicrurus leucophaeus leucogenis")
        ),
        BirdChecklistEntry(
            "참새목", "바람까마귀과", "검은바람까마귀",
            "Dicrurus macrocercus", "Black Drongo", "가-1",
            listOf("Dicrurus macrocercus cathoecus")
        ),
        BirdChecklistEntry(
            "참새목", "긴꼬리딱새과", "북방긴꼬리딱새",
            "Terpsiphone incei", "Amur Paradise Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "긴꼬리딱새과", "긴꼬리딱새",
            "Terpsiphone atrocaudata", "Black Paradise Flycatcher", "가-1",
            listOf("Terpsiphone atrocaudata atrocaudata")
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "물때까치",
            "Lanius sphenocercus", "Chinese Grey Shrike", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "재때까치",
            "Lanius borealis", "Northern Shrike", "가-1",
            listOf("Lanius borealis sibiricus", "Lanius borealis bianchii", "Lanius borealis mollis")
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "칡때까치",
            "Lanius tigrinus", "Tiger Shrike", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "덤불때까치",
            "Lanius isabellinus", "Isabelline Shrike", "가-1",
            listOf("Lanius isabellinus isabellinus")
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "붉은등때까치",
            "Lanius collurio", "Red-backed Shrike", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "붉은꼬리때까치",
            "Lanius phoenicuroides", "Red-tailed Shrike", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "노랑때까치",
            "Lanius cristatus", "Brown Shrike", "가-1",
            listOf("Lanius cristatus cristatus", "Lanius cristatus confusus", "Lanius cristatus lucionensis", "Lanius cristatus superciliosus")
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "때까치",
            "Lanius bucephalus", "Bull-headed Shrike", "가-1",
            listOf("Lanius bucephalus bucephalus")
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "긴꼬리때까치",
            "Lanius schach", "Long-tailed Shrike", "가-1",
            listOf("Lanius schach schach")
        ),
        BirdChecklistEntry(
            "참새목", "때까치과", "회색등때까치",
            "Lanius tephronotus", "Grey-backed Shrike", "가-1",
            listOf("Lanius tephronotus tephronotus")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "어치",
            "Garrulus glandarius", "Eurasian Jay", "가-1",
            listOf("Garrulus glandarius brandtii")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "물까치",
            "Cyanopica cyanus", "Azure-winged Magpie", "가-1",
            listOf("Cyanopica cyanus cyanus")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "까치",
            "Pica serica", "Oriental Magpie", "가-1",
            listOf("Pica serica anderssoni")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "잣까마귀",
            "Nucifraga caryocatactes", "Northern Nutcracker", "가-1",
            listOf("Nucifraga caryocatactes macrorhynchos")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "붉은부리까마귀",
            "Pyrrhocorax pyrrhocorax", "Red-billed Chough", "가-1",
            listOf("Pyrrhocorax pyrrhocorax brachypus")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "갈까마귀",
            "Coloeus dauuricus", "Daurian Jackdaw", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "집까마귀",
            "Corvus splendens", "House Crow", "가-1",
            listOf("Corvus splendens insolens")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "떼까마귀",
            "Corvus frugilegus", "Rook", "가-1",
            listOf("Corvus frugilegus pastinator")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "까마귀",
            "Corvus corone", "Carrion Crow", "가-1",
            listOf("Corvus corone orientalis")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "큰부리까마귀",
            "Corvus macrorhynchos", "Large-billed Crow", "가-1",
            listOf("Corvus macrorhynchos mandshuricus")
        ),
        BirdChecklistEntry(
            "참새목", "까마귀과", "큰까마귀",
            "Corvus corax", "Northern Raven", "가-1",
            listOf("Corvus corax kamtschaticus")
        ),
        BirdChecklistEntry(
            "참새목", "여새과", "황여새",
            "Bombycilla garrulus", "Bohemian Waxwing", "가-1",
            listOf("Bombycilla garrulus garrulus")
        ),
        BirdChecklistEntry(
            "참새목", "여새과", "홍여새",
            "Bombycilla japonica", "Japanese Waxwing", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "요정딱새과", "회색머리노랑딱새",
            "Culicicapa ceylonensis", "Grey-headed Canary-flycatcher", "가-1",
            listOf("Culicicapa ceylonensis calochrysea")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "진박새",
            "Periparus ater", "Coal Tit", "가-1",
            listOf("Periparus ater ater", "Periparus ater insularis")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "노랑배진박새",
            "Pardaliparus venustulus", "Yellow-bellied Tit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "곤줄박이",
            "Sittiparus varius", "Varied Tit", "가-1",
            listOf("Sittiparus varius varius")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "쇠박새",
            "Poecile palustris", "Marsh Tit", "가-1",
            listOf("Poecile palustris brevirostris", "Poecile palustris jeholicus", "Poecile palustris hellmayri")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "북방쇠박새",
            "Poecile montanus", "Willow Tit", "가-1",
            listOf("Poecile montanus baicalensis")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "노랑배박새",
            "Parus major", "Great Tit", "가-1",
            listOf("Parus major kapustini")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "박새",
            "Parus cinereus", "Cinereous Tit", "가-1",
            listOf("Parus cinereus minor", "Parus cinereus dageletensis")
        ),
        BirdChecklistEntry(
            "참새목", "박새과", "작은노랑배박새",
            "Parus monticolus", "Green-backed Tit", "가-1",
            listOf("Parus monticolus yunnanensis")
        ),
        BirdChecklistEntry(
            "참새목", "스윈호오목눈이과", "스윈호오목눈이",
            "Remiz consobrinus", "Chinese Penduline Tit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "수염오목눈이과", "수염오목눈이",
            "Panurus biarmicus", "Bearded Reedling", "가-1",
            listOf("Panurus biarmicus russicus")
        ),
        BirdChecklistEntry(
            "참새목", "종다리과", "종다리",
            "Alauda arvensis", "Eurasian Skylark", "가-1",
            listOf("Alauda arvensis intermedia", "Alauda arvensis pekinensis", "Alauda arvensis lonnbergi", "Alauda arvensis japonica")
        ),
        BirdChecklistEntry(
            "참새목", "종다리과", "뿔종다리",
            "Galerida cristata", "Crested Lark", "가-1",
            listOf("Galerida cristata coreensis")
        ),
        BirdChecklistEntry(
            "참새목", "종다리과", "해변종다리",
            "Eremophila alpestris", "Horned Lark", "가-1",
            listOf("Eremophila alpestris flava", "Eremophila alpestris brandti")
        ),
        BirdChecklistEntry(
            "참새목", "종다리과", "쇠종다리",
            "Calandrella dukhunensis", "Mongolian Short-toed Lark", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "종다리과", "흰날개종다리",
            "Melanocorypha mongolica", "Mongolian Lark", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "종다리과", "북방쇠종다리",
            "Alaudala cheleensis", "Asian Short-toed Lark", "가-1",
            listOf("Alaudala cheleensis cheleensis")
        ),
        BirdChecklistEntry(
            "참새목", "직박구리과", "직박구리",
            "Hypsipetes amaurotis", "Brown-eared Bulbul", "가-1",
            listOf("Hypsipetes amaurotis amaurotis")
        ),
        BirdChecklistEntry(
            "참새목", "직박구리과", "흰머리검은직박구리",
            "Hypsipetes leucocephalus", "Black Bulbul", "가-1",
            listOf("Hypsipetes leucocephalus leucocephalus")
        ),
        BirdChecklistEntry(
            "참새목", "직박구리과", "검은이마직박구리",
            "Pycnonotus sinensis", "Light-vented Bulbul", "가-1",
            listOf("Pycnonotus sinensis sinensis")
        ),
        BirdChecklistEntry(
            "참새목", "제비과", "갈색제비",
            "Riparia riparia", "Sand Martin", "가-1",
            listOf("Riparia riparia riparia", "Riparia riparia ijimae")
        ),
        BirdChecklistEntry(
            "참새목", "제비과", "바위산제비",
            "Ptyonoprogne rupestris", "Eurasian Crag Martin", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "제비과", "제비",
            "Hirundo rustica", "Barn Swallow", "가-1",
            listOf("Hirundo rustica gutturalis", "Hirundo rustica mandschurica")
        ),
        BirdChecklistEntry(
            "참새목", "제비과", "흰턱제비",
            "Delichon lagopodum", "Siberian House Martin", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "제비과", "흰털발제비",
            "Delichon dasypus", "Asian House Martin", "가-1",
            listOf("Delichon dasypus dasypus")
        ),
        BirdChecklistEntry(
            "참새목", "제비과", "귀제비",
            "Cecropis daurica", "Eastern Red-rumped Swallow", "가-1",
            listOf("Cecropis daurica japonica")
        ),
        BirdChecklistEntry(
            "참새목", "휘파람새과", "섬휘파람새",
            "Horornis diphone", "Japanese Bush Warbler", "가-1",
            listOf("Horornis diphone cantans")
        ),
        BirdChecklistEntry(
            "참새목", "휘파람새과", "휘파람새",
            "Horornis canturians", "Manchurian Bush Warbler", "가-1",
            listOf("Horornis canturians borealis")
        ),
        BirdChecklistEntry(
            "참새목", "휘파람새과", "숲새",
            "Urosphena squameiceps", "Asian Stubtail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "오목눈이과", "오목눈이",
            "Aegithalos caudatus", "Long-tailed Tit", "가-1",
            listOf("Aegithalos caudatus caudatus", "Aegithalos caudatus trivirgatus", "Aegithalos caudatus magnus")
        ),
        BirdChecklistEntry(
            "참새목", "오목눈이과", "검은턱오목눈이",
            "Aegithalos glaucogularis", "Silver-throated Bushtit", "가-1",
            listOf("Aegithalos glaucogularis vinaceus")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "노랑가슴솔새",
            "Phylloscopus sibilatrix", "Wood Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "연노랑눈썹솔새",
            "Phylloscopus humei", "Hume's Leaf Warbler", "가-1",
            listOf("Phylloscopus humei mandellii")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "노랑눈썹솔새",
            "Phylloscopus inornatus", "Yellow-browed Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "연노랑허리솔새",
            "Phylloscopus yunnanensis", "Chinese Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "노랑허리솔새",
            "Phylloscopus proregulus", "Pallas's Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "쇠긴다리솔새사촌",
            "Phylloscopus armandii", "Yellow-streaked Warbler", "가-1",
            listOf("Phylloscopus armandii armandii")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "긴다리솔새사촌",
            "Phylloscopus schwarzi", "Radde's Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "노랑배솔새사촌",
            "Phylloscopus affinis", "Tickell's Leaf Warbler", "가-1",
            listOf("Phylloscopus affinis occisinensis")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "솔새사촌",
            "Phylloscopus fuscatus", "Dusky Warbler", "가-1",
            listOf("Phylloscopus fuscatus fuscatus")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "담황턱솔새",
            "Phylloscopus subaffinis", "Buff-throated Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "연노랑솔새",
            "Phylloscopus trochilus", "Willow Warbler", "가-1",
            listOf("Phylloscopus trochilus yakutensis")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "검은다리솔새",
            "Phylloscopus collybita", "Common Chiffchaff", "가-1",
            listOf("Phylloscopus collybita tristis")
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "산솔새",
            "Phylloscopus coronatus", "Eastern Crowned Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "회색머리노랑솔새",
            "Phylloscopus tephrocephalus", "Grey-crowned Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "회색정수리노랑솔새",
            "Phylloscopus omeiensis", "Martens's Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "버들솔새",
            "Phylloscopus plumbeitarsus", "Two-barred Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "사할린되솔새",
            "Phylloscopus borealoides", "Sakhalin Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "되솔새",
            "Phylloscopus tenellipes", "Pale-legged Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "솔새",
            "Phylloscopus xanthodryas", "Japanese Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "큰솔새",
            "Phylloscopus examinandus", "Kamchatka Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "쇠솔새",
            "Phylloscopus borealis", "Arctic Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "노랑배솔새",
            "Phylloscopus ricketti", "Sulphur-breasted Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔새과", "히말라야산솔새",
            "Phylloscopus claudiae", "Claudia's Leaf Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "개개비",
            "Acrocephalus orientalis", "Oriental Reed Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "쇠개개비",
            "Acrocephalus bistrigiceps", "Black-browed Reed Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "풀쇠개개비",
            "Acrocephalus schoenobaenus", "Sedge Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "우수리개개비",
            "Acrocephalus tangorum", "Manchurian Reed Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "북방쇠개개비",
            "Acrocephalus agricola", "Paddyfield Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "덤불개개비",
            "Acrocephalus dumetorum", "Blyth's Reed Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "큰부리개개비",
            "Arundinax aedon", "Thick-billed Warbler", "가-1",
            listOf("Arundinax aedon rufescens")
        ),
        BirdChecklistEntry(
            "참새목", "개개비과", "쇠덤불개개비",
            "Iduna caligata", "Booted Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "붉은허리개개비",
            "Helopsaltes fasciolatus", "Gray's Grasshopper Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "큰개개비",
            "Helopsaltes pryeri", "Marsh Grassbird", "가-1",
            listOf("Helopsaltes pryeri sinensis")
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "북방개개비",
            "Helopsaltes certhiola", "Pallas's Grasshopper Warbler", "가-1",
            listOf("Helopsaltes certhiola certhiola")
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "알락꼬리쥐발귀",
            "Helopsaltes ochotensis", "Middendorff's Grasshopper Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "섬개개비",
            "Helopsaltes pleskei", "Styan's Grasshopper Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "쥐발귀개개비",
            "Locustella lanceolata", "Lanceolated Warbler", "가-1",
            listOf("Locustella lanceolata lanceolata")
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "땅개개비",
            "Locustella tacsanowskia", "Chinese Bush Warbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "섬개개비과", "점무늬가슴쥐발귀",
            "Locustella davidi", "Baikal Bush Warbler", "가-1",
            listOf("Locustella davidi davidi")
        ),
        BirdChecklistEntry(
            "참새목", "개개비사촌과", "개개비사촌",
            "Cisticola juncidis", "Zitting Cisticola", "가-1",
            listOf("Cisticola juncidis brunniceps")
        ),
        BirdChecklistEntry(
            "참새목", "흰턱딱새과", "비늘무늬덤불개개비",
            "Curruca nisoria", "Barred Warbler", "가-1",
            listOf("Curruca nisoria merzbacheri")
        ),
        BirdChecklistEntry(
            "참새목", "흰턱딱새과", "쇠흰턱딱새",
            "Curruca curruca", "Lesser Whitethroat", "가-1",
            listOf("Curruca curruca blythi")
        ),
        BirdChecklistEntry(
            "참새목", "붉은머리오목눈이과", "꼬리치레",
            "Rhopophilus pekinensis", "Beijing Babbler", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "붉은머리오목눈이과", "붉은머리오목눈이",
            "Suthora webbiana", "Vinous-throated Parrotbill", "가-1",
            listOf("Suthora webbiana fulvicauda")
        ),
        BirdChecklistEntry(
            "참새목", "동박새과", "한국동박새",
            "Zosterops erythropleurus", "Chestnut-flanked White-eye", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "동박새과", "작은동박새",
            "Zosterops simplex", "Swinhoe's White-eye", "가-1",
            listOf("Zosterops simplex simplex")
        ),
        BirdChecklistEntry(
            "참새목", "동박새과", "동박새",
            "Zosterops japonicus", "Warbling White-eye", "가-1",
            listOf("Zosterops japonicus japonicus")
        ),
        BirdChecklistEntry(
            "참새목", "웃음지빠귀과", "눈썹웃음지빠귀",
            "Garrulax canorus", "Chinese Hwamei", "가-1",
            listOf("Garrulax canorus canorus")
        ),
        BirdChecklistEntry(
            "참새목", "상모솔새과", "상모솔새",
            "Regulus regulus", "Goldcrest", "가-1",
            listOf("Regulus regulus japonensis")
        ),
        BirdChecklistEntry(
            "참새목", "굴뚝새과", "굴뚝새",
            "Troglodytes troglodytes", "Eurasian Wren", "가-1",
            listOf("Troglodytes troglodytes dauricus")
        ),
        BirdChecklistEntry(
            "참새목", "동고비과", "쇠동고비",
            "Sitta villosa", "Chinese Nuthatch", "가-1",
            listOf("Sitta villosa corea")
        ),
        BirdChecklistEntry(
            "참새목", "동고비과", "동고비",
            "Sitta europaea", "Eurasian Nuthatch", "가-1",
            listOf("Sitta europaea asiatica", "Sitta europaea amurensis", "Sitta europaea bedfordi")
        ),
        BirdChecklistEntry(
            "참새목", "나무발발이과", "나무발발이",
            "Certhia familiaris", "Eurasian Treecreeper", "가-1",
            listOf("Certhia familiaris daurica")
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "검은뿔찌르레기",
            "Acridotheres cristatellus", "Crested Myna", "가-1",
            listOf("Acridotheres cristatellus cristatellus")
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "자바뿔찌르레기",
            "Acridotheres javanicus", "Javan Myna", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "검은머리갈색찌르레기",
            "Acridotheres tristis", "Common Myna", "가-1",
            listOf("Acridotheres tristis tristis")
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "붉은부리찌르레기",
            "Spodiopsar sericeus", "Red-billed Starling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "찌르레기",
            "Spodiopsar cineraceus", "White-cheeked Starling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "북방쇠찌르레기",
            "Agropsar sturninus", "Daurian Starling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "쇠찌르레기",
            "Agropsar philippensis", "Chestnut-cheeked Starling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "잿빛쇠찌르레기",
            "Sturnia sinensis", "White-shouldered Starling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "분홍찌르레기",
            "Pastor roseus", "Rosy Starling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "찌르레기과", "흰점찌르레기",
            "Sturnus vulgaris", "Common Starling", "가-1",
            listOf("Sturnus vulgaris poltaratskyi")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "호랑지빠귀",
            "Zoothera aurea", "White's Thrush", "가-1",
            listOf("Zoothera aurea aurea", "Zoothera aurea toratugumi")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "흰눈썹지빠귀",
            "Geokichla sibirica", "Siberian Thrush", "가-1",
            listOf("Geokichla sibirica sibirica", "Geokichla sibirica davisoni")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "귤빛지빠귀",
            "Geokichla citrina", "Orange-headed Thrush", "가-1",
            listOf("Geokichla citrina courtoisi")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "큰점지빠귀",
            "Turdus mupinensis", "Chinese Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "대륙점지빠귀",
            "Turdus viscivorus", "Mistle Thrush", "가-1",
            listOf("Turdus viscivorus bonapartei")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "대륙검은지빠귀",
            "Turdus mandarinus", "Chinese Blackbird", "가-1",
            listOf("Turdus mandarinus mandarinus")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "붉은날개지빠귀",
            "Turdus iliacus", "Redwing", "가-1",
            listOf("Turdus iliacus iliacus")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "검은지빠귀",
            "Turdus cardis", "Japanese Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "되지빠귀",
            "Turdus hortulorum", "Grey-backed Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "흰눈썹붉은배지빠귀",
            "Turdus obscurus", "Eyebrowed Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "흰배지빠귀",
            "Turdus pallidus", "Pale Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "갈색지빠귀",
            "Turdus feae", "Grey-sided Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "붉은배지빠귀",
            "Turdus chrysolaus", "Brown-headed Thrush", "가-1",
            listOf("Turdus chrysolaus chrysolaus")
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "회색머리지빠귀",
            "Turdus pilaris", "Fieldfare", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "검은목지빠귀",
            "Turdus atrogularis", "Black-throated Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "붉은목지빠귀",
            "Turdus ruficollis", "Red-throated Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "개똥지빠귀",
            "Turdus eunomus", "Dusky Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "지빠귀과", "노랑지빠귀",
            "Turdus naumanni", "Naumann's Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "까치딱새",
            "Copsychus saularis", "Oriental Magpie-Robin", "가-1",
            listOf("Copsychus saularis saularis")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "제비딱새",
            "Muscicapa griseisticta", "Grey-streaked Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "솔딱새",
            "Muscicapa sibirica", "Dark-sided Flycatcher", "가-1",
            listOf("Muscicapa sibirica sibirica")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "회색머리딱새",
            "Muscicapa ferruginea", "Ferruginous Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "갈색솔딱새",
            "Muscicapa muttui", "Brown-breasted Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "쇠솔딱새",
            "Muscicapa dauurica", "Asian Brown Flycatcher", "가-1",
            listOf("Muscicapa dauurica dauurica")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "붉은가슴딱새",
            "Niltava davidi", "Fujian Niltava", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "큰유리새",
            "Cyanoptila cyanomelana", "Blue-and-white Flycatcher", "가-1",
            listOf("Cyanoptila cyanomelana intermedia", "Cyanoptila cyanomelana cyanomelana")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "대륙큰유리새",
            "Cyanoptila cumatilis", "Zappey's Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "파랑딱새",
            "Eumyias thalassinus", "Verditer Flycatcher", "가-1",
            listOf("Eumyias thalassinus thalassinus")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "꼬까울새",
            "Erithacus rubecula", "European Robin", "가-1",
            listOf("Erithacus rubecula tataricus")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰눈썹울새",
            "Luscinia svecica", "Bluethroat", "가-1",
            listOf("Luscinia svecica svecica")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰꼬리유리딱새",
            "Myiomela leucura", "White-tailed Robin", "가-1",
            listOf("Myiomela leucura leucura")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "진홍가슴",
            "Calliope calliope", "Siberian Rubythroat", "가-1",
            listOf("Calliope calliope calliope", "Calliope calliope camtschatkensis")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "쇠유리새",
            "Larvivora cyane", "Siberian Blue Robin", "가-1",
            listOf("Larvivora cyane bochaiensis", "Larvivora cyane cyane")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "붉은가슴울새",
            "Larvivora akahige", "Japanese Robin", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "울새",
            "Larvivora sibilans", "Rufous-tailed Robin", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰눈썹황금새",
            "Ficedula zanthopygia", "Yellow-rumped Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "북방황금새",
            "Ficedula elisae", "Green-backed Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "황금새",
            "Ficedula narcissina", "Narcissus Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "남방황금새",
            "Ficedula owstoni", "Ryukyu Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "노랑딱새",
            "Ficedula mugimaki", "Mugimaki Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "붉은가슴흰꼬리딱새",
            "Ficedula parva", "Red-breasted Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰꼬리딱새",
            "Ficedula albicilla", "Taiga Flycatcher", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "유리딱새",
            "Tarsiger cyanurus", "Red-flanked Bluetail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "부채꼬리바위딱새",
            "Phoenicurus fuliginosus", "Plumbeous Water Redstart", "가-1",
            listOf("Phoenicurus fuliginosus fuliginosus")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰머리바위딱새",
            "Phoenicurus leucocephalus", "White-capped Redstart", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "검은머리딱새",
            "Phoenicurus ochruros", "Black Redstart", "가-1",
            listOf("Phoenicurus ochruros rufiventris")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰이마딱새",
            "Phoenicurus phoenicurus", "Common Redstart", "가-1",
            listOf("Phoenicurus phoenicurus phoenicurus")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "딱새",
            "Phoenicurus auroreus", "Daurian Redstart", "가-1",
            listOf("Phoenicurus auroreus auroreus")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "꼬까직박구리",
            "Monticola gularis", "White-throated Rock Thrush", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "바다직박구리",
            "Monticola solitarius", "Blue Rock Thrush", "가-1",
            listOf("Monticola solitarius pandoo", "Monticola solitarius philippensis")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "검은뺨딱새",
            "Saxicola ferreus", "Grey Bush Chat", "가-1",
            listOf("Saxicola ferreus haringtoni")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "흰죽지검은딱새",
            "Saxicola caprata", "Pied Bush Chat", "가-1",
            listOf("Saxicola caprata rossorum")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "검은딱새",
            "Saxicola stejnegeri", "Amur Stonechat", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "사막딱새",
            "Oenanthe oenanthe", "Northern Wheatear", "가-1",
            listOf("Oenanthe oenanthe libanotica")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "긴다리사막딱새",
            "Oenanthe isabellina", "Isabelline Wheatear", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "검은꼬리사막딱새",
            "Oenanthe deserti", "Desert Wheatear", "가-1",
            listOf("Oenanthe deserti deserti")
        ),
        BirdChecklistEntry(
            "참새목", "솔딱새과", "검은등사막딱새",
            "Oenanthe pleschanka", "Pied Wheatear", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "물까마귀과", "물까마귀",
            "Cinclus pallasii", "Brown Dipper", "가-1",
            listOf("Cinclus pallasii pallasii")
        ),
        BirdChecklistEntry(
            "참새목", "참새과", "섬참새",
            "Passer cinnamomeus", "Russet Sparrow", "가-1",
            listOf("Passer cinnamomeus rutilans")
        ),
        BirdChecklistEntry(
            "참새목", "참새과", "참새",
            "Passer montanus", "Eurasian Tree Sparrow", "가-1",
            listOf("Passer montanus dybowskii", "Passer montanus saturatus")
        ),
        BirdChecklistEntry(
            "참새목", "참새과", "집참새",
            "Passer domesticus", "House Sparrow", "가-1",
            listOf("Passer domesticus domesticus")
        ),
        BirdChecklistEntry(
            "참새목", "납부리새과", "얼룩무늬납부리새",
            "Lonchura punctulata", "Scaly-breasted Munia", "가-1",
            listOf("Lonchura punctulata topela")
        ),
        BirdChecklistEntry(
            "참새목", "바위종다리과", "바위종다리",
            "Prunella collaris", "Alpine Accentor", "가-1",
            listOf("Prunella collaris erythropygia")
        ),
        BirdChecklistEntry(
            "참새목", "바위종다리과", "멧종다리",
            "Prunella montanella", "Siberian Accentor", "가-1",
            listOf("Prunella montanella montanella")
        ),
        BirdChecklistEntry(
            "참새목", "바위종다리과", "쇠바위종다리",
            "Prunella rubida", "Japanese Accentor", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "물레새",
            "Dendronanthus indicus", "Forest Wagtail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "긴발톱할미새",
            "Motacilla tschutschensis", "Eastern Yellow Wagtail", "가-1",
            listOf("Motacilla tschutschensis plexa", "Motacilla tschutschensis tschutschensis", "Motacilla tschutschensis macronyx", "Motacilla tschutschensis taivana")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "노랑머리할미새",
            "Motacilla citreola", "Citrine Wagtail", "가-1",
            listOf("Motacilla citreola citreola")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "노랑할미새",
            "Motacilla cinerea", "Grey Wagtail", "가-1",
            listOf("Motacilla cinerea cinerea")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "알락할미새",
            "Motacilla alba", "White Wagtail", "가-1",
            listOf("Motacilla alba alba", "Motacilla alba personata", "Motacilla alba baicalensis", "Motacilla alba ocularis", "Motacilla alba lugens", "Motacilla alba leucopsis", "Motacilla alba alboides")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "검은등할미새",
            "Motacilla grandis", "Japanese Wagtail", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "큰밭종다리",
            "Anthus richardi", "Richard's Pipit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "쇠밭종다리",
            "Anthus godlewskii", "Blyth's Pipit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "풀밭종다리",
            "Anthus pratensis", "Meadow Pipit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "나무밭종다리",
            "Anthus trivialis", "Tree Pipit", "가-1",
            listOf("Anthus trivialis trivialis")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "힝둥새",
            "Anthus hodgsoni", "Olive-backed Pipit", "가-1",
            listOf("Anthus hodgsoni hodgsoni", "Anthus hodgsoni yunnanensis")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "흰등밭종다리",
            "Anthus gustavi", "Pechora Pipit", "가-1",
            listOf("Anthus gustavi gustavi", "Anthus gustavi menzbieri")
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "한국밭종다리",
            "Anthus roseatus", "Rosy Pipit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "붉은가슴밭종다리",
            "Anthus cervinus", "Red-throated Pipit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "밭종다리",
            "Anthus japonicus", "Siberian Pipit", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "할미새과", "옅은밭종다리",
            "Anthus spinoletta", "Water Pipit", "가-1",
            listOf("Anthus spinoletta blakistoni")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "푸른머리되새",
            "Fringilla coelebs", "Eurasian Chaffinch", "가-1",
            listOf("Fringilla coelebs coelebs")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "되새",
            "Fringilla montifringilla", "Brambling", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "콩새",
            "Coccothraustes coccothraustes", "Hawfinch", "가-1",
            listOf("Coccothraustes coccothraustes shulpini", "Coccothraustes coccothraustes japonicus")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "밀화부리",
            "Eophona migratoria", "Chinese Grosbeak", "가-1",
            listOf("Eophona migratoria migratoria")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "큰부리밀화부리",
            "Eophona personata", "Japanese Grosbeak", "가-1",
            listOf("Eophona personata personata", "Eophona personata magnirostris")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "솔양진이",
            "Pinicola enucleator", "Pine Grosbeak", "가-1",
            listOf("Pinicola enucleator sakhalinensis")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "멋쟁이새",
            "Pyrrhula pyrrhula", "Eurasian Bullfinch", "가-1",
            listOf("Pyrrhula pyrrhula cineracea", "Pyrrhula pyrrhula cassinii", "Pyrrhula pyrrhula griseiventris", "Pyrrhula pyrrhula rosacea")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "바위양진이",
            "Bucanetes mongolicus", "Mongolian Finch", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "갈색양진이",
            "Leucosticte arctoa", "Asian Rosy Finch", "가-1",
            listOf("Leucosticte arctoa brunneonucha")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "붉은양진이",
            "Carpodacus erythrinus", "Common Rosefinch", "가-1",
            listOf("Carpodacus erythrinus grebnitskii")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "긴꼬리홍양진이",
            "Carpodacus sibiricus", "Siberian Long-tailed Rosefinch", "가-1",
            listOf("Carpodacus sibiricus ussuriensis")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "양진이",
            "Carpodacus roseus", "Pallas's Rosefinch", "가-1",
            listOf("Carpodacus roseus roseus")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "방울새",
            "Chloris sinica", "Oriental Greenfinch", "가-1",
            listOf("Chloris sinica ussuriensis", "Chloris sinica kawarahiba", "Chloris sinica minor")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "홍방울새",
            "Acanthis flammea", "Redpoll", "가-1",
            listOf("Acanthis flammea flammea", "Acanthis flammea exilipes")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "솔잣새",
            "Loxia curvirostra", "Red Crossbill", "가-1",
            listOf("Loxia curvirostra japonica")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "흰죽지솔잣새",
            "Loxia leucoptera", "Two-barred Crossbill", "가-1",
            listOf("Loxia leucoptera bifasciata")
        ),
        BirdChecklistEntry(
            "참새목", "되새과", "검은머리방울새",
            "Spinus spinus", "Eurasian Siskin", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "긴발톱멧새과", "긴발톱멧새",
            "Calcarius lapponicus", "Lapland Longspur", "가-1",
            listOf("Calcarius lapponicus kamtschaticus", "Calcarius lapponicus alascensis")
        ),
        BirdChecklistEntry(
            "참새목", "긴발톱멧새과", "흰멧새",
            "Plectrophenax nivalis", "Snow Bunting", "가-1",
            listOf("Plectrophenax nivalis vlasowae")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "노랑멧새",
            "Emberiza citrinella", "Yellowhammer", "가-1",
            listOf("Emberiza citrinella erythrogenys")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "흰머리멧새",
            "Emberiza leucocephalos", "Pine Bunting", "가-1",
            listOf("Emberiza leucocephalos leucocephalos")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "바위멧새",
            "Emberiza godlewskii", "Godlewski's Bunting", "가-1",
            listOf("Emberiza godlewskii godlewskii")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "멧새",
            "Emberiza cioides", "Meadow Bunting", "가-1",
            listOf("Emberiza cioides weigoldi", "Emberiza cioides castaneiceps", "Emberiza cioides ciopsis")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "점박이멧새",
            "Emberiza jankowskii", "Jankowski's Bunting", "가-2",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "회색머리멧새",
            "Emberiza hortulana", "Ortolan Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "흰배멧새",
            "Emberiza tristrami", "Tristram's Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "붉은뺨멧새",
            "Emberiza fucata", "Chestnut-eared Bunting", "가-1",
            listOf("Emberiza fucata fucata")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "쇠붉은뺨멧새",
            "Emberiza pusilla", "Little Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "노랑눈썹멧새",
            "Emberiza chrysophrys", "Yellow-browed Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "쑥새",
            "Emberiza rustica", "Rustic Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "노랑턱멧새",
            "Emberiza elegans", "Yellow-throated Bunting", "가-1",
            listOf("Emberiza elegans elegans")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "검은머리촉새",
            "Emberiza aureola", "Yellow-breasted Bunting", "가-1",
            listOf("Emberiza aureola ornata")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "꼬까참새",
            "Emberiza rutila", "Chestnut Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "검은머리멧새",
            "Emberiza melanocephala", "Black-headed Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "붉은머리멧새",
            "Emberiza bruniceps", "Red-headed Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "무당새",
            "Emberiza sulphurata", "Yellow Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "촉새",
            "Emberiza spodocephala", "Black-faced Bunting", "가-1",
            listOf("Emberiza spodocephala spodocephala")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "섬촉새",
            "Emberiza personata", "Masked Bunting", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "검은멧새",
            "Emberiza variabilis", "Grey Bunting", "가-1",
            listOf("Emberiza variabilis variabilis")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "북방검은머리쑥새",
            "Emberiza pallasi", "Pallas's Reed Bunting", "가-1",
            listOf("Emberiza pallasi pallasi", "Emberiza pallasi polaris")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "쇠검은머리쑥새",
            "Emberiza yessoensis", "Ochre-rumped Bunting", "가-1",
            listOf("Emberiza yessoensis yessoensis", "Emberiza yessoensis continentalis")
        ),
        BirdChecklistEntry(
            "참새목", "멧새과", "검은머리쑥새",
            "Emberiza schoeniclus", "Common Reed Bunting", "가-1",
            listOf("Emberiza schoeniclus pyrrhulina")
        ),
        BirdChecklistEntry(
            "참새목", "신대륙멧새과", "흰정수리북미멧새",
            "Zonotrichia leucophrys", "White-crowned Sparrow", "가-1",
            listOf("Zonotrichia leucophrys gambelii")
        ),
        BirdChecklistEntry(
            "참새목", "신대륙멧새과", "노랑정수리북미멧새",
            "Zonotrichia atricapilla", "Golden-crowned Sparrow", "가-1",
            emptyList()
        ),
        BirdChecklistEntry(
            "참새목", "신대륙멧새과", "초원멧새",
            "Passerculus sandwichensis", "Savannah Sparrow", "가-1",
            listOf("Passerculus sandwichensis anthinus")
        )
    )

    val byKoreanName: Map<String, BirdChecklistEntry> = ALL.associateBy { it.koreanName }
    val byScientificName: Map<String, BirdChecklistEntry> = ALL.associateBy { it.scientificName }
}
