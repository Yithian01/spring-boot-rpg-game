package com.example.demo.domain.meta;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrowthMeta {
    private int id;
    private String grade;
    private double weight;
    private int rate;
}
