package com.example.demo.domain.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TribeInitialMeta {
    private int tribeId;
    private String image;
    private int gold;
    @JsonProperty("combatBaseStats")
    private Map<Integer, Integer> initialStats;
}
