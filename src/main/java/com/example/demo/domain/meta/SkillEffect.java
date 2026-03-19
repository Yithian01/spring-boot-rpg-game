package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillEffect {
    private String type;            // DAMAGE, ESCAPE, BUFF, DEBUFF
    private String element;         // FIRE, ICE, LIGHTNING, PHYSICAL, NONE

    // --- 지속 효과 관련 ---
    private String status;          // STUN, BLEED, BURN, POISON, STRENGTHEN(강화) 등
    private Integer duration;       // 효과 지속 턴 수
    private Integer chance;         // 상태이상/효과 부여 확률

    // --- 수치 보정 관련 ---
    private Integer baseChance;     // 도망 기본 확률
    private Double critRate;        // 치명타 확률
    private Double critDamage;      // 치명타 배수
    private Double penetration;     // 관통력

    // --- 버프/디버프 상세 (일관성을 위해 Map 활용) ---
    // Key: StatId(1~24), Value: 보정치 (1.2는 20% 상승, 0.8은 20% 감소)
    private Map<Integer, Double> statModifiers;
    private Map<Integer, Integer> statOffsets;
    private Map<String, Double> combatStatOffsets;
    private Map<String, Double> combatStatModifiers;
}