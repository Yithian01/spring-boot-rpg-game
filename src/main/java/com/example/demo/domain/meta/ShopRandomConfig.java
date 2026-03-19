package com.example.demo.domain.meta;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopRandomConfig {
    private String type;     // EQUIPMENT, ARTIFACT 등
    private String slot;     // HEAD, BODY, WEAPON 등
    private String subType;  // PLATE, LEATHER 등
    private String grade;    // UNCOMMON, RARE 등
    private int count;       // 몇 개를 뽑을 것인지
}