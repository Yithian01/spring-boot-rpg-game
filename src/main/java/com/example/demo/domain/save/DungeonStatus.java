package com.example.demo.domain.save;

import com.example.demo.domain.meta.MonsterStatsDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 던전의 '현재 상태'만 기록하는 데이터 모델
 * 모든 변경은 Service에서 수행한 뒤 Repository를 통해 파일로 덮어씌워짐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DungeonStatus {

    private int currentFloor;      // 현재 층
    private String dungeonId;      // 던전 식별자
    private int progress;      // 던전 진행도

    // 전투 관련 상태
    private ActiveMonster activeMonster;
    private int playerRemainingTurns; // 이번 라운드에 유저가 사용할 수 있는 남은 턴(AP)
    private int playerMaxTurns;       // 스탯 기반으로 계산된 라운드당 최대 턴

    @Builder.Default
    private List<String> battleLogs = new ArrayList<>();

    private int pendingExp;       // 승리 시 획득할 예정인 경험치
    private int pendingGold;      // 승리 시 획득할 예정인 골드

    /**
     * 로그 추가 메소드
     * @param log 표시할 로그
     */
    public void addLog(String log) {
        if (this.battleLogs == null) {
            this.battleLogs = new ArrayList<>();
        }
        // 최근 로그가 위로 오게 하고 싶다면 index 0에 추가, 아니면 그냥 add
        this.battleLogs.add(0, log);

        // 너무 많은 로그가 쌓여 파일이 커지는 것 방지 (예: 최근 20개만 유지)
        if (this.battleLogs.size() > 20) {
            this.battleLogs.remove(this.battleLogs.size() - 1);
        }
    }

    /**
     * 전투 중인지 판별
     * @return true/false
     */
    public boolean isInBattle() {
        return this.activeMonster != null;
    }
}