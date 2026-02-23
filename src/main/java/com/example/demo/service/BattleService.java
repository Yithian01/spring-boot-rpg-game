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
        // 1. 현재 무기 타입 파악 및 모든 장착 아이템으로부터 추가 스킬 수집
        String weaponType = "NONE";
        Set<Integer> availableSkillIds = new HashSet<>(user.getLearnedSkillIds()); // 영구 습득 스킬 먼저 담기

        for (var entry : user.getEquippedItems().entrySet()) {
            int itemId = entry.getValue();
            if (itemId == 0) continue;

            var itemMeta = gameDataManager.getItemMap().get(itemId);
            if (itemMeta == null) continue;

            // 무기 타입 저장
            if ("WEAPON".equals(entry.getKey())) {
                weaponType = itemMeta.getSubType();
            }

            // 아이템에 붙은 스킬 ID 리스트가 있다면 합치기 (Set이라 중복은 자동 제거됨)
            if (itemMeta.getGrantedSkillIds() != null) {
                availableSkillIds.addAll(itemMeta.getGrantedSkillIds());
            }
        }

        final String currentWeapon = weaponType;

        return gameDataManager.getSkillMetaMap().values().stream()
                .filter(meta -> {
                    // [조건 1] 내가 보유한 스킬인가? (습득했거나, 장비가 부여했거나)
                    boolean hasSkill = availableSkillIds.contains(meta.getId());

                    // [조건 2] 무기 적합성 체크 (공용이거나 현재 무기와 맞거나)
                    boolean canUseWithWeapon = meta.getRequiredWeapon().equalsIgnoreCase("NONE") ||
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);

                    // [조건 3] 무기 전용 기본기 예외 처리
                    // (보유하지 않았더라도, 현재 무기 타입과 정확히 일치하는 전용 스킬은 보여줌)
                    boolean isWeaponIntrinsic = !meta.getRequiredWeapon().equalsIgnoreCase("NONE") &&
                            meta.getRequiredWeapon().equalsIgnoreCase(currentWeapon);

                    return (hasSkill || isWeaponIntrinsic) && canUseWithWeapon;
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
