package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DungeonMeta {
    private int id;
    private int floor;
    private String name;
    private int maxActionCount;
    private int monsterTableId;

    // 효과가 없는 경우 빈 Map으로 초기화됨
    private Map<String, Double> effects = new HashMap<>();

    /**
     * 특정 효과 값을 가져올 때 사용하는 헬퍼 메서드
     * 효과가 없으면 기본값(1.0)을 반환하여 계산 시 오류를 방지함
     */
    public double getEffectValue(String key) {
        return effects != null && effects.containsKey(key) ? effects.get(key) : 1.0;
    }
}