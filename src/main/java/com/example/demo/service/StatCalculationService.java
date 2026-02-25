package com.example.demo.service;

import com.example.demo.domain.meta.CombatStats;
import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.MonsterSkillMeta;
import com.example.demo.domain.meta.SkillMeta;
import com.example.demo.domain.save.ActiveMonster;
import com.example.demo.domain.save.ActiveStatus;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.UserStatus;
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
     * [추가] 장착된 아이템들의 기초 스탯 보너스를 합산하여 UserStatus의 전용 레이어에 저장합니다.
     * 장비 장착/해제 시에만 호출하면 됩니다.
     */
    public void updateEquipmentLayer(UserStatus user, Map<Integer, ItemMeta> itemMap) {
        Map<Integer, Integer> equipBonus = new HashMap<>();

        if (user.getEquippedItems() != null) {
            for (Integer itemId : user.getEquippedItems().values()) {
                // 아이템 ID가 0이 아니고 메타 데이터가 존재하는 경우만 합산
                if (itemId != null && itemId != 0 && itemMap.containsKey(itemId)) {
                    ItemMeta item = itemMap.get(itemId);
                    if (item.getBaseStatsBonus() != null) {
                        item.getBaseStatsBonus().forEach((statId, bonus) ->
                                equipBonus.merge(statId, bonus, Integer::sum));
                    }
                }
            }
        }

        // 유저 객체의 장비 보너스 레이어 갱신
        user.setEquipmentBonusStats(equipBonus);
        log.debug("장비 스탯 레이어 업데이트 완료: {}", equipBonus);
    }

    /**
     * 유저의 모든 전투 스탯을 [아이템 + 버프/디버프]를 포함하여 완전히 새로고침합니다.
     * 매번 아이템을 전수 조사하지 않고, 저장된 equipmentBonusStats 레이어를 활용합니다.
     * @param user 현재 유저 상태 (참조를 통해 직접 수정)
     * @param itemMap GameDataManager에서 관리하는 전체 아이템 메타 맵
     */
    public void refreshUserCombatStats(UserStatus user, Map<Integer, ItemMeta> itemMap) {
        // --- [STEP 1] 기초 스탯 레이어 통합 (Base + Equipment) ---
        Map<Integer, Double> calculationMap = new HashMap<>();

        // 1-1. 순수 태생 스탯 (BaseStats)
        user.getBaseStats().forEach((id, val) -> calculationMap.put(id, val.doubleValue()));

        // 1-2. 장비 보너스 합산 (미리 계산된 equipmentBonusStats 활용)
        if (user.getEquipmentBonusStats() != null) {
            user.getEquipmentBonusStats().forEach((id, val) ->
                    calculationMap.merge(id, val.doubleValue(), Double::sum));
        }

        // --- [STEP 2] 상태 효과 레이어 (ActiveStatus - Stat Layer) ---
        if (user.getActiveStatuses() != null && !user.getActiveStatuses().isEmpty()) {
            // 2-1. Stat Offsets (고정치 합산: 예: 힘 +10)
            for (ActiveStatus status : user.getActiveStatuses()) {
                if (status.getStatOffsets() != null) {
                    status.getStatOffsets().forEach((statId, offset) ->
                            calculationMap.merge(statId, offset.doubleValue(), Double::sum));
                }
            }

            // 2-2. Stat Modifiers (비율 보정: 예: 지능 * 1.2)
            // 곱연산이므로 합연산이 모두 끝난 후 마지막에 적용
            for (ActiveStatus status : user.getActiveStatuses()) {
                if (status.getStatModifiers() != null) {
                    status.getStatModifiers().forEach((statId, modifier) -> {
                        if (calculationMap.containsKey(statId)) {
                            double currentVal = calculationMap.get(statId);
                            calculationMap.put(statId, currentVal * modifier);
                        }
                    });
                }
            }
        }

        // --- [STEP 3] 최종 스탯(finalStats) 확정 ---
        Map<Integer, Integer> finalStats = new HashMap<>();
        calculationMap.forEach((id, val) -> finalStats.put(id, (int) Math.round(val)));
        user.setFinalStats(finalStats);

        // --- [STEP 4] 전투 능력치 수식 레이어 (Formulas) ---
        applyBaseFormulas(user, finalStats);

        // --- [STEP 5] 최종 보정 레이어 (Combat Stats Bonuses & Modifiers) ---
        // 5-1. 아이템 깡스탯 (CombatStatsBonus: 예: 물리공격력 +50)
        for (Integer itemId : user.getEquippedItems().values()) {
            if (itemId != null && itemId != 0 && itemMap.containsKey(itemId)) {
                ItemMeta item = itemMap.get(itemId);
                if (item.getCombatStatsBonus() != null) {
                    applyCombatStatsBonus(user, item.getCombatStatsBonus());
                }
            }
        }

        // 5-2. ActiveStatus 전투 비율 보정 (CombatModifiers: 예: 최종 데미지 1.5배)
        if (user.getActiveStatuses() != null) {
            for (ActiveStatus status : user.getActiveStatuses()) {
                if (status.getCombatModifiers() != null) {
                    applyCombatModifiers(user, status.getCombatModifiers());
                }
            }
        }

        // --- [STEP 6] 자원 상한선 보정 ---
        user.setCurrentHp(Math.min(user.getCurrentHp(), user.getCombatStats().getMaxHp()));
        user.setCurrentMp(Math.min(user.getCurrentMp(), user.getCombatStats().getMaxMp()));
        user.setCurrentStamina(Math.min(user.getCurrentStamina(), user.getCombatStats().getMaxStamina()));
    }

    /**
     * [헬퍼] 수식을 통한 기본 전투 능력치 세팅
     */
    private void applyBaseFormulas(UserStatus user, Map<Integer, Integer> stats) {
        user.getCombatStats().setMaxHp(calculateMaxHp(stats));
        user.getCombatStats().setMaxMp(calculateMana(stats));
        user.getCombatStats().setMaxStamina(calculateStamina(stats));
        user.getCombatStats().setHpRegen(calculateHpRegen(stats));
        user.getCombatStats().setMpRegen(calculateManaRegen(stats));
        user.getCombatStats().setMeleeAtk(calculateMeleeAtk(stats));
        user.getCombatStats().setMagicAtk(calculateMagicAtk(stats));
        user.getCombatStats().setCritRate(calculateCritRate(stats));
        user.getCombatStats().setCritDmg(calculateCritDmg(stats));
        user.getCombatStats().setPenetration(calculatePenetration(stats));
        user.getCombatStats().setPhysDef(calculatePhysDef(stats));
        user.getCombatStats().setMagRes(calculateMagRes(stats));
        user.getCombatStats().setDodge(calculateDodge(stats));
        user.getCombatStats().setAccuracy(calculateAccuracy(stats));
        user.getCombatStats().setMoveSpeed(calculateMoveSpd(stats));
        user.getCombatStats().setStatusResist(calculateStatusResist(stats));
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
        return (100 + (getStat(baseStats, 1) * 10) + (getStat(baseStats, 2) * 5) + (getStat(baseStats, 3) * 2));
    }

    /**
     * 최대 마나 계산 함수
     */
    public int calculateMana(Map<?, ?> baseStats) {
        return (50 + (getStat(baseStats, 4) * 10) + (getStat(baseStats, 5) * 2));
    }

    /**
     * 최대 스태미나 계산 함수
     */
    public int calculateStamina(Map<?, ?> baseStats) {
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
     * 물리 공격력 계산 함수
     */
    public double calculateMeleeAtk(Map<?, ?> baseStats) {
        return (getStat(baseStats, 10) * 2.0) + (getStat(baseStats, 11) * 1.5) + (getStat(baseStats, 12) * 0.5);
    }

    /**
     * 마법 공격력 계산 함수
     */
    public double calculateMagicAtk(Map<?, ?> baseStats) {
        return (getStat(baseStats, 13) * 2.5) + (getStat(baseStats, 14) * 1.0);
    }

    /**
     * 치명타 확률 계산 함수
     */
    public double calculateCritRate(Map<?, ?> baseStats) {
        return (getStat(baseStats, 15) * 1.0) + (getStat(baseStats, 16) * 0.5); // 기존 코드 오타 수정 (13,14 중복 방지)
    }

    /**
     * 치명타 피해 계산 함수
     */
    public double calculateCritDmg(Map<?, ?> baseStats) {
        return (150 + (getStat(baseStats, 17) * 2.0));
    }

    /**
     * 관통력 계산 함수
     */
    public double calculatePenetration(Map<?, ?> baseStats) {
        return (getStat(baseStats, 17) * 0.5) + (getStat(baseStats, 13) * 0.5);
    }

    /**
     * 물리 방어 계산 함수
     */
    public double calculatePhysDef(Map<?, ?> baseStats) {
        return (getStat(baseStats, 2) * 1.5) + (getStat(baseStats, 1) * 0.5);
    }

    /**
     * 마법 저항 계산 함수
     */
    public double calculateMagRes(Map<?, ?> baseStats) {
        return (getStat(baseStats, 18) * 1.0) + (getStat(baseStats, 19) * 0.5);
    }

    /**
     * 회피율 계산 함수
     */
    public double calculateDodge(Map<?, ?> baseStats) {
        return (getStat(baseStats, 20) * 0.8) + (getStat(baseStats, 21) * 0.5) + (getStat(baseStats, 22) * 0.3);
    }

    /**
     * 명중율 계산 함수
     */
    public double calculateAccuracy(Map<?, ?> baseStats) {
        return (getStat(baseStats, 16) * 1.0) + (getStat(baseStats, 23) * 0.5) + (getStat(baseStats, 24) * 0.2);
    }

    /**
     * 이동속도 계산 함수
     */
    public double calculateMoveSpd(Map<?, ?> baseStats) {
        return (getStat(baseStats, 21) * 0.1) + (getStat(baseStats, 7) * 0.05);
    }

    /**
     * [추가] 상태 이상 저항력 계산 함수
     * 3: 대사 효율 (물리적 저항)
     * 9: 계율 준수 (정신적 저항)
     */
    public double calculateStatusResist(Map<?, ?> baseStats) {
        // 기본 저항 5.0 + (대사 효율 * 0.8) + (계율 준수 * 0.8)
        double resistScore = 5.0 + (getStat(baseStats, 3) * 0.8) + (getStat(baseStats, 9) * 0.8);
        // 최대 80%까지만 저항 가능하도록 캡핑
        return Math.min(resistScore, 80.0);
    }

    /**
     * 노동 효율(파워) 계산: 상완/대흉근/전완근 기반
     */
    public int calculateWorkPower(Map<?, ?> baseStats) {
        return getStat(baseStats, 10) + getStat(baseStats, 11) + getStat(baseStats, 12);
    }

    /**
     * 예상 획득 골드 계산
     */
    public int calculateEarnedGold(Map<?, ?> baseStats) {
        int power = calculateWorkPower(baseStats);
        return 30 + (power * 2); // 기본 30G + 파워당 2G
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
     * 기본 40% + (행운 스탯 * 1.5) + (통찰 스탯 * 0.5)
     */
    public double calculateGambleWinRate(Map<?, ?> baseStats) {
        double baseRate = 10.0;
        double luckBonus = getStat(baseStats, 20) * 1.5;
        double insightBonus = getStat(baseStats, 16) * 0.5;

        return Math.min(baseRate + luckBonus + insightBonus, 85.0);
    }

    /**
     * 도박 승리 시 배당금 보너스 배율 계산
     * 기본 2.0배 + (지능 스탯 * 0.05)
     */
    public double calculateGambleMultiplier(Map<?, ?> baseStats) {
        double baseMultiplier = 2.0;
        double intelligenceBonus = getStat(baseStats, 23) * 0.05;

        return baseMultiplier + intelligenceBonus;
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
     * 21: 비복근(기동성), 23: 공간 지각(지형 파악), 24: 직관(길찾기)
     */
    public int calculateExplorationEfficiency(Map<?, ?> baseStats) {
        // 기본 진행도 5% + 스탯 가중치
        double bonus = (getStat(baseStats, 21) * 0.1) +
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
     * 1. 기술 고유 위력 계산 (Scaling 기반)
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
     * 2. 방어력/관통력 감쇄 계산 (점감법)
     */
    public int applyDefense(double rawDamage, double penetration, double defense) {
        double effectiveDefense = defense * (1 - (penetration / 100.0));
        double damageReduction = 100.0 / (100.0 + Math.max(0, effectiveDefense));
        return (int) Math.round(rawDamage * damageReduction);
    }

    /**
     * 3. 최종 통합 데미지 계산기 (BattleService에서 이걸 호출)
     */
    public int calculateFinalDamage(SkillMeta skill, CombatStats attacker, CombatStats defender, Map<Integer, Integer> attackerFinalStats) {
        // [STEP A] 기술 위력 + 공격자 기본 공격력
        double skillPower = calculateSkillPower(skill, attackerFinalStats); // 추가 공격력(스탯기반)

        boolean isMagic = "MAGIC".equals(skill.getType());
        double baseAtk = isMagic ? attacker.getMagicAtk() : attacker.getMeleeAtk();

        String scalingKey = isMagic ? "magicAtk" : "meleeAtk";
        double scaling = skill.getPlayerScaling().getOrDefault(scalingKey, 1.0);

        double rawTotalDamage = (baseAtk * scaling) + skillPower;

        // [STEP B] 방어력 및 관통 적용
        double targetDef = isMagic ? defender.getMagRes() : defender.getPhysDef();
        int damageAfterDef = applyDefense(rawTotalDamage, attacker.getPenetration(), targetDef);

        // [STEP C] 크리티컬 판정 (skill.getEffect().getCritMod()가 있다면 추가 곱연산 가능)
        if (Math.random() * 100 < attacker.getCritRate()) {
            double critMultiplier = attacker.getCritDmg() / 100.0;

            // 암습 같은 스킬에 특수 치명타 배율이 있다면 적용
            if (skill.getEffect() != null && skill.getEffect().getCritMod() != null && skill.getEffect().getCritMod() > 0) {
                critMultiplier *= skill.getEffect().getCritMod();
            }

            damageAfterDef = (int) Math.round(damageAfterDef * critMultiplier);
        }

        return Math.max(1, damageAfterDef);
    }

    /**
     * 1단계: 공격자 명중 판정 (내 명중 + 스킬 기본 명중)
     */
    public boolean isAttackerHit(double attackerAcc, int skillHitChance) {
        // 예: (유저 명중 14 + 스킬 명중 70) = 84% 확률로 '정확한 공격'
        double hitProb = attackerAcc + skillHitChance;
        hitProb = Math.max(10, Math.min(100, hitProb)); // 최소 10% 보장

        return Math.random() * 100 <= hitProb;
    }

    /**
     * 2단계: 방어자 회피 판정 (상대 회피율)
     */
    public boolean isDefenderDodge(double defenderDodge) {
        // 예: 상대 회피 12 = 12% 확률로 '회피 성공'
        double dodgeProb = defenderDodge;
        dodgeProb = Math.max(0, Math.min(95, dodgeProb)); // 최대 95%까지만 회피 가능

        return Math.random() * 100 <= dodgeProb;
    }

    /**
     * 몬스터 데미지 계산기 (MonsterBattleService에서 호출)
     * 몬스터의 스케일링 공격력과 유저의 방어력을 대조하여 최종 데미지 산출
     */
    public int calculateMonsterDamage(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill) {
        double rawDamage = 0.0;
        double defenseValue = 0.0;

        // 1. 스킬 타입에 따른 공격력 및 방어 스탯 결정
        // PHYSICAL이면 meleeAtk 사용, 그 외(MAGIC 등)는 magicAtk 사용
        if ("PHYSICAL".equals(skill.getType())) {
            double scaling = skill.getMonsterScaling().getOrDefault("meleeAtk", 1.0);
            rawDamage = monster.getActiveStats().getMeleeAtk() * scaling;
            defenseValue = user.getCombatStats().getPhysDef(); // 유저의 물리 방어력
        } else {
            // MAGIC 또는 MONSTER_MAGIC 등 마법 계열 처리
            double scaling = skill.getMonsterScaling().getOrDefault("magicAtk", 1.0);
            rawDamage = monster.getActiveStats().getMagicAtk() * scaling;
            defenseValue = user.getCombatStats().getMagRes(); // 유저의 마법 저항력
        }

        // 2. 방어력 적용 (기존에 작성된 applyDefense 메서드 활용)
        // 몬스터에게 관통(Penetration) 스탯이 있다면 monster.getStats().getPenetration() 전달
        double penetration = monster.getActiveStats().getPenetration();

        int finalDamage = applyDefense(rawDamage, penetration, defenseValue);

        // 3. 최소 데미지 보정 (아무리 방어력이 높아도 최소 1의 피해는 입힘)
        return Math.max(1, finalDamage);
    }

    public int calculateHeal(UserStatus user, SkillMeta skill) {
        // 1. 기술 위력 (statScaling 기반 추가 힐량) 계산
        double skillPower = calculateSkillPower(skill, user.getBaseStats());

        // 2. 마법 여부에 따른 베이스 스탯 및 계수 결정
        boolean isMagic = "MAGIC".equals(skill.getType()) || "HEAL".equals(skill.getType());
        double baseStatValue = isMagic ? user.getCombatStats().getMagicAtk() : user.getCombatStats().getMeleeAtk();

        String scalingKey = isMagic ? "magicAtk" : "meleeAtk";
        double scaling = skill.getPlayerScaling().getOrDefault(scalingKey, 1.0);
        double rawTotalHeal = (baseStatValue * scaling) + skillPower;

        return (int) Math.ceil(rawTotalHeal);
    }

    /**
     * 몬스터 [BUFF/DEBUFF] 반영 스탯 처리
     * @param monster 몬스터 정보
     * @return 몬스터 정보 처리해서 반환
     */
    public ActiveMonster statCalculationMonster(ActiveMonster monster) {
        if(monster == null) return null;

        CombatStats calculatedStats = monster.getBaseStats().toBuilder().build();

        // 5-2. ActiveStatus 전투 비율 보정 (CombatModifiers: 예: 최종 데미지 1.5배)
        if (monster.getActiveStatuses() != null) {
            for (ActiveStatus status : monster.getActiveStatuses()) {
                if (status.getCombatModifiers() != null) {
                    applyCombatModifiers(calculatedStats, status.getCombatModifiers());
                }
            }
        }
        monster.setActiveStats(calculatedStats);
        monster.setCurrentHp(Math.min(monster.getCurrentHp(), monster.getActiveStats().getMaxHp()));
        monster.setCurrentMp(Math.min(monster.getCurrentMp(), monster.getActiveStats().getMaxMp()));

        return monster;
    }
}