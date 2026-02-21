package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.SkillMeta;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
     * 유저의 모든 전투 스탯을 아이템 보너스를 포함하여 완전히 새로고침합니다.
     * @param user 현재 유저 상태 (참조를 통해 직접 수정)
     * @param itemMap GameDataManager에서 관리하는 전체 아이템 메타 맵
     */
    public void refreshUserCombatStats(UserStatus user, Map<Integer, ItemMeta> itemMap) {
        // --- [STEP 1] 기초 스탯(BaseStats) 합산 ---
        // 유저 순수 스탯(B1~B24) 복사
        Map<Integer, Integer> combinedStats = new HashMap<>(user.getBaseStats());

        // 2. [아이템 보너스 합산] 장착 장비의 기초 스탯 보너스 누적
        for (Integer itemId : user.getEquippedItems().values()) {
            if (itemId != 0 && itemMap.containsKey(itemId)) {
                ItemMeta item = itemMap.get(itemId);
                if (item.getBaseStatsBonus() != null) {
                    item.getBaseStatsBonus().forEach((statId, bonus) -> {
                        combinedStats.merge(statId, bonus, Integer::sum);
                    });
                }
            }
        }
        // 3. [최종 스탯 확정] 계산된 값을 user.finalStats에 저장 (UI용/세이브용)
        user.setFinalStats(combinedStats);


        // 4. [전투 능력치 계산] 확정된 finalStats(아이템 포함)를 기반으로 수식 적용
        user.setMaxHp(calculateMaxHp(combinedStats));
        user.setMaxMp(calculateMana(combinedStats));
        user.setMaxStamina(calculateStamina(combinedStats));
        user.setHpRegen(calculateHpRegen(combinedStats));
        user.setMpRegen(calculateManaRegen(combinedStats));
        user.setMeleeAtk(calculateMeleeAtk(combinedStats));
        user.setMagicAtk(calculateMagicAtk(combinedStats));
        user.setCritRate(calculateCritRate(combinedStats));
        user.setCritDmg(calculateCritDmg(combinedStats));
        user.setPenetration(calculatePenetration(combinedStats));
        user.setPhysDef(calculatePhysDef(combinedStats));
        user.setMagRes(calculateMagRes(combinedStats));
        user.setDodge(calculateDodge(combinedStats));
        user.setAccuracy(calculateAccuracy(combinedStats));
        user.setMoveSpeed(calculateMoveSpd(combinedStats));

        // 5. [최종 고정 보너스 합산] CombatStatsBonus(깡공, 깡체 등) 추가 적
        for (Integer itemId : user.getEquippedItems().values()) {
            if (itemId != 0 && itemMap.containsKey(itemId)) {
                ItemMeta item = itemMap.get(itemId);
                if (item.getCombatStatsBonus() != null) {
                    applyFinalCombatBonus(user, item.getCombatStatsBonus());
                }
            }
        }

        // 6. [안전 장치] 최대치 초과 보정
        if (user.getCurrentHp() > user.getMaxHp()) user.setCurrentHp(user.getMaxHp());
        if (user.getCurrentMp() > user.getMaxMp()) user.setCurrentMp(user.getMaxMp());
        if (user.getCurrentStamina() > user.getMaxStamina()) user.setCurrentStamina(user.getMaxStamina());
    }

    /**
     * 아이템의 최종 고정 보너스를 유저 객체에 더함
     */
    private void applyFinalCombatBonus(UserStatus user, ItemMeta.CombatStatsBonus cb) {
        user.setMaxHp(user.getMaxHp() + cb.getMaxHp());
        user.setMaxMp(user.getMaxMp() + cb.getMaxMp());
        user.setMaxStamina(user.getMaxStamina() + cb.getMaxStamina());
        user.setHpRegen(user.getHpRegen() + cb.getHpRegen());
        user.setMpRegen(user.getMpRegen() + cb.getMpRegen());
        user.setMeleeAtk(user.getMeleeAtk() + cb.getMeleeAtk());
        user.setMagicAtk(user.getMagicAtk() + cb.getMagicAtk());
        user.setCritRate(user.getCritRate() + cb.getCritRate());
        user.setCritDmg(user.getCritDmg() + cb.getCritDmg());
        user.setPenetration(user.getPenetration() + cb.getPenetration());
        user.setPhysDef(user.getPhysDef() + cb.getPhysDef());
        user.setMagRes(user.getMagRes() + cb.getMagRes());
        user.setDodge(user.getDodge() + cb.getDodge());
        user.setAccuracy(user.getAccuracy() + cb.getAccuracy());
        user.setMoveSpeed(user.getMoveSpeed() + cb.getMoveSpeed());
    }

    /**
     * baseStats(B1~B24)를 기반으로 UserStatus의 모든 전투 능력치를 일괄 갱신합니다.
     */
    public void refreshUserCombatStats(UserStatus user) {
        Map<Integer, Integer> stats = user.getBaseStats();

        // 1. 생존 자원 최대치
        user.setMaxHp(calculateMaxHp(stats));
        user.setMaxMp(calculateMana(stats));
        user.setMaxStamina(calculateStamina(stats));

        // 2. 재생 관련
        user.setHpRegen(calculateHpRegen(stats));
        user.setMpRegen(calculateManaRegen(stats));

        // 3. 공격 관련
        user.setMeleeAtk(calculateMeleeAtk(stats));
        user.setMagicAtk(calculateMagicAtk(stats));
        user.setCritRate(calculateCritRate(stats));
        user.setCritDmg(calculateCritDmg(stats));
        user.setPenetration(calculatePenetration(stats));

        // 4. 방어 및 유틸리티
        user.setPhysDef(calculatePhysDef(stats));
        user.setMagRes(calculateMagRes(stats));
        user.setDodge(calculateDodge(stats));
        user.setAccuracy(calculateAccuracy(stats));
        user.setMoveSpeed(calculateMoveSpd(stats));

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
        int base = (int) (user.getMaxHp() * 0.2);
        int bonus = getStat(user.getBaseStats(), 3) * 5;
        return base + bonus;
    }

    /**
     * 휴식 시 MP 회복량 계산: 기본 20% + (마나 회로 전도율 * 5)
     */
    public int calculateMpRestoration(UserStatus user) {
        int base = (int) (user.getMaxMp() * 0.2);
        int bonus = getStat(user.getBaseStats(), 8) * 5;
        return base + bonus;
    }

    /**
     * 휴식 시 ST 회복량 계산: 기본 30% + (대사 효율 * 5)
     */
    public int calculateStRestoration(UserStatus user) {
        int base = (int) (user.getMaxStamina() * 0.3);
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
     * 스킬의 스케일링 맵을 기반으로 최종 위력을 계산합니다.
     * 예: {"10": 2.5} -> 10번 스탯(공격력)의 2.5배
     */
    public int calculateSkillDamage(UserStatus user, SkillMeta skill) {
        double totalDamage = 0;

        // 유저의 최종 스탯 가져오기 (아이템/버프 포함)
        Map<Integer, Integer> currentStats = (user.getFinalStats() != null)
                ? user.getFinalStats()
                : user.getBaseStats();

        // Scaling 계산 로직
        if (skill.getScaling() != null) {
            for (Map.Entry<Integer, Double> entry : skill.getScaling().entrySet()) {
                int statId = entry.getKey();
                double multiplier = entry.getValue();

                int statValue = currentStats.getOrDefault(statId, 0);
                totalDamage += (statValue * multiplier);
            }
        }

        return (int) Math.round(totalDamage);
    }
}