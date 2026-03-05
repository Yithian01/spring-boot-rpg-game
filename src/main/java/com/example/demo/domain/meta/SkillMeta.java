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
public class SkillMeta {
    private int id;
    private String name;
    private String icon;
    private String description;
    private String type;         // PHYSICAL, MAGIC, UTILITY 등
    private String grade;        // 마석등급에 따라 뽑기위해 BASE, COMMON, UNCOMMON 등
    private int turnCost;        // 행동 소모 턴 (AP)
    private int hitChance;        // 적중확률

    private Map<String, Integer> cost;       // stamina, mp 등
    private Map<String, Double> DamageScaling;    // { "meleeAtk": 1.2 } 스킬 계수
    private Map<Integer, Double> statScaling;    // 스탯 인덱스 : 스탯 계수

    private SkillEffect effect;
    private List<String> requiredWeapons; // SWORD, BOW, BLUNT, NONE 등
}