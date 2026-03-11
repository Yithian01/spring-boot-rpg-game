package com.example.demo.domain.meta;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeldEffect {
    private String category;
    private String type;
    private double value;
    private String description;
}
