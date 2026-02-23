package com.example.demo.service;

import com.example.demo.domain.meta.SkillEffect;
import com.example.demo.domain.meta.SkillMeta;
import com.example.demo.domain.save.ActiveMonster;
import com.example.demo.domain.save.ActiveStatus;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.dto.SkillCardDto;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleService {

    private final GameDataManager gameDataManager;
    private final StatCalculationService statCalculationService;
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;

    public List<SkillCardDto> getSkillHand(UserStatus user, DungeonStatus ds) {
        // 1. 현재 무기 타입 파악 및 모든 장착 아이템으로부터 추가 스킬 수집
        String weaponType = "NONE";
        Set<Integer> availableSkillIds = new HashSet<>(user.getLearnedSkillIds()); // 영구 습득 스킬 먼저 담기

        for (var entry : user.getEquippedItems().entrySet()) {
            int itemId = entry.getValue();
            if (itemId == 0) continue;

            var itemMeta = gameDataManager.getItemMap().get(itemId);
            if (itemMeta == null) continue;

            // 무기 타입 저장
            if ("WEAPON".equals(entry.getKey())) {
                weaponType = itemMeta.getSubType();
            }

            // 아이템에 붙은 스킬 ID 리스트가 있다면 합치기 (Set이라 중복은 자동 제거됨)
            if (itemMeta.getGrantedSkillIds() != null) {
                availableSkillIds.addAll(itemMeta.getGrantedSkillIds());
            }
        }

        final String currentWeapon = weaponType;

        return gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    // [조건 1] 내가 보유한 스킬인가? (습득했거나, 장비가 부여했거나)
                    boolean hasSkill = availableSkillIds.contains(meta.getId());

                    // [조건 2] 무기 적합성 체크 (공용이거나 현재 무기와 맞거나)
                    boolean canUseWithWeapon = meta.getRequiredWeapon().equalsIgnoreCase("NONE") ||
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);

                    // [조건 3] 무기 전용 기본기 예외 처리
                    // (보유하지 않았더라도, 현재 무기 타입과 정확히 일치하는 전용 스킬은 보여줌)
                    boolean isWeaponIntrinsic = !meta.getRequiredWeapon().equalsIgnoreCase("NONE") &&
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);

                    return (hasSkill || isWeaponIntrinsic) && canUseWithWeapon;
                })
                .map(meta -> {
                    boolean canAct = statCalculationService.checkSkillAvailability(user, ds, meta);

                    // 불가 사유 구체화
                    String msg = "";
                    if (!canAct) {
                        if (ds.getPlayerRemainingTurns() < meta.getTurnCost()) msg = "AP 부족";
                        else if (user.getCurrentStamina() < meta.getCost().getOrDefault("stamina", 0)) msg = "기력 부족";
                        else if (user.getCurrentMp() < meta.getCost().getOrDefault("mp", 0)) msg = "마력 부족";
                        else if (user.getCurrentHp() <= meta.getCost().getOrDefault("hp", 0)) msg = "생명력 위험";
                    }

                    return SkillCardDto.builder()
                            .id(meta.getId())
                            .name(meta.getName())
                            .icon(meta.getIcon())
                            .description(meta.getDescription())
                            .turnCost(meta.getTurnCost())
                            .staminaCost(meta.getCost().getOrDefault("stamina", 0))
                            .mpCost(meta.getCost().getOrDefault("mp", 0))
                            .hpCost(meta.getCost().getOrDefault("hp", 0)) // HP 비용 매핑
                            .canAct(canAct)
                            .message(msg)
                            .build();
                })
                .toList();
    }

    /**
     * 스킬 실행 메인 프로세스
     */
    public String executeSkill(int skillId) {
        UserStatus user = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        SkillMeta skill = gameDataManager.getSkillMetaMap().get(skillId);
        ActiveMonster monster = ds.getActiveMonster();

        // 1. 코스트 체크 및 차감
        if (!statCalculationService.checkSkillAvailability(user, ds, skill)) {
            return "자원이 부족하여 기술을 사용할 수 없습니다.";
        }
        applyCost(user, ds, skill);

        // 2. 효과 타입별 분기 처리
        String resultMsg;
        SkillEffect effect = skill.getEffect();

        switch (effect.getType()) {
            case "DAMAGE" -> resultMsg = handleDamage(user, monster, skill, ds);
            case "BUFF", "DEBUFF" -> resultMsg = handleStatus(user, monster, skill, ds);
            case "ESCAPE" -> resultMsg = handleEscape(user, ds, skill);
            default -> resultMsg = "정의되지 않은 효과입니다.";
        }

        // [중요] 상태 이상이 추가되었을 수 있으므로 실시간 스탯 재계산
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());

        // 3. 데이터 저장
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);

        return resultMsg;
    }

    private String handleDamage(UserStatus user, ActiveMonster monster, SkillMeta skill, DungeonStatus ds) {
        // --- [STEP 1] 명중 판정 ---
        double attackerAccuracy = user.getCombatStats().getAccuracy();
        if (Math.random() * 100 > attackerAccuracy) {
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[실패] %s 기술이 헛방을 쳤습니다!</span>", skill.getName()));
            return "ATTACK_MISS";
        }

        // --- [STEP 2] 회피 판정 ---
        double monsterDodge = monster.getStats().getDodge();
        double finalDodgeChance = Math.max(0, monsterDodge - (attackerAccuracy * 0.2));
        if (Math.random() * 100 < finalDodgeChance) {
            ds.addLog(String.format("<span style='color:#ffcc00;'>[회피] %s이(가) %s을(를) 피했습니다!</span>", monster.getName(), skill.getName()));
            return "ATTACK_DODGED";
        }

        // --- [STEP 3] 데미지 계산 ---
        int damage = statCalculationService.calculateSkillDamage(user, skill);
        boolean isCrit = Math.random() * 100 < user.getCombatStats().getCritRate();
        if (isCrit) damage = (int) (damage * (user.getCombatStats().getCritDmg() / 100.0));

        int finalDamage = Math.max(1, damage - (int) monster.getStats().getPhysDef());
        monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - finalDamage));

        String critTag = isCrit ? "<b style='color:#ff4d4d;'>[크리티컬!]</b> " : "";
        ds.addLog(String.format("⚔️ %s[%s]! %s에게 %d의 피해!", critTag, skill.getName(), monster.getName(), finalDamage));

        if (monster.getCurrentHp() <= 0) {
            finishBattle(user, ds, true);
            return "VICTORY";
        }

        // --- [STEP 4] 공격 스킬의 부가 상태 이상 처리 ---
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            double baseChance = skill.getEffect().getBaseChance();
            double targetResist = monster.getStats().getStatusResist();

            if (isStatusApplied(baseChance, targetResist)) {
                monster.getActiveStatuses().add(createStatus(skill, "DEBUFF"));
                ds.addLog(String.format("<span style='color:#da70d6;'>[효과] %s에게 %s 부여!</span>", monster.getName(), skill.getName()));
            } else {
                ds.addLog(String.format("<span style='color:#aaaaaa;'>[저항] %s이(가) %s 효과를 저항했습니다.</span>", monster.getName(), skill.getName()));
            }
        }
        return "HIT_SUCCESS";
    }

    private String handleStatus(UserStatus user, ActiveMonster monster, SkillMeta skill, DungeonStatus ds) {
        SkillEffect effect = skill.getEffect();
        String type = effect.getType();

        if ("BUFF".equals(type)) {
            user.getActiveStatuses().add(createStatus(skill, "BUFF"));
            ds.addLog("🛡️ " + user.getName() + "에게 [" + skill.getName() + "] 효과 적용!");
        } else {
            double targetResist = monster.getStats().getStatusResist();
            if (isStatusApplied(effect.getBaseChance(), targetResist)) {
                monster.getActiveStatuses().add(createStatus(skill, "DEBUFF"));
                ds.addLog("💢 " + monster.getName() + "에게 [" + skill.getName() + "] 효과 적용!");
            } else {
                ds.addLog("🛡️ " + monster.getName() + "이(가) [" + skill.getName() + "]을(를) 저항했습니다!");
            }
        }
        return "STATUS_APPLIED";
    }

    /**
     * 상태 이상 저항력 판정 핵심 수식
     */
    public boolean isStatusApplied(double baseChance, double targetResist) {
        // 저항 1당 확률 1% 감소 (선형 방식)
        double resistanceFactor = targetResist / 100.0;
        double finalChance = baseChance * (1.0 - resistanceFactor);
        return Math.random() <= finalChance;
    }

    private ActiveStatus createStatus(SkillMeta skill, String category) {
        return ActiveStatus.builder()
                .skillId(skill.getId())
                .name(skill.getName())
                .remainingTurns(skill.getEffect().getDuration())
                .category(category)
                .effectCode(skill.getEffect().getStatus())
                .statModifiers(skill.getEffect().getStatModifiers()) // 비율 추가
                .statOffsets(skill.getEffect().getStatOffsets()) // 만약 데미지라면 여기에 추가
                .build();
    }

    private void applyCost(UserStatus user, DungeonStatus ds, SkillMeta skill) {
        user.setCurrentStamina(user.getCurrentStamina() - skill.getCost().getOrDefault("stamina", 0));
        user.setCurrentMp(user.getCurrentMp() - skill.getCost().getOrDefault("mp", 0));
        user.setCurrentHp(user.getCurrentHp() - skill.getCost().getOrDefault("hp", 0));
        ds.setPlayerRemainingTurns(ds.getPlayerRemainingTurns() - skill.getTurnCost());
    }

    /**
     * [도망 로직]
     */
    private String handleEscape(UserStatus user, DungeonStatus ds, SkillMeta skill) {
        SkillEffect effect = skill.getEffect();

        double baseChance = effect.getBaseChance();
        double dodgeBonus = user.getCombatStats().getDodge() * 0.5;
        double finalChance = Math.min(95.0, baseChance + dodgeBonus);

        if (Math.random() * 100 < finalChance) {
            ds.addLog("<span style='color:#70db70;'>[도망]</span> 성공! 적을 따돌리고 거리를 벌렸습니다.");

            // 전투 종료 상태 기록
            finishBattle(user, ds, false);

            return "ESCAPE_SUCCESS";
        } else {
            ds.addLog("<span style='color:#ff4d4d;'>[도망]</span> 실패! 적이 앞을 가로막습니다.");
            return "ESCAPE_FAIL";
        }
    }

    /**
     * [전투 종료 공통 처리 메서드]
     * @param isVictory 승리 여부 (승리 시에만 보상 지급)
     */
    private void finishBattle(UserStatus user, DungeonStatus ds, boolean isVictory) {
        if (isVictory) {
            // 1. 보상 지급 (경험치, 골드)
            user.setCurrentGold(user.getCurrentGold() + ds.getPendingGold());
            // TODO: 경험치 로직 (레벨업 체크 등은 별도 서비스 권장)

            ds.addLog(String.format("<span style='color:#ffd700;'>[획득] %d Gold와 %d Exp를 얻었습니다!</span>",
                    ds.getPendingGold(), ds.getPendingExp()));
        }

        // 2. 몬스터 제거 및 데이터 초기화
        ds.setActiveMonster(null);
        ds.setPendingGold(0);
        ds.setPendingExp(0);

        // 3. 전투 턴 리필 (말씀하신 대로 전투 종료 즉시 풀 충전)
        int maxTurns = statCalculationService.calculateCombatTurns(user);
        ds.setPlayerMaxTurns(maxTurns);
        ds.setPlayerRemainingTurns(maxTurns);

        // 4. 상태 저장
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
    }

    /**
     * TO-DO
     * [회복 로직]
     */
