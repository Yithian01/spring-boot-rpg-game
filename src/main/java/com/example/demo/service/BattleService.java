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

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleService {

    private final GameDataManager gameDataManager;
    private final StatCalculationService statCalculationService;
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final MonsterBattleService monsterBattleService;

    /**
     * 저장 담당 메소드
     * @param user 플레이어 정보
     * @param ds 던전 + 몬스터 정보
     */
    private void saveAll(UserStatus user, DungeonStatus ds) {
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
    }

    /**
     * 턴 시작 시 도트 데미지 처리 + 걸려있는 효과들 1턴씩 감소
     * @param user 플레이어 정보
     * @param ds 던전 + 몬스터 정보
     */
    private void updatePlayerStatusTick(UserStatus user, DungeonStatus ds) {
        if (user.getActiveStatuses() == null || user.getActiveStatuses().isEmpty()) return;

        user.getActiveStatuses().removeIf(status -> {
            String code = status.getEffectCode();
            String icon = gameDataManager.getIcon(code); // 통합 아이콘 가져오기
            boolean isExpired = false;

            // 1. 도트 데미지 처리
            if (status.getTickDamage() > 0) {
                user.setCurrentHp(Math.max(0, user.getCurrentHp() - status.getTickDamage()));
                ds.addLog(String.format("%s <b style='color:#ff4d4d;'>%s</b> 피해! (-%d HP)",
                        icon, status.getName(), status.getTickDamage()));
            }

            // 2. 특수 CC 효과 처리
            switch (code) {
                case "STUN" -> {
                    ds.setPlayerRemainingTurns(0);
                    ds.addLog(String.format("%s <b>기절</b> 상태입니다! 이번 턴을 상실합니다.", icon));
                }
                case "FROZEN", "FREEZE" -> {
                    int freezePenalty = 1;
                    ds.setPlayerRemainingTurns(Math.max(0, ds.getPlayerRemainingTurns() - freezePenalty));
                    ds.addLog(String.format("%s <b>빙결</b> 효과로 행동력이 %d 감소했습니다.", icon, freezePenalty));
                }
            }

            // 3. 지속시간 감소
            status.setRemainingTurns(status.getRemainingTurns() - 1);
            if (status.getRemainingTurns() <= 0) {
                ds.addLog(String.format("<span style='color:#aaaaaa;'>[해제] %s %s 효과 종료</span>",
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
     * UI로 사용 가능한 스킬을 반환
     * @param user 플레이어
     * @param ds 몬스터 정보
     * @return 스킬 카드 정보
     */
    public List<SkillCardDto> getSkillHand(UserStatus user, DungeonStatus ds) {
        // 1. 현재 무기 타입 파악 및 아이템 부여 스킬 수집
        String weaponType = "NONE";
        Set<Integer> availableSkillIds = new HashSet<>(user.getLearnedSkillIds());

        for (var entry : user.getEquippedItems().entrySet()) {
            int itemId = entry.getValue();
            if (itemId == 0) continue;
            var itemMeta = gameDataManager.getItemMetaMap().get(itemId);
            if (itemMeta == null) continue;

            if ("WEAPON".equals(entry.getKey())) {
                weaponType = itemMeta.getSubType();
            }
            if (itemMeta.getGrantedSkillIds() != null) {
                availableSkillIds.addAll(itemMeta.getGrantedSkillIds());
            }
        }

        final String currentWeapon = weaponType;

        return gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    boolean hasSkill = availableSkillIds.contains(meta.getId());
                    boolean canUseWithWeapon = meta.getRequiredWeapon().equalsIgnoreCase("NONE") ||
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);
                    boolean isWeaponIntrinsic = !meta.getRequiredWeapon().equalsIgnoreCase("NONE") &&
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);
                    return (hasSkill || isWeaponIntrinsic) && canUseWithWeapon;
                })
                .map(meta -> {
                    // 사용 가능 여부 체크
                    boolean canAct = statCalculationService.checkSkillAvailability(user, ds, meta);
                    String msg = "";
                    if (!canAct) {
                        if (ds.getPlayerRemainingTurns() < meta.getTurnCost()) msg = "AP 부족";
                        else if (user.getCurrentStamina() < meta.getCost().getOrDefault("stamina", 0)) msg = "기력 부족";
                        else if (user.getCurrentMp() < meta.getCost().getOrDefault("mp", 0)) msg = "마력 부족";
                        else if (user.getCurrentHp() <= meta.getCost().getOrDefault("hp", 0)) msg = "생명력 위험";
                    }

                    // --- 데이터 가공 영역 ---
                    SkillEffect effect = meta.getEffect();

                    // [4] 스케일링 정보 가공 ("meleeAtk 1.3" -> "근거리 공격력 130%")
                    List<String> scalingInfo = new ArrayList<>();
                    if (meta.getPlayerScaling() != null) {
                        meta.getPlayerScaling().forEach((k, v) -> {
                            String statLabel = k.equals("meleeAtk") ? "물리 공격력" : (k.equals("magicAtk") ? "마법 공격력" : k);
                            scalingInfo.add(statLabel + " " + (int)(v * 100) + "%");
                        });
                    }
                    if (meta.getStatScaling() != null) {
                        meta.getStatScaling().forEach((statId, val) -> {
                            // Manager에서 스탯 메타 정보 참조 (예: 10 -> "근력")
                            var statMeta = gameDataManager.getStatMetaMap().get(statId);
                            String statName = (statMeta != null) ? statMeta.getName() : "Stat " + statId;
                            scalingInfo.add(statName + " 계수 " + val);
                        });
                    }

                    // [5] 수치 보정 정보 가공 (버프/디버프 상세)
                    List<String> modifierDetails = new ArrayList<>();
                    if (effect != null) {
                        if (effect.getCombatStatModifiers() != null) {
                            effect.getCombatStatModifiers().forEach((k, v) -> {
                                String trend = v >= 1.0 ? "증가" : "감소";
                                int percent = (int) (Math.abs(1.0 - v) * 100);
                                modifierDetails.add(k + " " + percent + "% " + trend);
                            });
                        }
                        if (effect.getStatModifiers() != null) {
                            effect.getStatModifiers().forEach((statId, v) -> {
                                var statMeta = gameDataManager.getStatMetaMap().get(statId);
                                String statName = (statMeta != null) ? statMeta.getName() : "Stat " + statId;
                                String trend = v >= 1.0 ? "증가" : "감소";
                                int percent = (int) (Math.abs(1.0 - v) * 100);
                                modifierDetails.add(statName + " " + percent + "% " + trend);
                            });
                        }
                    }

                    // DTO 빌드
                    return SkillCardDto.builder()
                            .id(meta.getId())
                            .name(meta.getName())
                            .icon(meta.getIcon())
                            .description(meta.getDescription())
                            .turnCost(meta.getTurnCost())
                            .staminaCost(meta.getCost().getOrDefault("stamina", 0))
                            .mpCost(meta.getCost().getOrDefault("mp", 0))
                            .hpCost(meta.getCost().getOrDefault("hp", 0))
                            .canAct(canAct)
                            .message(msg)
                            // 신규 필드 매핑
                            .type(meta.getType())
                            .element(effect != null ? effect.getElement() : "NONE")
                            .requiredWeapon(meta.getRequiredWeapon())
                            .baseHitChance(meta.getHitChance())
                            .effectType(effect != null ? effect.getType() : "NONE")
                            .status(effect != null ? effect.getStatus() : null)
                            .statusName(effect != null ? gameDataManager.getStatusName(effect.getStatus()) : null)
                            .duration(effect != null ? effect.getDuration() : null)
                            .effectChance(effect != null ? effect.getChance() : null)
                            .scalingInfo(scalingInfo)
                            .modifierDetails(modifierDetails)
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
        saveAll(user, ds);

        // 2. 명중 판정 분기
        String skillType = skill.getType();
        String skillEffectType = skill.getEffect().getType();
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
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[실패] %s 이 빗나갔습니다! (Miss)</span>", skill.getName()));
            saveAll(user, ds);
            return "MISS";
        }

        // [2단계] 방어자 회피 판정 (Dodge Check)
        // 버프인 경우 회피 판정을 생략하기 위해 0 전달
        double dodgeStat = ("BUFF".equals(skillType) || "HEAL".equals(skillType)) ? 0 : monster.getStats().getDodge();
        boolean isDodged = statCalculationService.isDefenderDodge(dodgeStat);
        if (isDodged) {
            ds.addLog(String.format("💨 <span style='color:#ffcc00;'>[회피] %s이(가) 당신의 <b>%s</b> 공격을 회피했습니다!</span>",
                    monster.getName(), skill.getName()));
            saveAll(user, ds);
            return "Dodge";
        }

        switch (skillEffectType) {
            case "PASS" -> resultMsg =  handleWait(user, ds);
            case "DAMAGE" -> resultMsg = handleDamage(user, monster, skill, ds, false);
            case "DOT" -> resultMsg = handleDamage(user, monster, skill, ds, true);
            case "BUFF", "DEBUFF" -> resultMsg = handleStatus(user, monster, skill, ds);
            case "HEAL" -> resultMsg = handleHeal(user, skill, ds);
            default -> resultMsg = "정의되지 않은 효과입니다.";
        }

        // [중요] 상태 이상이 추가되었을 수 있으므로 실시간 스탯 재계산
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());

        // 3. 데이터 저장
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);

        return resultMsg;
    }

    /**
     * 즉발 데미지 스킬 처리 로직 (최종 통합본)
     */
    private String handleDamage(UserStatus user, ActiveMonster monster, SkillMeta skill, DungeonStatus ds, boolean isDotDmg) {
        CombatStats attackerStats = user.getCombatStats();
        CombatStats defenderStats = monster.getStats();
        Map<Integer, Integer> attackerFinalStats = user.getFinalStats();

        // 통합 데미지 계산기 (도트기여도 스케일링을 통해 계산된 수치가 나옴)
        int finalDamage = statCalculationService.calculateFinalDamage(
                skill, attackerStats, defenderStats, attackerFinalStats
        );

        String battleLog = "";
        if (!isDotDmg) {
            // [일반 공격] 몬스터 체력 즉시 차감
            monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - finalDamage));
            battleLog = String.format("⚔️ <b style='color:#ffffff;'>%s</b>! %s에게 <b style='color:#ff4d4d;'>%d</b>의 피해!",
                    skill.getName(), monster.getName(), finalDamage);
            ds.addLog(battleLog);
        }

        // 몬스터 사망 체크
        if (monster.getCurrentHp() <= 0) {
            finishBattle(user, ds, true);
            return "VICTORY";
        }

        // 부가 효과 판정 (isDotDmg 여부를 함께 전달)
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyAdditionalEffect(monster, skill, ds, finalDamage, isDotDmg);
        }

        return "HIT_SUCCESS";
    }

    /**
     * 상태이상 스킬 전용 처리 로직 (공격기 외에 디버프/버프 전용 스킬용)
     */
    private String handleStatus(UserStatus user, ActiveMonster monster, SkillMeta skill, DungeonStatus ds) {
        SkillEffect effect = skill.getEffect();
        String icon = gameDataManager.getIcon(effect.getStatus());

        if ("BUFF".equals(effect.getType())) {
            // 플레이어의 상태 이상 리스트 중에서
            // 1. 카테고리가 "BUFF"이고
            // 2. 스킬 ID가 현재 시전한 스킬과 일치하는 것만 찾기
            ActiveStatus existingBuff = user.getActiveStatuses().stream()
                    .filter(s -> "BUFF".equals(s.getCategory()) && s.getSkillId() == skill.getId())
                    .findFirst()
                    .orElse(null);

            if (existingBuff != null) {
                // 시간 갱신 (지속시간 초기화)
                existingBuff.setRemainingTurns(Math.max(existingBuff.getRemainingTurns(), effect.getDuration()));
                ds.addLog(String.format("%s %s의 [%s] 지속시간 갱신!", icon, user.getName(), skill.getName()));
            } else {
                ds.addLog(String.format("%s %s에게 [%s] 부여! (%d턴)", icon, user.getName(), skill.getName(), effect.getDuration()));
                user.getActiveStatuses().add(createStatus(skill, "BUFF", 0));
            }
        }else {
            applyAdditionalEffect(monster, skill, ds, 0, false);
        }
        return "STATUS_APPLIED";
    }

    /**
     * [헬퍼] 대상에게 상태이상을 부여할지 판정하고 처리합니다.
     */
    private void applyAdditionalEffect(ActiveMonster monster, SkillMeta skill, DungeonStatus ds, int baseDamage, boolean isDotDmg) {
        String status = skill.getEffect().getStatus();
        String icon = gameDataManager.getIcon(status);

        if (!isDotDmg && !isStatusApplied(skill.getEffect().getChance(), monster.getStats().getStatusResist())) {
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[저항] %s %s이(가) 효과를 저항했습니다.</span>",
                    icon, monster.getName()));
            return;
        }

        int newTickDamage = 0;
        int newDuration = skill.getEffect().getDuration();

        // 1. 도트 데미지 계산 (BLEED, POISON, BURN 등)
        if (isDotDmg) {
            // 1. 순수 도트 스킬: 계산된 데미지 100%를 틱뎀으로 사용
            newTickDamage = baseDamage;
        } else {
            // 2. 공격기 부가 효과 (출혈, 화상 등): 준 데미지의 1/3 적용
            if (List.of("BLEED", "POISON", "BURN", "PAIN").contains(status)) {
                newTickDamage = Math.max(1, (int) Math.ceil(baseDamage / 3.0));
            }
        }

        // 2. 기존 동일 상태이상 찾기
        ActiveStatus existingStatus = monster.getActiveStatuses().stream()
                .filter(s -> s.getEffectCode().equals(status))
                .findFirst()
                .orElse(null);

        String color = switch(status) {
            case "BURN" -> "#ff4500";
            case "FROZEN", "FREEZE" -> "#87ceeb";
            case "POISON" -> "#70db70";
            case "CURSE", "PAIN" -> "#da70d6";
            default -> "#ffffff";
        };

        if (existingStatus != null) {
            existingStatus.setRemainingTurns(Math.max(existingStatus.getRemainingTurns(), skill.getEffect().getDuration()));
            existingStatus.setTickDamage(existingStatus.getTickDamage() + newTickDamage);

            String damageInfo = existingStatus.getTickDamage() > 0 ? String.format(" / 틱당 %d 피해", existingStatus.getTickDamage()) : "";
            ds.addLog(String.format("<span style='color:%s;'>[중첩]</span> %s의 %s <b>%s</b> 강화! (남은 %d턴%s)",
                    color, monster.getName(), icon, status, existingStatus.getRemainingTurns(), damageInfo));
        } else {
            monster.getActiveStatuses().add(createStatus(skill, "DEBUFF", newTickDamage));
            String damageInfo = newTickDamage > 0 ? String.format(" (틱당 %d)", newTickDamage) : "";
            ds.addLog(String.format("<span style='color:%s;'>[효과]</span> %s에게 %s <b>%s</b> 부여! (%d턴%s)",
                    color, monster.getName(), icon, status, skill.getEffect().getDuration(), damageInfo));
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
    private String handleEscape(UserStatus user, DungeonStatus ds, SkillMeta skill) {
        // 1. 기본 확률 (히트 찬스를 탈출 기본 확률로 활용)
        double escapeChance = skill.getHitChance();

        // 2. 스탯 보정치 (Scaling) 합산
        double statBonus = 0;
        if (skill.getStatScaling() != null) {
            for (var entry : skill.getStatScaling().entrySet()) {
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

        updatePlayerStatusTick(user, ds);

        // 3. 몬스터에게 디버프를 받았을 수 있으므로 플레이어 스탯 갱신
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());

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
     * [회복 로직]
     */
    private String handleHeal(UserStatus user, SkillMeta skill, DungeonStatus ds) {
        int healAmount = statCalculationService.calculateHeal(user, skill);
        String icon = gameDataManager.getIcon("REGEN");

        user.setCurrentHp(Math.min(user.getCombatStats().getMaxHp(), user.getCurrentHp() + healAmount));
        ds.addLog(String.format("%s [<b style='color:#70db70;'>%s</b>]! HP를 %d 회복했습니다.",
                icon, skill.getName(), healAmount));
        return "HEAL_SUCCESS";
    }

    /**
     * [던전 + 마을] 자연 재생 처리
     */
    public void applyPlayerRegeneration() {
        UserStatus user = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();


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
            ds.addLog(String.format("<span style='color:#70db70;'>[자연 재생] %s</span>", regenLog.toString()));
        }

        saveAll(user, ds);
    }
}