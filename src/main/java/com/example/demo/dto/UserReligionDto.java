package com.example.demo.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReligionDto {
    private int id;             // 스탯 ID (정렬용)
    private String name;        // 표시 이름
    private String description; // 툴팁 설명
}
