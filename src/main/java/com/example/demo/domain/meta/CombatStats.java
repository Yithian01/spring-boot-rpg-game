package com.example.demo.domain.meta;

import com.example.demo.domain.save.UserStatus;
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

            case "meleeAtk" -> this.meleeAtk += multiplier;
            case "magicAtk" -> this.magicAtk += multiplier;

            case "critRate" -> this.critRate += multiplier;
            case "critDmg" -> this.critDmg += multiplier;
            case "penetration" -> this.penetration += multiplier;

            case "physDef" -> this.physDef += multiplier;
            case "magRes" -> this.magRes += multiplier;

            case "dodge" -> this.dodge += multiplier;
            case "accuracy" -> this.accuracy += multiplier;
            case "moveSpeed" -> this.moveSpeed += multiplier;
            case "statusResist" -> this.statusResist += multiplier;
            default -> { /* 로그를 남기거나 무시 */ }
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

            case "meleeAtk" -> this.meleeAtk *= multiplier;
            case "magicAtk" -> this.magicAtk *= multiplier;

            case "critRate" -> this.critRate *= multiplier;
            case "critDmg" -> this.critDmg *= multiplier;
            case "penetration" -> this.penetration *= multiplier;

            case "physDef" -> this.physDef *= multiplier;
            case "magRes" -> this.magRes *= multiplier;

            case "dodge" -> this.dodge *= multiplier;
            case "accuracy" -> this.accuracy *= multiplier;
            case "moveSpeed" -> this.moveSpeed *= multiplier;
            case "statusResist" -> this.statusResist *= multiplier;
            default -> { /* 로그를 남기거나 무시 */ }
        }
    }
}