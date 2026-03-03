package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonsterSkillMeta {
    private int id;
    private String name;
    private String description;
    private String type;               // PHYSICAL, MAGIC, DEBUFF, BUFF, MELEE 등
    private int turnCost;             // 사용 턴 소모량
    private int hitChance;             // 기본 명중률

    private Map<String, Integer> cost;            // 소모 자원 (예: { "mp": 10 })

    // 몬스터가 사용할 때의 계수 (예: { "meleeAtk": 1.5 })
    private Map<String, Double> monsterScaling;

    // 플레이어가 사용할 때의 기본 스탯 계수 (예: { "10": 1.5, "15": 1.2 })
    private Map<String, Double> statScaling;

    private MonsterSkillEffect effect;

    // 스킬 사용에 필요한 무기 타입 리스트 (예: ["SWORD", "DAGGER"])
    private List<String> requiredWeapons;
}