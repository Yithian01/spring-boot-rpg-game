package com.example.demo.service;

import com.example.demo.domain.meta.MonsterSkillMeta;
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
        int currentAp = (monsterMeta != null) ? monsterMeta.getBaseActionPoints() : 1;

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

            // [주의] 이미 밖에서 updateMonsterStatusTick을 했으므로,
            // while문 내부의 updateMonsterStatusTick 호출은 삭제하거나
            // 기획에 따라 '행동당 지속시간 감소'가 아니라면 밖으로 빼는 것이 맞습니다.

            List<MonsterSkillMeta> affordableSkills = monsterMeta.getSkillIds().stream()
                    .map(id -> gameDataManager.getMonsterSkillMetaMap().get(id))
                    .filter(s -> s != null && s.getTurnCost() <= finalCurrentAp)
                    .toList();

            if (affordableSkills.isEmpty()) break;

            MonsterSkillMeta selectedSkill = affordableSkills.get(random.nextInt(affordableSkills.size()));
            executeMonsterAction(us, monster, selectedSkill, ds, gs);

            currentAp -= selectedSkill.getTurnCost();

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
    private void executeMonsterAction(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds, GameStatus gs) {
        String skillName = skill.getName();
        String skillType = skill.getType();

        // 버프나 회복이 아닌 '공격성' 스킬인 경우에만 명중/회피 판정
        boolean isAlwaysHit = "BUFF".equals(skillType) || "HEAL".equals(skillType);

        if (!isAlwaysHit) {
            // [공식 적용] (몬스터 기본 명중 + 스킬 보너스) - 유저 회피
            double monsterAcc = monster.getActiveStats().getAccuracy();
            int skillHitBonus = skill.getHitChance();
            double userDodge = user.getCombatStats().getDodge();

            int finalHitChance = (int) Math.max(5, Math.min(100, (monsterAcc + skillHitBonus) - userDodge));

            if (random.nextInt(100) >= finalHitChance) {
                // 회피/빗나감 로그
                if (random.nextBoolean()) {
                    gs.addLog(String.format("💨 <span style='color:#ffcc00;'>[회피] %s이(가) %s의 <b>%s</b> 공격을 유연하게 피했습니다!</span>",
                            user.getName(), monster.getName(), skillName));
                } else {
                    gs.addLog(String.format("<span style='color:#aaaaaa;'>[빗나감] %s의 <b>%s</b> 공격이 빗나갔습니다.</span> <small>(확률: %d%%)</small>",
                            monster.getName(), skillName, finalHitChance));
                }
                return;
            }
        }

        // [효과 처리]
        String effectType = skill.getEffect().getType();
        switch (effectType) {
            case "DAMAGE" -> handleMonsterDamage(user, monster, skill, ds, gs, false);
            case "DOT" -> handleMonsterDamage(user, monster, skill, ds, gs, true);
            case "BUFF", "DEBUFF" -> applyStatusEffect(user, monster, skill, ds, gs, 0);
            default -> log.warn("정의되지 않은 몬스터 스킬 효과: {}", effectType);
        }
    }

    /**
     * 몬스터 데미지 통합 처리 (즉발/도트 부가효과 포함)
     */
    private void handleMonsterDamage(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds, GameStatus gs, boolean isDotOnly) {
        // 플레이어 방어력 등을 고려한 최종 데미지 계산
        int finalDamage = statCalculationService.calculateMonsterDamage(user, monster, skill);

        if (!isDotOnly) {
            user.setCurrentHp(Math.max(0, user.getCurrentHp() - finalDamage));
            gs.addLog(String.format("<span style='color:#ff4d4d;'>[피격]</span> %s의 %s! <b>%d</b>의 피해!",
                    monster.getName(), skill.getName(), finalDamage));
        }

        // 플레이어 사망 체크는 BattleService의 handleWait 등에서 처리하거나 여기서 즉시 체크

        // 부가 효과(상태이상) 판정
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyStatusEffect(user, monster, skill, ds, gs, finalDamage);
        }
    }

    /**
     * [통합] 몬스터 상태이상 부여 (자가 버프 & 유저 디버프)
     */
    private void applyStatusEffect(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds, GameStatus gs, int baseDamage) {
        var effect = skill.getEffect();
        String status = effect.getStatus();
        String icon = gameDataManager.getIcon(status);
        String category = effect.getType();
        boolean isBuff = "BUFF".equals(category);

        String targetName = isBuff ? monster.getName() : user.getName();
        List<ActiveStatus> targetStatuses = isBuff ? monster.getActiveStatuses() : user.getActiveStatuses();

        // [수정] 디버프 전용 스킬(baseDamage 0)인 경우, 시전 로그를 먼저 남겨줌
        if (!isBuff && baseDamage == 0) {
            gs.addLog(String.format("✨ %s이(가) %s에게 <b>%s</b> 시전!", monster.getName(), user.getName(), skill.getName()));
        }

        if (!isBuff) {
            // 1. 스킬 기본 확률
            double baseChance = effect.getChance();
            // 2. 몬스터의 상태이상 부여 보너스 (필요 시 monster.getActiveStats()에서 가져옴)
            double monsterStatusAtk = 0;
            // 3. 유저의 상태이상 저항력
            double userResist = user.getCombatStats().getStatusResist();

            // 최종 확률: (스킬 확률 + 몬스터 보너스) - 유저 저항
            int finalApplyChance = (int) Math.max(5, Math.min(100, (baseChance + monsterStatusAtk) - userResist));

            if (random.nextInt(100) >= finalApplyChance) {
                gs.addLog(String.format("<span style='color:#aaaaaa;'>[저항] %s %s이(가) %s의 효과를 견뎌냈습니다! (확률: %d%%)</span>",
                        icon, user.getName(), status, finalApplyChance));
                return;
            }
        }

        // 2. 틱 데미지 계산
        int tickDamage = 0;
        if (!isBuff) {
            if ("DOT".equals(effect.getType())) {
                tickDamage = baseDamage; // 순수 도트기는 계산된 데미지 전체 반영
            } else if (List.of("BLEED", "POISON", "BURN", "PAIN").contains(status)) {
                tickDamage = Math.max(1, (int) Math.ceil(baseDamage / 3.0)); // 공격 부가효과는 1/3
            }
        }

        final int nowTickDmg = tickDamage;
        // 3. 중첩 및 갱신 처리
        ActiveStatus existing = targetStatuses.stream()
                .filter(s -> {
                    if (isBuff) {
                        // 자가 버프는 스킬 ID로 개별 관리 (이미 잘 되어 있음)
                        return s.getSkillId() == skill.getId() && "BUFF".equals(s.getCategory());
                    } else {
                        // 플레이어에게 거는 디버프 처리
                        boolean isDotValue = (nowTickDmg > 0);
                        if (isDotValue) {
                            // 1. 도트류 (중첩 합산): BURN, BLEED 등은 상태 코드로 찾음
                            return s.getEffectCode().equals(status);
                        } else {
                            // 2. 순수 디버프 (개별 유지): WEAKNESS 등은 스킬 ID로 찾음
                            // 이렇게 해야 '쇠약(명중)'과 '쇠약(공격력)'이 공존 가능
                            return s.getSkillId() == skill.getId();
                        }
                    }
                })
                .findFirst().orElse(null);

        String color = isBuff ? "#70db70" : switch(status) {
            case "BURN" -> "#ff4500";
            case "FROZEN", "FREEZE" -> "#87ceeb";
            case "POISON" -> "#70db70";
            case "CURSE", "PAIN" -> "#da70d6";
            default -> "#ffffff";
        };

        if (existing != null) {
            // 지속 시간은 더 긴 것으로 갱신
            existing.setRemainingTurns(Math.max(existing.getRemainingTurns(), effect.getDuration()));

            if (!isBuff && tickDamage > 0) {
                // 도트 데미지가 있는 경우만 수치 합산
                existing.setTickDamage(existing.getTickDamage() + tickDamage);
                String damageInfo = String.format(" / 틱당 %d 피해", existing.getTickDamage());
                gs.addLog(String.format("<span style='color:%s;'>[중첩]</span> %s %s %s 강화! (남은 %d턴%s)",
                        color, icon, targetName, status, existing.getRemainingTurns(), damageInfo));
            } else {
                // 버프나 순수 디버프는 시간만 갱신
                gs.addLog(String.format("<span style='color:%s;'>[%s]</span> %s %s %s 지속시간 갱신!",
                        color, isBuff ? "강화" : "유지", icon, targetName, isBuff ? "버프" : "디버프"));
            }
        } else {
            ActiveStatus newStatus = ActiveStatus.builder()
                    .skillId(skill.getId())
                    .name(isBuff ? skill.getName() : status)
                    .remainingTurns(effect.getDuration())
                    .category(category)
                    .effectCode(status)
                    .tickDamage(tickDamage)
                    .statModifiers(effect.getStatModifiers())
                    .combatModifiers(effect.getCombatStatModifiers())
                    .build();
            targetStatuses.add(newStatus);

            String dmgInfo = tickDamage > 0 ? String.format(" (틱당 %d)", tickDamage) : "";
            gs.addLog(String.format("<span style='color:%s;'>[%s]</span> %s에게 %s <b>%s</b> 부여! (%d턴%s)",
                    color, isBuff ? "버프" : "효과", targetName, icon, newStatus.getName(), effect.getDuration(), dmgInfo));
        }

        // 상태 이상 변화 후 플레이어 스탯 리프레시
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());
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