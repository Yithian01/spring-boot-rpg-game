package com.example.demo.dto;

import com.example.demo.domain.save.ActiveMonster;
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
    private int currentFloor;
    private String dungeonId;
    private int progress;

    private int explorationEfficiency; // "탐사 시 진척도 +5%" 식의 표시용
    private double restSafetyRate;      // "휴식 안전도 85%" 표시용

    private boolean isInBattle;
    private ActiveMonster activeMonster;

    private List<SkillCardDto> skillCards;

    private int playerRemainingTurns;
    private int playerMaxTurns;

    private List<String> battleLogs;
    private int pendingExp;
    private int pendingGold;
}