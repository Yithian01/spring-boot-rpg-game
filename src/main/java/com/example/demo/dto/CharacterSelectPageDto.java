package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CharacterSelectPageDto {
    private int tribeId;
    private String characterName;
    private String imageUrl;
    private String description;
    private int initGold;
}
