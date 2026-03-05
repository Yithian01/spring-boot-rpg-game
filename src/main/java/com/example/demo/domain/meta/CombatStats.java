package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class CombatStats {
    /* =========================
     * 1. 기본 생존 및 유틸리티
     * ========================= */
    private int maxHp;
    private int maxMp;
    private int maxStamina;

    private double hpRegen;
    private double mpRegen;
    private double moveSpeed;
    private int maxTurns;

    /* =========================
     * 공통 공격 스탯
     * ========================= */
    private double critRate;      // 치명타 확률 (%)
    private double critDmg;       // 치명타 피해 (배율, 기본 150% + a)
    private double accuracy;      // 명중률 (%)
    private double dodge;         // 회피율 (%)

    /* =========================
     * 상태 이상 저항 스탯 - (%)
     * ========================= */
    private double statusResist;  // 상태이상 저항 (%)
    private double bleedRes; // 출혈 저항 (%)
    private double stunRes; // 스턴 저항 (%)
    private double burnRes; // 화상저항 (%)
    private double frozenRes; // 빙결 저항 (%)
    private double poisonRes; // 중독 저항 (%)

    /* =========================
     * 상태 이상 관통 스탯 - (%)
     * ========================= */
    private double bleedPen;    // 출혈 부여 확률 보정
    private double stunPen;     // 스턴 부여 확률 보정
    private double burnPen;     // 화상 부여 확률 보정
    private double frozenPen;   // 빙결 부여 확률 보정
    private double poisonPen;   // 중독 부여 확률 보정

    /* =========================
     * 3. 속성별 상세 스탯 (공격 Atk / 저항 Res / 관통 Pen)
     * Atk: 고정치 (meleeAtk 등과 동일)
     * Res: 배율 (0 ~ 100% Over 시 흡수까지 고려 X)
     * Pen: 배율 (0 ~ 100% Over 시 추가 대미지 고려 O )
     * ========================= */

    // 물리 (Physical)
    private double meleeAtk;
    private double physDef;      // 물리 방어력 (고정치 감쇄용)
    private double physRes;      // 물리 저항력 (%)
    private double physPen;      // 물리 저항 관통 (%)

    // 마법 공통 및 속성별 공격력 (고정치)
    private double magicAtk;
    private double magRes;       // 마법 방어력 (%)
    private double magPen;       // 마법 저항 관통 (%)

    private double fireAtk;
    private double iceAtk;
    private double lightAtk;
    private double earthAtk;
    private double holyAtk;
    private double darkAtk;
    private double chaosAtk;
    private double toxicAtk;

    // 속성별 저항력 (%)
    private double fireRes;
    private double iceRes;
    private double lightRes;
    private double earthRes;
    private double holyRes;
    private double darkRes;
    private double chaosRes;
    private double toxicRes;

    // 속성별 관통력 (%)
    private double firePen;
    private double icePen;
    private double lightPen;
    private double earthPen;
    private double holyPen;
    private double darkPen;
    private double chaosPen;
    private double toxicPen;

    /**
     * 보너스 스탯 증가 (합연산: + 수치)
     * @param statName 스탯 명칭 (JSON 키값)
     * @param val 증가 수치
     */
    public void applyCombatStatsBonus(String statName, double val) {
        switch (statName) {
            // 기본 자원 (Integer)
            case "maxHp" -> this.maxHp += (int) val;
            case "maxMp" -> this.maxMp += (int) val;
            case "maxStamina" -> this.maxStamina += (int) val;

            // 공통 공격/유틸
            case "hpRegen" -> this.hpRegen = roundOne(this.hpRegen + val);
            case "mpRegen" -> this.mpRegen = roundOne(this.mpRegen + val);
            case "moveSpeed" -> this.moveSpeed = roundOne(this.moveSpeed + val);
            case "maxTurns" -> this.maxTurns += (int) val;

            case "critRate" -> this.critRate = roundOne(this.critRate + val);
            case "critDmg" -> this.critDmg = roundOne(this.critDmg + val);
            case "accuracy" -> this.accuracy = roundOne(this.accuracy + val);
            case "dodge" -> this.dodge = roundOne(this.dodge + val);

            // 상태 이상 저항
            case "statusResist" -> this.statusResist = roundOne(this.statusResist + val);
            case "bleedRes" -> this.bleedRes = roundOne(this.bleedRes + val);
            case "stunRes" -> this.stunRes = roundOne(this.stunRes + val);
            case "burnRes" -> this.burnRes = roundOne(this.burnRes + val);
            case "frozenRes" -> this.frozenRes = roundOne(this.frozenRes + val);
            case "poisonRes" -> this.poisonRes = roundOne(this.poisonRes + val);

            // 상태 이상 관통 (추가)
            case "bleedPen" -> this.bleedPen = roundOne(this.bleedPen + val);
            case "stunPen" -> this.stunPen = roundOne(this.stunPen + val);
            case "burnPen" -> this.burnPen = roundOne(this.burnPen + val);
            case "frozenPen" -> this.frozenPen = roundOne(this.frozenPen + val);
            case "poisonPen" -> this.poisonPen = roundOne(this.poisonPen + val);

            // 공격력 (Atk - 고정치 합산)
            case "meleeAtk" -> this.meleeAtk = roundOne(this.meleeAtk + val);
            case "magicAtk" -> this.magicAtk = roundOne(this.magicAtk + val);
            case "fireAtk" -> this.fireAtk = roundOne(this.fireAtk + val);
            case "iceAtk" -> this.iceAtk = roundOne(this.iceAtk + val);
            case "lightAtk" -> this.lightAtk = roundOne(this.lightAtk + val);
            case "earthAtk" -> this.earthAtk = roundOne(this.earthAtk + val);
            case "holyAtk" -> this.holyAtk = roundOne(this.holyAtk + val);
            case "darkAtk" -> this.darkAtk = roundOne(this.darkAtk + val);
            case "chaosAtk" -> this.chaosAtk = roundOne(this.chaosAtk + val);
            case "toxicAtk" -> this.toxicAtk = roundOne(this.toxicAtk + val);

            // 방어력/저항력/관통력 (Def, Res, Pen - 고정치/배율 합산)
            case "physDef" -> this.physDef = roundOne(this.physDef + val);
            case "magRes" -> this.magRes = roundOne(this.magRes + val);

            case "physRes" -> this.physRes = roundOne(this.physRes + val);
            case "magPen" -> this.magPen = roundOne(this.magPen + val);
            case "fireRes" -> this.fireRes = roundOne(this.fireRes + val);
            case "iceRes" -> this.iceRes = roundOne(this.iceRes + val);
            case "lightRes" -> this.lightRes = roundOne(this.lightRes + val);
            case "earthRes" -> this.earthRes = roundOne(this.earthRes + val);
            case "holyRes" -> this.holyRes = roundOne(this.holyRes + val);
            case "darkRes" -> this.darkRes = roundOne(this.darkRes + val);
            case "chaosRes" -> this.chaosRes = roundOne(this.chaosRes + val);
            case "toxicRes" -> this.toxicRes = roundOne(this.toxicRes + val);

            case "physPen" -> this.physPen = roundOne(this.physPen + val);
            case "firePen" -> this.firePen = roundOne(this.firePen + val);
            case "icePen" -> this.icePen = roundOne(this.icePen + val);
            case "lightPen" -> this.lightPen = roundOne(this.lightPen + val);
            case "earthPen" -> this.earthPen = roundOne(this.earthPen + val);
            case "holyPen" -> this.holyPen = roundOne(this.holyPen + val);
            case "darkPen" -> this.darkPen = roundOne(this.darkPen + val);
            case "chaosPen" -> this.chaosPen = roundOne(this.chaosPen + val);
            case "toxicPen" -> this.toxicPen = roundOne(this.toxicPen + val);
        }
    }

    /**
     * 컴포넌트형 스탯 업데이트 (곱연산: 버프/디버프 % 적용)
     * @param statName 스탯 명칭
     * @param multiplier 곱연산 계수 (예: 1.2 = 20% 증가)
     */
    public void applyModifier(String statName, double multiplier) {
        switch (statName) {
            case "maxHp" -> this.maxHp *= multiplier;
            case "maxMp" -> this.maxMp *= multiplier;
            case "maxStamina" -> this.maxStamina *= multiplier;

            // 공통 공격/유틸
            case "hpRegen" -> this.hpRegen = roundOne(this.hpRegen * multiplier);
            case "mpRegen" -> this.mpRegen = roundOne(this.mpRegen * multiplier);
            case "moveSpeed" -> this.moveSpeed = roundOne(this.moveSpeed * multiplier);
            case "maxTurns" -> this.maxTurns = (int)(this.maxTurns * multiplier);

            case "critRate" -> this.critRate = roundOne(this.critRate * multiplier);
            case "critDmg" -> this.critDmg = roundOne(this.critDmg * multiplier);
            case "accuracy" -> this.accuracy = roundOne(this.accuracy * multiplier);
            case "dodge" -> this.dodge = roundOne(this.dodge * multiplier);

            // 상태 이상 저항
            case "statusResist" -> this.statusResist = roundOne(this.statusResist * multiplier);
            case "bleedRes" -> this.bleedRes = roundOne(this.bleedRes * multiplier);
            case "stunRes" -> this.stunRes = roundOne(this.stunRes * multiplier);
            case "burnRes" -> this.burnRes = roundOne(this.burnRes  * multiplier);
            case "frozenRes" -> this.frozenRes = roundOne(this.frozenRes * multiplier);
            case "poisonRes" -> this.poisonRes = roundOne(this.poisonRes * multiplier);

            // 상태 이상 관통 (추가)
            case "bleedPen" -> this.bleedPen = roundOne(this.bleedPen * multiplier);
            case "stunPen" -> this.stunPen = roundOne(this.stunPen * multiplier);
            case "burnPen" -> this.burnPen = roundOne(this.burnPen * multiplier);
            case "frozenPen" -> this.frozenPen = roundOne(this.frozenPen * multiplier);
            case "poisonPen" -> this.poisonPen = roundOne(this.poisonPen * multiplier);

            // 공격력 (Atk - 고정치 합산)
            case "meleeAtk" -> this.meleeAtk = roundOne(this.meleeAtk * multiplier);
            case "magicAtk" -> this.magicAtk = roundOne(this.magicAtk * multiplier);
            case "fireAtk" -> this.fireAtk = roundOne(this.fireAtk * multiplier);
            case "iceAtk" -> this.iceAtk = roundOne(this.iceAtk * multiplier);
            case "lightAtk" -> this.lightAtk = roundOne(this.lightAtk * multiplier);
            case "earthAtk" -> this.earthAtk = roundOne(this.earthAtk * multiplier);
            case "holyAtk" -> this.holyAtk = roundOne(this.holyAtk * multiplier);
            case "darkAtk" -> this.darkAtk = roundOne(this.darkAtk * multiplier);
            case "chaosAtk" -> this.chaosAtk = roundOne(this.chaosAtk * multiplier);
            case "toxicAtk" -> this.toxicAtk = roundOne(this.toxicAtk * multiplier);

            // 방어력/저항력/관통력 (Def, Res, Pen - 고정치/배율 합산)
            case "physDef" -> this.physDef = roundOne(this.physDef * multiplier);
            case "physRes" -> this.physRes = roundOne(this.physRes * multiplier);
            case "magRes" -> this.magRes = roundOne(this.magRes * multiplier);

            case "magPen" -> this.magPen = roundOne(this.magPen * multiplier);
            case "fireRes" -> this.fireRes = roundOne(this.fireRes * multiplier);
            case "iceRes" -> this.iceRes = roundOne(this.iceRes * multiplier);
            case "lightRes" -> this.lightRes = roundOne(this.lightRes * multiplier);
            case "earthRes" -> this.earthRes = roundOne(this.earthRes * multiplier);
            case "holyRes" -> this.holyRes = roundOne(this.holyRes * multiplier);
            case "darkRes" -> this.darkRes = roundOne(this.darkRes * multiplier);
            case "chaosRes" -> this.chaosRes = roundOne(this.chaosRes * multiplier);
            case "toxicRes" -> this.toxicRes = roundOne(this.toxicRes * multiplier);

            case "physPen" -> this.physPen = roundOne(this.physPen * multiplier);
            case "firePen" -> this.firePen = roundOne(this.firePen * multiplier);
            case "icePen" -> this.icePen = roundOne(this.icePen * multiplier);
            case "lightPen" -> this.lightPen = roundOne(this.lightPen * multiplier);
            case "earthPen" -> this.earthPen = roundOne(this.earthPen * multiplier);
            case "holyPen" -> this.holyPen = roundOne(this.holyPen * multiplier);
            case "darkPen" -> this.darkPen = roundOne(this.darkPen * multiplier);
            case "chaosPen" -> this.chaosPen = roundOne(this.chaosPen * multiplier);
            case "toxicPen" -> this.toxicPen = roundOne(this.toxicPen * multiplier);
        }
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}