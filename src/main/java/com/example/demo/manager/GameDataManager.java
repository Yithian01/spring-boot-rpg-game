package com.example.demo.manager;

import com.example.demo.domain.meta.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class GameDataManager  implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Getter private Map<Integer, GrowthMeta> growthMap;
    @Getter private Map<Integer, TribeMeta> tribeMap;
    @Getter private Map<Integer, ReligionMeta> religionMap;
    @Getter private Map<Integer, StatMeta> statMap;
    @Getter private Map<Integer, ItemMeta> itemMap;
    @Getter private Map<Integer, TribeInitialMeta> tribeInitialMetaMap;
    @Getter private Map<Integer, MonsterMeta> monsterMetaMap;
    @Getter private Map<Integer, SkillMeta> skillMetaMap;

    @Override
    public void run(ApplicationArguments args) {
        init();
    }

    public void init() {
        log.info("========== [GameData] 초기화 시작 ==========");
        long start = System.currentTimeMillis();

        // 1. 성장 정보 로딩 (일급 컬렉션)
        this.growthMap = loadMapData("growth.json", GrowthMeta.class, GrowthMeta::getId);

        // 2. 종족 정보 로딩 (ID로 빠르게 찾기 위해 Map으로 변환)
        this.tribeMap = loadMapData("tribe.json", TribeMeta.class, TribeMeta::getId);

        // 3. 종교 정보 로딩
        this.religionMap = loadMapData("religion.json", ReligionMeta.class, ReligionMeta::getId);

        // 4. 스탯 정보 로딩
        this.statMap = loadMapData("stat.json", StatMeta.class, StatMeta::getId);

        // 5. 아이템 정보 로딩
        this.itemMap = loadMapData("item.json", ItemMeta.class, ItemMeta::getId);

        //6. 플레이어블 캐릭터 정보 로딩
        this.tribeInitialMetaMap = loadMapData("tribe-initial-meta.json", TribeInitialMeta.class, TribeInitialMeta::getTribeId);

        // 7. 몬스터 정보 로딩 추가
        this.monsterMetaMap = loadMapData("monster.json", MonsterMeta.class, MonsterMeta::getId);

        // 8. 플레이어 스킬 정보 로딩 추가
        this.skillMetaMap = loadMapData("skill.json", SkillMeta.class, SkillMeta::getId);

        long end = System.currentTimeMillis();
        log.info("========== [GameData] 초기화 완료 (소요시간: {}ms) ==========", end - start);
    }

    /**
     * 공통 로딩 로직 (제네릭 활용) - 리스트 형태의 JSON을 읽어서 Map으로 변환
     * @param fileName
     * @param clazz
     * @param keyMapper
     * @return
     * @param <T>
     * @param <K>
     */
    private <T, K> Map<K, T> loadMapData(String fileName, Class<T> clazz, Function<T, K> keyMapper) {
        try {
            ClassPathResource resource = new ClassPathResource("meta/" + fileName);
            // JSON이 [ {object}, {object} ] 배열 형태라고 가정
            List<T> list = objectMapper.readValue(
                    resource.getInputStream(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz)
            );

            log.info("  -> {} 로딩 완료 (항목: {}개)", fileName, list.size());

            return list.stream().collect(Collectors.toMap(keyMapper, Function.identity()));
        } catch (Exception e) {
            log.error(fileName + " 로딩 실패", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 랜덤 성장치 초기화 함수
     * @return int 로 각 포텐셜 반환
     */
    public int getRandomGrowthId() {
        // 1. 전체 가중치(Rate)의 합을 구합니다. (예: 1+3+10+21+30+30+20 = 115)
        // (데이터가 적으므로 매번 계산해도 성능 문제 없음)
        int totalRate = growthMap.values().stream()
                .mapToInt(GrowthMeta::getRate)
                .sum();

        // 2. 0 ~ (TotalRate - 1) 사이의 랜덤 값을 뽑습니다.
        int randomVal = random.nextInt(totalRate);

        // 3. 루프를 돌며 구간을 확인합니다.
        int currentSum = 0;
        for (GrowthMeta meta : growthMap.values()) {
            currentSum += meta.getRate();
            if (randomVal < currentSum) {
                return meta.getId(); // 당첨된 ID 반환
            }
        }

        return 7;
    }

    /**
     * 아이템 ID를 입력받아, 해당 아이템이 속한 종교(또는 그룹) ID를 반환합니다.
     * @param itemId 아이템 고유 번호
     * @return 1=무교(공용), 2~21=특정종교, 0=오류/범위밖
     */
    public int getItemTargetReligionId(int itemId) {
        // 범위 밖 예외 처리
        if (itemId < 1 || itemId > 1250) return 0;

        // --- [구간 1] 100개 단위 할당 ---
        if (itemId <= 100) return 1;   // 1~100: 무교 (General)
        if (itemId <= 200) return 2;   // 101~200: 2번 종교
        if (itemId <= 300) return 3;   // 201~300: 3번 종교 (적혈)

        // --- [구간 2] 50개 단위 할당 ---
        if (itemId <= 350) return 4;   // 301~350
        if (itemId <= 400) return 5;   // 351~400

        // --- [구간 3] 특이 케이스 (100개) ---
        if (itemId <= 500) return 6;   // 401~500 (공허)

        // --- [구간 4] 다시 50개 단위 정규 구간 ---
        if (itemId <= 550) return 7;
        if (itemId <= 600) return 8;
        if (itemId <= 650) return 9;
        if (itemId <= 700) return 10;
        if (itemId <= 750) return 11;
        if (itemId <= 800) return 12;
        if (itemId <= 850) return 13;
        if (itemId <= 900) return 14;
        if (itemId <= 950) return 15;
        if (itemId <= 1000) return 16;
        if (itemId <= 1050) return 17;
        if (itemId <= 1100) return 18;
        if (itemId <= 1150) return 19;
        if (itemId <= 1200) return 20;
        if (itemId <= 1250) return 21;

        return 0; // 혹시 모를 예외
    }
}