package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GambleDetailDto {
    private double winRate;        // 기본 승률 (예: 50.0)
    private double bonusWinRate;   // 보너스 승률 (예: 5.0)

    private double dividendRate;      // 기본 배당률 (예: 2.0)
    private double bonusDividendRate; // 보너스 배당률 (예: 10% 시 UI에서 2.1배로 표시)
}