package com.example.demo.dto;

import com.example.demo.domain.save.DungeonStatus;
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

    private boolean isInBattle;
    private DungeonStatus.ActiveMonster activeMonster;

    private int playerRemainingTurns;
    private int playerMaxTurns;

    private List<String> battleLogs;
    private int pendingExp;
    private int pendingGold;
}