package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillMeta {
    private int id;
    private String name;
    private String icon;
    private String description;
    private String type;         // PHYSICAL, MAGIC, UTILITY 등
    private int turnCost;        // 행동 소모 턴 (AP)

    private Map<String, Integer> cost;       // stamina, mp 등
    private Map<Integer, Double> scaling;    // 스탯 인덱스 : 계수

    private SkillEffect effect;
    private String requiredWeapon; // SWORD, BOW, BLUNT, NONE 등

    @Data
    public static class SkillEffect {
        private String type;            // DAMAGE, ESCAPE, BUFF, DEBUFF
        private String element;         // FIRE, ICE, LIGHTNING, PHYSICAL, NONE

        // --- 지속 효과 관련 ---
        private String status;          // STUN, BLEED, BURN, POISON, STRENGTHEN(강화) 등
        private Integer duration;       // 효과 지속 턴 수
        private Integer chance;         // 상태이상/효과 부여 확률

        // --- 수치 보정 관련 ---
        private Double critMod;         // 치명타 배수
        private Integer baseChance;     // 도망 기본 확률

        // --- 버프/디버프 상세 (일관성을 위해 Map 활용) ---
        // Key: StatId(1~24), Value: 보정치 (1.2는 20% 상승, 0.8은 20% 감소)
        private Map<Integer, Double> statModifiers;
    }
}