package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInstance {
    private String npcId;                    // 상점 주인 식별자
    private long lastRestockTime;            // 재입고 계산을 위한 시간 정보
    private Map<Integer, Integer> itemQty;   // key: itemMetaId, value: 남은 수량
}