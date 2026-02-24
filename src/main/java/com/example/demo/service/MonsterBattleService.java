package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.MonsterSkillMeta;
import com.example.demo.domain.save.ActiveMonster;
import com.example.demo.domain.save.ActiveStatus;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
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
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final StatCalculationService statCalculationService;
    private final Random random = new Random();

    private void saveAll(UserStatus user, DungeonStatus ds) {
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
    }

    public void processMonsterPhase(UserStatus user, ActiveMonster monster, DungeonStatus ds) {
        if (monster == null || monster.getCurrentHp() <= 0) return;

        var monsterMeta = gameDataManager.getMonsterMetaMap().get(monster.getMonsterId());
        int currentAp = (monsterMeta != null) ? monsterMeta.getBaseActionPoints() : 1;

        ds.addLog(String.format("<b style='color:#ff9f43;'>▶ %s의 차례 (행동력: %d)</b>", monster.getName(), currentAp));

        // 몬스터 행동 시작 전, 모든 상태 이상의 지속시간을 1턴 감소시키고 도트딜 정산
        updateMonsterStatusTick(monster, ds);

        // 자연 재생
        applyMonsterRegeneration(monster, ds);


        // 정산 중 사망 시 종료
        if (monster.getCurrentHp() <= 0) {
            ds.addLog(String.format("<b style='color:#ffd700;'>%s이(가) 상태 이상으로 쓰러졌습니다!</b>", monster.getName()));
            saveAll(user, ds);
            return;
        }

        // 2. 기절(STUN) 체크: 현재 기절 상태라면 AP가 얼마든 즉시 턴 종료
        if (isStunned(monster)) {
            ds.addLog(String.format("💫 <b>기절</b> 상태인 %s이(가) 몸을 가누지 못해 턴을 넘깁니다.", monster.getName()));

            // [추가] 기절 지속시간 감소 및 해제 로직
            monster.getActiveStatuses().removeIf(status -> {
                if ("STUN".equals(status.getEffectCode())) {
                    status.setRemainingTurns(status.getRemainingTurns() - 1);
                    if (status.getRemainingTurns() <= 0) {
                        ds.addLog("<span style='color:#aaaaaa;'>[해제] 💫 기절 종료</span>");
                        return true; // 리스트에서 제거
                    }
                }
                return false; // 아직 기절이 남았으면 유지
            });

            saveAll(user, ds);
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
            executeMonsterAction(user, monster, selectedSkill, ds);

            currentAp -= selectedSkill.getTurnCost();

            if (user.getCurrentHp() <= 0) {
                ds.addLog("<b style='color:#ff4d4d;'>[치명상] 더 이상 버틸 수 없습니다...</b>");
                break;
            }
        }
        saveAll(user, ds);
    }

    private void executeMonsterAction(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds) {

        // [1단계] 공격자 명중 판정 (Hit Check)
        boolean isHit = statCalculationService.isAttackerHit(
                monster.getStats().getAccuracy(),
                skill.getHitChance()
        );

        if (!isHit) {
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[실패] %s의 %s! 빗나갔습니다.</span>", monster.getName(), skill.getName()));
            return;
        }

        // [2단계] 방어자 회피 판정 (Dodge Check)
        // 버프인 경우 회피 판정을 생략하기 위해 0 전달
        double dodgeStat = "BUFF".equals(skill.getType()) ? 0 : user.getCombatStats().getDodge();
        boolean isDodged = statCalculationService.isDefenderDodge(dodgeStat);
        if (isDodged) {
            ds.addLog(String.format("<span style='color:#ffcc00;'>[회피] %s이(가) 공격을 피했습니다!</span>", user.getName()));
            return;
        }

        // 2. 효과 처리
        if ("DAMAGE".equals(skill.getEffect().getType())) {
            handleMonsterDamage(user, monster, skill, ds);
        } else if ("DEBUFF".equals(skill.getEffect().getType())) {
            applyEffectToPlayer(user, monster, skill, ds, 0);
        }
    }

    private void handleMonsterDamage(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds) {

        int finalDamage = statCalculationService.calculateMonsterDamage(user, monster, skill);

        user.setCurrentHp(Math.max(0, user.getCurrentHp() - finalDamage));
        ds.addLog(String.format("<span style='color:#ff4d4d;'>[피격]</span> %s의 %s! <b>%d</b>의 피해!",
                monster.getName(), skill.getName(), finalDamage));

        // 3. 추가 효과(상태이상) 판정
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyEffectToPlayer(user, monster, skill, ds, finalDamage);
        }
    }

    private void applyEffectToPlayer(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds, int baseDamage) {
        var effect = skill.getEffect();
        double finalChance = effect.getChance() * (1.0 - (user.getCombatStats().getStatusResist() / 100.0));

        if (random.nextDouble() * 100 <= finalChance) {
            String statusName = effect.getStatus();
            int tickDamage = List.of("BLEED", "POISON", "BURN").contains(statusName) ? Math.max(1, baseDamage / 3) : 0;

            // 중첩/갱신 로직
            ActiveStatus existing = user.getActiveStatuses().stream()
                    .filter(s -> s.getEffectCode().equals(statusName))
                    .findFirst().orElse(null);

            if (existing != null) {
                // 시간은 Max, 데미지는 합산
                existing.setRemainingTurns(Math.max(existing.getRemainingTurns(), effect.getDuration()));
                existing.setTickDamage(existing.getTickDamage() + tickDamage);
                ds.addLog(String.format("<span style='color:#da70d6;'>[중첩]</span> %s 효과가 강화되었습니다! (틱뎀: %d)", statusName, existing.getTickDamage()));
            } else {
                ActiveStatus newStatus = ActiveStatus.builder()
                        .skillId(skill.getId())
                        .name(statusName)
                        .remainingTurns(effect.getDuration())
                        .category("DEBUFF")
                        .effectCode(statusName)
                        .tickDamage(tickDamage)
                        .statModifiers(effect.getStatModifiers())
                        .build();
                user.getActiveStatuses().add(newStatus);
                ds.addLog(String.format("<span style='color:#da70d6;'>[효과]</span> %s에게 <b>%s</b> 부여!", user.getName(), statusName));
            }
            // 스탯 재계산 필요 (BattleService나 StatCalculationService에서 관리)
            statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());
            saveAll(user, ds);
        }
    }

    private boolean updateMonsterStatusTick(ActiveMonster monster, DungeonStatus ds) {
        if (monster.getActiveStatuses() == null || monster.getActiveStatuses().isEmpty()) return false;

        return monster.getActiveStatuses().removeIf(status -> {
            // [특수 처리] 기절(STUN)은 여기서 지속시간을 줄이지 않고 유지함 (별도 관리)
            if ("STUN".equals(status.getEffectCode())) {
                return false;
            }

            String code = status.getEffectCode();
            String icon = gameDataManager.getIcon(code);

            // 1. 도트 데미지 정산 (독, 화상, 출혈 등은 기절 중에도 여기서 정상 작동)
            if (status.getTickDamage() > 0) {
                int damage = status.getTickDamage();
                monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - damage));
                ds.addLog(String.format("%s <span style='color:#ff4d4d;'>%d</span> 대미지! (%s %s)",
                        icon, damage, monster.getName(), status.getName()));
            }

            // 2. 지속시간 감소 (기절을 제외한 모든 상태이상)
            status.setRemainingTurns(status.getRemainingTurns() - 1);
            if (status.getRemainingTurns() <= 0) {
                ds.addLog(String.format("<span style='color:#aaaaaa;'>[해제] %s %s 종료</span>", icon, status.getName()));
                return true;
            }
            return false;
        });
    }

    private boolean isStunned(ActiveMonster monster) {
        return monster.getActiveStatuses().stream().anyMatch(s -> "STUN".equals(s.getEffectCode()));
    }

    /**
     * 몬스터의 턴마다 회복
     * @param monster 몬스터 정보
     * @param ds 던전 + 몬스터 정보
     */
    private void applyMonsterRegeneration(ActiveMonster monster, DungeonStatus ds) {
        if (monster == null || monster.getCurrentHp() <= 0) return;

        StringBuilder regenLog = new StringBuilder();
        boolean recovered = false;

        // HP 회복
        double hpRegen = monster.getStats().getHpRegen();
        if (hpRegen > 0 && monster.getCurrentHp() < monster.getMaxHp()) {
            int oldHp = monster.getCurrentHp();
            int newHp = Math.min(monster.getMaxHp(), oldHp + (int)hpRegen);
            int actualHp = newHp - oldHp;

            if (actualHp > 0) {
                monster.setCurrentHp(newHp);
                regenLog.append(String.format("💚 HP +%d ", actualHp));
                recovered = true;
            }
        }

        // MP 회복
        double mpRegen = monster.getStats().getMpRegen();
        if (mpRegen > 0 && monster.getCurrentMp() < monster.getMaxMp()) {
            int oldMp = monster.getCurrentMp();
            int newMp = Math.min(monster.getMaxMp(), oldMp + (int)mpRegen);
            int actualMp = newMp - oldMp;

            if (actualMp > 0) {
                monster.setCurrentMp(newMp);
                regenLog.append(String.format("💙 MP +%d ", actualMp));
                recovered = true;
            }
        }

        if (recovered) {
            ds.addLog(String.format("<span style='color:#70db70;'>[재생] %s이(가) %s회복했습니다.</span>",
                    monster.getName(), regenLog.toString()));
        }
    }

}