package com.example.demo.service;

import com.example.demo.domain.meta.*;
import com.example.demo.domain.save.*;
import com.example.demo.dto.SkillCardDto;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleService {

    private final GameDataManager gameDataManager;
    private final StatCalculationService statCalculationService;
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final GameFileRepository gameFileRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final EssenceRepository essenceRepository;
    private final MonsterBattleService monsterBattleService;
    private final EssenceService essenceService;
    private final DropItemService dropItemService;

    /**
     * 저장 담당 메소드 + 행동 포인트 올림
     * @param user 플레이어 정보
     * @param ds 던전 + 몬스터 정보
     */
    private void saveAll(UserStatus user, DungeonStatus ds, GameStatus gs) {
        ds.setActionCount(ds.getActionCount() + 1);
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * 행동 카운트를 올리지 않고 현재 모든 상태(로그 포함)를 저장
     */
    private void saveCurrentState(UserStatus user, DungeonStatus ds, GameStatus gs) {
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.saveGameStatus(gs); // 로그가 파일에 기록됨
    }

    /**
     * 저장 담당 메소드
     * @param user 플레이어 정보
     * @param ds 던전 + 몬스터 정보
     */
    private void saveAllNotCount(UserStatus user, DungeonStatus ds) {
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
    }

    /**
     * 턴 시작 시 도트 데미지 처리 + 걸려있는 효과들 1턴씩 감소
     * @param user 플레이어 정보
     * @param ds 던전 + 몬스터 정보
     * @param gs 로그 처리
     */
    public void updatePlayerStatusTick(UserStatus user, DungeonStatus ds, GameStatus gs) {
        if (user.getActiveStatuses() == null || user.getActiveStatuses().isEmpty()) return;

        user.getActiveStatuses().removeIf(status -> {
            String code = status.getEffectCode();
            String icon = gameDataManager.getIcon(code); // 통합 아이콘 가져오기
            boolean isExpired = false;

            // 1. 도트 데미지 처리
            if (status.getTickDamage() > 0) {
                user.setCurrentHp(Math.max(0, user.getCurrentHp() - status.getTickDamage()));
                gs.addLog(String.format("%s <b style='color:#ff4d4d;'>%s</b> 피해! (-%d HP)",
                        icon, status.getName(), status.getTickDamage()));
            }

            // 2. 특수 CC 효과 처리
            switch (code) {
                case "STUN" -> {
                    ds.setPlayerRemainingTurns(0);
                    gs.addLog(String.format("%s <b>기절</b> 상태입니다! 이번 턴을 상실합니다.", icon));
                }
                case "FROZEN", "FREEZE" -> {
                    int freezePenalty = 1;
                    ds.setPlayerRemainingTurns(Math.max(0, ds.getPlayerRemainingTurns() - freezePenalty));
                    gs.addLog(String.format("%s <b>빙결</b> 효과로 행동력이 %d 감소했습니다.", icon, freezePenalty));
                }
            }

            // 3. 지속시간 감소
            status.setRemainingTurns(status.getRemainingTurns() - 1);
            if (status.getRemainingTurns() <= 0) {
                gs.addLog(String.format("<span style='color:#aaaaaa;'>[해제] %s %s 효과 종료</span>",
                        gameDataManager.getIcon(code), status.getName()));
                isExpired = true;
                userFileRepository.saveUserStatus(user);
                statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());
            }
            return isExpired;
        });

        userFileRepository.saveUserStatus(user);
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());
    }

    /**
     * UI로 사용 가능한 모든 스킬을 하나의 리스트에 담아 반환
     * (플레이어 스킬 + 정수 스킬 통합)
     */
    public List<SkillCardDto> getSkillHand(UserStatus user, DungeonStatus ds) {
        ActiveMonster monster = ds.getActiveMonster();
        List<SkillCardDto> totalHand = new ArrayList<>();

        // 1. 현재 무기 타입 파악 및 아이템 부여 스킬 ID 수집
        String weaponType = "NONE";
        Set<Integer> availableSkillIds = new HashSet<>(user.getLearnedSkillIds());

        for (var entry : user.getEquippedItems().entrySet()) {
            if (entry.getValue() == null || "0".equals(entry.getValue())) continue;
            ItemInstance ii = itemInstanceRepository.findById(entry.getValue()).orElse(null);
            if (ii == null) continue;

            if ("WEAPON".equals(entry.getKey())) weaponType = ii.getSubType();
            if (ii.getGrantedSkillIds() != null) availableSkillIds.addAll(ii.getGrantedSkillIds());
        }
        final String currentWeapon = weaponType;

        // ---------------------------------------------------------
        // 2. 플레이어 본연의 스킬 처리 (SkillMeta 기반)
        // ---------------------------------------------------------
        List<SkillCardDto> playerSkills = gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    boolean hasSkill = availableSkillIds.contains(meta.getId());
                    List<String> required = meta.getRequiredWeapons();
                    boolean isGeneric = required.contains("NONE");
                    boolean canUse = required.contains(currentWeapon);
                    boolean isIntrinsic = !isGeneric && canUse;
                    return (hasSkill && (isGeneric || canUse)) || isIntrinsic;
                })
                .map(meta -> buildSkillCardDto(user, ds, monster, meta, meta.getGrade(), meta.getIcon()))
                .toList();

        totalHand.addAll(playerSkills);

        // ---------------------------------------------------------
        // 3. 정수(Essence) 스킬 처리 (MonsterSkillMeta -> SkillCardDto)
        // ---------------------------------------------------------
        Map<String, EssenceInstance> essenceMap = essenceRepository.findAll();
        if (user.getActiveEssenceIds() != null && essenceMap != null) {
            for (String activeId : user.getActiveEssenceIds()) {
                EssenceInstance ei = essenceMap.get(activeId);
                if (ei == null || ei.getActiveSkillIds() == null) continue;

                for (Integer sId : ei.getActiveSkillIds()) {
                    // 정수 스킬은 MonsterSkillMetaMap에서 가져옴
                    SkillMeta mMeta = gameDataManager.getMonsterSkillMetaMap().get(sId);
                    if (mMeta == null) continue;

                    // 무기 제한 필터링
                    if (!mMeta.getRequiredWeapons().contains("NONE") &&
                            !mMeta.getRequiredWeapons().contains(currentWeapon)) continue;

                    // DTO 생성 (skillType: MONSTER, icon: monsterId 기반)
                    String monsterIcon = "/images/monsters/" + ei.getMonsterId() + ".png";
                    totalHand.add(buildSkillCardDto(user, ds, monster, mMeta, mMeta.getGrade(), monsterIcon));
                }
            }
        }

        return totalHand;
    }

    /**
     * [공통 가공 함수] SkillMeta 정보를 바탕으로 UI용 DTO를 빌드
     */
    private SkillCardDto buildSkillCardDto(UserStatus user, DungeonStatus ds, ActiveMonster monster,
                                           SkillMeta meta, String skillType, String iconPath) {

        boolean canAct = statCalculationService.checkSkillAvailability(user, ds, meta);
        String msg = "";
        if (!canAct) {
            if (ds.getPlayerRemainingTurns() < meta.getTurnCost()) msg = "AP 부족";
            else if (user.getCurrentStamina() < meta.getCost().getOrDefault("stamina", 0)) msg = "기력 부족";
            else if (user.getCurrentMp() < meta.getCost().getOrDefault("mp", 0)) msg = "마력 부족";
        }

        // 스케일링 가공
        List<String> scalingInfo = new ArrayList<>();
        if (meta.getDamageScaling() != null) {
            // [A] 공격력 계수 (물리/마법 + 속성)
            boolean isMagic = "MAGIC".equals(meta.getType()) || "MAGICAL".equals(meta.getType());
            String baseLabel = isMagic ? "마법" : "물리";
            String element = (meta.getEffect() != null) ? meta.getEffect().getElement() : "NONE";

            double atkScaling = meta.getDamageScaling().getOrDefault(isMagic ? "magicAtk" : "meleeAtk", 0.0);
            if (atkScaling > 0) {
                String elementLabel = gameDataManager.getKoreanElement(element);
                String label = (element.equals("NONE") || element.equals("PHYSICAL")) ? baseLabel : baseLabel + "+" + elementLabel;
                scalingInfo.add(label + " : " + (int)(atkScaling * 100) + "%");
            }

            // [B] 세부 스탯 보정 (제공해주신 스탯 리스트 기반)
            if (meta.getStatScaling() != null) {
                meta.getStatScaling().forEach((statId, factor) -> {
                    String statName = gameDataManager.getStatName(statId);
                    scalingInfo.add(statName + " : " + (int)(factor * 100) + "%");
                });
            }
        }

        // 명중/위력 계산
        int realHitChance = 0;
        if (monster != null) {
            int skillHitChance = meta.getHitChance();
            double attackerAcc = user.getCombatStats().getAccuracy();
            double defenderDog = monster.getBaseStats().getDodge();
            realHitChance = (int) statCalculationService.attackerHitChance(skillHitChance, attackerAcc, defenderDog);
        }

        int expectedPower = (int) statCalculationService.expectDamage(meta, user.getCombatStats(), user.getFinalStats());

        return SkillCardDto.builder()
                .id(meta.getId())
                .name(meta.getName())
                .icon(iconPath)
                .description(meta.getDescription())
                .skillType(skillType)

                .turnCost(meta.getTurnCost())
                .staminaCost(meta.getCost().getOrDefault("stamina", 0))
                .mpCost(meta.getCost().getOrDefault("mp", 0))
                .hpCost(meta.getCost().getOrDefault("hp", 0))
                .canAct(canAct)
                .message(msg)

                .type(meta.getType())
                .element(meta.getEffect() != null ? meta.getEffect().getElement() : "NONE")
                .requiredWeapons(meta.getRequiredWeapons())

                .effectType(meta.getEffect() != null ? meta.getEffect().getType() : null)
                .status(meta.getEffect() != null ? meta.getEffect().getStatus() : null)
                .statusName(meta.getEffect() != null ? gameDataManager.getStatusName(meta.getEffect().getStatus()) : null)
                .duration(meta.getEffect() != null ? meta.getEffect().getDuration() : null)
                .effectChance(meta.getEffect() != null ? meta.getEffect().getChance() : null)

                .scalingInfo(scalingInfo)

                .expectedPower(expectedPower)
                .realHitChance(realHitChance)
                .build();
    }

    /**
     * 스킬 실행 메인 프로세스
     */
    public String executeSkill(int skillId, String skillCardType) {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        SkillMeta skill;

        if ("MONSTER".equals(skillCardType)) {
            // 몬스터 정수 스킬 맵에서 가져오기
            skill = gameDataManager.getMonsterSkillMetaMap().get(skillId);
        } else {
            // 기존 플레이어 스킬 맵에서 가져오기
            skill = gameDataManager.getSkillMetaMap().get(skillId);
        }
        if (skill == null) {
            log.error("Skill not found! ID: {}, Type: {}", skillId, skillCardType);
            return "존재하지 않는 기술입니다.";
        }
        ActiveMonster monster = ds.getActiveMonster();

        // 1. 코스트 체크 및 차감
        if (!statCalculationService.checkSkillAvailability(us, ds, skill)) {
            return "자원이 부족하여 기술을 사용할 수 없습니다.";
        }

        applyCost(us, ds, skill);
        saveCurrentState(us, ds, gs);

        // 2. 명중 판정 분기
        String skillType = skill.getType();
        String skillEffectType = skill.getEffect().getType();
        String resultMsg = null;

        applyPlayerRegeneration(us, gs);

        // 도망(ESCAPE)의 확률은 scaling stat 기반
        if ("ESCAPE".equals(skillType) ) {
            return handleEscape(us, ds, gs, skill);
        }

        boolean isAlwaysHit = "BUFF".equals(skillType) || "HEAL".equals(skillType) || "PASS".equals(skillEffectType);

        if (!isAlwaysHit) {
            // 공격 스킬인 경우에만 명중/회피 계산
            int skillHitBonus = skill.getHitChance();
            double attackerAcc = us.getCombatStats().getAccuracy();
            double defenderDodge = monster.getActiveStats().getDodge();
            double finalHitChance =  statCalculationService.attackerHitChance(skillHitBonus, attackerAcc, defenderDodge);

            // 최종 확률 계산
            if (Math.random() * 100 > finalHitChance) {
                String failType = (Math.random() < 0.5) ? "회피" : "실패";
                String logMsg;

                if ("회피".equals(failType)) {
                    // 방어자가 주인공인 로그
                    logMsg = String.format("💨 <span style='color:#ffcc00;'>[회피] %s이(가) 당신의 <b>%s</b> 공격을 피했습니다!</span>",
                            monster.getName(), skill.getName());
                } else {
                    // 공격자가 주인공인 로그
                    logMsg = String.format("<span style='color:#aaaaaa;'>[빗나감] 당신의 <b>%s</b> 공격이 빗나갔습니다!</span>",
                            skill.getName());
                }
                logMsg += String.format(" <small style='color:#888;'>(확률: %d%%)</small>", (int) finalHitChance);
                gs.addLog(logMsg);
                saveAll(us, ds, gs);
                return "MISS";
            }
        }

        switch (skillEffectType) {
            case "PASS" -> resultMsg =  handlePass(us, ds, gs);
            case "DAMAGE" -> resultMsg = handleDamage(us, monster, skill, ds, gs, false);
            case "DOT" -> resultMsg = handleDamage(us, monster, skill, ds, gs, true);
            case "BUFF", "DEBUFF" -> resultMsg = handleStatus(us, monster, skill, ds, gs);
            case "HEAL" -> resultMsg = handleHeal(us, skill, ds, gs);
            default -> resultMsg = "정의되지 않은 효과입니다.";
        }

        // [중요] 상태 이상이 추가되었을 수 있으므로 실시간 스탯 재계산
        statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());

        // 3. 데이터 저장
        saveAll(us, ds, gs);

        return resultMsg;
    }

    /**
     * 즉발 데미지 스킬 처리 로직 (최종 통합본)
     */
    private String handleDamage(UserStatus us, ActiveMonster monster, SkillMeta skill, DungeonStatus ds, GameStatus gs, boolean isDotDmg) {
        CombatStats attackerStats = us.getCombatStats();
        CombatStats defenderStats = monster.getActiveStats();
        Map<Integer, Integer> attackerFinalStats = us.getFinalStats();

        String battleLog = "";

        // 1. 기본 데미지 계산 (StatCalculationService에서는 이제 순수 데미지만 반환)
        int baseDamage = statCalculationService.calculateFinalDamage(
                skill, attackerStats, defenderStats, attackerFinalStats
        );

        if (baseDamage == 0) {
            String icon = gameDataManager.getIcon(skill.getEffect().getElement());

            battleLog = String.format("%s %s! %s에게 공격이 통하지 않습니다! <b style='color:#ffcc00;'>[RESIST]</b>",
                    icon, skill.getName(), monster.getName());

            gs.addLog(battleLog);
            saveCurrentState(us, ds, gs);
        }

        // 2. 치명타 판정 (도트 스킬 포함 모든 데미지 스킬 적용)
        boolean isCrit = statCalculationService.isCrit(skill, us.getCombatStats());
        int finalDamage = isCrit ? statCalculationService.calculateCritDamage(baseDamage, skill, us.getCombatStats()) : baseDamage;


        // 3. 데미지 적용 분기
        if (!isDotDmg) {
            // [즉발 공격]
            monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - finalDamage));

            String critPrefix = (finalDamage > 0 && isCrit) ? "<b style='color:#ffcc00;'>[치명타!] 💥 </b>" : "⚔️ ";
            battleLog = String.format("%s<b style='color:#ffffff;'>%s</b>! %s에게 <b style='color:#ff4d4d;'>%d</b>의 피해!",
                    critPrefix, skill.getName(), monster.getName(), finalDamage);

            gs.addLog(battleLog);
            saveCurrentState(us, ds, gs);
        } else {
            // [도트 스킬 (최초 부여 시점)]
            // 도트 스킬 자체가 '데미지' 타입을 가지고 있다면,
            // 여기서 결정된 finalDamage가 applyAdditionalEffect로 넘어가 틱 데미지의 기준이 됨
            String critText = isCrit ? "<small style='color:#ffcc00;'>(치명타 적용됨)</small>" : "";
            gs.addLog(String.format("✨ <b>%s</b> 시전! %s", skill.getName(), critText));
        }

        // 4. 몬스터 사망 체크
        if (monster.getCurrentHp() <= 0) {
            finishBattle(us, ds, gs, true);
            return "VICTORY";
        }

        // 5. 부가 효과 및 도트 데미지 부여
        // 여기서 finalDamage를 넘겨주므로, 치명타가 터진 도트 데미지는 틱당 위력도 강해짐
        if (finalDamage > 0 && skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyAdditionalEffect(skill, us, monster, gs, finalDamage, isDotDmg);
        }

        return "HIT_SUCCESS";
    }

    /**
     * 상태이상 스킬 전용 처리 로직 (공격기 외에 디버프/버프 전용 스킬용)
     */
    private String handleStatus(UserStatus attacker, ActiveMonster defender, SkillMeta skill, DungeonStatus ds, GameStatus gs) {
        SkillEffect effect = skill.getEffect();
        String icon = gameDataManager.getIcon(effect.getStatus());

        if ("BUFF".equals(effect.getType())) {
            // 플레이어의 상태 이상 리스트 중에서
            // 1. 카테고리가 "BUFF"이고
            // 2. 스킬 ID가 현재 시전한 스킬과 일치하는 것만 찾기
            ActiveStatus existingBuff = attacker.getActiveStatuses().stream()
                    .filter(s -> "BUFF".equals(s.getCategory()) && s.getSkillId() == skill.getId())
                    .findFirst()
                    .orElse(null);

            if (existingBuff != null) {
                // 시간 갱신 (지속시간 초기화)
                existingBuff.setRemainingTurns(Math.max(existingBuff.getRemainingTurns(), effect.getDuration()));
                gs.addLog(String.format("%s %s의 [%s] 지속시간 갱신!", icon, attacker.getName(), skill.getName()));
            } else {
                gs.addLog(String.format("%s %s에게 [%s] 부여! (%d턴)", icon, attacker.getName(), skill.getName(), effect.getDuration()));
                attacker.getActiveStatuses().add(createStatus(skill, "BUFF", 0));
            }
        }else {
            applyAdditionalEffect(skill, attacker, defender, gs, -1, false);
        }
        saveCurrentState(attacker, ds, gs);
        return "STATUS_APPLIED";
    }

    /**
     * [헬퍼] 대상에게 상태이상을 부여할지 판정하고 처리합니다.
     */
    private void applyAdditionalEffect(SkillMeta skill, UserStatus attacker, ActiveMonster defender, GameStatus gs, int baseDamage, boolean isDotDmg) {
        String status = skill.getEffect().getStatus();
        String icon = gameDataManager.getIcon(status);

        // [저항 판정] 도트 데미지 계산이 아닐 때(즉, 최초 부여 시)만 저항 확률 체크
        if (baseDamage > -1 && !isDotDmg) {
            int finalApplyChance = statCalculationService.calculateStatusChance(skill.getEffect(), attacker.getCombatStats(), defender.getActiveStats());

            // 5. 판정
            if (Math.random() * 100 > finalApplyChance) {
                gs.addLog(String.format("<span style='color:#aaaaaa;'>[저항] %s %s이(가) 효과를 저항했습니다! (확률: %d%%)</span>",
                        icon, defender.getName(), finalApplyChance));
                return;
            }
        }

        int newTickDamage = 0;
        boolean isStatDebuff = (skill.getEffect().getCombatStatModifiers() != null && !skill.getEffect().getCombatStatModifiers().isEmpty())
                || (skill.getEffect().getStatModifiers() != null && !skill.getEffect().getStatModifiers().isEmpty());

        // 1. 도트 데미지 수치 계산
        if (isDotDmg) {
            newTickDamage = baseDamage; // 순수 도트 스킬
        } else if (List.of("BLEED", "POISON", "BURN", "PAIN").contains(status)) {
            newTickDamage = Math.max(1, (int) Math.ceil(baseDamage / 3.0)); // 부가 효과 도트
        }

        // 2. 기존 상태이상 찾기 (분기 처리)
        ActiveStatus existingStatus;
        if (isStatDebuff) {
            // [디버프류] 스킬 ID로 찾아서 개별 인스턴스 유지 (다양성 확보)
            existingStatus = defender.getActiveStatuses().stream()
                    .filter(s -> s.getSkillId() == skill.getId())
                    .findFirst().orElse(null);
        } else {
            // [도트류] 기존처럼 상태 코드(status)로 찾아서 데미지 합산
            existingStatus = defender.getActiveStatuses().stream()
                    .filter(s -> s.getEffectCode().equals(status))
                    .findFirst().orElse(null);
        }

        // 3. 로그 컬러 설정
        String color = switch(status) {
            case "BURN" -> "#ff4500";
            case "FROZEN", "FREEZE" -> "#87ceeb";
            case "POISON" -> "#70db70";
            case "CURSE", "PAIN" -> "#da70d6";
            default -> "#ffffff";
        };

        if (existingStatus != null) {
            existingStatus.setRemainingTurns(Math.max(existingStatus.getRemainingTurns(), skill.getEffect().getDuration()));

            if (!isStatDebuff) {
                existingStatus.setTickDamage(existingStatus.getTickDamage() + newTickDamage);
                String damageInfo = String.format(" / 틱당 %d 피해", existingStatus.getTickDamage());
                gs.addLog(String.format("<span style='color:%s;'>[중첩]</span> %s의 %s <b>%s</b> 강화! (남은 %d턴%s)",
                        color, defender.getName(), icon, status, existingStatus.getRemainingTurns(), damageInfo));
            } else {
                gs.addLog(String.format("<span style='color:%s;'>[유지]</span> %s의 <b>%s</b> 효과가 갱신되었습니다. (%d턴)",
                        color, defender.getName(), skill.getName(), existingStatus.getRemainingTurns()));
            }
        } else {
            ActiveStatus newStatus = createStatus(skill, isStatDebuff ? "DEBUFF" : "DOT", newTickDamage);
            newStatus.setSkillId(skill.getId());
            defender.getActiveStatuses().add(newStatus);

            String damageInfo = newTickDamage > 0 ? String.format(" (틱당 %d)", newTickDamage) : "";
            String effectName = isStatDebuff ? skill.getName() : status;
            gs.addLog(String.format("<span style='color:%s;'>[효과]</span> %s에게 %s <b>%s</b> 부여! (%d턴%s)",
                    color, defender.getName(), icon, effectName, skill.getEffect().getDuration(), damageInfo));
        }
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
                .combatModifiers(skill.getEffect().getCombatStatModifiers())
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
    private String handleEscape(UserStatus us, DungeonStatus ds, GameStatus gs, SkillMeta skill) {
        // 1. 기본 확률 (히트 찬스를 탈출 기본 확률로 활용)
        double escapeChance = skill.getHitChance();

        // 2. 스탯 보정치 (Scaling) 합산
        double statBonus = 0;
        if (skill.getStatScaling() != null) {
            for (var entry : skill.getStatScaling().entrySet()) {
                double statValue = us.getFinalStats().getOrDefault(entry.getKey(), 0);
                statBonus += (statValue * entry.getValue());
            }
        }

        // 3. 최종 확률 산출 (보정치 합산)
        // 예: 40(기본) + 스탯보너스(15) = 55%
        double finalChance = Math.min(95.0, escapeChance + statBonus);

        // 4. 주사위 굴리기
        if (Math.random() * 100 < finalChance) {
            gs.addLog(String.format("<span style='color:#70db70;'>[성공]</span> 도망에 성공했습니다! (확률: %.1f%%)", finalChance));
            finishBattle(us, ds, gs, false);
            return "ESCAPE_SUCCESS";
        } else {
            gs.addLog(String.format("<span style='color:#ff4d4d;'>[실패]</span> 적이 앞을 가로막아 도망치지 못했습니다! (확률: %.1f%%)", finalChance));
            saveCurrentState(us, ds, gs);
            return "ESCAPE_FAIL";
        }
    }

    /**
     * 현재 턴을 넘김니다.
     * @param us 플레이어 정보
     * @param ds 몬스터 정보
     * @param gs 게임 정보
     * @return 로그 메시지 추가
     */
    private String handlePass(UserStatus us, DungeonStatus ds, GameStatus gs) {
        gs.addLog("<b style='color:#888;'>[대기]</b> 턴을 종료합니다.");

        // 1. 몬스터 행동 처리 (도트딜, 공격, 상태이상 부여 등)
        monsterBattleService.processMonsterPhase(us, ds.getActiveMonster(), ds, gs);

        // 2. 몬스터 사망 체크 (도트딜로 죽었을 경우)
        if (ds.getActiveMonster().getCurrentHp() <= 0) {
            finishBattle(us, ds, gs,true);
            return "VICTORY";
        }

        updatePlayerStatusTick(us, ds, gs);

        // 3. 몬스터에게 디버프를 받았을 수 있으므로 플레이어 스탯 갱신
        statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());

        // 4. 플레이어 턴(AP) 리필
        int maxTurns = statCalculationService.calculateCombatTurns(us);
        ds.setPlayerMaxTurns(maxTurns);
        ds.setPlayerRemainingTurns(maxTurns);

        //4-2. 플레이어 스테미나 회복
        int stRecovery = statCalculationService.calculateStRestoration(us);
        us.setCurrentStamina(Math.min(us.getCombatStats().getMaxStamina(), us.getCurrentStamina() + stRecovery));

        gs.addLog("<span style='color:#70db70;'>[시스템]</span> 당신의 턴이 시작되었습니다.");

        // 파일 저장
        saveCurrentState(us, ds, gs);
        return "MONSTER_TURN_END";
    }

    /**
     * [전투 종료 공통 처리 메서드]
     * 승리 시 즉시 경험치 정산 -> 레벨업 체크 -> 상승된 레벨 기반 정수 생성 순으로 진행합니다.
     */
    private void finishBattle(UserStatus us, DungeonStatus ds, GameStatus gs, boolean isVictory) {
        if (isVictory) {
            ActiveMonster monster = ds.getActiveMonster();
            MonsterMeta monsterMeta = gameDataManager.getMonsterMetaMap().get(monster.getMonsterId());

            if (us.getDefeatedMonsterIds() == null) {
                us.setDefeatedMonsterIds(new HashSet<>());
            }

            // 1. 경험치 즉시 정산 및 레벨업 (DungeonService에서 이관된 핵심 로직)
            if (!us.getDefeatedMonsterIds().contains(monster.getMonsterId())) {
                int expAmount = ds.getPendingExp();
                if (expAmount > 0) {
                    us.setCurrentExp(us.getCurrentExp() + expAmount);
                    us.getDefeatedMonsterIds().add(monster.getMonsterId());

                    // [핵심] 경험치를 먼저 더한 후 바로 레벨업 체크
                    checkLevelUp(us, gs);

                    gs.addLog(String.format("<span style='color:#51cf66;'>[전투 완료] %s 처치 성공!</span>", monster.getName()));
                }
            }

            // 2. [추가] 마석 및 아이템 드롭 처리 (마석은 100%, 일반 템은 확률)
            if (monsterMeta != null) {

                // DropItemService 호출 (마석 100% 생성 포함)
                dropItemService.processDrops(monsterMeta);

                // 로그 추가
                gs.addLog(String.format("<span style='color:#74c0fc;'>💎 %s의 마석을 획득했습니다.</span>", monster.getName()));
            }

            // 3. 정수 드랍 판정 (이제 us.getLevel()은 레벨업이 완료된 상태임)
            double dropChance = 100.0;
            if (Math.random() * 100 < dropChance) {
                EssenceInstance dropped = essenceService.generateEssence(monster.getMonsterId());
                ds.setPendingEssence(dropped);
                gs.addLog(String.format("<b style='color:#ffd700;'>✨ [발견] %s의 정수가 드랍되었습니다!</b>", monster.getName()));
            }

            // 4. 필드 상태 업데이트 (UI 표시용)
            monster.setCurrentHp(0);
            ds.setPendingExp(0);
            ds.setActiveMonster(monster);

            int nextProgress = Math.min(100, ds.getProgress() + statCalculationService.calculateExplorationEfficiency(us.getBaseStats()));
            ds.setProgress(nextProgress);

        } else {
            // 패배 시 초기화
            ds.setActiveMonster(null);
            ds.setPendingExp(0);
            ds.setPendingEssence(null);
        }

        // 5. 전투 턴 리필 (신규 레벨/스탯 기준 재계산 가능)
        int maxTurns = statCalculationService.calculateCombatTurns(us);
        ds.setPlayerMaxTurns(maxTurns);
        ds.setPlayerRemainingTurns(maxTurns);

        // 6. 상태 저장
        saveCurrentState(us, ds, gs);
    }

    /**
     * [회복 로직]
     */
    private String handleHeal(UserStatus us, SkillMeta skill, DungeonStatus ds, GameStatus gs) {
        int healAmount = statCalculationService.calculateHeal(us, skill);
        String icon = gameDataManager.getIcon("REGEN");

        us.setCurrentHp(Math.min(us.getCombatStats().getMaxHp(), us.getCurrentHp() + healAmount));
        gs.addLog(String.format("%s [<b style='color:#70db70;'>%s</b>]! HP를 %d 회복했습니다.",
                icon, skill.getName(), healAmount));

        saveCurrentState(us, ds, gs);
        return "HEAL_SUCCESS";
    }

    /**
     * [던전 + 전투] 자연 재생 처리
     */
    public void applyPlayerRegeneration(UserStatus user, GameStatus gs) { // 인자 추가
        // 여기서 새로 가져오지 말고 넘겨받은걸 씁니다.
        if (user == null || user.getCurrentHp() <= 0) return;

        StringBuilder regenLog = new StringBuilder();
        boolean recovered = false;

        // HP 재생 (최대치 초과 방지)
        double hpRegen = user.getCombatStats().getHpRegen();
        if (hpRegen > 0 && user.getCurrentHp() < user.getCombatStats().getMaxHp()) {
            int oldHp = user.getCurrentHp();
            int newHp = Math.min(user.getCombatStats().getMaxHp(), oldHp + (int)hpRegen);
            int actualHp = newHp - oldHp;
            if (actualHp > 0) {
                user.setCurrentHp(newHp);
                regenLog.append(String.format("💚 HP +%d ", actualHp));
                recovered = true;
            }
        }

        // MP 재생 (최대치 초과 방지)
        double mpRegen = user.getCombatStats().getMpRegen();
        if (mpRegen > 0 && user.getCurrentMp() < user.getCombatStats().getMaxMp()) {
            int oldMp = user.getCurrentMp();
            int newMp = Math.min(user.getCombatStats().getMaxMp(), oldMp + (int)mpRegen);
            int actualMp = newMp - oldMp;
            if (actualMp > 0) {
                user.setCurrentMp(newMp);
                regenLog.append(String.format("💙 MP +%d ", actualMp));
                recovered = true;
            }
        }

        if (recovered) {
            gs.addLog(String.format("<span style='color:#70db70;'>[자연 재생] %s</span>", regenLog.toString()));
        }
        // 여기서 저장(save)하지 않습니다. executeSkill의 마지막 saveAll에서 한꺼번에 저장될 거니까요.
    }

    /**
     * 레벨업 처리 (경험치 이월 및 자동 스탯 성장)
     */
    private void checkLevelUp(UserStatus us, GameStatus gs) {
        boolean leveledUp = false;

        // while문을 사용하여 한 번에 여러 레벨이 오르는 경우 처리
        while (us.getCurrentExp() >= us.getRequiredExp() && us.getRequiredExp() > 0) {
            us.setCurrentExp(us.getCurrentExp() - us.getRequiredExp()); // 경험치 이월
            us.setLevel(us.getLevel() + 1);

            // 다음 레벨 필요 경험치 상승 (예: 기존 대비 40% 증가)
            us.setRequiredExp((int)(us.getRequiredExp() * 1.4));

            // [중요] potentials 기반 자동 스탯 성장 로직 호출 가능
            applyPotentialGrowth(us, gs);

            leveledUp = true;
            gs.addLog(String.format("<b style='color:#00ff00;'>[LEVEL UP!] 레벨이 %d가 되었습니다!</b>", us.getLevel()));
        }

        if (leveledUp) {
            // 최대 체력 등이 변했을 것이므로 전투 능력치 재계산
            statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());
            // currentHp는 건드리지 않음 (질문하신 의도 반영)
        }
    }

    /**
     * 유저의 Potentials(잠재력 등급)에 따른 자동 스탯 성장 실행
     */
    private void applyPotentialGrowth(UserStatus us, GameStatus gs) {
        Map<Integer, Integer> baseStats = us.getBaseStats();
        Map<Integer, Integer> potentials = us.getPotentials();

        // 로그를 예쁘게 한 줄로 모으기 위한 리스트
        List<String> growthResults = new ArrayList<>();

        // 고정된 기본 성장치 (이 값을 조절하여 게임의 전체적인 성장 속도를 제어할 수 있습니다)
        final double BASE_GROWTH_UNIT = 5.0;

        for (Integer statId : baseStats.keySet()) {
            int potentialId = potentials.getOrDefault(statId, 7); // 기본 F등급(ID:7)

            // [작성하신 메서드 활용] 잠재력 등급별 가중치 가져오기 (예: S=2.0, B=1.0, F=0.2)
            double weight = gameDataManager.getPotentialWeight(potentialId);

            // 공식 적용: 기본 5 * 가중치 (반올림 처리)
            int increment = (int) Math.round(BASE_GROWTH_UNIT * weight);

            if (increment > 0) {
                // 1. 실제 데이터(BaseStats) 반영
                int before = baseStats.getOrDefault(statId, 0);
                baseStats.put(statId, before + increment);

                // 2. 로그용 데이터 수집
                StatMeta statMeta = gameDataManager.getStatMetaMap().get(statId);
                String statName = (statMeta != null) ? statMeta.getName() : "스탯" + statId;
                growthResults.add(statName + "+" + increment);
            }
        }

        // 로그 출력: "└ [성장] 근력+10, 민첩+5, 지능+1"
        if (!growthResults.isEmpty()) {
            String logMsg = "  <span style='color:#ce93d8;'>└ [성장] " + String.join(", ", growthResults) + "</span>";
            gs.addLog(logMsg);
        }
    }
}