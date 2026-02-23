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

        // 1. 현재 무기 정보 안전하게 가져오기
        int weaponId = user.getEquippedItems().getOrDefault("WEAPON", 0);
        String weaponType = "NONE";

        if (weaponId != 0 && gameDataManager.getItemMap().containsKey(weaponId)) {
            weaponType = gameDataManager.getItemMap().get(weaponId).getSubType();
        }

        // 2. 배운 스킬 ID 셋 (Integer 타입)
        Set<Integer> learnedSkills = new HashSet<>(user.getLearnedSkillIds());

        System.out.println("sdssdds" + weaponType);


        final String currentWeapon = weaponType; // 람다용 final 변수

        return gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    // [조건 1] 일단 유저가 배운 스킬 리스트에 있어야 함
                    boolean isLearned = learnedSkills.contains(meta.getId());

                    // 2. 현재 무기 타입과 스킬의 요구 무기가 일치하는가? (예: BLUNT == BLUNT)
                    // (NONE은 제외하고 '전용 무기 스킬'들만 체크)
                    boolean isWeaponMatch = !meta.getRequiredWeapon().equalsIgnoreCase("NONE") &&
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);

                    // [최종 통과 조건]
                    // - 내가 배운 스킬이면 통과 (단, 배운 스킬이라도 다른 무기 전용이면 안됨)
                    // - 혹은, 내가 배우지 않았어도 현재 무기에 맞는 전용 스킬이면 통과

                    // 무기 적합성 체크 (공용이거나 현재 무기와 맞거나)
                    boolean canUseWithWeapon = meta.getRequiredWeapon().equalsIgnoreCase("NONE") ||
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);

                    // (습득했거나 OR 무기 타입이 딱 맞거나) AND 현재 낀 무기로 사용 가능해야 함
                    return (isLearned || isWeaponMatch) && canUseWithWeapon;
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
