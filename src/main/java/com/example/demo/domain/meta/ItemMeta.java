package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private Map<Integer, Integer> baseStatsBonus;
    private CombatStatsBonus combatStatsBonus;

    // [소모품용] 회복 수치 추가
    private RecoveryBonus recoveryBonus;

    @Data
    public static class RecoveryBonus {
        private int hp;      // 즉시 회복 HP
        private int mp;      // 즉시 회복 MP
        private int stamina; // 즉시 회복 스테미나
    }

    @Data
    public static class CombatStatsBonus {
        private int maxHp;
        private int maxMp;
        private int maxStamina;
        private double hpRegen;
        private double mpRegen;
        private double meleeAtk;
        private double magicAtk;
        private double critRate;
        private double critDmg;
        private double penetration;
        private double physDef;
        private double magRes;
        private double dodge;
        private double accuracy;
        private double moveSpeed;
    }
}