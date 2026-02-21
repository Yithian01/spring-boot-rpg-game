package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonsterMeta {
    private int id;
    private String name;
    private int tier;           // 8(약함) ~ 1(강함)
    private String description;
    private String img;          // 이미지 경로 추가 대비

    private MonsterStatsDto stats; // 전투 수치 결과값들

    private int expReward;      // 처치 시 경험치
    private int goldMin;        // 최소 드랍 골드
    private int goldMax;        // 최대 드랍 골드
}