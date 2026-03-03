package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 던전의 '현재 상태'만 기록하는 데이터 모델
 * 모든 변경은 Service에서 수행한 뒤 Repository를 통해 파일로 덮어씌워짐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DungeonStatus {

    private int dungeonId;      // 층수랑 다름
    private int parentDungeonId; // 이전층수 ID 층수가 X
    private String parentDungeonName; // 이전층수 ID 층수가 X
    private String dungeonName; // 지역 명칭
    private int currentFloor;   // 현재 층
    private int progress;      // 던전 진행도
    private int actionCount;   // 행동 횟수
    private int maxActionCount;   // 행동 횟수

    // 전투 관련 상태
    private ActiveMonster activeMonster;
    private int playerRemainingTurns; // 이번 라운드에 유저가 사용할 수 있는 남은 턴(AP)
    private int playerMaxTurns;       // 스탯 기반으로 계산된 라운드당 최대 턴

    private int pendingExp;       // 승리 시 획득할 예정인 경험치
    private EssenceInstance pendingEssence; // 승리 시 나왔다면 추가하는 정수

    @Builder.Default
    private Map<Integer, Integer> floorProgressMap = new HashMap<>(); // 각 맵의 진척도를 저장

    /**
     * 전투 중인지 판별
     * @return true/false
     */
    public boolean isInBattle() {
        return (this.activeMonster != null && this.activeMonster.getCurrentHp() > 0);
    }
}