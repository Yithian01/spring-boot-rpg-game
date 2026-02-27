package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemPageDto {
    private String id;
    private String name;
    private String grade;
    private String icon;
    private String description;
    private int gold;
    private String type;      // EQUIPPABLE, CONSUMABLE 등
    private String slot;      // WEAPON, HEAD 등
    private String subType;
    private int quantity;     // 인벤토리 수량
    private List<String> statBonuses;
    private String recoveryEffect;
    private boolean twoHanded;
}
