package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * 맵 별로 존재하는 효과 정보
     * 효과가 없는 경우 빈 Map으로 초기화됨
     */
    private Map<String, Double> effects = new HashMap<>();

    /**
     * [NEXT FLOOR] 다음 던전 ID와 가중치(확률) 맵
     * 예: { "201": 50, "202": 30, "203": 20 } -> 총합 100
     */
    private Map<Integer, Integer> nextFloorWeights = new HashMap<>();

    /**
     * [PREV FLOOR] 이전 층
     */
    private int prevDungeonId;

    /**
     * 특정 효과 값을 가져올 때 사용하는 헬퍼 메서드
     * 효과가 없으면 기본값(1.0)을 반환하여 계산 시 오류를 방지함
     */
    public double getEffectValue(String key) {
        return effects != null && effects.containsKey(key) ? effects.get(key) : 1.0;
    }

    /**
     * 가중치를 계산하여 다음 던전 ID를 랜덤으로 선택합니다.
     */
    public int pickNextDungeonId() {
        // meta.getNextFloorWeights() 대신 그냥 자기 필드인 nextFloorWeights 사용
        if (this.nextFloorWeights == null || this.nextFloorWeights.isEmpty()) {
            return this.id + 100;
        }

        int totalWeight = nextFloorWeights.values().stream().mapToInt(Integer::intValue).sum();
        int randomValue = (int) (Math.random() * totalWeight);

        int currentSum = 0;
        for (Map.Entry<Integer, Integer> entry : nextFloorWeights.entrySet()) {
            currentSum += entry.getValue();
            if (randomValue < currentSum) {
                return entry.getKey();
            }
        }
        return nextFloorWeights.keySet().iterator().next();
    }
}