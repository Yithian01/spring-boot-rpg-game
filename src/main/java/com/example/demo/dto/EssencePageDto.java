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
public class EssencePageDto {
    private String instanceId;    // 식별자
    private String monsterName;   // 몬스터 이름 (ex: 하급 고블린)
    private String monsterType;   // 타입 (ex: 변이종)
    private int monsterTier;      // 티어 (1~10)

    // UI에 "근력 +5", "이동속도 +10%" 처럼 뿌려줄 텍스트 리스트
    private List<String> statBonuses;

    // 보유한 스킬 이름들 (아이콘이나 설명을 위해 SkillPageDto 리스트로 확장 가능)
    private List<String> skillNames;

    private String icon;
}