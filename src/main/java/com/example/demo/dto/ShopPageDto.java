package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopPageDto {
    private String npcId;
    private String npcName;
    private String npcDescription;
    private double priceModifier; // 화면에서 "20% 세일 중!" 같은 문구 표시용
    private List<ShopItemDetailDto> saleItems; // 상세 정보가 포함된 아이템 리스트
}