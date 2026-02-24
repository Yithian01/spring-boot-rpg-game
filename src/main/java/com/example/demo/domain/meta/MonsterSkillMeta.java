package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonsterSkillMeta {
    private int id;
    private String name;
    private String description;
    private String type;        // PHYSICAL, MAGIC, DEBUFF 등
    private int turnCost;      // 사용 턴
    private int hitChance;      // 기본 명중률

    private Map<String, Integer> cost;            // { "mp": 10 }
    private Map<String, Double> monsterScaling;   // { "meleeAtk": 1.2 }

    private MonsterSkillEffect effect;
}
