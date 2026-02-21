package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCardDto {
    private int id;
    private String name;
    private String icon;
    private String description;

    private int turnCost;
    private int staminaCost;
    private int mpCost;
    private int hpCost;

    private boolean canAct;
    private String message;
}