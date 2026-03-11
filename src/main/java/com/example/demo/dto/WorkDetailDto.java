package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkDetailDto {
    private String workType;       // PHYSIQUE, SPIRIT 등
    private String workName;       // 성벽 보수 등
    private int baseExpectedGold;  // 기본 예상 수익
    private double bonusGold;         // 아이템/특수 효과로 추가되는 수익 (+20% 등)
    private int staminaCost;       // 소모 스테미나
    private double bonusStaminaCost;  // 보너스 스테미나 감소율 혹은 증가율
    private double successRate;    // 성공 확률 (또는 대박 확률)
    private double bonusSuccessRate;    // 보너스 성공 확률 (또는 대박 확률)
    private String description;    // 간단한 설명
}
