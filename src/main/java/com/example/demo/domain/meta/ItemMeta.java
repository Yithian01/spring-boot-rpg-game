package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ItemMeta {
    private int id;
    private String name;
    private String grade;
    private String type;
    private String slot;
    private String subType;
    private boolean twoHanded;
    private String description;
    private int price;
    private String icon;

    // [-- 단일 --]
    private Map<Integer, Integer> baseStatsBonus;
    private Map<String, Double> combatStatsBonus;
    private Map<String, Integer> recoveryBonus;
    private Map<String, Double> lifeStatsBonus;

    // [-- 배수 --]
    private Map<Integer, Double> baseStatsBonusModifiers;
    private Map<String, Double> combatStatsBonusModifiers;

    private List<Integer> grantedSkillIds;
}