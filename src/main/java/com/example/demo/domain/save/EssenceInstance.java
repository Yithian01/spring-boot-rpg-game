package com.example.demo.domain.save;

import com.example.demo.domain.meta.MonsterMeta;
import com.example.demo.domain.meta.SkillMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@Builder(toBuilder = true) // 기존 정수를 바탕으로 강화/수정할 때 유용
@NoArgsConstructor
@AllArgsConstructor
public class EssenceInstance {

    private String instanceId;
    private int monsterId;
    private String description;
    private String monsterName;
    private int monsterTier;    // 추가: 정렬 및 등급 판별용
    private String monsterType; // 일반, 변이종, 상위종, 수호자

    private long obtainedAt;    // 추가: 획득 시점 (System.currentTimeMillis())

    @Builder.Default
    private List<Integer> activeSkillIds = new ArrayList<>(); // 보유 액티브
    @Builder.Default
    private List<Integer> passiveSkillIds = new ArrayList<>(); // 보유 패시브

    // 스탯 보너스 레이어
    @Builder.Default
    private Map<Integer, Integer> baseStatsBonus = new HashMap<>();
    @Builder.Default
    private Map<String, Double> combatStatsBonus = new HashMap<>();
    @Builder.Default
    private Map<String, Double> lifeStatsBonus = new HashMap<>();
}