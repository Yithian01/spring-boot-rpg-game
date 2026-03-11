package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
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
    private List<FeldEffect> effects;

    /**
     * [NEXT FLOOR] 다음 던전 ID와 가중치(확률) 맵
     * 예: { "201": 50, "202": 30, "203": 20 } -> 총합 100
     */
    private Map<Integer, Integer> nextFloorWeights = new HashMap<>();

    /**
     * [OTHER AREAS] 다른 지역
     */
    private Map<Integer, Integer> otherAreas = new HashMap<>(); // List에서 Map으로 변경!

    /**
     * [PREV FLOOR] 이전 층
     */
    private List<Integer> prevDungeonId;

    /**
     * [기본 정보] 탐사율 BASE 값
     */
    private double explorationRate;

    /**
     * [기본 정보] 함정 조우 인카운터 BASE 확률
     */
    private double trapEncounterRate;

    /**
     * [기본 정보] 몬스터 조우 인카운터 BASE 확률
     */
    private double monsterEncounterRate;

    /**
     * [기본 정보] 상인 조우 인카운터 BASE 확률
     */
    private double mistEncounterRate;


    //======= HELPER METHOD ========
    // GET, RANDOM .. ETC
    //==============================

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

    /**
     * 이전 층 ID 목록 중 하나를 랜덤으로 선택합니다.
     * 리스트에 ID가 하나만 있으면 100% 확률로 그 ID가 선택됩니다.
     */
    public int pickPrevDungeonId() {
        if (this.prevDungeonId == null || this.prevDungeonId.isEmpty()) {
            // 도망칠 곳이 없는 경우 (예: 1층 혹은 최종 보스방 외통수)
            return this.id;
        }

        // 리스트 사이즈가 1이면 무조건 0번 인덱스 추출 (고정)
        // 리스트 사이즈가 N이면 1/N 확률로 추출 (랜덤)
        int randomIndex = (int) (Math.random() * this.prevDungeonId.size());
        return this.prevDungeonId.get(randomIndex);
    }

    /**
     * 가중치를 계산하여 주변 지역 ID를 랜덤으로 선택합니다.
     */
    public int pickOtherAreaDungeonId() {
        if (this.otherAreas == null || this.otherAreas.isEmpty()) {
            return this.id;
        }

        int totalWeight = otherAreas.values().stream().mapToInt(Integer::intValue).sum();
        int randomValue = (int) (Math.random() * totalWeight);

        int currentSum = 0;
        for (Map.Entry<Integer, Integer> entry : otherAreas.entrySet()) {
            currentSum += entry.getValue();
            if (randomValue < currentSum) {
                return Integer.parseInt(String.valueOf(entry.getKey()));
            }
        }
        return Integer.parseInt(String.valueOf(otherAreas.keySet().iterator().next()));
    }
}