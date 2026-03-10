package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ShopItemDetailDto {
    private String id;
    private String name;
    private String grade;
    private String icon;
    private String description;
    private int gold;
    private String type;      // EQUIPPABLE, CONSUMABLE 등
    private String slot;      // WEAPON, HEAD 등
    private String subType;
    private List<String> statBonuses;
    private String recoveryEffect;
    private boolean twoHanded;
    private int stock;          // 남은 재고
    private boolean isSoldOut;
}