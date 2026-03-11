package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Field;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class LifeStats {

    /* =========================
     * 1. 노동 관련 (Work) - 단위: %
     * ========================= */
    private double workGoldBonus;      // 노동 골드 보너스 (예: 10 -> +10%)
    private double workStaminaBonus;   // 노동 스테미나 소모 보정 (예: -10 -> 10% 감소)
    private double workSuccessBonus;   // 노동 대박 확률 보정 (예: 5 -> +5% 절대치 추가)

    /* =========================
     * 2. 도박 관련 (Gamble) - 단위: %
     * ========================= */
    private double gambleWinRateBonus; // 겜블 승률 보정 (예: 5 -> +5% 추가)
    private double gambleMultiplierBonus;   // 겜블 배당금 보정 (예: 20 -> 배당금 20% 증가)

    /* =========================
     * 3. 경제 및 화술 (Economy) - 단위: %
     * ========================= */
    private double bartering;          // 화술 (예: 10 -> 구매 10% 할인 / 판매 10% 할증)

    /* =========================
     * 4. 기타 유틸리티
     * ========================= */
    private double restEfficiency;     // 휴식 효율 (%)
    private double taxReduction;       // 세금 감면율 (%)

    /**
     * 보너스 스탯 합산 (정수 합연산)
     */
    public void applyLifeStatsBonus(String statName, double val) {
        switch (statName) {
            case "workGoldBonus" -> this.workGoldBonus += val;
            case "workStaminaBonus" -> this.workStaminaBonus += val;
            case "workSuccessBonus" -> this.workSuccessBonus += val;
            case "gambleWinRateBonus" -> this.gambleWinRateBonus += val;
            case "gambleMultiplierBonus" -> this.gambleMultiplierBonus += val;
            case "bartering" -> this.bartering += val;
            case "restEfficiency" -> this.restEfficiency += val;
            case "taxReduction" -> this.taxReduction += val;
        }
    }
}