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
        private String type;        // DAMAGE, ESCAPE, BUFF 등
        private String status;      // STUN, BLEED (있을 경우)
        private Integer chance;     // 상태이상 확률
        private Double critMod;     // 치명타 배수
        private Integer baseChance; // 도망 확률 등
    }
}