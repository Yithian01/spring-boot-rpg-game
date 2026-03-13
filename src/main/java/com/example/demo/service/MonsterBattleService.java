package com.example.demo.service;

import com.example.demo.domain.meta.SkillEffect;
import com.example.demo.domain.meta.SkillMeta;
import com.example.demo.domain.save.*;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonsterBattleService {

    private final GameDataManager gameDataManager;
    private final GameFileRepository gameFileRepository;
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final StatCalculationService statCalculationService;
    private final Random random = new Random();

    private void saveAll(UserStatus us, DungeonStatus ds, GameStatus gs) {
        userFileRepository.saveUserStatus(us);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.saveGameStatus(gs);

    }

    public void processMonsterPhase(UserStatus us, ActiveMonster monster, DungeonStatus ds, GameStatus gs) {
        if (monster == null || monster.getCurrentHp() <= 0) return;

        var monsterMeta = gameDataManager.getMonsterMetaMap().get(monster.getMonsterId());
        // int currentAp = (monsterMeta != null) ? monsterMeta.getBaseActionPoints() : 1;
        int currentAp = (monsterMeta != null) ? monster.getActiveStats().getMaxTurns() : 1;

        gs.addLog(String.format("<b style='color:#ff9f43;'>▶ %s의 차례 (행동력: %d)</b>", monster.getName(), currentAp));

        // 몬스터 행동 시작 전, 모든 상태 이상의 지속시간을 1턴 감소시키고 도트딜 정산
        updateMonsterStatusTick(monster, ds, gs);

        // 자연 재생
        applyMonsterRegeneration(monster, gs);


        // 정산 중 사망 시 종료
        if (monster.getCurrentHp() <= 0) {
            gs.addLog(String.format("<b style='color:#ffd700;'>%s이(가) 상태 이상으로 쓰러졌습니다!</b>", monster.getName()));
            saveAll(us, ds, gs);
            return;
        }

        // 2. 기절(STUN) 체크: 현재 기절 상태라면 AP가 얼마든 즉시 턴 종료
        if (isStunned(monster)) {
            gs.addLog(String.format("💫 <b>기절</b> 상태인 %s이(가) 몸을 가누지 못해 턴을 넘깁니다.", monster.getName()));

            // [추가] 기절 지속시간 감소 및 해제 로직
            monster.getActiveStatuses().removeIf(status -> {
                if ("STUN".equals(status.getEffectCode())) {
                    status.setRemainingTurns(status.getRemainingTurns() - 1);
                    if (status.getRemainingTurns() <= 0) {
                        gs.addLog("<span style='color:#aaaaaa;'>[해제] 💫 기절 종료</span>");
                        saveAll(us, ds, gs);
                        return true; // 리스트에서 제거
                    }
                }
                return false; // 아직 기절이 남았으면 유지
            });

            saveAll(us, ds, gs);
            return; // 이번 턴의 공격 로직(while문)을 건너뛰고 종료
        }

        // 3. 남은 AP가 있는 동안 스킬 실행 (기절이 아닐 때만 진입)
        while (currentAp > 0 && monster.getCurrentHp() > 0) {
            final int finalCurrentAp = currentAp;

            // 자신에게 걸려있는 버프 스킬 ID 리스트 추출
            List<Integer> currentBuffSkillIds = monster.getActiveStatuses().stream()
                    .filter(s -> "BUFF".equals(s.getCategory()))
                    .map(ActiveStatus::getSkillId)
                    .toList();

            // 유저(상대방)가 현재 걸려있는 디버프 스킬 ID 리스트 추출
            List<Integer> currentDebuffSkillIds = us.getActiveStatuses().stream()
                    .filter(s -> "DEBUFF".equals(s.getCategory()))
                    .map(ActiveStatus::getSkillId)
                    .toList();

            List<SkillMeta> affordableSkills = monsterMeta.getActiveSkillIds().stream()
                    .map(id -> gameDataManager.getMonsterSkillMetaMap().get(id))
                    .filter(s -> s != null
                            && s.getTurnCost() <= finalCurrentAp
                            && monster.getCurrentMp() >= s.getCost().getOrDefault("mp", 0))
                    .filter(s -> !("BUFF".equals(s.getType()) && currentBuffSkillIds.contains(s.getId())))
                    .filter(s -> !("DEBUFF".equals(s.getType()) && currentDebuffSkillIds.contains(s.getId())))
                    .toList();

            if (affordableSkills.isEmpty()) {
                // 행동력은 충분한데(최소 1코스트 이상 스킬이 있는데) 마나 때문에 못 쓰는 스킬이 있는지 확인
                boolean isMpLacking = monsterMeta.getActiveSkillIds().stream()
                        .map(id -> gameDataManager.getMonsterSkillMetaMap().get(id))
                        .anyMatch(s -> s != null
                                && s.getTurnCost() <= finalCurrentAp // 행동력은 충분하지만
                                && monster.getCurrentMp() < s.getCost().getOrDefault("mp", 0)); // 마나만 부족한 경우

                if (isMpLacking) {
                    gs.addLog(String.format("<span style='color:#aaaaaa;'>⚡ %s이(가) 스킬을 사용하려 했으나 마력이 부족해 주춤거립니다.</span>", monster.getName()));
                } else {
                    // 마나 문제가 아니라 행동력이 부족하거나 스킬 데이터가 없는 경우
                    gs.addLog(String.format("<span style='color:#aaaaaa;'>☕ %s이(가) 숨을 고르며 행동을 마칩니다.</span>", monster.getName()));
                }

                break;
            }

            SkillMeta selectedSkill = affordableSkills.get(random.nextInt(affordableSkills.size()));
            executeMonsterAction(us, monster, selectedSkill, ds, gs);

            currentAp -= selectedSkill.getTurnCost();

            int mpCost = selectedSkill.getCost().getOrDefault("mp", 0);
            if (mpCost > 0) {
                monster.setCurrentMp(Math.max(0, monster.getCurrentMp() - mpCost));
            }

            saveAll(us, ds, gs);

            if (us.getCurrentHp() <= 0) {
                gs.addLog("<b style='color:#ff4d4d;'>[치명상] 더 이상 버틸 수 없습니다...</b>");
                break;
            }
        }
        saveAll(us, ds, gs);
    }

    /**
     * 몬스터 개별 액션 실행 (BattleService와 동일한 구조)
     */
    private void executeMonsterAction(UserStatus us, ActiveMonster monster, SkillMeta skill, DungeonStatus ds, GameStatus gs) {
        String skillName = skill.getName();
        String skillType = skill.getType();

        // 버프나 회복이 아닌 '공격성' 스킬인 경우에만 명중/회피 판정
        boolean isAlwaysHit = "BUFF".equals(skillType) || "HEAL".equals(skillType);

        if (!isAlwaysHit) {
            // [공식 적용] (몬스터 기본 명중 + 스킬 보너스) - 유저 회피
            int skillHitBonus = skill.getHitChance();
            double attackerAcc = monster.getActiveStats().getAccuracy();
            double defenderDodge = us.getCombatStats().getDodge();
            double finalHitChance = statCalculationService.attackerHitChance(skillHitBonus, attackerAcc, defenderDodge);

            if (Math.random() * 100 > finalHitChance) {
                String failType = (Math.random() < 0.5) ? "회피" : "실패";
                String logMsg;

                if ("회피".equals(failType)) {
                    logMsg = String.format("💨 <span style='color:#ffcc00;'>[회피] %s이(가) %s의 <b>%s</b> 공격을 피했습니다!</span>",
                            us.getName(), monster.getName(), skillName);
                } else {
                    logMsg = String.format("<span style='color:#aaaaaa;'>[빗나감] %s의 <b>%s</b> 공격이 빗나갔습니다.</span> <small>(확률: %d%%)</small>",
                            monster.getName(), skillName, (int) finalHitChance);
                }
                logMsg += String.format(" <small style='color:#888;'>(확률: %d%%)</small>", (int) finalHitChance);
                gs.addLog(logMsg);
                saveAll(us, ds, gs);
                return;
            }
        }

        // [효과 처리]
        String effectType = skill.getEffect().getType();
        switch (effectType) {
            case "DAMAGE" -> handleMonsterDamage(monster, us, skill, ds, gs, false);
            case "DOT" -> handleMonsterDamage(monster, us, skill, ds, gs, true);
            case "BUFF", "DEBUFF" -> handleStatus(monster, us, skill, ds, gs);
            default -> log.warn("정의되지 않은 몬스터 스킬 효과: {}", effectType);
        }
    }

    /**
     * 몬스터 데미지 통합 처리 (즉발/도트 부가효과 포함)
     */
    private void handleMonsterDamage(ActiveMonster attacker, UserStatus defender, SkillMeta skill, DungeonStatus ds, GameStatus gs, boolean isDotDmg) {
        // 플레이어 방어력 등을 고려한 최종 데미지 계산
        int baseDamage = statCalculationService.calculateFinalDamage(skill, attacker.getActiveStats(), defender.getCombatStats(), null);

        // 치명타 판정 (도트 스킬 포함 모든 데미지 스킬 적용)
        boolean isCrit = statCalculationService.isCrit(skill, attacker.getActiveStats());
        int finalDamage = isCrit ? statCalculationService.calculateCritDamage(baseDamage, skill, attacker.getActiveStats()) : baseDamage;

        String battleLog = "";

        if (!isDotDmg) {
            defender.setCurrentHp(Math.max(0, defender.getCurrentHp() - finalDamage));

            String critPrefix = isCrit ? "<b style='color:#ffcc00;'>[치명타!] 💥 </b>" : "⚔️ ";
            battleLog = String.format("<span style='color:#ff4d4d;'>%s[피격]</span> %s의 %s! <b>%d</b>의 피해!",
                    critPrefix, attacker.getName(), skill.getName(), finalDamage);

            gs.addLog(battleLog);
            saveAll(defender, ds, gs);
        } else {
            // [도트 스킬 (최초 부여 시점)]
            // 도트 스킬 자체가 '데미지' 타입을 가지고 있다면,
            // 여기서 결정된 finalDamage가 applyAdditionalEffect로 넘어가 틱 데미지의 기준이 됨
            String critText = isCrit ? "<small style='color:#ffcc00;'>(치명타 적용됨)</small>" : "";
            gs.addLog(String.format("✨ <b>%s</b> 시전! %s", skill.getName(), critText));
        }

        // 부가 효과(상태이상) 판정
        if (finalDamage > 0 && skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyAdditionalEffect(skill, attacker, defender, gs, finalDamage, isDotDmg);
        }
    }

    /**
     * 상태이상 스킬 전용 처리 로직 (공격기 외에 디버프/버프 전용 스킬용)
     */
    private String handleStatus(ActiveMonster attacker, UserStatus defender, SkillMeta skill, DungeonStatus ds, GameStatus gs) {
        SkillEffect effect = skill.getEffect();
        String icon = gameDataManager.getIcon(effect.getStatus());

        if ("BUFF".equals(effect.getType())) {
            // 몬스터 상태 이상 리스트 중에서
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
        saveAll(defender, ds, gs);
        return "STATUS_APPLIED";
    }

    /**
     * [헬퍼] 대상에게 상태이상을 부여할지 판정하고 처리합니다.
     */
    private void applyAdditionalEffect(SkillMeta skill, ActiveMonster attacker, UserStatus defender, GameStatus gs, int baseDamage, boolean isDotDmg) {
        String status = skill.getEffect().getStatus();
        String icon = gameDataManager.getIcon(status);

        // [저항 판정] 도트 데미지 계산이 아닐 때(즉, 최초 부여 시)만 저항 확률 체크
        if (baseDamage > -1 && !isDotDmg) {
            int finalApplyChance = statCalculationService.calculateStatusChance(skill.getEffect(), attacker.getActiveStats(), defender.getCombatStats());

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

        statCalculationService.refreshUserCombatStats(defender, gameDataManager.getItemMetaMap());
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
                .combatStatOffsets(skill.getEffect().getCombatStatOffsets())
                .combatStatModifiers(skill.getEffect().getCombatStatModifiers())
                .statOffsets(skill.getEffect().getStatOffsets())
                .build();
    }

    /**
     * 행동을 할 때마다 도트데미지 처리
     * @param monster
     * @param ds
     * @return
     */
    private boolean updateMonsterStatusTick(ActiveMonster monster, DungeonStatus ds, GameStatus gs) {
        if (monster.getActiveStatuses() == null || monster.getActiveStatuses().isEmpty()) return false;

        return monster.getActiveStatuses().removeIf(status -> {
            if ("STUN".equals(status.getEffectCode())) return false;

            String code = status.getEffectCode();
            String icon = gameDataManager.getIcon(code);

            if (status.getTickDamage() > 0) {
                int damage = status.getTickDamage();
                monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - damage));
                // 로그 가독성 개선
                gs.addLog(String.format("%s <b style='color:#ff4d4d;'>%s</b> 피해! (%s -%d HP)",
                        icon, status.getName(), monster.getName(), damage));
            }

            status.setRemainingTurns(status.getRemainingTurns() - 1);
            if (status.getRemainingTurns() <= 0) {
                gs.addLog(String.format("<span style='color:#aaaaaa;'>[해제] %s %s 종료</span>", icon, status.getName()));
                return true;
            }
            return false;
        });
    }

    /**
     * 스턴에 걸려있는지 확인하는 메소드
     * @param monster 몬스터 정보
     * @return ture/false
     */
    private boolean isStunned(ActiveMonster monster) {
        return monster.getActiveStatuses().stream().anyMatch(s -> "STUN".equals(s.getEffectCode()));
    }

    /**
     * 몬스터의 턴마다 회복
     * @param monster 몬스터 정보
     * @param gs 로그 정보
     */
    private void applyMonsterRegeneration(ActiveMonster monster, GameStatus gs) {
        if (monster == null || monster.getCurrentHp() <= 0) return;

        StringBuilder regenLog = new StringBuilder();
        boolean recovered = false;

        // HP 회복
        double hpRegen = monster.getActiveStats().getHpRegen();
        if (hpRegen > 0 && monster.getCurrentHp() < monster.getActiveStats().getMaxHp()) {
            int oldHp = monster.getCurrentHp();
            int newHp = Math.min(monster.getActiveStats().getMaxHp(), oldHp + (int)hpRegen);
            int actualHp = newHp - oldHp;

            if (actualHp > 0) {
                monster.setCurrentHp(newHp);
                regenLog.append(String.format("💚 HP +%d ", actualHp));
                recovered = true;
            }
        }

        // MP 회복
        double mpRegen = monster.getActiveStats().getMpRegen();
        if (mpRegen > 0 && monster.getCurrentMp() < monster.getActiveStats().getMaxMp()) {
            int oldMp = monster.getCurrentMp();
            int newMp = Math.min(monster.getActiveStats().getMaxMp(), oldMp + (int)mpRegen);
            int actualMp = newMp - oldMp;

            if (actualMp > 0) {
                monster.setCurrentMp(newMp);
                regenLog.append(String.format("💙 MP +%d ", actualMp));
                recovered = true;
            }
        }

        if (recovered) {
            gs.addLog(String.format("<span style='color:#70db70;'>[재생] %s이(가) %s회복했습니다.</span>",
                    monster.getName(), regenLog.toString()));
        }
    }
}