//    private String handleHeal(UserStatus user, SkillMeta skill, DungeonStatus ds) {
//        int healAmount = (int) statCalculationService.calculateHeal(user, skill);
//        user.setCurrentHp(Math.min(user.getMaxHp(), user.getCurrentHp() + healAmount));
//        ds.addLog("💚 [" + skill.getName() + "]! HP를 " + healAmount + " 회복했습니다.");
//        return "HEAL_SUCCESS";
//    }

    /**
     * 상태이상 처리
     * @param user 플레이어
     * @param monster 몬스터 메타
     * @param ds 몬스터 정보
     */
    public void processTurnEffects(UserStatus user, ActiveMonster monster, DungeonStatus ds) {
        // 1. 몬스터의 상태 이상 처리
        if (monster != null && monster.getActiveStatuses() != null) {
            monster.getActiveStatuses().removeIf(status -> {
                // [출혈/독 로직] effectCode가 BLEED나 POISON이면 체력 차감
                if ("BLEED".equals(status.getEffectCode())) {
                    int tickDamage = status.getStatOffsets().getOrDefault(999, 10); // 999를 도트딜 전용 키로 약속
                    monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - tickDamage));
                    ds.addLog(String.format("🩸 출혈! %s이(가) %d의 피해를 입었습니다.", monster.getName(), tickDamage));
                }

                // 턴 감소
                status.setRemainingTurns(status.getRemainingTurns() - 1);
                return status.getRemainingTurns() <= 0; // 0턴이면 리스트에서 제거
            });
        }
    }
}