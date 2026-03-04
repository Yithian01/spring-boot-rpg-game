package com.example.demo.service;

import com.example.demo.domain.meta.MonsterMeta;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.EssenceInstance;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.EssenceRepository;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class EssenceService {

    private final GameDataManager gameDataManager;
    private final StatCalculationService statCalculationService;
    private final EssenceRepository essenceRepository;
    private final UserFileRepository userFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final GameFileRepository gameFileRepository;

    private void saveAll(UserStatus us, DungeonStatus ds, GameStatus gs){
        userFileRepository.saveUserStatus(us);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * 몬스터 메타 정보를 바탕으로 고유한 정수 인스턴스를 생성
     */
    public EssenceInstance generateEssence(int monsterId) {
        MonsterMeta monster = gameDataManager.getMonsterMetaMap().get(monsterId);
        if (monster == null) return null;

        String instanceId = "ESS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        MonsterMeta.EssenceBonusMeta bonusMeta = monster.getEssenceBonus();

        // Stats 매핑
        Map<Integer, Integer> baseBonus = new HashMap<>();
        if (bonusMeta != null && bonusMeta.getBaseStats() != null) {
            bonusMeta.getBaseStats().forEach((k, v) -> baseBonus.put(Integer.parseInt(k), v));
        }

        Map<String, Double> combatBonus = new HashMap<>();
        if (bonusMeta != null && bonusMeta.getCombatStats() != null) {
            bonusMeta.getCombatStats().forEach((k, v) -> combatBonus.put(k, (double) Math.round(v)));
        }

        // 스킬 추출 로직 추가
        List<Integer> finalActiveSkills = new ArrayList<>();
        List<Integer> finalPassiveSkills = new ArrayList<>();

        // 3(수호자), 4(계층 군주)가 아닌 경우 1개만 무작위 추출
        if (monster.getType() < 3) {
            // 액티브 스킬 중 1개 무작위 선택
            if (monster.getActiveSkillIds() != null && !monster.getActiveSkillIds().isEmpty()) {
                List<Integer> tempActives = new ArrayList<>(monster.getActiveSkillIds());
                Collections.shuffle(tempActives);
                finalActiveSkills.add(tempActives.get(0));
            }

            // 패시브 스킬 중 1개 무작위 선택 (액티브와 별개로 1개 더 주고 싶을 경우)
            if (monster.getPassiveSkillIds() != null && !monster.getPassiveSkillIds().isEmpty()) {
                List<Integer> tempPassives = new ArrayList<>(monster.getPassiveSkillIds());
                Collections.shuffle(tempPassives);
                finalPassiveSkills.add(tempPassives.get(0));
            }
        } else {
            // 3, 4번(보스급)인 경우 모든 스킬 보유
            if (monster.getActiveSkillIds() != null) finalActiveSkills.addAll(monster.getActiveSkillIds());
            if (monster.getPassiveSkillIds() != null) finalPassiveSkills.addAll(monster.getPassiveSkillIds());
        }

        return EssenceInstance.builder()
                .instanceId(instanceId)
                .monsterId(monster.getId())
                .monsterName(monster.getName())
                .monsterTier(monster.getTier())
                .monsterType(mapTypeToString(monster.getType()))
                .obtainedAt(System.currentTimeMillis())
                .activeSkillIds(finalActiveSkills)
                .passiveSkillIds(finalPassiveSkills)
                .baseStatsBonus(baseBonus)
                .combatStatsBonus(combatBonus)
                .description(monster.getName() + "의 정수입니다.")
                .build();
    }

    /**
     * 정수 흡수(먹기) 선택 시 실행되는 핵심 로직
     */
    public void claimEssence(UserStatus us, DungeonStatus ds, GameStatus gs) {
        EssenceInstance pending = ds.getPendingEssence();
        if (pending == null) return;

        if (us.getActiveEssenceIds() == null) {
            us.setActiveEssenceIds(new ArrayList<>());
        }

        // 1. [중복 몬스터 체크]
        if (isAlreadyAssimilated(us, pending.getMonsterId())) {
            gs.addLog(String.format("<span style='color:#ff9f43;'>[알림] 이미 %s의 정수와 동화된 상태입니다. 중복 흡수는 불가능합니다.</span>",
                    pending.getMonsterName()));
            return;
        }

        // 2. [슬롯 제한 체크] 내 레벨 >= 현재 장착 개수
        int currentEssenceCount = us.getActiveEssenceIds().size();
        if (currentEssenceCount >= us.getLevel()) {
            gs.addLog(String.format("<span style='color:#ff4d4d;'>[흡수 실패] 영혼의 그릇이 가득 찼습니다. (현재 슬롯: %d/%d)</span>",
                    currentEssenceCount, us.getLevel()));
            gs.addLog("<span style='color:#aaaaaa;'>* 새로운 정수를 흡수하려면 기존 정수를 삭제해야 합니다.</span>");
            return;
        }



        // 3. [저장 순서 변경]
        // 먼저 정수 자체를 시스템 저장소에 등록 (ID가 유실되지 않도록)
        essenceRepository.save(pending);

        // 유저 장착 목록에 ID 추가
        if (us.getActiveEssenceIds() == null) {
            us.setActiveEssenceIds(new ArrayList<>());
        }
        us.getActiveEssenceIds().add(pending.getInstanceId());

        // 4. [스탯 갱신] - 여기서 장착된 정수 정보를 읽어오므로 save가 먼저 선행되어야 함
        statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());

        // 5. [중요] 던전 보관함 비우기 및 로그 작성
        ds.setPendingEssence(null);

        // UI에서 "조사" 버튼이 몬스터가 null일 때만 뜨게 되어있다면 여기서 null 처리
        // ds.setActiveMonster(null); // 만약 finalizeRewards를 따로 안 부른다면 여기서 처리 필요

        gs.addLog(String.format("<b style='color:#70db70;'>[성공] %s의 정수를 흡수했습니다!</b>",
                pending.getMonsterName()));

        saveAll(us, ds, gs);
    }

    /**
     * 중복 체크 로직 실구현
     */
    private boolean isAlreadyAssimilated(UserStatus us, int monsterId) {
        List<String> myEssenceIds = us.getActiveEssenceIds();
        if (myEssenceIds == null || myEssenceIds.isEmpty()) return false;

        Map<String, EssenceInstance> allEssences = essenceRepository.findAll();

        return myEssenceIds.stream()
                .map(allEssences::get)       // ID로 인스턴스 찾기
                .filter(Objects::nonNull)    // 혹시 모를 null 방지
                .anyMatch(e -> e.getMonsterId() == monsterId); // 같은 몬스터 ID가 있는지 확인
    }

    private String mapTypeToString(int type) {
        return switch (type) {
            case 0 -> "일반";
            case 1 -> "변이종";
            case 2 -> "상위종";
            case 3 -> "수호자";
            case 4 -> "계층 군주";
            default -> "미분류";
        };
    }
}