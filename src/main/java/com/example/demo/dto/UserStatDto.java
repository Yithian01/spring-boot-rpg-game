package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatDto {

    // --- [1] 메타 데이터 (StatMeta)에서 가져올 정보 ---
    private int id;             // 스탯 ID (정렬용)
    private String category;    // 스탯 카테고리 (예: Structural & Muscular)
    private String name;        // 표시 이름 (예: 전완 굴근/신근)
    private String description; // 툴팁 설명

    // --- [2] 유저 데이터 (GameUser)에서 가져올 정보 ---
    private int value;          // 현재 스탯 수치 (json의 stats 값)

    // --- [3] 계산된 정보 ---
    private String growthGrade; // 잠재력 등급 (S, A, F 등) -> CSS 클래스 매핑용
}