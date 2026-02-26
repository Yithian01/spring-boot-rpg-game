package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TownPageDto {
    private int day;            // 현재 며칠 차인지
    private int currentTurn;    // 현재 턴
    private int maxTurn;        // 최대 턴 (보통 30)
    private int currentTax;     // 이번 년도 낼 세금
    private boolean isTaxPaid;  // 세금 납부 여부
    private boolean portalOpen; // 매달 1일에 열린 여부
}