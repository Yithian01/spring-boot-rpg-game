package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonsterMeta {
    private int id;
    private String name;
    private int tier;           // 9(약함) ~ 1(강함)
    private int type;           // 0: 일반, 1: 변이종 등 (JSON 기준)
    private String description;
    private String img;

    // 1. 몬스터 본체의 전투 능력치
    private CombatStats stats;

    // 2. 스킬 아이디 분리 (액티브 / 패시브)
    private List<Integer> activeSkillIds;
    private List<Integer> passiveSkillIds;

    // 3. 전투 및 보상 관련
    private int baseActionPoints;
    private int expReward;      // 최초 처치 시 부여할 경험치
    private String dropTableId; // 드랍 테이블 key 값

    // 4. 정수(Essence) 드랍 시 부여될 보너스 메타 & 스킬
    private EssenceBonusMeta essenceBonus;
    private List<Integer> essenceSkillIds;
    private Double dropRate;

    /**
     * 정수에 포함될 능력치 보너스 정보를 담는 내부 클래스
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EssenceBonusMeta {
        // baseStats: { "21": 1 } -> 21번 부위(예: 신경계)에 1포인트
        private Map<String, Integer> baseStats;

        // combatStats: { "dodge": 2.0 } -> 회피율 2.0 증가
        private Map<String, Double> combatStats;
    }
}