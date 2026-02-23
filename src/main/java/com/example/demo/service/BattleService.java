package com.example.demo.service;

import com.example.demo.domain.meta.CombatStats;
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
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleService {

    private final GameDataManager gameDataManager;
    private final StatCalculationService statCalculationService;
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final MonsterBattleService monsterBattleService;

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

        System.out.println(skill);

        // 1. 코스트 체크 및 차감
        if (!statCalculationService.checkSkillAvailability(user, ds, skill)) {
            return "자원이 부족하여 기술을 사용할 수 없습니다.";
        }
        applyCost(user, ds, skill);

        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);

        // 2. 명중 판정 분기
        String skillType = skill.getEffect().getType();
        String resultMsg = null;

        // 도망(ESCAPE)의 확률은 scaling stat 기반
        if ("ESCAPE".equals(skillType) ) {
            return handleEscape(user, ds, skill);
        }

        // [1단계] 공격자 명중 판정 (Hit Check)
        boolean isHit = statCalculationService.isAttackerHit(
                user.getCombatStats().getAccuracy(),
                skill.getHitChance()
        );

        if (!isHit) {
            return String.format("<span style='color:#aaaaaa;'>[실패] %s 이 빗나갔습니다! (Miss)</span>", skill.getName());
        }

        // [2단계] 방어자 회피 판정 (Dodge Check)
        // 버프인 경우 회피 판정을 생략하기 위해 0 전달
        double dodgeStat = "BUFF".equals(skillType) ? 0 : monster.getStats().getDodge();
        boolean isDodged = statCalculationService.isDefenderDodge(dodgeStat);

        if (isDodged) {
            return String.format("<span style='color:#ffcc00;'>[회피] %s이(가) %s의 공격을 회피했습니다! (Dodge)</span>",
                    monster.getName(), user.getName());
        }

        switch (skillType) {
            case "WAIT" -> resultMsg =  handleWait(user, ds);
            case "DAMAGE" -> resultMsg = handleDamage(user, monster, skill, ds);
            case "BUFF", "DEBUFF" -> resultMsg = handleStatus(user, monster, skill, ds);
            default -> resultMsg = "정의되지 않은 효과입니다.";
        }

        // [중요] 상태 이상이 추가되었을 수 있으므로 실시간 스탯 재계산
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());

        // 3. 데이터 저장
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);

        return resultMsg;
    }

    /**
     * 데미지 스킬 처리 로직 (최종 통합본)
     */
    private String handleDamage(UserStatus user, ActiveMonster monster, SkillMeta skill, DungeonStatus ds) {
        // 1. 공격자/방어자 정보 및 기초 스탯(ID 1~24) 추출
        CombatStats attackerStats = user.getCombatStats();
        CombatStats defenderStats = monster.getStats();
        Map<Integer, Integer> attackerFinalStats = user.getFinalStats();

        // 2. 통합 데미지 계산기 호출
        // 이제 StatCalculationService에서 Scaling(ID 기반) + Melee/MagicAtk + 방어력 + 크리티컬을 한 번에 계산합니다.
        int finalDamage = statCalculationService.calculateFinalDamage(
                skill,
                attackerStats,
                defenderStats,
                attackerFinalStats
        );

        // 3. 실데미지 적용 및 체력 차감
        monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - finalDamage));

        // 4. 전투 로그 기록 (데미지 강조)
        ds.addLog(String.format("⚔️ <b style='color:#ffffff;'>%s</b>! %s에게 <b style='color:#ff4d4d;'>%d</b>의 피해!",
                skill.getName(), monster.getName(), finalDamage));

        // 5. 승리 조건 체크
        if (monster.getCurrentHp() <= 0) {
            finishBattle(user, ds, true);
            return "VICTORY";
        }

        // 6. 부가 효과 판정 (1/3 규칙 적용)
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyAdditionalEffect(monster, skill, ds, finalDamage); // 데미지 전달
        }

        return "HIT_SUCCESS";
    }

    /**
     * 상태이상 스킬 전용 처리 로직 (공격기 외에 디버프/버프 전용 스킬용)
     */
    private String handleStatus(UserStatus user, ActiveMonster monster, SkillMeta skill, DungeonStatus ds) {
        SkillEffect effect = skill.getEffect();

        if ("BUFF".equals(effect.getType())) {
            // BUFF는 플레이어 본인에게 적용
            user.getActiveStatuses().add(createStatus(skill, "BUFF", 0));
            ds.addLog(String.format("🛡️ %s에게 [<span style='color:#70db70;'>%s</span>] 효과 적용!", user.getName(), skill.getName()));
        } else {
            // DEBUFF는 몬스터에게 적용 (데미지가 없으므로 baseDamage에 0 전달)
            applyAdditionalEffect(monster, skill, ds, 0);
        }
        return "STATUS_APPLIED";
    }

    /**
     * [헬퍼] 대상에게 상태이상을 부여할지 판정하고 처리합니다.
     */
    private void applyAdditionalEffect(ActiveMonster monster, SkillMeta skill, DungeonStatus ds, int baseDamage) {
        if (!isStatusApplied(skill.getEffect().getChance(), monster.getStats().getStatusResist())) {
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[저항]</span> %s이(가) 효과를 저항했습니다.", monster.getName()));
            return;
        }

        String status = skill.getEffect().getStatus();
        int newTickDamage = 0;
        int newDuration = skill.getEffect().getDuration();

        // 1. 도트 데미지 계산 (BLEED, POISON, BURN 등)
        if (List.of("BLEED", "POISON", "BURN").contains(status)) {
            newTickDamage = Math.max(1, baseDamage / 3);
        }

        // 2. 기존 동일 상태이상 찾기
        ActiveStatus existingStatus = monster.getActiveStatuses().stream()
                .filter(s -> s.getEffectCode().equals(status))
                .findFirst()
                .orElse(null);

        if (existingStatus != null) {
            // [합산 및 갱신 로직]
            // 시간은 기존 남은 시간과 새 시간 중 큰 값으로 (Max)
            existingStatus.setRemainingTurns(Math.max(existingStatus.getRemainingTurns(), newDuration));

            // 데미지는 기존 값에 추가 (+)
            existingStatus.setTickDamage(existingStatus.getTickDamage() + newTickDamage);

            ds.addLog(String.format("<span style='color:#da70d6;'>[중첩]</span> %s의 <b>%s</b> 강화! (남은 %d턴 / 틱당 %d 피해)",
                    monster.getName(), status, existingStatus.getRemainingTurns(), existingStatus.getTickDamage()));
        } else {
            // [신규 부여]
            monster.getActiveStatuses().add(createStatus(skill, "DEBUFF", newTickDamage));

            String color = "FREEZE".equals(status) ? "#87ceeb" : "#da70d6";
            ds.addLog(String.format("<span style='color:%s;'>[효과]</span> %s에게 %s 부여! (%d턴)",
                    color, monster.getName(), status, newDuration));
        }
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

    /**
     * 스킬의 추가 효과 상태이상
     * @param skill 스킬 정보
     * @param category 어떤 상태이상 인지
     * @param tickDamage 틱 데미지
     * @return 디버프 상태이상 객체 반환
     */
    private ActiveStatus createStatus(SkillMeta skill, String category, int tickDamage) {
        return ActiveStatus.builder()
                .skillId(skill.getId())
                .name(skill.getName())
                .remainingTurns(skill.getEffect().getDuration())
                .category(category)
                .effectCode(skill.getEffect().getStatus())
                .tickDamage(tickDamage) // 1/3 데미지 저장
                .statModifiers(skill.getEffect().getStatModifiers())
                .statOffsets(skill.getEffect().getStatOffsets())
                .build();
    }

    /**
     * 스킬 코스트 계산 로직
     * @param user 플레이어
     * @param ds 몬스터 정보
     * @param skill 스킬
     */
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
        // 1. 기본 확률 (히트 찬스를 탈출 기본 확률로 활용)
        double escapeChance = skill.getHitChance();

        // 2. 스탯 보정치 (Scaling) 합산
        double statBonus = 0;
        if (skill.getScaling() != null) {
            for (var entry : skill.getScaling().entrySet()) {
                double statValue = user.getFinalStats().getOrDefault(entry.getKey(), 0);
                statBonus += (statValue * entry.getValue());
            }
        }

        // 3. 최종 확률 산출 (보정치 합산)
        // 예: 40(기본) + 스탯보너스(15) = 55%
        double finalChance = Math.min(95.0, escapeChance + statBonus);

        // 4. 주사위 굴리기
        if (Math.random() * 100 < finalChance) {
            ds.addLog(String.format("<span style='color:#70db70;'>[성공]</span> 도망에 성공했습니다! (확률: %.1f%%)", finalChance));
            finishBattle(user, ds, false);
            return "ESCAPE_SUCCESS";
        } else {
            ds.addLog(String.format("<span style='color:#ff4d4d;'>[실패]</span> 적이 앞을 가로막아 도망치지 못했습니다! (확률: %.1f%%)", finalChance));
            dungeonFileRepository.saveDungeonStatus(ds);
            return "ESCAPE_FAIL";
        }
    }

    /**
     * 현재 턴을 넘김니다.
     * @param user 플레이어
     * @param ds 몬스터 정보
     * @return 로그 메시지 추가
     */
    private String handleWait(UserStatus user, DungeonStatus ds) {
        ds.addLog("<b style='color:#888;'>[대기]</b> 턴을 종료합니다.");

        // 1. 몬스터 행동 처리 (도트딜, 공격, 상태이상 부여 등)
        monsterBattleService.processMonsterPhase(user, ds.getActiveMonster(), ds);

        // 2. 몬스터 사망 체크 (도트딜로 죽었을 경우)
        if (ds.getActiveMonster().getCurrentHp() <= 0) {
            finishBattle(user, ds, true);
            return "VICTORY";
        }

        // 3. 몬스터에게 디버프를 받았을 수 있으므로 플레이어 스탯 갱신
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());

        // 4. 플레이어 턴(AP) 리필
        int maxTurns = statCalculationService.calculateCombatTurns(user);
        ds.setPlayerMaxTurns(maxTurns);
        ds.setPlayerRemainingTurns(maxTurns);

        ds.addLog("<span style='color:#70db70;'>[시스템]</span> 당신의 턴이 시작되었습니다.");

        // 파일 저장
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);

        return "MONSTER_TURN_END";
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

            ActiveMonster monster = ds.getActiveMonster();
            monster.setCurrentHp(0);
            ds.setActiveMonster(monster);

        }else{
            ds.setActiveMonster(null);
            ds.setPendingExp(0);
            ds.setPendingGold(0);
        }

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