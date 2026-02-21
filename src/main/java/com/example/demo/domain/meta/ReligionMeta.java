package com.example.demo.domain.meta;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReligionMeta {
    private int id;
    private String name;
    private String description;
}
