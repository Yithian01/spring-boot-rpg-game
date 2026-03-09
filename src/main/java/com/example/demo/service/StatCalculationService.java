package com.example.demo.service;

import com.example.demo.domain.meta.CombatStats;
import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.SkillEffect;
import com.example.demo.domain.meta.SkillMeta;
import com.example.demo.domain.save.*;
import com.example.demo.repository.EssenceRepository;
import com.example.demo.repository.ItemInstanceRepository;
import com.fasterxml.jackson.databind.JsonSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatCalculationService {
    private final ItemInstanceRepository itemInstanceRepository;
    private final EssenceRepository essenceRepository;

    /**
     * 공통 조회 메서드: 키가 String일 경우를 대비해 조회
     * @param baseStats 스탯 메타 데이터
     * @param id 스탯 ID
     * @return int 로 변환 후 반환
     */
    private int getStat(Map<?, ?> baseStats, int id) {
        Object val = baseStats.get(String.valueOf(id));
        if (val == null) {
            val = baseStats.get(id);
        }
        return val != null ? (int) val : 0;
    }

    /**
     * 유저의 모든 전투 스탯을 [아이템 + 버프/디버프]를 포함하여 완전히 새로고침합니다.
     * 매번 아이템을 전수 조사하지 않고, 저장된 equipmentBonusStats 레이어를 활용합니다.
     * @param user 현재 유저 상태 (참조를 통해 직접 수정)
     * @param itemMap GameDataManager에서 관리하는 전체 아이템 메타 맵
     */
    public void refreshUserCombatStats(UserStatus user, Map<Integer, ItemMeta> itemMap) {
        // [현재 체력비율 저장]
        double hpRate = user.getCombatStats().getMaxHp() > 0
                ? (double) user.getCurrentHp() / user.getCombatStats().getMaxHp() : 1.0;
        double mpRate = user.getCombatStats().getMaxMp() > 0
                ? (double) user.getCurrentMp() / user.getCombatStats().getMaxMp() : 1.0;
        double staminaRate = user.getCombatStats().getMaxStamina() > 0
                ? (double) user.getCurrentStamina() / user.getCombatStats().getMaxStamina() : 1.0;

        Map<Integer, Integer> baseStatOffsets = new HashMap<>();     // 고정치 (+)
        Map<Integer, Double> baseStatModifiers = new HashMap<>();    // 배율합 (%)

        Map<String, Double> combatStatOffsets = new HashMap<>();    // 전투스탯 고정치
        Map<String, Double> combatStatModifiers = new HashMap<>();   // 전투스탯 배율합

        // 1. [장착 아이템] 보너스 수집
        if (user.getEquippedItems() != null) {
            for (String instanceId : user.getEquippedItems().values()) {
                if (instanceId == null || "0".equals(instanceId)) continue;

                ItemInstance ii = itemInstanceRepository.findById(instanceId).orElse(null);
                if (ii == null) continue;

                // 기초 스탯 보너스 수집
                if (ii.getBaseStatsBonus() != null) {
                    ii.getBaseStatsBonus().forEach((id, val) -> baseStatOffsets.merge(id, val, Integer::sum));
                }
                // 기초 스탯 배율 수집 (0.1 + 0.1 = 0.2 로직)
                if (ii.getBaseStatsBonusModifiers() != null) {
                    ii.getBaseStatsBonusModifiers().forEach((id, val) -> baseStatModifiers.merge(id, val, Double::sum));
                }
                // 전투 스탯 보너스 수집
                if (ii.getCombatStatsBonus() != null) {
                    ii.getCombatStatsBonus().forEach((name, val) -> combatStatOffsets.merge(name, val, Double::sum));
                }
                // 전투 스탯 배율 수집
                if (ii.getCombatStatsBonusModifiers() != null) {
                    ii.getCombatStatsBonusModifiers().forEach((name, val) -> combatStatModifiers.merge(name, val, Double::sum));
                }
            }
        }

        // 🔥 2. [추가] 장착 중인 정수(Essence) 보너스 수집
        // UserStatus에 activeEssenceIds(UUID)가 있고, 이를 통해 EssenceInstance를 가져올 수 있다고 가정합니다.
        if (user.getActiveEssenceIds() != null && !user.getActiveEssenceIds().isEmpty()) {
            for (String essenceId : user.getActiveEssenceIds()) {
                if (essenceId == null) continue;

                // DB 또는 Repository에서 실제 정수 인스턴스 조회
                // (주의: 이미 UserStatus에 EssenceInstance 객체 리스트가 있다면 직접 순회)
                EssenceInstance ei = essenceRepository.findById(essenceId).orElse(null);
                if (ei == null) continue;

                // (A) 정수의 기초 스탯 보너스 (+고정치)
                if (ei.getBaseStatsBonus() != null) {
                    ei.getBaseStatsBonus().forEach((id, val) ->
                            baseStatOffsets.merge(id, val, Integer::sum));
                }

                // (B) 정수의 전투 스탯 보너스 (+고정치)
                if (ei.getCombatStatsBonus() != null) {
                    ei.getCombatStatsBonus().forEach((name, val) ->
                            combatStatOffsets.merge(name, val, Double::sum));
                }
                // 만약 정수에 배율(%) 보너스도 있다면 동일하게 merge 처리
            }
        }



        // 3. [액티브 상태(버프)] 보너스 수집
        if (user.getActiveStatuses() != null) {
            for (ActiveStatus status : user.getActiveStatuses()) {
                if (status.getStatOffsets() != null) {
                    status.getStatOffsets().forEach((id, val) -> baseStatOffsets.merge(id, val, Integer::sum));
                }
                if (status.getStatModifiers() != null) {
                    status.getStatModifiers().forEach((id, val) -> baseStatModifiers.merge(id, val, Double::sum));
                }
                if (status.getCombatStatOffsets() != null) {
                    status.getCombatStatOffsets().forEach((name, val) -> combatStatOffsets.merge(name, val, Double::sum));
                }
                if (status.getCombatStatModifiers() != null) {
                    status.getCombatStatModifiers().forEach((name, val) -> combatStatModifiers.merge(name, val, Double::sum));
                }
            }
        }

        // 4. [최종 기초 스탯(finalStats) 계산]
        // 공식: (기본 스탯 + 고정치 합) * (1.0 + 배율 합)
        Map<Integer, Integer> finalStats = new HashMap<>();
        user.getBaseStats().forEach((id, baseVal) -> {
            double offset = baseStatOffsets.getOrDefault(id, 0);
            double modifierSum = baseStatModifiers.getOrDefault(id, 0.0);

            int finalVal = (int) Math.round((baseVal + offset) * (1.0 + modifierSum));
            finalStats.put(id, finalVal);
        });
        user.setFinalStats(finalStats);

        // 5. [전투 능력치 수식 레이어 적용]
        CombatStats combat = new CombatStats();
        applyBaseFormulas(combat, finalStats); // calculateMaxHp 등 기존 수식 함수들 호출

        // 6. [전투 스탯 최종 보정]
        // (A) 고정치 보정: 예) 공격력 +50
        combatStatOffsets.forEach((name, val) -> combat.applyCombatStatsBonus(name, val.doubleValue()));

        // (B) 배율 보정: 예) 공격력 +20% (합연산된 배율 적용)
        combatStatModifiers.forEach((name, val) -> combat.applyModifier(name, val));
        user.setCombatStats(combat);

        user.setCurrentHp((int) Math.round(user.getCombatStats().getMaxHp() * hpRate));
        user.setCurrentMp((int) Math.round(user.getCombatStats().getMaxMp() * mpRate));
        user.setCurrentStamina((int) Math.round(user.getCombatStats().getMaxStamina() * staminaRate));

        // 7. 자원 상한선 보정
        user.setCurrentHp(Math.min(user.getCurrentHp(), user.getCombatStats().getMaxHp()));
        user.setCurrentMp(Math.min(user.getCurrentMp(), user.getCombatStats().getMaxMp()));
        user.setCurrentStamina(Math.min(user.getCurrentStamina(), user.getCombatStats().getMaxStamina()));
    }

    /**
     * [헬퍼] 수식을 통한 기본 전투 능력치 세팅
     */
    private void applyBaseFormulas(CombatStats combat, Map<Integer, Integer> stats) {
        combat.setMaxHp(calculateMaxHp(stats));
        combat.setMaxMp(calculateMana(stats));
        combat.setMaxStamina(calculateStamina(stats));
        combat.setHpRegen(calculateHpRegen(stats));
        combat.setMpRegen(calculateManaRegen(stats));
        combat.setMoveSpeed(calculateMoveSpd(stats));
        combat.setMaxTurns(calculateCombatTurns(stats));

        combat.setCritRate(calculateCritRate(stats));
        combat.setCritDmg(calculateCritDmg(stats));
        combat.setAccuracy(calculateAccuracy(stats));
        combat.setDodge(calculateDodge(stats));

        combat.setStatusResist(calculateStatusResist(stats));
        combat.setBleedRes(0);
        combat.setStunRes(0);
        combat.setBurnRes(0);
        combat.setFrozenRes(0);
        combat.setPoisonRes(0);

        combat.setBleedPen(0);
        combat.setStunPen(0);
        combat.setBurnPen(0);
        combat.setFrozenPen(0);
        combat.setPoisonPen(0);

        combat.setMeleeAtk(calculateMeleeAtk(stats));
        combat.setPhysDef(calculatePhysDef(stats));
        combat.setPhysRes(0);
        combat.setPhysPen(0);

        combat.setMagicAtk(calculateMagicAtk(stats));
        combat.setMagRes(calculateMagRes(stats));
        combat.setMagPen(0);

        combat.setFireAtk(0);
        combat.setIceAtk(0);
        combat.setLightningAtk(0);
        combat.setEarthAtk(0);
        combat.setHolyAtk(0);
        combat.setDarkAtk(0);
        combat.setChaosAtk(0);

        combat.setFirePen(0);
        combat.setIcePen(0);
        combat.setLightningPen(0);
        combat.setEarthPen(0);
        combat.setHolyPen(0);
        combat.setDarkPen(0);
        combat.setChaosPen(0);
    }

    /**
     * 전투 스탯 계산 메소드 (합연산: item + potion)
     * @param user
     * @param mods
     */
    private void applyCombatStatsBonus(UserStatus user, Map<String, Double> mods) {
        if (mods == null || mods.isEmpty()) return;

        mods.forEach((name, value) -> user.getCombatStats().applyCombatStatsBonus(name, value));
    }

    /**
     * 전투 계산 메소드 (곱연산: BUFF + DEBUFF + potion)
     * @param user 플레이어
     * @param mods 전투 스탯
     */
    private void applyCombatModifiers(UserStatus user, Map<String, Double> mods) {
        if (mods == null || mods.isEmpty()) return;

        mods.forEach((name, value) -> user.getCombatStats().applyModifier(name, value));
    }

    /**
     * 전투 계산 메소드 (곱연산: BUFF + DEBUFF)
     * @param mstActiveStats 몬스터 적용할 스탯
     * @param mods 전투 스탯
     */
    private void applyCombatModifiers(CombatStats mstActiveStats, Map<String, Double> mods) {
        if (mods == null || mods.isEmpty()) return;

        mods.forEach((name, value) -> mstActiveStats.applyModifier(name, value));
    }

    /**
     * 최대 생명력 계산 함수
     */
    public int calculateMaxHp(Map<?, ?> baseStats) {
        // 1:코어, 2:조직강도, 3:대사효율
        return (100 + (getStat(baseStats, 1) * 10) + (getStat(baseStats, 2) * 5) + (getStat(baseStats, 3) * 2));
    }

    /**
     * 최대 마나 계산 함수
     */
    public int calculateMana(Map<?, ?> baseStats) {
        // 4:혈관탄성, 5:기억용량
        return (50 + (getStat(baseStats, 4) * 10) + (getStat(baseStats, 5) * 2));
    }

    /**
     * 최대 스태미나 계산 함수
     */
    public int calculateStamina(Map<?, ?> baseStats) {
        // 6:심폐지구력, 7:대퇴사두
        return (100 + (getStat(baseStats, 6) * 10) + (getStat(baseStats, 7) * 3));
    }

    /**
     * 체력 재생 계산 함수
     */
    public double calculateHpRegen(Map<?, ?> baseStats) {
        return (getStat(baseStats, 3) * 0.5);
    }

    /**
     * 마나 재생 계산 함수
     */
    public double calculateManaRegen(Map<?, ?> baseStats) {
        return (getStat(baseStats, 8) * 0.5) + (getStat(baseStats, 9) * 0.2);
    }

    /**
     * 이동속도 계산 함수
     */
    public double calculateMoveSpd(Map<?, ?> baseStats) {
        // 21:비복근, 7:대퇴사두
        double rawSpd = (getStat(baseStats, 21) * 0.1) + (getStat(baseStats, 7) * 0.05);
        return Math.round(rawSpd * 10.0) / 10.0;
    }

    /**
     * 치명타 확률 계산 함수
     */
    public double calculateCritRate(Map<?, ?> baseStats) {
        // 15:인과율간섭(주), 16:동체시력(부)
        double statCrit = 5.0 + (getStat(baseStats, 15) * 0.1) + (getStat(baseStats, 16) * 0.1);

        double threshold = 30.0;
        if(statCrit > threshold) {
            statCrit = threshold + (statCrit - threshold) * 0.1;
        }
        return Math.round(statCrit * 10.0) / 10.0;
    }

    /**
     * 치명타 피해 계산 (스탯 100 기준 200% 설계)
     * 150% (기본) + (100 스탯 * 0.5) = 200%
     */
    public double calculateCritDmg(Map<?, ?> baseStats) {
        double statValue = getStat(baseStats, 17);
        double baseCritDmg = 150.0;

        double statBonus = statValue * 0.5;

        double cappedBonus = Math.min(statBonus, 100.0);

        return baseCritDmg + cappedBonus;
    }

    public double calculateAccuracy(Map<?, ?> baseStats) {
        double rawScore = (getStat(baseStats, 16) * 0.5)
                + (getStat(baseStats, 23) * 0.3)
                + (getStat(baseStats, 24) * 0.2);

        double threshold = 30.0; // 스탯 보너스 상한선
        if (rawScore <= threshold) {
            return Math.round(rawScore * 10.0) / 10.0;
        } else {
            // 30 초과분은 2% 효율만 적용
            double finalAcc = threshold + (rawScore - threshold) * 0.02;
            return Math.round(finalAcc * 10.0) / 10.0;
        }
    }

    /**
     * 회피율(Dodge) 계산 함수 (30 상한제 적용)
     * 스탯 기여도를 30 내외로 제한하여 장비의 회피 옵션 가치를 보존
     */
    public double calculateDodge(Map<?, ?> baseStats) {
        // 20:신경반응(0.4), 21:비복근(0.2), 24:육감(0.1)
        double rawScore = (getStat(baseStats, 20) * 0.4)
                + (getStat(baseStats, 21) * 0.2)
                + (getStat(baseStats, 24) * 0.1);

        double threshold = 30.0;
        double finalDodge;

        if (rawScore <= threshold) {
            finalDodge = rawScore;
        } else {
            finalDodge = threshold + (rawScore - threshold) * 0.02;
        }

        return Math.round(finalDodge * 10.0) / 10.0;
    }

    /**
     * [추가] 상태 이상 저항력 계산 함수 (소프트 캡 적용)
     * 3: 대사 효율 (물리적 저항)
     * 9: 계율 준수 (정신적 저항)
     */
    public double calculateStatusResist(Map<?, ?> baseStats) {
        // 기본 저항 5.0 + (대사 효율 * 0.8) + (계율 준수 * 0.8)
        double rawResist = 5.0 + (getStat(baseStats, 3) * 0.8) + (getStat(baseStats, 9) * 0.8);

        double finalStatusRes = rawResist;

        double threshold = 50.0;
        if(rawResist > threshold) {
            finalStatusRes = threshold + (rawResist - threshold) * 0.02;
        }
        return Math.round(finalStatusRes * 10.0) / 10.0;
    }

    /**
     * 물리 공격력 계산 함수
     */
    public double calculateMeleeAtk(Map<?, ?> baseStats) {
        // 10:상완, 11:대흉근, 12:전완
        return (getStat(baseStats, 10) * 1.5) + (getStat(baseStats, 11) * 1.0) + (getStat(baseStats, 12) * 0.5);
    }

    /**
     * 마법 공격력 계산 함수
     */
    public double calculateMagicAtk(Map<?, ?> baseStats) {
        // 13:에너지응집, 14:논리연산
        return (getStat(baseStats, 13) * 1.8) + (getStat(baseStats, 14) * 0.7);
    }

    /**
     * 물리 방어(Phys Def) 계산 함수
     * 200까지는 정직하게 상승, 이후 효율 급감
     */
    public double calculatePhysDef(Map<?, ?> baseStats) {
        // 기초 방어력 계산: 2(골밀도)*1.0 + 1(코어)*0.4
        double rawDef = (getStat(baseStats, 2) * 1.0) + (getStat(baseStats, 1) * 0.4);

        double finalDef;
        double softCap = 200.0;

        if (rawDef <= softCap) {
            finalDef = rawDef;
        } else {
            // 구간 2: 200 초과분은 10%(0.1) 효율만 적용
            // 스탯 600 유저(rawDef 840) 기준: 200 + (640 * 0.1) = 264
            finalDef = softCap + (rawDef - softCap) * 0.1;
        }

        return Math.round(finalDef * 10.0) / 10.0;
    }

    /**
     * 마법 저항 계산 함수 (소프트 캡 적용 )
     * 50%까지는 정상 적용, 초과분은 10%의 효율만 적용
     */
    public double calculateMagRes(Map<?, ?> baseStats) {
        // 18:부패저항, 19:원소공명
        double rawRes = (getStat(baseStats, 18) * 1.0) + (getStat(baseStats, 19) * 0.5);

        if (rawRes <= 50) {
            return Math.round(rawRes * 10.0) / 10.0;
        } else {
            // 50 + (초과분의 2%)
            double softCapped = 50.0 + (rawRes - 50.0) * 0.02;
            return Math.round(softCapped * 10.0) / 10.0;
        }
    }

    /**
     * 노동 효율(파워) 계산: 상완/대흉근/전완근 기반
     */
    public int calculateWorkPower(Map<?, ?> baseStats) {
        // 물리 카테고리 10, 11, 12 기반
        int power = (getStat(baseStats, 10) + getStat(baseStats, 11) + getStat(baseStats, 12)) / 2;
        return 10 + power;
    }

    /**
     * 예상 획득 골드 계산
     */
    public int calculateEarnedGold(Map<?, ?> baseStats) {
        int power = calculateWorkPower(baseStats);
        return 10 + (power * 1);
    }

    /**
     * 노동 시 스태미나 소모량 계산
     * 지구력 스탯이 높을수록 소모량이 감소하지만, 최소 소모량은 보장함.
     */
    public int calculateWorkStaminaCost(Map<?, ?> baseStats) {
        int baseCost = 25; // 기본 소모량

        // 6: 심폐 지구력, 7: 하체 추진력(지구력 보조)
        int endurance = getStat(baseStats, 6) + getStat(baseStats, 7);

        // 수식: 기본 소모량 - (지구력 / 2)
        int finalCost = baseCost - (endurance / 2);

        // 최소 5는 소모하도록 설정 (공짜 노동 방지 및 음수 방지)
        return Math.max(finalCost, 5);
    }

    /**
     * 휴식 시 HP 회복량 계산: 기본 20% + (대사 효율 * 5)
     */
    public int calculateHpRestoration(UserStatus user) {
        int base = (int) (user.getCombatStats().getMaxHp() * 0.2);
        int bonus = getStat(user.getBaseStats(), 3) * 5;
        return base + bonus;
    }

    /**
     * 휴식 시 MP 회복량 계산: 기본 20% + (마나 회로 전도율 * 5)
     */
    public int calculateMpRestoration(UserStatus user) {
        int base = (int) (user.getCombatStats().getMaxMp() * 0.2);
        int bonus = getStat(user.getBaseStats(), 8) * 5;
        return base + bonus;
    }

    /**
     * 휴식 시 ST 회복량 계산: 기본 30% + (대사 효율 * 5)
     */
    public int calculateStRestoration(UserStatus user) {
        int base = (int) (user.getCombatStats().getMaxStamina() * 0.3);
        int bonus = getStat(user.getBaseStats(), 3) * 5;
        return base + bonus;
    }

    /**
     * 도박 기본 승률 계산 (%)
     * 기본 5% + (행운 스탯 * 1.0) + (통찰 스탯 * 0.5)
     */
    public double calculateGambleWinRate(Map<?, ?> baseStats) {
        return Math.min(5.0 + (getStat(baseStats, 20) * 0.5) + (getStat(baseStats, 16) * 0.3), 85.0);
    }

    /**
     * 도박 승리 시 배당금 보너스 배율 계산
     */
    public double calculateGambleMultiplier(Map<?, ?> baseStats) {
        return 2.0 + (getStat(baseStats, 23) * 0.1);
    }

    /**
     * 비밀 상자(랜덤 가챠) 최종 비용 계산
     * @param baseStats 현재 유저의 최종/기초 스탯 맵
     * @param originalPrice 상자의 기본 가격
     * @return 스탯 할인이 적용된 최종 가격
     */
    public int calculateGambleItemCost(Map<?, ?> baseStats, int originalPrice) {
        // 9: 계율 준수/금욕 (정신적 통제)
        // 14: 논리 연산 속도 (가격 분석)
        int intelligenceSum = getStat(baseStats, 9) + getStat(baseStats, 14);

        // 할인율 수식: (스탯합 / 500) -> 스탯 합이 100이면 20%, 250이면 50% 할인
        // 최대 할인율은 80%로 제한 (0.8)
        double discountRate = Math.min(intelligenceSum / 500.0, 0.8);

        int finalPrice = (int) (originalPrice * (1.0 - discountRate));

        return Math.max(finalPrice, 100);
    }

    /**
     * UI 표시용 현재 할인율 (%)
     */
    public int calculateGambleItemDiscountPercent(Map<?, ?> baseStats) {
        int intelligenceSum = getStat(baseStats, 9) + getStat(baseStats, 14);
        double discountRate = Math.min(intelligenceSum / 500.0, 0.8);
        return (int) (discountRate * 100);
    }

    /**
     * 유저 스탯 기반 등급별 확률 가중치 계산
     * @param baseStats 현재 유저의 스탯 맵
     * @return 등급별 가중치 맵 (합계가 반드시 100일 필요는 없음)
     */
    public Map<String, Double> calculateGambleGradeWeights(Map<?, ?> baseStats) {
        // 스탯 추출 (20: 행운, 16: 통찰)
        int luck = getStat(baseStats, 20);
        int insight = getStat(baseStats, 16);

        // 기본 가중치 설정 (Common이 압도적)
        double legendaryWeight = 0.5; // 0.5%
        double epicWeight = 3.5;      // 3.5%
        double rareWeight = 16.0;     // 16.0%
        double commonWeight = 80.0;   // 80.0%

        // 스탯 보정 (행운은 모든 상위 등급에, 통찰은 레어/에픽에 보너스)
        // 행운 10당 전설 확률 +0.2%, 에픽 +1% 가량 증가 예시
        legendaryWeight += (luck * 0.05);
        epicWeight += (luck * 0.1) + (insight * 0.05);
        rareWeight += (luck * 0.2) + (insight * 0.2);

        // 상위 등급 가중치가 늘어난 만큼 Common 가중치를 줄임 (최소 10% 유지)
        commonWeight = Math.max(10.0, 100.0 - (legendaryWeight + epicWeight + rareWeight));

        Map<String, Double> weights = new HashMap<>();
        weights.put("LEGENDARY", legendaryWeight);
        weights.put("EPIC", epicWeight);
        weights.put("RARE", rareWeight);
        weights.put("COMMON", commonWeight);

        return weights;
    }

    /**
     * [던전 전용] 탐사 진척도 효율 계산
     * 이동속도 23: 공간 지각(지형 파악), 24: 직관(길찾기)
     */
    public int calculateExplorationEfficiency(Map<?, ?> baseStats) {
        // 기본 진행도 5% + 스탯 가중치
        double bonus = calculateMoveSpd(baseStats) +
                (getStat(baseStats, 23) * 0.2) +
                (getStat(baseStats, 24) * 0.2);

        return (int) (5 + bonus); // 탐사 한 번당 증가할 진척도 기본값
    }

    /**
     * [던전 전용] 휴식 안전도(확률) 계산
     * 23: 공간 지각(매복 확인), 24: 직관(위험 감지)
     */
    public double calculateRestSafetyRate(Map<?, ?> baseStats) {
        // 기본 안전도 60% + 스탯 보너스 (최대 95% 제한)
        double safety = 60.0 + (getStat(baseStats, 23) * 1.5) + (getStat(baseStats, 24) * 1.5);
        return Math.min(safety, 95.0);
    }

    /**
     * 도망 확률 계산 함수 (소프트 캡 적용)
     * 기본 30% + 스탯 보너스(임계점 20% 이후 감쇄)
     */
    public double calculateEscapeChance(Map<?, ?> baseStats) {
        double baseEscape = 30.0; // 기본 성공 확률 30%

        // 21:비복근(순발력) 0.1, 24:육감(상황판단) 0.05
        // 올 스탯 600 기준: (600 * 0.1) + (600 * 0.05) = 90.0
        double rawStatBonus = (getStat(baseStats, 21) * 0.1)
                + (getStat(baseStats, 24) * 0.05);

        // 2. 스탯 보너스 소프트 캡 적용 (임계점: +20%)
        double threshold = 20.0;
        double finalStatBonus;

        if (rawStatBonus <= threshold) {
            // 구간 1: 보너스 20%까지는 1:1 효율 (스탯 합 약 133 지점까지)
            finalStatBonus = rawStatBonus;
        } else {
            // 구간 2: 20% 초과분은 2% 효율만 적용 (소프트 캡)
            // 올 스탯 600 기준: 20 + (70 * 0.02) = 21.4
            finalStatBonus = threshold + (rawStatBonus - threshold) * 0.02;
        }

        // 3. 최종 확률 산출 (기본 30% + 보정치, 최대 상한 95%)
        double finalChance = baseEscape + finalStatBonus;

        return Math.round(Math.min(95.0, finalChance) * 10.0) / 10.0;
    }

    /**
     * [플레이어 정보] OVERRIDE
     * 24: 비복근(기동성), 22: 순발력(반응), 16: 통찰(예측)
     */
    public int calculateCombatTurns(Map<?, ?> baseStats) {
        // 1. 기본 행동 횟수 (예: 기본 2회)
        int baseTurns = 2;

        // 2. 민첩성 점수 계산 (21: 비복근, 22: 순발력)
        double agilityScore = (getStat(baseStats, 21) * 0.4) + (getStat(baseStats, 22) * 0.6);

        // 3. 통찰 보너스 (16: 통찰 - 적의 움직임을 읽어 선제권 확보)
        double insightBonus = getStat(baseStats, 16) * 0.2;

        // 4. 최종 계산: 15점당 1턴 추가 (최대 5턴 제한)
        int bonusTurns = (int) ((agilityScore + insightBonus) / 15);

        int finalTurns = baseTurns + bonusTurns;

        // 게임 밸런스를 위해 최소 2턴, 최대 5턴으로 제한
        return Math.max(2, Math.min(finalTurns, 5));
    }

    /**
     * [던전 전용] 전투 시 유저가 사용할 수 있는 행동 횟수(턴) 계산
     * 24: 비복근(기동성), 22: 순발력(반응), 16: 통찰(예측)
     */
    public int calculateCombatTurns(UserStatus user) {
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();

        // 1. 기본 행동 횟수 (예: 기본 2회)
        int baseTurns = 2;

        // 2. 민첩성 점수 계산 (21: 비복근, 22: 순발력)
        double agilityScore = (getStat(stats, 21) * 0.4) + (getStat(stats, 22) * 0.6);

        // 3. 통찰 보너스 (16: 통찰 - 적의 움직임을 읽어 선제권 확보)
        double insightBonus = getStat(stats, 16) * 0.2;

        // 4. 최종 계산: 15점당 1턴 추가 (최대 5턴 제한)
        int bonusTurns = (int) ((agilityScore + insightBonus) / 15);

        int finalTurns = baseTurns + bonusTurns;

        // 게임 밸런스를 위해 최소 2턴, 최대 5턴으로 제한
        return Math.max(2, Math.min(finalTurns, 5));
    }

    /**
     * 현재 유저 상태와 던전 상태를 기반으로 스킬 사용 가능 여부를 판단합니다. (HP 소모 추가)
     */
    public boolean checkSkillAvailability(UserStatus user, DungeonStatus ds, SkillMeta skill) {
        // 1. AP(턴) 체크
        if (ds.getPlayerRemainingTurns() < skill.getTurnCost()) return false;

        // 2. 스테미나 체크
        if (user.getCurrentStamina() < skill.getCost().getOrDefault("stamina", 0)) return false;

        // 3. 마나 체크
        if (user.getCurrentMp() < skill.getCost().getOrDefault("mp", 0)) return false;

        // 4. HP 체크 (HP 소모 스킬이 있을 경우)
        // 현재 HP가 소모량보다 적거나 같으면 시전 불가 (시전 후 최소 1은 남아야 함)
        int hpCost = skill.getCost().getOrDefault("hp", 0);
        if (hpCost > 0 && user.getCurrentHp() <= hpCost) return false;

        return true;
    }

    /**
     * 기술 고유 추가 위력 계산 (stat scaling)
     * @param skill 스킬 정보
     * @param finalStats 플레이어 최종 스탯
     * @return [int] 수치 반환
     */
    public int calculateSkillPower(SkillMeta skill, Map<Integer, Integer> finalStats) {
        double power = 0;
        if (skill.getStatScaling() != null) {
            for (Map.Entry<Integer, Double> entry : skill.getStatScaling().entrySet()) {
                int statId = entry.getKey();
                double multiplier = entry.getValue();
                int statValue = finalStats.getOrDefault(statId, 0);
                power += (statValue * multiplier);
            }
        }
        return (int) Math.round(power);
    }

    /**
     * 방어력(저항)과 관통력을 계산하여 최종 데미지를 산출합니다.
     * * [계산 원리]
     * 1. 상대의 저항에서 나의 관통력을 빼서 '최종 저항값'을 구함.
     * 2. (1.0 - 최종 저항값)을 통해 데미지 배율(Multiplier)을 결정.
     * 3. 관통이 저항보다 높으면 1.0배 이상의 추가 데미지가 발생 (재미 포인트!).
     * 4. 저항이 너무 높아 배율이 마이너스가 되더라도 최소 0(무효)으로 처리.
     */
    public int applyDefense(double rawDamage, double penetration, double resist) {
        double finalResist = resist - penetration;
        double damageMultiplier = Math.max(0, (100 - finalResist) / 100);
        return (int) Math.round(rawDamage * damageMultiplier);
    }

    /**
     * [Player] 단순 위력 계산기 (monster 생각 X)
     */
    public double expectDamage(SkillMeta skill, CombatStats attacker, Map<Integer, Integer> attackerFinalStats){
        // [STEP A] 기술 위력 + 공격자 기본 공격력
        double skillPower = (attackerFinalStats != null ) ? calculateSkillPower(skill, attackerFinalStats) : 0;

        String element = (skill.getEffect() != null && skill.getEffect().getElement() != null)
                ? skill.getEffect().getElement() : "PHYSICAL";

        boolean isMagic = "MAGIC".equals(skill.getType()) || "MAGICAL".equals(skill.getType());
        double baseAtk = isMagic ? attacker.getMagicAtk() : attacker.getMeleeAtk();
        double elementalAtk = getElementalAtkValue(attacker, element);

        String scalingKey = isMagic ? "magicAtk" : "meleeAtk";
        double scaling = skill.getDamageScaling().getOrDefault(scalingKey, 1.0);

        // [방어력 적용 전] 최종 로우 데미지
        double rawTotalDamage = ((baseAtk + elementalAtk) * scaling) + skillPower;

        return rawTotalDamage;
    }

    /**
     * [PLAYER/MONSTER] 스킬 대미지 계산기
     * 최종 통합 대미지 계산기 (BattleService에서 이걸 호출)
     * MONSTER의 경우 stat 추가 공격력 X
     */
    public int calculateFinalDamage(SkillMeta skill, CombatStats attacker, CombatStats defender, Map<Integer, Integer> attackerFinalStats) {
        // [STEP A] 방어력 적용 전 대미지
        double rawTotalDamage = (attackerFinalStats != null) ? expectDamage(skill, attacker, attackerFinalStats) : expectDamage(skill, attacker, null);

        boolean isMagic = "MAGIC".equals(skill.getType()) || "MAGICAL".equals(skill.getType());
        String element = (skill.getEffect() != null && skill.getEffect().getElement() != null)
                ? skill.getEffect().getElement() : "PHYSICAL";

        // [STEP B] 타입관통 + 속성관통
        double skillBonusPen = (skill.getEffect().getPenetration() != null && skill.getEffect().getPenetration() > 0) ? skill.getEffect().getPenetration() : 0;

        double basePen = isMagic ? attacker.getMagPen() : attacker.getPhysPen();
        double elePen = getPenetrationValue(attacker, element);
        double finalPen = basePen + elePen + skillBonusPen;

        // [STEP C] 타입저항 + 속성저항
        double targetBaseRes = isMagic ? defender.getMagRes() : defender.getPhysRes();
        double targetEleRes = getResistValue(defender, element);
        double finalRes = targetBaseRes + targetEleRes;

        // [STEP D] 관통 적용 후 대미지
        int damageAfterDef = applyDefense(rawTotalDamage, finalPen, finalRes);

        // [STEP E] 타입에따라 최종 Def 차감 처리
        double baseDef = isMagic ? 0 : defender.getPhysDef();
        damageAfterDef = (int) Math.max(0, damageAfterDef - baseDef);

        // 크리티컬 없이 기본 결과만 반환
        return damageAfterDef;
    }

    /**
     * 속성 공격력 반환
     */
    private double getElementalAtkValue(CombatStats s, String el) {
        return switch(el) {
            case "FIRE" -> s.getFireAtk();
            case "ICE" -> s.getIceAtk();
            case "LIGHT" -> s.getLightningAtk();
            case "EARTH" -> s.getEarthAtk();
            case "HOLY" -> s.getHolyAtk();
            case "DARK" -> s.getDarkAtk();
            case "CHAOS"    -> s.getChaosAtk();
            case "TOXIC"    -> s.getToxicAtk();
            default -> 0;
        };
    }

    /**
     * 데미지 저항력 반환
     */
    private double getResistValue(CombatStats s, String el) {
        return switch(el) {
            case "PHYSICAL" -> s.getPhysRes();
            case "FIRE"     -> s.getFireRes();
            case "ICE"      -> s.getIceRes();
            case "MAGIC"    -> s.getMagRes();
            case "HOLY"     -> s.getHolyRes();
            case "DARK"     -> s.getDarkRes();
            case "LIGHT"    -> s.getLightningRes();
            case "EARTH"    -> s.getEarthRes();
            case "CHAOS"    -> s.getChaosRes();
            case "TOXIC"    -> s.getToxicRes();
            default -> 0;
        };
    }

    /**
     * 데미지 관통력 반환
     */
    private double getPenetrationValue(CombatStats s, String el) {
        return switch(el) {
            case "PHYSICAL" -> s.getPhysPen();
            case "FIRE"     -> s.getFirePen();
            case "ICE"      -> s.getIcePen();
            case "HOLY"     -> s.getHolyPen();
            case "DARK"     -> s.getDarkPen();
            case "LIGHT"    -> s.getLightningPen();
            case "EARTH"    -> s.getEarthPen();
            case "CHAOS"    -> s.getChaosPen();
            case "TOXIC"    -> s.getToxicPen();
            default -> 0;
        };
    }

    /**
     * 상태이상 저항력 반환
     */
    private double getStatusResistValue(CombatStats s, String el) {
        return switch(el) {
            case "BLEED" -> s.getBleedRes();
            case "STUN"  -> s.getStunRes();
            case "BURN"  -> s.getBurnRes();
            case "FROZEN"  -> s.getFrozenRes();
            case "POISON"  -> s.getPoisonRes();
            default -> 0;
        };
    }

    /**
     * 상태이상 관통력 반환
     */
    private double getStatusPenetrationValue(CombatStats s, String el) {
        return switch(el) {
            case "BLEED" -> s.getBleedPen();
            case "STUN"  -> s.getStunPen();
            case "BURN"  -> s.getBurnPen();
            case "FROZEN"  -> s.getFrozenPen();
            case "POISON"  -> s.getPoisonPen();
            default -> 0;
        };
    }

    /**
     * 공격 회피 판정 (내 명중 + 스킬 기본 명중 - 상대의 회피율)
     * min 10% * max 100%
     * return [true/false]
     */
    public double attackerHitChance(int skillHitChance, double attackerAcc, double defenderDog) {
        double hitProb =  Math.max(10, attackerAcc + skillHitChance - defenderDog);
        return hitProb;
    }

    public int calculateHeal(UserStatus user, SkillMeta skill) {
        // 1. 기술 위력 (statScaling 기반 추가 힐량) 계산
        double skillPower = calculateSkillPower(skill, user.getBaseStats());

        // 2. 마법 여부에 따른 베이스 스탯 및 계수 결정
        boolean isMagic = "MAGIC".equals(skill.getType()) || "HEAL".equals(skill.getType());
        double baseStatValue = isMagic ? user.getCombatStats().getMagicAtk() : user.getCombatStats().getMeleeAtk();

        String scalingKey = isMagic ? "magicAtk" : "meleeAtk";
        double scaling = skill.getDamageScaling().getOrDefault(scalingKey, 1.0);
        double rawTotalHeal = (baseStatValue * scaling) + skillPower;

        return (int) Math.ceil(rawTotalHeal);
    }

    /**
     * 몬스터 [BUFF/DEBUFF] 반영 스탯 처리 (플레이어와 동일한 합연산 로직 적용)
     * @param monster 몬스터 정보
     * @return 몬스터 정보 처리해서 반환
     */
    public ActiveMonster statCalculationMonster(ActiveMonster monster) {
        if (monster == null) return null;

        // 1. 순수 베이스 스탯 복사
        CombatStats base = monster.getBaseStats();
        CombatStats result = base.toBuilder().build();

        // 2. 배율 합산을 위한 바구니
        Map<String, Double> offsetSumMap = new HashMap<>();
        Map<String, Double> modifierSumMap = new HashMap<>();

        // 3. 모든 액티브 상태에서 배율(Modifiers) 수집 및 합산
        if (monster.getActiveStatuses() != null) {
            for (ActiveStatus status : monster.getActiveStatuses()) {
                if (status.getCombatStatModifiers() != null) {
                    status.getCombatStatModifiers().forEach((name, val) ->
                            // 1.2가 들어오면 0.2를 더하고, 0.7이 들어오면 -0.3을 더함
                            modifierSumMap.merge(name, val - 1.0, Double::sum)
                    );
                }
                if (status.getCombatStatOffsets() != null) {
                    status.getCombatStatOffsets().forEach((name, val) ->
                            offsetSumMap.merge(name, val, Double::sum)
                    );
                }
            }
        }

        // 4. 최종 합산된 배율을 베이스 스탯에 한 번만 적용
        // 수치 합산
        offsetSumMap.forEach((name, val) -> {
            result.applyCombatStatsBonus(name, val);
        });
        // modifier가 0.1(10%) 이라면 1.1을 곱해주는 방식
        modifierSumMap.forEach((name, totalMod) -> {
            double finalMultiplier = 1.0 + totalMod;
            result.applyModifier(name, finalMultiplier);
        });

        monster.setActiveStats(result);

        // 5. HP/MP가 최대치를 넘지 않도록 보정
        monster.setCurrentHp(Math.min(monster.getCurrentHp(), monster.getActiveStats().getMaxHp()));
        monster.setCurrentMp(Math.min(monster.getCurrentMp(), monster.getActiveStats().getMaxMp()));

        return monster;
    }

    /**
     * 데미지 스킬 크리티컬 판정
     * @param skill 스킬 추가 확률 보정
     * @param stats 기본 확률
     * @return [true/fasle]
     */
    public boolean isCrit(SkillMeta skill, CombatStats stats){
        double skillBonusRate =(skill.getEffect().getCritRate() != null && skill.getEffect().getCritRate() > 0) ? skill.getEffect().getCritRate() : 0;
        double baseRate = stats.getCritRate();

        int finalRate = (int) Math.round(baseRate + skillBonusRate);
        return Math.random() * 100 < finalRate;
    }

    /**
     * 크리티컬 대미지 계산
     * @param skillDamage 스킬 대미지
     * @param skill 스킬 추가 크리티컬 대미지
     * @param stats 기본 크리티컬 대미지
     * @return [int] 최종 대미지
     */
    public int calculateCritDamage(int skillDamage, SkillMeta skill, CombatStats stats){
        double skillBonusDamage =(skill.getEffect().getCritDamage() != null && skill.getEffect().getCritDamage() > 0) ? skill.getEffect().getCritDamage() : 0;
        double baseDamage = stats.getCritDmg() + skillBonusDamage;
        double critMultiplier = baseDamage / 100.0;

        return (int) Math.round(skillDamage * critMultiplier);
    }

    /**
     * 부가 효과로 오는 확률만 계산
     * @param skillEffect 부가 효과 확률
     * @param attacker 공격자 상태이상 관통률
     * @param defender 방어자 상태이상 저항룰
     * @return
     */
    public int calculateStatusChance(SkillEffect skillEffect, CombatStats attacker, CombatStats defender){
        String status = skillEffect.getStatus();
        double skillBaseChance = skillEffect.getChance();
        double bonusPen = getStatusPenetrationValue(attacker, status);

        double baseRes = defender.getStatusResist();
        double bonusRes = getStatusResistValue(defender, status);

        // 상태이상 관통력 = 부여확률, 최소값 보정 0 <-- 완전 면역 몬스터 고려
        double finalChance = Math.max(0, (skillBaseChance + bonusPen)- (baseRes + bonusRes));

        return (int) Math.round(finalChance);
    }
}