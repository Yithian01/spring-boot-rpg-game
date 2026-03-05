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

import java.util.ArrayList;
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

    @Getter private Map<Integer, GrowthMeta> growthMetaMap;
    @Getter private Map<Integer, TribeMeta> tribeMetaMap;
    @Getter private Map<Integer, ReligionMeta> religionMetaMap;
    @Getter private Map<Integer, StatMeta> statMetaMap;
    @Getter private Map<Integer, ItemMeta> itemMetaMap;
    @Getter private Map<Integer, TribeInitialMeta> tribeInitialMetaMap;
    @Getter private Map<Integer, MonsterMeta> monsterMetaMap;
    @Getter private Map<Integer, SkillMeta> skillMetaMap;
    @Getter private Map<Integer, SkillMeta> monsterSkillMetaMap;
    @Getter private Map<Integer, DungeonMeta> dungeonMetaMap;
    @Getter private Map<Integer, MonsterSpawnTableMeta> monsterSpawnTableMetaMap;
    @Getter private Map<String, DropTableMeta> dropTableMetaMap;


    @Override
    public void run(ApplicationArguments args) {
        init();
    }

    public void init() {
        log.info("========== [GameData] 초기화 시작 ==========");
        long start = System.currentTimeMillis();

        // 1. 성장 정보 로딩 (일급 컬렉션)
        this.growthMetaMap = loadMapData("growth.json", GrowthMeta.class, GrowthMeta::getId);

        // 2. 종족 정보 로딩 (ID로 빠르게 찾기 위해 Map으로 변환)
        this.tribeMetaMap = loadMapData("tribe.json", TribeMeta.class, TribeMeta::getId);

        // 3. 종교 정보 로딩
        this.religionMetaMap = loadMapData("religion.json", ReligionMeta.class, ReligionMeta::getId);

        // 4. 스탯 정보 로딩
        this.statMetaMap = loadMapData("stat.json", StatMeta.class, StatMeta::getId);

        // 5. 아이템 정보 로딩
        this.itemMetaMap = loadMapData("item.json", ItemMeta.class, ItemMeta::getId);

        //6. 플레이어블 캐릭터 정보 로딩
        this.tribeInitialMetaMap = loadMapData("tribe-initial-meta.json", TribeInitialMeta.class, TribeInitialMeta::getTribeId);

        // 7. 몬스터 정보 로딩 추가
        this.monsterMetaMap = loadMapData("monster.json", MonsterMeta.class, MonsterMeta::getId);

        // 8. 플레이어 스킬 정보 로딩
        this.skillMetaMap = loadMapData("skill.json", SkillMeta.class, SkillMeta::getId);

        // 9. 몬스터 스킬 정보 로딩
        this.monsterSkillMetaMap = loadMapData("monster-skill.json", SkillMeta.class, SkillMeta::getId);

        // 10. 던전 정보 로딩
        this.dungeonMetaMap = loadMapData("dungeon.json", DungeonMeta.class, DungeonMeta::getId);

        // 11. 몬스터 테이블 정보 로딩
        this.monsterSpawnTableMetaMap = loadMapData("monster-spawn-table.json", MonsterSpawnTableMeta.class, MonsterSpawnTableMeta::getTableId);

        // 12. 드랍 테이블 정보 로딩
        this.dropTableMetaMap = loadMapData("drop-table.json", DropTableMeta.class, DropTableMeta::getId);

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
        int totalRate = growthMetaMap.values().stream()
                .mapToInt(GrowthMeta::getRate)
                .sum();

        // 2. 0 ~ (TotalRate - 1) 사이의 랜덤 값을 뽑습니다.
        int randomVal = random.nextInt(totalRate);

        // 3. 루프를 돌며 구간을 확인합니다.
        int currentSum = 0;
        for (GrowthMeta meta : growthMetaMap.values()) {
            currentSum += meta.getRate();
            if (randomVal < currentSum) {
                return meta.getId(); // 당첨된 ID 반환
            }
        }

        return 7;
    }

    /**
     * 잠재력 ID를 받아 해당 등급(S, A, B...)을 반환
     */
    public String getPotentialGrade(int potentialId) {
        GrowthMeta meta = growthMetaMap.get(potentialId);
        return (meta != null) ? meta.getGrade() : "F";
    }

    /**
     * 잠재력 ID를 받아 실제 성장 배율(weight)을 반환 (나중에 수련 로직에서 사용)
     */
    public double getPotentialWeight(int potentialId) {
        GrowthMeta meta = growthMetaMap.get(potentialId);
        return (meta != null) ? meta.getWeight() : 0.2;
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

    /**
     * 이모티콘 헬퍼 메서드
     * @param code 상태이상 String
     * @return 표현하는 아이콘 반환
     */
    public String getIcon(String code) {
        return switch (code) {
            case "STUN" -> "💫";
            case "BURN" -> "🔥";
            case "POISON, TOXIC" -> "🧪";
            case "BLEED" -> "🩸";
            case "FROZEN", "FREEZE" -> "❄️";
            case "SHOCK" -> "⚡";
            case "PARALYSIS" -> "🗲";
            case "CURSE", "WEAKNESS" -> "💀";
            case "PAIN" -> "🖤";
            case "BUFF" -> "🔼";
            case "DEBUFF" -> "🔽";
            case "REGEN", "HEAL" -> "💚";
            default -> "💢";
        };
    }

    /**
     * 상태이상 코드를 한글 명칭으로 변환 (UI 표시용)
     */
    public String getStatusName(String code) {
        if (code == null) return null;
        return switch (code) {
            case "STUN" -> "기절";
            case "BLEED" -> "출혈";
            case "BURN" -> "화상";
            case "POISON" -> "중독";
            case "PAIN" -> "고통";
            case "STRENGTHEN" -> "강화";
            case "WEAKNESS" -> "약화";
            case "REGEN" -> "재생";
            default -> code;
        };
    }

    /**
     * 속성을 문자열로
     * @param element 속성
     * @return 속성 문자
     */
    public String getKoreanElement(String element) {
        switch(element) {
            case "PHYSICAL": return "물리";
            case "FIRE": return "화염";
            case "ICE": return "냉기";
            case "LIGHT": return "번개";
            case "EARTH": return "대지";
            case "HOLY": return "신성";
            case "DARK": return "암흑";
            case "CHAOS": return "혼돈";
            case "TOXIC": return "독성";
            default: return "";
        }
    }

    /**
     * UI에 보낼 스탯명칭 ID --> NAME
     * @param statId 스탯 ID
     * @return 스탯 NAME
     */
    public String getStatName(Integer statId){
        return this.getStatMetaMap().get(statId).getName();
    }

    /**
     * 아이템 추가 효과 UI 변환용
     */
    public Map<String, String> STAT_NAME_MAP = Map.ofEntries(
            Map.entry("maxHp", "최대 체력"),
            Map.entry("maxMp", "최대 마나"),
            Map.entry("maxStamina", "최대 스태미나"),

            Map.entry("hpRegen", "체력 재생"),
            Map.entry("mpRegen", "마나 재생"),
            Map.entry("moveSpeed", "이동 속도"),
            Map.entry("maxTurns", "행동 턴"),

            Map.entry("critRate", "치명타 확률"),
            Map.entry("critDmg", "치명타 피해"),
            Map.entry("accuracy", "명중률"),
            Map.entry("dodge", "회피율"),

            Map.entry("statusResist", "상태이상 저항"),
            Map.entry("bleedRes", "출혈 저항"),
            Map.entry("stunRes", "스턴 저항"),
            Map.entry("burnRes", "화상 저항"),
            Map.entry("frozenRes", "빙결 저항"),
            Map.entry("poisonRes", "중독 저항"),

            Map.entry("bleedPen", "출혈 확률"),
            Map.entry("stunPen", "스턴 확률"),
            Map.entry("burnPen", "화상 확률"),
            Map.entry("frozenPen", "빙결 확률"),
            Map.entry("poisonPen", "중독 확률"),

            Map.entry("meleeAtk", "물리 공격"),
            Map.entry("physDef", "물리 방어"),
            Map.entry("physRes", "물리 저항"),
            Map.entry("physPen", "물리 관통"),

            Map.entry("magicAtk", "마법 공격"),
            Map.entry("magRes", "마법 저항"),
            Map.entry("magPen", "마법 관통"),

            Map.entry("fireAtk", "화염 공격"),
            Map.entry("iceAtk", "냉기 공격"),
            Map.entry("lightAtk", "번개 공격"),
            Map.entry("earthAtk", "대지 공격"),
            Map.entry("holyAtk", "신성 공격"),
            Map.entry("darkAtk", "암흑 공격"),
            Map.entry("chaosAtk", "혼돈 공격"),
            Map.entry("toxicAtk", "독성 공격"),

            Map.entry("fireRes", "화염 저항"),
            Map.entry("iceRes", "냉기 저항"),
            Map.entry("lightRes", "번개 저항"),
            Map.entry("earthRes", "대지 저항"),
            Map.entry("holyRes", "신성 저항"),
            Map.entry("darkRes", "암흑 저항"),
            Map.entry("chaosRes", "혼돈 저항"),
            Map.entry("toxicRes", "독성 저항"),

            Map.entry("firePen", "화염 관통"),
            Map.entry("icePen", "냉기 관통"),
            Map.entry("lightPen", "번개 관통"),
            Map.entry("earthPen", "대지 관통"),
            Map.entry("holyPen", "신성 관통"),
            Map.entry("darkPen", "암흑 관통"),
            Map.entry("chaosPen", "혼돈 관통"),
            Map.entry("toxicPen", "독성 관통")
    );

    /**
     * 던전 ID를 받아 해당 던전의 스폰 테이블에 정의된 확률대로 몬스터를 반환합니다.
     */
    public MonsterMeta getRandomMonsterByDungeon(int dungeonId) {
        // 1. 던전 메타 확인
        DungeonMeta dungeon = dungeonMetaMap.get(dungeonId);
        if (dungeon == null) return null;

        // 2. 해당 던전의 스폰 테이블 확인
        MonsterSpawnTableMeta table = monsterSpawnTableMetaMap.get(dungeon.getMonsterTableId());
        if (table == null) return null;

        // 3. 테이블 내부에 만들어둔 랜덤 로직 호출 (이전에 만든 pickRandomMonsterId 사용)
        int monsterId = table.pickRandomMonsterId(this.random);

        // 4. 최종 몬스터 메타 반환
        return monsterMetaMap.get(monsterId);
    }

    /**
     * 마석 등급(9~1)을 받아 랜덤하게 스킬 등급(COMMON~LEGENDARY)을 반환합니다.
     * @param magicStoneGrade 마석의 아이템 메타 ID (9: 최하급 ~ 1: 최상급)
     * @return 결정된 스킬 등급 문자열
     */
    public String rollSkillGrade(int magicStoneGrade) {
        int roll = random.nextInt(100); // 0~99
        return switch (magicStoneGrade) {
            case 9 -> "COMMON"; // 9등급: 무조건 커먼
            case 8 -> (roll < 70) ? "COMMON" : "UNCOMMON"; // 8등급: 커먼(70%), 언커먼(30%)
            case 7 -> (roll < 30) ? "COMMON" : "UNCOMMON"; // 7등급: 커먼(30%), 언커먼(70%)
            case 6 -> (roll < 60) ? "UNCOMMON" : "RARE"; // 6등급: 언커먼(60%), 레어(40%)
            case 5 -> { // 5등급: 언커먼(20%), 레어(70%), 에픽(10%)
                if (roll < 20) yield "UNCOMMON";
                if (roll < 90) yield "RARE";
                yield "EPIC";
            }
            case 4 -> (roll < 50) ? "RARE" : "EPIC";  // 4등급: 레어(50%), 에픽(50%)
            case 3 -> { // 3등급: 레어(10%), 에픽(70%), 레전더리(20%)
                if (roll < 10) yield "RARE";
                if (roll < 80) yield "EPIC";
                yield "LEGENDARY";
            }
            case 2 ->(roll < 50) ? "EPIC" : "LEGENDARY"; // 2등급: 에픽(50%), 레전더리(50%)
            case 1 -> (roll < 20) ? "EPIC" : "LEGENDARY"; // 1등급: 에픽(20%), 레전더리(80%)
            default -> "COMMON";
        };
    }

    /**
     * 등급별로 스킬 메타를 필터링하여 리스트로 반환 (메모리 풀 활용)
     */
    public List<SkillMeta> getSkillsByGrade(String grade) {
        return skillMetaMap.values().stream()
                .filter(s -> s.getGrade().equalsIgnoreCase(grade))
                .collect(Collectors.toList());
    }

    /**
     * 스탯 ID가 0이거나 정의되지 않은 경우 랜덤으로 카테고리 결정
     * @param statId 스킬의 보정 스탯
     * @return PHYSIQUE, AGILITY 등 반환
     */
    public String getStatBaseCategory(int statId) {
        if (statId <= 0) {
            String[] categories = {"PHYSIQUE", "AGILITY", "PERCEPTION", "SPIRIT"};
            return categories[random.nextInt(categories.length)];
        }
        StatMeta meta = this.statMetaMap.get(statId);
        if (meta == null) {
            return "PHYSIQUE";
        }
        return meta.getCategory();
    }

    /**
     * 마석 등급에 따른 중복 스킬 연마 보너스 수치를 반환합니다.
     * @param stoneGrade 마석 메타 ID (1~9)
     * @return 증가할 스탯 수치
     */
    public int calculateBonusValue(int stoneGrade) {
        return switch (stoneGrade) {
            case 1 -> 15;
            case 2, 3 -> 10;
            case 4, 5, 6 -> 5;
            case 7, 8 -> 2;
            case 9 -> 1;
            default -> 1;
        };
    }

    public List<String> createSkillScalingInfo(SkillMeta meta) {
        List<String> scalingInfo = new ArrayList<>();

        if (meta.getDamageScaling() != null) {
            // [A] 공격력 계수 (물리/마법 + 속성)
            boolean isMagic = "MAGIC".equals(meta.getType()) || "MAGICAL".equals(meta.getType()) || "HEAL".equals(meta.getType()) ;
            String baseLabel = isMagic ? "마법" : "물리";
            String element = (meta.getEffect() != null) ? meta.getEffect().getElement() : "NONE";

            double atkScaling = meta.getDamageScaling().getOrDefault(isMagic ? "magicAtk" : "meleeAtk", 0.0);
            if (atkScaling > 0) {
                String elementLabel = getKoreanElement(element);
                String label = (element.equals("NONE") || element.equals("PHYSICAL")) ? baseLabel : baseLabel + "+" + elementLabel;
                scalingInfo.add(label + " : " + (int) (atkScaling * 100) + "%");
            }

            // [B] 세부 스탯 보정 (제공해주신 스탯 리스트 기반)
            if (meta.getStatScaling() != null) {
                meta.getStatScaling().forEach((statId, factor) -> {
                    String statName = getStatName(statId);
                    scalingInfo.add(statName + " : " + (int) (factor * 100) + "%");
                });
            }
        }
        return scalingInfo;
    }

    public List<String> createSkillModifierInfo(SkillMeta meta) {
        List<String> modifierInfo = new ArrayList<>();
        SkillEffect effect = meta.getEffect();
        if (effect == null) return modifierInfo;

        // 1. 베이스 스탯 가산 (statOffsets: Integer) -> "근력 +5"
        if (effect.getStatOffsets() != null) {
            effect.getStatOffsets().forEach((statId, value) -> {
                String name = getStatName(statId);
                if (value != 0) {
                    String sign = value > 0 ? "+" : "";
                    modifierInfo.add(name + " " + sign + value);
                }
            });
        }

        // 2. 베이스 스탯 배율 (statModifiers: Double) -> "근력 +20%"
        if (effect.getStatModifiers() != null) {
            effect.getStatModifiers().forEach((statId, value) -> {
                String name = getStatName(statId);
                int percent = (int) Math.round((value - 1.0) * 100);
                if (percent != 0) {
                    String sign = percent > 0 ? "+" : "";
                    modifierInfo.add(name + " " + sign + percent + "%");
                }
            });
        }

        // 3. 전투 스탯 가산 (combatStatOffsets: Double) -> "화염 저항 +10%"
        // fireRes: 0.1 같은 경우를 여기서 처리
        if (effect.getCombatStatOffsets() != null) {
            effect.getCombatStatOffsets().forEach((key, value) -> {
                String name = STAT_NAME_MAP.getOrDefault(key, key);
                int displayVal = (int) (value * 100); // 0.1 -> 10
                if (displayVal != 0) {
                    String sign = displayVal > 0 ? "+" : "";
                    modifierInfo.add(name + " " + sign + displayVal + "%");
                }
            });
        }

        // 4. 전투 스탯 배율 (combatStatModifiers: Double) -> "명중률 +20%"
        if (effect.getCombatStatModifiers() != null) {
            effect.getCombatStatModifiers().forEach((key, value) -> {
                String name = STAT_NAME_MAP.getOrDefault(key, key);
                int percent = (int) Math.round((value - 1.0) * 100);
                if (percent != 0) {
                    String sign = percent > 0 ? "+" : "";
                    modifierInfo.add(name + " " + sign + percent + "%");
                }
            });
        }

        processSpecialEffect(effect, modifierInfo);

        return modifierInfo;
    }

    // 상세 수치 중 누락되기 쉬운 특수 필드들 처리
    private void processSpecialEffect(SkillEffect effect, List<String> info) {
        if (effect.getPenetration() != null && effect.getPenetration() > 0) {
            info.add("방어 관통: " + (int)(effect.getPenetration() * 100) + "%");
        }
        if (effect.getCritRate() != null && effect.getCritRate() > 0) {
            info.add("추가 치명타율: +" + effect.getCritRate() + "%");
        }
        if (effect.getCritDamage() != null && effect.getCritDamage() > 0) {
            info.add("추가 치명타배율: +" + effect.getCritDamage() + "%");
        }
    }
}