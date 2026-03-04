package com.example.demo.domain.save;

import com.example.demo.domain.meta.ItemMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemInstance {

    private String instanceId;
    private int itemMetaId;

    // UI 및 로직 편의를 위해 Meta에서 복사해오는 기본 정보
    private String customName;
    private String grade;
    private String type;
    private String slot;
    private String subType;
    private boolean twoHanded;
    private String description;

    private int quantity;
    private int enhancementLevel;
    private Integer price;

    // 능력치 및 회복 정보 (Meta에서 복사)
    private Map<Integer, Integer> baseStatsBonus;
    private Map<String, Double> combatStatsBonus;
    private Map<String, Integer> recoveryBonus;

    private Map<Integer, Double> baseStatsBonusModifiers;
    private Map<String, Double> combatStatsBonusModifiers;

    private List<Integer> grantedSkillIds;

    /**
     * [소모품 생성]
     */
    public static ItemInstance createConsumable(ItemMeta meta, int quantity) {
        return ItemInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .itemMetaId(meta.getId())
                .customName(meta.getName())
                .grade(meta.getGrade())
                .type(meta.getType())
                .slot(meta.getSlot())
                .subType(meta.getSubType())
                .description(meta.getDescription())
                .price(meta.getPrice())
                .quantity(quantity)
                .enhancementLevel(0)
                // 스탯 및 스킬 복사
                .baseStatsBonus(meta.getBaseStatsBonus() != null ? new HashMap<>(meta.getBaseStatsBonus()) : new HashMap<>())
                .combatStatsBonus(meta.getCombatStatsBonus() != null ? new HashMap<>(meta.getCombatStatsBonus()) : new HashMap<>())
                .recoveryBonus(meta.getRecoveryBonus() != null ? new HashMap<>(meta.getRecoveryBonus()) : new HashMap<>())
                .baseStatsBonusModifiers(meta.getBaseStatsBonusModifiers() != null ? new HashMap<>(meta.getBaseStatsBonusModifiers()) : new HashMap<>())
                .combatStatsBonusModifiers(meta.getCombatStatsBonusModifiers() != null ? new HashMap<>(meta.getCombatStatsBonusModifiers()) : new HashMap<>())
                .grantedSkillIds(meta.getGrantedSkillIds() != null ? List.copyOf(meta.getGrantedSkillIds()) : List.of())
                .build();
    }

    /**
     * [장비 생성]
     */
    public static ItemInstance createEquipment(ItemMeta meta) {
        return ItemInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .itemMetaId(meta.getId())
                .customName(meta.getName())
                .grade(meta.getGrade())
                .type(meta.getType())
                .slot(meta.getSlot())
                .subType(meta.getSubType())
                .twoHanded(meta.isTwoHanded())
                .description(meta.getDescription())
                .price(meta.getPrice())
                .quantity(1)
                .enhancementLevel(0)
                .baseStatsBonus(meta.getBaseStatsBonus() != null ? new HashMap<>(meta.getBaseStatsBonus()) : new HashMap<>())
                .combatStatsBonus(meta.getCombatStatsBonus() != null ? new HashMap<>(meta.getCombatStatsBonus()) : new HashMap<>())
                .recoveryBonus(meta.getRecoveryBonus() != null ? new HashMap<>(meta.getRecoveryBonus()) : new HashMap<>())
                .baseStatsBonusModifiers(meta.getBaseStatsBonusModifiers() != null ? new HashMap<>(meta.getBaseStatsBonusModifiers()) : new HashMap<>())
                .combatStatsBonusModifiers(meta.getCombatStatsBonusModifiers() != null ? new HashMap<>(meta.getCombatStatsBonusModifiers()) : new HashMap<>())
                .grantedSkillIds(meta.getGrantedSkillIds() != null ? List.copyOf(meta.getGrantedSkillIds()) : List.of())
                .build();
    }

    /**
     * [마석 생성]
     */
    public static ItemInstance createStone(ItemMeta meta, String monsterName, int quantity, Integer priceOverride) {
        return ItemInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .itemMetaId(meta.getId())
                .customName(monsterName + "의 " + meta.getName())
                .grade(meta.getGrade())
                .type(meta.getType())
                .description(meta.getDescription())
                .price(priceOverride != null ? priceOverride : meta.getPrice())
                .quantity(quantity)
                .enhancementLevel(0)
                .build();
    }

    /**
     * [재료 생성]
     */
    public static ItemInstance createMaterial(ItemMeta meta, int quantity, Integer priceOverride) {
        return ItemInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .itemMetaId(meta.getId())
                .customName(meta.getName())
                .grade(meta.getGrade())
                .type(meta.getType())
                .description(meta.getDescription())
                .price(priceOverride != null ? priceOverride : meta.getPrice())
                .quantity(quantity)
                .enhancementLevel(0)
                .build();
    }
}