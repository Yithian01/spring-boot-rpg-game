package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CombatStats {
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
    private double statusResist;
}