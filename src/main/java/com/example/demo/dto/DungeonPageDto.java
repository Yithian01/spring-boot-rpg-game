package com.example.demo.dto;

import com.example.demo.domain.save.ActiveMonster;
import com.example.demo.domain.save.EssenceInstance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DungeonPageDto {
    private int dungeonId;
    private String dungeonName;
    private int currentFloor;
    private int parentDungeonId;
    private String parentDungeonName;
    private int progress;
    private int actionCount;
    private int maxActionCount;

    private int explorationEfficiency; // "탐사 시 진척도 +5%" 식의 표시용
    private double restSafetyRate;      // "휴식 안전도 85%" 표시용

    private boolean isInBattle;
    private ActiveMonster activeMonster;

    private List<SkillCardDto> skillCards;

    private int playerRemainingTurns;
    private int playerMaxTurns;

    private int pendingExp;
    private EssenceInstance pendingEssence;
}