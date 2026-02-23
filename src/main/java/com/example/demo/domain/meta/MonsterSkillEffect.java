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
public class MonsterSkillEffect {
    private String type;    // DAMAGE, BUFF, DEBUFF
    private String element; // PHYSICAL, FIRE 등
    private String status;  // BLEED, BURN 등 (상태이상 코드)
    private int duration;
    private int chance;     // 상태이상 부여 확률
    private Map<Integer, Double> statModifiers; // 플레이어에게 줄 디버프 (스탯ID, 비율)
}
