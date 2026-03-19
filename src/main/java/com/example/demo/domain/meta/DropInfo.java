package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 개별 아이템의 드롭 조건 정보
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DropInfo {
    private int itemId;        // 아이템 메타 ID
    private double dropRate;   // 드롭 확률 (0.0 ~ 100.0)
    private int minQty;        // 최소 드롭 수량
    private int maxQty;        // 최대 드롭 수량
}