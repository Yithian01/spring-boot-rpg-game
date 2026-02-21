package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonsterStatsDto {
    private int maxHp;
    private int maxMp;
    private double meleeAtk;
    private double magicAtk;
    private double physDef;
    private double magRes;
    private double dodge;
    private double accuracy;
    private double critRate;
    private double moveSpeed;
}