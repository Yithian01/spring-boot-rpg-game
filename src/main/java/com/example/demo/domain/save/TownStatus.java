package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TownStatus {
    // 1. 턴 시스템 (행동력)
    private int currentTurn;   // 현재 남은 턴 (행동 시 감소)
    private int maxTurn;       // 최대 턴 (하루 기준, 예: 30)

    // 2. 시간/세금 시스템
    private int day;           // 게임 진행 일수 (턴이 다 지나면 1 증가)
    private int currentTax;   // 현재 납부해야 할 세금
    private boolean isTaxPaid; // 해당 기간(day)의 세금 납부 여부
}