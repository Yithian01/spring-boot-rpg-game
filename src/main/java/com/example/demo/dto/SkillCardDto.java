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

    // 비용 관련
    private int turnCost;
    private int staminaCost;
    private int mpCost;
    private int hpCost;

    // 상태 관련
    private boolean canAct;
    private String message;

    // --- 추가된 메타 데이터 전송 필드 ---
    private String type;        // PHYSICAL, MAGIC 등
    private String element;     // FIRE, ICE, PHYSICAL 등
    private Integer duration;   // 효과 지속 시간 (턴)
    private String status;      // 부여하는 상태이상 (BURN, STUN 등)
}