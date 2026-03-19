package com.example.demo.domain.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TribeInitialMeta {
    private int tribeId;
    private String image;
    private int gold;
    @JsonProperty("initialEquipment")
    private Map<String, Integer> initialEquipment;
    @JsonProperty("combatBaseStats")
    private Map<Integer, Integer> initialStats;
    @JsonProperty("initialItem")
    private List<Integer> initialItem;
    @JsonProperty("initialSkill")
    private List<Integer> initialSkill;
}
