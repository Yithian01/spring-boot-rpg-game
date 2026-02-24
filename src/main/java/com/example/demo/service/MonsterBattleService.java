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

    /**
     * 몬스터 개별 액션 실행 (BattleService와 동일한 구조)
     */
    private void executeMonsterAction(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds) {
        String skillName = skill.getName();
        // [1단계] 명중 판정
        boolean isHit = statCalculationService.isAttackerHit(
                monster.getStats().getAccuracy(),
                skill.getHitChance()
        );

        if (!isHit) {
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[실패] %s의 <b>%s</b>! 허공을 가르고 빗나갔습니다.</span>",
                    monster.getName(), skillName));
            return;
        }

        // [2단계] 플레이어 회피 판정 (버프/회복은 회피 불가)
        String skillType = skill.getType();
        double dodgeStat = ("BUFF".equals(skillType) || "HEAL".equals(skillType)) ? 0 : user.getCombatStats().getDodge();
        boolean isDodged = statCalculationService.isDefenderDodge(dodgeStat);

        if (isDodged) {
            ds.addLog(String.format("💨 <span style='color:#ffcc00;'>[회피] %s이(가) %s의 <b>%s</b> 공격을 가볍게 피했습니다!</span>",
                    user.getName(), monster.getName(), skillName));
            return;
        }

        // [3단계] 효과별 처리
        String effectType = skill.getEffect().getType();
        switch (effectType) {
            case "DAMAGE" -> handleMonsterDamage(user, monster, skill, ds, false);
            case "DOT" -> handleMonsterDamage(user, monster, skill, ds, true);
            case "BUFF", "DEBUFF" -> applyStatusEffect(user, monster, skill, ds, 0);
            // 필요 시 HEAL 추가 가능
            default -> log.warn("정의되지 않은 몬스터 스킬 효과: {}", effectType);
        }
    }

    /**
     * 몬스터 데미지 통합 처리 (즉발/도트 부가효과 포함)
     */
    private void handleMonsterDamage(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds, boolean isDotOnly) {
        // 플레이어 방어력 등을 고려한 최종 데미지 계산
        int finalDamage = statCalculationService.calculateMonsterDamage(user, monster, skill);

        if (!isDotOnly) {
            user.setCurrentHp(Math.max(0, user.getCurrentHp() - finalDamage));
            ds.addLog(String.format("<span style='color:#ff4d4d;'>[피격]</span> %s의 %s! <b>%d</b>의 피해!",
                    monster.getName(), skill.getName(), finalDamage));
        }

        // 플레이어 사망 체크는 BattleService의 handleWait 등에서 처리하거나 여기서 즉시 체크

        // 부가 효과(상태이상) 판정
        if (skill.getEffect() != null && skill.getEffect().getStatus() != null) {
            applyStatusEffect(user, monster, skill, ds, finalDamage);
        }
    }

    /**
     * [통합] 몬스터 상태이상 부여 (자가 버프 & 유저 디버프)
     */
    private void applyStatusEffect(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds, int baseDamage) {
        var effect = skill.getEffect();
        String status = effect.getStatus();
        String icon = gameDataManager.getIcon(status);
        String category = effect.getType();
        boolean isBuff = "BUFF".equals(category);

        String targetName = isBuff ? monster.getName() : user.getName();
        List<ActiveStatus> targetStatuses = isBuff ? monster.getActiveStatuses() : user.getActiveStatuses();

        // [수정] 디버프 전용 스킬(baseDamage 0)인 경우, 시전 로그를 먼저 남겨줌
        if (!isBuff && baseDamage == 0) {
            ds.addLog(String.format("✨ %s이(가) %s에게 <b>%s</b> 시전!", monster.getName(), user.getName(), skill.getName()));
        }

        if (!isBuff) {
            double resist = user.getCombatStats().getStatusResist();
            double finalChance = effect.getChance() * (1.0 - (resist / 100.0));

            if (random.nextDouble() * 100 > finalChance) {
                // [수정] 어떤 효과(상태이상 이름)에 저항했는지 명시
                ds.addLog(String.format("<span style='color:#aaaaaa;'>[저항] %s %s이(가) %s 효과를 저항했습니다.</span>",
                        icon, targetName, status));
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

        // 3. 중첩 및 갱신 처리
        ActiveStatus existing = targetStatuses.stream()
                .filter(s -> isBuff
                        ? (s.getSkillId() == skill.getId() && "BUFF".equals(s.getCategory()))
                        : s.getEffectCode().equals(status))
                .findFirst().orElse(null);

        String color = isBuff ? "#70db70" : switch(status) {
            case "BURN" -> "#ff4500";
            case "FROZEN", "FREEZE" -> "#87ceeb";
            case "POISON" -> "#70db70";
            case "CURSE", "PAIN" -> "#da70d6";
            default -> "#ffffff";
        };

        if (existing != null) {
            existing.setRemainingTurns(Math.max(existing.getRemainingTurns(), effect.getDuration()));
            existing.setTickDamage(existing.getTickDamage() + tickDamage);
            ds.addLog(String.format("<span style='color:%s;'>[%s]</span> %s %s %s",
                    color, isBuff ? "강화" : "중첩", icon, targetName, isBuff ? "지속시간 갱신!" : "효과 강화!"));
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
            ds.addLog(String.format("<span style='color:%s;'>[%s]</span> %s에게 %s <b>%s</b> 부여! (%d턴%s)",
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
    private boolean updateMonsterStatusTick(ActiveMonster monster, DungeonStatus ds) {
        if (monster.getActiveStatuses() == null || monster.getActiveStatuses().isEmpty()) return false;

        return monster.getActiveStatuses().removeIf(status -> {
            if ("STUN".equals(status.getEffectCode())) return false;

            String code = status.getEffectCode();
            String icon = gameDataManager.getIcon(code);

            if (status.getTickDamage() > 0) {
                int damage = status.getTickDamage();
                monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - damage));
                // 로그 가독성 개선
                ds.addLog(String.format("%s <b style='color:#ff4d4d;'>%s</b> 피해! (%s -%d HP)",
                        icon, status.getName(), monster.getName(), damage));
            }

            status.setRemainingTurns(status.getRemainingTurns() - 1);
            if (status.getRemainingTurns() <= 0) {
                ds.addLog(String.format("<span style='color:#aaaaaa;'>[해제] %s %s 종료</span>", icon, status.getName()));
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