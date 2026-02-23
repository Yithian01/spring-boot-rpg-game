package com.example.demo.service;

import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.dto.SkillCardDto;
import com.example.demo.manager.GameDataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleService {

    private final GameDataManager gameDataManager;
    private final StatCalculationService statCalculationService;

    public List<SkillCardDto> getSkillHand(UserStatus user, DungeonStatus ds) {
        int weaponId = user.getEquippedItems().getOrDefault("WEAPON", 0);
        String weaponType = (weaponId != 0)
                ? gameDataManager.getItemMap().get(weaponId).getSubType()
                : "NONE";

        Set<Integer> learnedSkills = new HashSet<>(user.getLearnedSkillIds());


        return gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    // 1. 무기 조건이 NONE이거나 현재 무기와 일치하는 경우 (기본 무기 기술 등)
                    boolean isWeaponSkill = meta.getRequiredWeapon().equals("NONE") ||
                            meta.getRequiredWeapon().equals(weaponType);

                    // 2. 무기 상관없이 유저가 명시적으로 습득한 스킬인 경우
                    boolean isLearned = learnedSkills.contains(meta.getId());

                    // 둘 중 하나라도 해당하면 리스트에 포함
                    return isWeaponSkill || isLearned;
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
}
