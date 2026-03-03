package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 드롭 테이블 메타데이터
 * 한 테이블 내에서 여러 아이템이 각각의 확률로 드롭될 수 있는 구조입니다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DropTableMeta {
    private String id;           // 테이블 식별자 (예: DT_BOSS_BRUTAL_FANG)
    private String description;  // 테이블 설명 (기획용)
    private List<DropInfo> drops; // 포함된 드롭 아이템 리스트
}