package com.example.demo.service;

import com.example.demo.domain.meta.MonsterSkillMeta;
import com.example.demo.domain.save.ActiveMonster;
import com.example.demo.domain.save.ActiveStatus;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
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
    private final StatCalculationService statCalculationService;
    private final Random random = new Random();

    public void processMonsterPhase(UserStatus user, ActiveMonster monster, DungeonStatus ds) {
        if (monster == null || monster.getCurrentHp() <= 0) return;

        // 1. 티어 기반 행동 횟수 결정 (티어 7 이하 2회, 그 외 1회)
        int actionCount = (monster.getTier() <= 7) ? 2 : 1;

        ds.addLog("<b style='color:#ff9f43;'>▶ 몬스터의 차례</b>");

        for (int i = 1; i <= actionCount; i++) {
            // [A] 도트 데미지 처리 (행동 직전)
            handleDotDamage(monster, ds);
            if (monster.getCurrentHp() <= 0) return;

            // [B] 기절(STUN) 체크
            if (isStunned(monster)) {
                ds.addLog(String.format("<span style='color:#f1c40f;'>[기절] %s이(가) 정신을 차리지 못합니다!</span>", monster.getName()));
                continue;
            }

            // [C] 실제 공격/행동 수행
            executeMonsterAction(user, monster, ds);

            // 플레이어 사망 시 중단
            if (user.getCurrentHp() <= 0) {
                ds.addLog("<b style='color:#ff4d4d;'>[치명상] 당신은 더 이상 움직일 수 없습니다...</b>");
                break;
            }
        }

        // [D] 전체 행동 종료 후 모든 상태 이상 지속시간 감소
        updateStatusDuration(monster, ds);
    }

    private void executeMonsterAction(UserStatus user, ActiveMonster monster, DungeonStatus ds) {
        // 1. 스킬 선택
        var monsterMeta = gameDataManager.getMonsterMetaMap().get(monster.getMonsterId());
        if (monsterMeta == null || monsterMeta.getSkillIds().isEmpty()) return;

        int skillId = monsterMeta.getSkillIds().get(random.nextInt(monsterMeta.getSkillIds().size()));
        MonsterSkillMeta skill = gameDataManager.getMonsterSkillMetaMap().get(skillId);
        if (skill == null) return;

        // 2. 명중 및 회피 판정 (StatCalculationService 활용)
        if (!statCalculationService.isAttackerHit(monster.getStats().getAccuracy(), skill.getHitChance())) {
            ds.addLog(String.format("<span style='color:#aaaaaa;'>[실패] %s의 %s! 공격이 빗나갔습니다.</span>", monster.getName(), skill.getName()));
            return;
        }

        if (statCalculationService.isDefenderDodge(user.getCombatStats().getDodge())) {
            ds.addLog(String.format("<span style='color:#ffcc00;'>[회피] %s이(가) %s의 공격을 피했습니다!</span>", user.getName(), monster.getName()));
            return;
        }

        // 3. 효과 처리
        if ("DAMAGE".equals(skill.getEffect().getType())) {
            handleMonsterDamage(user, monster, skill, ds);
        } else if ("DEBUFF".equals(skill.getEffect().getType())) {
            applyDebuffToPlayer(user, monster, skill, ds);
        }
    }

    private void handleMonsterDamage(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds) {
        // 데미지 계산 (공격력 * 계수) - (유저 방어력 점감법)
        double scaling = skill.getMonsterScaling().getOrDefault("meleeAtk", 1.0);
        double rawDamage = monster.getStats().getMeleeAtk() * scaling;

        // 유저의 물리방어/마법저항 적용
        boolean isMagic = "MONSTER_MAGIC".equals(skill.getType());
        double userDef = isMagic ? user.getCombatStats().getMagRes() : user.getCombatStats().getPhysDef();

        int finalDamage = statCalculationService.applyDefense(rawDamage, monster.getStats().getPenetration(), userDef);
        finalDamage = Math.max(1, finalDamage); // 최소 데미지 1

        user.setCurrentHp(Math.max(0, user.getCurrentHp() - finalDamage));
        ds.addLog(String.format("<span style='color:#ff4d4d;'>[피격]</span> %s의 <b>%s</b>! <b>%d</b>의 피해!",
                monster.getName(), skill.getName(), finalDamage));

        // 추가 상태이상(디버프) 판정
        if (skill.getEffect().getStatus() != null) {
            applyDebuffToPlayer(user, monster, skill, ds);
        }
    }

    private void applyDebuffToPlayer(UserStatus user, ActiveMonster monster, MonsterSkillMeta skill, DungeonStatus ds) {
        var effect = skill.getEffect();
        double finalChance = effect.getChance() * (1.0 - (user.getCombatStats().getStatusResist() / 100.0));

        if (random.nextDouble() * 100 <= finalChance) {
            ActiveStatus newStatus = ActiveStatus.builder()
                    .skillId(skill.getId())
                    .name(skill.getName()) // 또는 효과 코드에 맞는 이름
                    .remainingTurns(effect.getDuration())
                    .category("DEBUFF")
                    .effectCode(effect.getStatus())
                    .statModifiers(effect.getStatModifiers())
                    .build();

            user.getActiveStatuses().add(newStatus);
            ds.addLog(String.format("<span style='color:#da70d6;'>[효과]</span> %s에게 <b>%s</b> 부여!", user.getName(), effect.getStatus()));

            // 상태 이상이 추가되었으므로 유저 실시간 스탯 재계산이 필요함을 BattleService에 알릴 필요가 있음
        }
    }

    private void handleDotDamage(ActiveMonster monster, DungeonStatus ds) {
        if (monster.getActiveStatuses() == null) return;

        for (ActiveStatus status : monster.getActiveStatuses()) {
            if (status.getTickDamage() > 0) { // 합산된 데미지가 있는 것만 처리
                monster.setCurrentHp(Math.max(0, monster.getCurrentHp() - status.getTickDamage()));

                String icon = switch(status.getEffectCode()) {
                    case "BLEED" -> "🩸 출혈";
                    case "POISON" -> "🧪 중독";
                    case "BURN" -> "🔥 화상";
                    default -> "💢 도트딜";
                };
                ds.addLog(String.format("%s! %s에게 <b style='color:#ff4d4d;'>%d</b>의 피해!",
                        icon, monster.getName(), status.getTickDamage()));

                if (monster.getCurrentHp() <= 0) break;
            }
        }
    }

    private void updateStatusDuration(ActiveMonster monster, DungeonStatus ds) {
        if (monster.getActiveStatuses() == null) return;
        monster.getActiveStatuses().removeIf(status -> {
            status.setRemainingTurns(status.getRemainingTurns() - 1);
            if (status.getRemainingTurns() <= 0) {
                ds.addLog(String.format("<span style='color:#aaaaaa;'>[%s] 효과가 해제되었습니다.</span>", status.getName()));
                return true;
            }
            return false;
        });
    }

    private boolean isStunned(ActiveMonster monster) {
        if (monster.getActiveStatuses() == null) return false;
        return monster.getActiveStatuses().stream().anyMatch(s -> "STUN".equals(s.getEffectCode()));
    }
}