package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OLD_ItemMeta {
    private int id;
    private String name;
    private String description;
    private int rate;
    private List<Integer> costType;
    private List<Integer> costValue;
    private List<Integer> targetStatId;
    private List<Integer> targetStatValue;
    private int gold;
}