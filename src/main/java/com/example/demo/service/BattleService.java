package com.example.demo.service;

import com.example.demo.domain.meta.*;
import com.example.demo.domain.save.*;
import com.example.demo.dto.SkillCardDto;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.ItemInstanceRepository;
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
    private final GameFileRepository gameFileRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final MonsterBattleService monsterBattleService;
    private final EssenceService essenceService;

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
     */
    private void updatePlayerStatusTick(UserStatus user, DungeonStatus ds, GameStatus gs) {
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
     * UI로 사용 가능한 스킬을 반환
     * @param user 플레이어
     * @param ds 몬스터 정보
     * @return 스킬 카드 정보
     */
    public List<SkillCardDto> getSkillHand(UserStatus user, DungeonStatus ds) {
        ActiveMonster monster = ds.getActiveMonster();

        // 1. 현재 무기 타입 파악 및 아이템 부여 스킬 수집
        String weaponType = "NONE";
        Set<Integer> availableSkillIds = new HashSet<>(user.getLearnedSkillIds());

        for (var entry : user.getEquippedItems().entrySet()) {
            String slotName = entry.getKey();
            String instanceId = entry.getValue();

            if (instanceId == null || "0".equals(instanceId)) continue;

            ItemInstance ii = itemInstanceRepository.findById(instanceId).orElse(null);
            if (ii == null) continue;

            // (A) 무기 타입 파악 (WEAPON 슬롯인 경우)
            if ("WEAPON".equals(slotName)) {
                weaponType = ii.getSubType();
            }

            // (B) 아이템이 부여하는 스킬 수집 (Meta 정보 + 인스턴스 추가 스킬 모두 포함)
            if (ii.getGrantedSkillIds() != null) {
                availableSkillIds.addAll(ii.getGrantedSkillIds());
            }
        }

        final String currentWeapon = weaponType;

        return gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    // 1. 스킬 소유 여부 (학습했거나 아이템 부여 스킬인가?)
                    boolean hasSkill = availableSkillIds.contains(meta.getId());

                    // 2. 무기 조건 리스트 가져오기 (List<String>이라고 가정)
                    List<String> requiredWeapons = meta.getRequiredWeapons();

                    // 3. 무기 조건 없이 사용 가능한가? (NONE 포함 여부)
                    boolean isGenericSkill = requiredWeapons.stream()
                            .anyMatch(w -> w.equalsIgnoreCase("NONE"));

                    // 4. 현재 무기로 사용 가능한가?
                    boolean canUseWithCurrentWeapon = requiredWeapons.stream()
                            .anyMatch(w -> w.equalsIgnoreCase(currentWeapon));

                    // 5. 무기 고유 스킬인가? (학습하지 않았어도 무기 타입이 맞으면 노출)
                    boolean isWeaponIntrinsic = !isGenericSkill && canUseWithCurrentWeapon;

                    // 최종 조건: (배운 스킬이면서 현재 무기로 쓸 수 있거나) OR (현재 무기의 고유 스킬이거나)
                    return (hasSkill && (isGenericSkill || canUseWithCurrentWeapon)) || isWeaponIntrinsic;
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

                    // 1. 최종 명중률 계산 (상대 회피 고려)
                    // 공식: (내 명중 + 스킬 명중) - 상대 회피
                    int realHitChance = 0;
                    if (monster != null) {
                        double attackerAcc = user.getCombatStats().getAccuracy();
                        int skillHit = meta.getHitChance();
                        double defenderDodge = meta.getType().equals("BUFF") || meta.getType().equals("HEAL")
                                ? 0 : monster.getActiveStats().getDodge();

                        // (공격자 명중 + 스킬 기본 명중) * (1 - 상대 회피율/100) 형태로 가거나 단순 차감
                        // 여기서는 직관적으로 (명중확률 - 회피확률)로 계산
                        realHitChance = (int) Math.max(5, Math.min(100, (attackerAcc + skillHit) - defenderDodge));
                    }

                    // 2. 예상 위력 계산
                    int expectedPower = 0;
                    if ("HEAL".equals(meta.getEffect().getType())) {
                        expectedPower = statCalculationService.calculateHeal(user, meta);
                    } else if ("DAMAGE".equals(meta.getEffect().getType()) || "DOT".equals(meta.getEffect().getType())) {
                        // 방어력을 제외한 순수 위력 혹은 평균 데미지 계산
                        expectedPower = statCalculationService.calculateSkillPower(meta, user.getFinalStats());
                        // 기본 공격력 합산 (calculateFinalDamage의 로직 일부 차용)
                        boolean isMagic = "MAGIC".equals(meta.getType());
                        double baseAtk = isMagic ? user.getCombatStats().getMagicAtk() : user.getCombatStats().getMeleeAtk();
                        double scaling = meta.getPlayerScaling().getOrDefault(isMagic ? "magicAtk" : "meleeAtk", 1.0);
                        expectedPower += (int)(baseAtk * scaling);
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
                            .type(meta.getType())
                            .element(effect != null ? effect.getElement() : "NONE")
                            .requiredWeapons(meta.getRequiredWeapons())
                            .baseHitChance(meta.getHitChance())
                            .effectType(effect != null ? effect.getType() : "NONE")
                            .status(effect != null ? effect.getStatus() : null)
                            .statusName(effect != null ? gameDataManager.getStatusName(effect.getStatus()) : null)
                            .duration(effect != null ? effect.getDuration() : null)
                            .effectChance(effect != null ? effect.getChance() : null)
                            .scalingInfo(scalingInfo)
                            .modifierDetails(modifierDetails)
                            .realHitChance(realHitChance) // 신규: 실시간 명중률
                            .expectedPower(expectedPower) // 신규: 실시간 예상 위력
                            .build();
                })
                .toList();
    }

    /**
     * 스킬 실행 메인 프로세스
     */
    public String executeSkill(int skillId) {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        SkillMeta skill = gameDataManager.getSkillMetaMap().get(skillId);
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

        // 도망(ESCAPE)의 확률은 scaling stat 기반
        if ("ESCAPE".equals(skillType) ) {
            return handleEscape(us, ds, gs, skill);
        }

        boolean isAlwaysHit = "BUFF".equals(skillType) || "HEAL".equals(skillType) || "PASS".equals(skillEffectType);

        if (!isAlwaysHit) {
            // 공격 스킬인 경우에만 명중/회피 계산
            double attackerAcc = us.getCombatStats().getAccuracy();
            int skillHitBonus = skill.getHitChance();
            double defenderDodge = monster.getActiveStats().getDodge();

            // 최종 확률 계산
            int finalHitChance = (int) Math.max(0, Math.min(100, (attackerAcc + skillHitBonus) - defenderDodge));

            if (Math.random() * 100 > finalHitChance) {
                String failType = (Math.random() < 0.5) ? "회피" : "실패";
                String logMsg;

                if ("회피".equals(failType)) {
                    // 방어자가 주인공인 로그
                    logMsg = String.format("💨 <span style='color:#ffcc00;'>[회피] %s이(가) 당신의 <b>%s</b> 공격을 유연하게 피했습니다!</span>",
                            monster.getName(), skill.getName());
                } else {
                    // 공격자가 주인공인 로그
                    logMsg = String.format("<span style='color:#aaaaaa;'>[빗나감] 당신의 <b>%s</b> 공격이 허공을 갈랐습니다!</span>",
                            skill.getName());
                }
                logMsg += String.format(" <small style='color:#888;'>(확률: %d%%)</small>", finalHitChance);
                gs.addLog(logMsg);
                saveAll(us, ds, gs);
                return "MISS";
            }
        }

        switch (skillEffectType) {
            case "PASS" -> resultMsg =  handleWait(us, ds, gs);
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

        // 1. 기본 데미지 계산 (StatCalculationService에서는 이제 순수 데미지만 반환)
        int baseDamage = statCalculationService.calculateFinalDamage(
                skill, attackerStats, defenderStats, attackerFinalStats
        );

        // 2. 치명타 판정 (도트 스킬 포함 모든 데미지 스킬 적용)
        boolean isCrit = false;
        int finalDamage = baseDamage;

        if (Math.random() * 100 < attackerStats.getCritRate()) {
            isCrit = true;
            double critMultiplier = attackerStats.getCritDmg() / 100.0;

            // 스킬 고유의 치명타 배율 보정이 있다면 추가 곱산
            if (skill.getEffect() != null && skill.getEffect().getCritMod() != null) {
                critMultiplier *= skill.getEffect().getCritMod();
            }
            finalDamage = (int) Math.round(baseDamage * critMultiplier);
        }

        String battleLog = "";

        // 3. 데미지 적용 분기
        if (!isDotDmg) {
            // [즉발 공격]
            monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - finalDamage));

            String critPrefix = isCrit ? "<b style='color:#ffcc00;'>[치명타!] 💥 </b>" : "⚔️ ";
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
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyAdditionalEffect(monster, skill, gs, finalDamage, isDotDmg);
        }

        return "HIT_SUCCESS";
    }

    /**
     * 상태이상 스킬 전용 처리 로직 (공격기 외에 디버프/버프 전용 스킬용)
     */
    private String handleStatus(UserStatus us, ActiveMonster monster, SkillMeta skill, DungeonStatus ds, GameStatus gs) {
        SkillEffect effect = skill.getEffect();
        String icon = gameDataManager.getIcon(effect.getStatus());

        if ("BUFF".equals(effect.getType())) {
            // 플레이어의 상태 이상 리스트 중에서
            // 1. 카테고리가 "BUFF"이고
            // 2. 스킬 ID가 현재 시전한 스킬과 일치하는 것만 찾기
            ActiveStatus existingBuff = us.getActiveStatuses().stream()
                    .filter(s -> "BUFF".equals(s.getCategory()) && s.getSkillId() == skill.getId())
                    .findFirst()
                    .orElse(null);

            if (existingBuff != null) {
                // 시간 갱신 (지속시간 초기화)
                existingBuff.setRemainingTurns(Math.max(existingBuff.getRemainingTurns(), effect.getDuration()));
                gs.addLog(String.format("%s %s의 [%s] 지속시간 갱신!", icon, us.getName(), skill.getName()));
            } else {
                gs.addLog(String.format("%s %s에게 [%s] 부여! (%d턴)", icon, us.getName(), skill.getName(), effect.getDuration()));
                us.getActiveStatuses().add(createStatus(skill, "BUFF", 0));
            }
        }else {
            applyAdditionalEffect(monster, skill, gs, 0, false);
        }
        saveCurrentState(us, ds, gs);
        return "STATUS_APPLIED";
    }

    /**
     * [헬퍼] 대상에게 상태이상을 부여할지 판정하고 처리합니다.
     */
    private void applyAdditionalEffect(ActiveMonster monster, SkillMeta skill, GameStatus gs, int baseDamage, boolean isDotDmg) {
        String status = skill.getEffect().getStatus();
        String icon = gameDataManager.getIcon(status);

        // [저항 판정] 도트 데미지 계산이 아닐 때(즉, 최초 부여 시)만 저항 확률 체크
        if (!isDotDmg) {
            // 1. 스킬 기본 확률 (예: 70%)
            double baseChance = skill.getEffect().getChance();

            // 2. 공격자(유저)의 추가 부여 확률 (CombatStats에 부여 보너스가 있다고 가정, 없으면 0)
            // 예: 특정 장비나 특성으로 인한 "상태이상 부여 확률 +10%"
            double attackerBonus = 0; // 필요 시 us.getCombatStats().getStatusPen() 등으로 확장 가능

            // 3. 방어자(몬스터)의 저항력 (예: 30)
            double defenderResist = monster.getActiveStats().getStatusResist();

            // 4. 최종 확률 계산: (스킬 확률 + 내 보너스) - 상대 저항
            int finalApplyChance = (int) Math.max(5, Math.min(100, (baseChance + attackerBonus) - defenderResist));

            // 5. 판정
            if (Math.random() * 100 > finalApplyChance) {
                gs.addLog(String.format("<span style='color:#aaaaaa;'>[저항] %s %s이(가) 효과를 저항했습니다! (확률: %d%%)</span>",
                        icon, monster.getName(), finalApplyChance));
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
            existingStatus = monster.getActiveStatuses().stream()
                    .filter(s -> s.getSkillId() == skill.getId())
                    .findFirst().orElse(null);
        } else {
            // [도트류] 기존처럼 상태 코드(status)로 찾아서 데미지 합산
            existingStatus = monster.getActiveStatuses().stream()
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
                        color, monster.getName(), icon, status, existingStatus.getRemainingTurns(), damageInfo));
            } else {
                gs.addLog(String.format("<span style='color:%s;'>[유지]</span> %s의 <b>%s</b> 효과가 갱신되었습니다. (%d턴)",
                        color, monster.getName(), skill.getName(), existingStatus.getRemainingTurns()));
            }
        } else {
            ActiveStatus newStatus = createStatus(skill, isStatDebuff ? "DEBUFF" : "DOT", newTickDamage);
            newStatus.setSkillId(skill.getId());
            monster.getActiveStatuses().add(newStatus);

            String damageInfo = newTickDamage > 0 ? String.format(" (틱당 %d)", newTickDamage) : "";
            String effectName = isStatDebuff ? skill.getName() : status;
            gs.addLog(String.format("<span style='color:%s;'>[효과]</span> %s에게 %s <b>%s</b> 부여! (%d턴%s)",
                    color, monster.getName(), icon, effectName, skill.getEffect().getDuration(), damageInfo));
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
     * @param user 플레이어
     * @param ds 몬스터 정보
     * @return 로그 메시지 추가
     */
    private String handleWait(UserStatus user, DungeonStatus ds, GameStatus gs) {
        gs.addLog("<b style='color:#888;'>[대기]</b> 턴을 종료합니다.");

        // 1. 몬스터 행동 처리 (도트딜, 공격, 상태이상 부여 등)
        monsterBattleService.processMonsterPhase(user, ds.getActiveMonster(), ds, gs);

        // 2. 몬스터 사망 체크 (도트딜로 죽었을 경우)
        if (ds.getActiveMonster().getCurrentHp() <= 0) {
            finishBattle(user, ds, gs,true);
            return "VICTORY";
        }

        updatePlayerStatusTick(user, ds, gs);

        // 3. 몬스터에게 디버프를 받았을 수 있으므로 플레이어 스탯 갱신
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());

        // 4. 플레이어 턴(AP) 리필
        int maxTurns = statCalculationService.calculateCombatTurns(user);
        ds.setPlayerMaxTurns(maxTurns);
        ds.setPlayerRemainingTurns(maxTurns);

        gs.addLog("<span style='color:#70db70;'>[시스템]</span> 당신의 턴이 시작되었습니다.");

        // 파일 저장
        saveCurrentState(user, ds, gs);
        return "MONSTER_TURN_END";
    }

    /**
     * [전투 종료 공통 처리 메서드]
     * 승리 시 즉시 경험치 정산 -> 레벨업 체크 -> 상승된 레벨 기반 정수 생성 순으로 진행합니다.
     */
    private void finishBattle(UserStatus us, DungeonStatus ds, GameStatus gs, boolean isVictory) {
        if (isVictory) {
            ActiveMonster monster = ds.getActiveMonster();

            // 1. NPE 방지: 처치 목록 초기화 확인
            if (us.getDefeatedMonsterIds() == null) {
                us.setDefeatedMonsterIds(new HashSet<>());
            }

            // 2. 경험치 즉시 정산 및 레벨업 (DungeonService에서 이관된 핵심 로직)
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

            // 3. 정수 드랍 판정 (이제 us.getLevel()은 레벨업이 완료된 상태임)
            double dropChance = 20.0;
            if (Math.random() * 100 < dropChance) {
                EssenceInstance dropped = essenceService.generateEssence(monster.getMonsterId());
                ds.setPendingEssence(dropped);
                gs.addLog(String.format("<b style='color:#ffd700;'>✨ [발견] %s의 정수가 응축되었습니다!</b>", monster.getName()));
            }

            // 4. 필드 상태 업데이트 (UI 표시용)
            monster.setCurrentHp(0);
            ds.setPendingExp(0); // 정산 완료했으므로 보류 경험치 비움
            ds.setActiveMonster(monster);

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
     * [던전 + 마을] 자연 재생 처리
     */
    public void applyPlayerRegeneration() {
        GameStatus gs = gameFileRepository.findGameStatus();
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
            gs.addLog(String.format("<span style='color:#70db70;'>[자연 재생] %s</span>", regenLog.toString()));
        }

        saveAllNotCount(user, ds);
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