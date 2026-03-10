package com.example.demo.domain.meta;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopMeta {
    private String npcId;
    private String npcName;
    private String npcDescription;
    private double priceModifier;

    private List<Integer> fixedItems;
    private List<ShopRandomConfig> randomConfigs;
}