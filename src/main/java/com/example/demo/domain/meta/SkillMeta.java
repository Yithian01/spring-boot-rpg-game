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
    private int hitChance;        // 적중확률

    private Map<String, Integer> cost;       // stamina, mp 등
    private Map<Integer, Double> scaling;    // 스탯 인덱스 : 계수

    private SkillEffect effect;
    private String requiredWeapon; // SWORD, BOW, BLUNT, NONE 등
}