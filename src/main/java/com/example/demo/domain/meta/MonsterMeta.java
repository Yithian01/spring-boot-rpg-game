package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonsterMeta {
    private int id;
    private String name;
    private int tier;           // 9(약함) ~ 1(강함)
    private String description;
    private String img;          // 이미지 경로 추가 대비

    private CombatStats stats; // 전투 수치 결과값들
    private List<Integer> skillIds; // 사용 스킬들

    private int baseActionPoints; // 행동 포인트
    private int expReward;      // 처치 시 경험치
    private int goldMin;        // 최소 드랍 골드
    private int goldMax;        // 최대 드랍 골드
}