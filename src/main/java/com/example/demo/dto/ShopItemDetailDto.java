package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ShopItemDetailDto {
    private int itemMetaId;
    private String itemName;
    private String itemDescription;
    private String grade;       // UNCOMMON, RARE 등 (색상 입히기용)
    private String type;        // EQUIPMENT, CONSUMABLE 등
    private int basePrice;      // 도감상 기본 가격
    private int finalPrice;     // priceModifier가 적용된 실제 판매가
    private int stock;          // 남은 재고
    private boolean isUnique;
    private boolean isSoldOut;  // stock == 0 인지 여부 편의 필드
}