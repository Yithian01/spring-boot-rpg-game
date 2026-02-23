package com.example.demo.domain.save;

import com.example.demo.domain.meta.CombatStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 전투 중인 실시간 몬스터 스냅샷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveMonster {
    private int monsterId;
    private String name;
    private int tier;

    private int currentHp;
    private int maxHp;
    private int currentMp;
    private int maxMp;

    private CombatStats stats; // 메타데이터에서 복사해온 스탯
    @Builder.Default
    private List<ActiveStatus> activeStatuses = new ArrayList<>();
}
