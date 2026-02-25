package com.example.demo.domain.meta;

import com.example.demo.domain.save.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
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

    /**
     * 보너스 스탯 증가 +
     * @param statName 스탯 명칭 (JSON의 키값과 일치)
     * @param multiplier 합연산 계수
     */
    public void applyCombatStatsBonus(String statName, double multiplier) {
        switch (statName) {
            case "maxHp" -> this.maxHp += (int)multiplier;
            case "maxMp" -> this.maxMp += (int)multiplier;
            case "maxStamina" -> this.maxStamina += (int)multiplier;

            case "meleeAtk" -> this.meleeAtk = roundOne(this.meleeAtk + multiplier);
            case "magicAtk" -> this.magicAtk = roundOne(this.magicAtk + multiplier);

            case "critRate" -> this.critRate = roundOne(this.critRate + multiplier);
            case "critDmg" -> this.critDmg = roundOne(this.critDmg + multiplier);
            case "penetration" -> this.penetration = roundOne(this.penetration + multiplier);

            case "physDef" -> this.physDef = roundOne(this.physDef + multiplier);
            case "magRes" -> this.magRes = roundOne(this.magRes + multiplier);

            case "dodge" -> this.dodge = roundOne(this.dodge + multiplier);
            case "accuracy" -> this.accuracy = roundOne(this.accuracy + multiplier);
            case "moveSpeed" -> this.moveSpeed = roundOne(this.moveSpeed + multiplier);
            case "statusResist" -> this.statusResist = roundOne(this.statusResist + multiplier);
        }
    }

    /**
     * 컴포넌트형 스탯 업데이트 메서드
     * @param statName 스탯 명칭 (JSON의 키값과 일치)
     * @param multiplier 곱연산 계수
     */
    public void applyModifier(String statName, double multiplier) {
        switch (statName) {
            case "maxHp" -> this.maxHp *= multiplier;
            case "maxMp" -> this.maxMp *= multiplier;
            case "maxStamina" -> this.maxStamina *= multiplier;

            case "meleeAtk" -> this.meleeAtk = roundOne(this.meleeAtk * multiplier);
            case "magicAtk" -> this.magicAtk = roundOne(this.magicAtk * multiplier);

            case "critRate" -> this.critRate = roundOne(this.critRate * multiplier);
            case "critDmg" -> this.critDmg = roundOne(this.critDmg * multiplier);
            case "penetration" -> this.penetration = roundOne(this.penetration * multiplier);

            case "physDef" -> this.physDef = roundOne(this.physDef * multiplier);
            case "magRes" -> this.magRes = roundOne(this.magRes * multiplier);

            case "dodge" -> this.dodge = roundOne(this.dodge * multiplier);
            case "accuracy" -> this.accuracy = roundOne(this.accuracy * multiplier);
            case "moveSpeed" -> this.moveSpeed = roundOne(this.moveSpeed * multiplier);
            case "statusResist" -> this.statusResist = roundOne(this.statusResist * multiplier);
        }
    }

    /**
     * 소수점 첫째 자리 반올림 헬퍼
     */
    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}