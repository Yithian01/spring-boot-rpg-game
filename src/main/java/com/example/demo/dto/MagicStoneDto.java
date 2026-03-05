package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MagicStoneDto {
    private String instanceId;   // 실제 소모할 때 쓸 UUID
    private String customName;   // "고블린의 9등급 마석"
    private int grade;           // 9 (metaId)
    private int quantity;        // 2개
}
