package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatCategoryGroupDto {
    private String categoryName;    // "육신", "기민" 등 (한글 명칭)
    private String categoryKey;     // "PHYSIQUE", "AGILITY" 등 (ID용)
    private int totalValue;         // 해당 계통 스탯들의 총합
    private List<UserStatDto> details; // 계통에 속한 세부 스탯 리스트 (24개 중 일부)
}