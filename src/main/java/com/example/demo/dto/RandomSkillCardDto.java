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
public class RandomSkillCardDto {
    private int id;
    private String name;
    private String icon;
    private String description;
    private String grade;        // COMMON, UNCOMMON 등 (테두리 색상 결정용)
    private List<String> requiredWeapon; // 필요한 무기군

    // 비용 정보
    private int turnCost;
    private int staminaCost;
    private int mpCost;
    private int hpCost;

    // 분류 및 제약
    private String type;         // PHYSICAL, MAGIC 등
    private String element;      // FIRE, ICE 등
    private List<String> requiredWeapons;

    // 효과 및 상태이상
    private String effectType;
    private String statusName;   // "화상", "기절" 등
    private Integer duration;
    private Integer effectChance;

    // 핵심: 빌드 확인용 문구
    private List<String> scalingInfo;     // "공격력의 150% + 힘의 2배"
    private List<String> modifierDetails; // "3턴간 명중률 20% 증가"

    // 중복 처리 관련 핵심 필드
    private boolean alreadyLearned;     // 유저가 이미 배운 스킬인지 여부
    private String bonusStatType;         // 중복 시 상승할 스탯 계열의 명칭
    private int bonusStatValue;           // 상승 수치 (COMMON이면 3)
}