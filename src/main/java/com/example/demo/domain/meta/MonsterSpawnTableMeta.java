package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonsterSpawnTableMeta {
    private int tableId;
    private String name;
    private List<MonsterSpawn> spawns;

    /**
     * 이 테이블의 설정된 가중치에 따라 랜덤하게 monsterId 하나를 반환합니다.
     */
    public int pickRandomMonsterId(java.util.Random random) {
        if (spawns == null || spawns.isEmpty()) return -1;

        // 1. 전체 가중치 합산
        int totalWeight = spawns.stream()
                .mapToInt(MonsterSpawn::getWeight)
                .sum();

        // 2. 랜덤 값 추출
        int randomVal = random.nextInt(totalWeight);
        int currentSum = 0;

        // 3. 구간 확인
        for (MonsterSpawn spawn : spawns) {
            currentSum += spawn.getWeight();
            if (randomVal < currentSum) {
                return spawn.getMonsterId();
            }
        }
        return spawns.get(0).getMonsterId(); // 예외 방지용 기본값
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MonsterSpawn {
        private int monsterId;
        private int weight;
        private String comment;
    }
}