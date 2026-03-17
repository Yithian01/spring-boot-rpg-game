package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierDropPool {
    private String grade;   // NONE, UNCOMMON, RARE 등
    private int weight;     // 드랍 확률
}
