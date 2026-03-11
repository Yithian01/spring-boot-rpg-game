package com.example.demo.domain.save;

import com.example.demo.domain.enums.LocationType;
import com.example.demo.dto.ShopPageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameStatus {
    private LocationType location;
    private Integer dungeonId; // TOWN이면 null
    private String activeShopNpcId;

    // 통합 게임 로그 리스트
    @Builder.Default
    private List<String> gameLogs = new ArrayList<>();
    private boolean isClear;

    /**
     * 통합 로그 추가 메소드
     * 마을 활동(수련, 도박) 및 던전 활동(전투, 탐사) 로그를 모두 기록합니다.
     * @param log 표시할 로그 메시지
     */
    public void addLog(String log) {
        if (this.gameLogs == null) {
            this.gameLogs = new ArrayList<>();
        }

        // 최신 로그가 리스트의 맨 앞(0번 인덱스)에 오도록 추가
        this.gameLogs.add(0, log);

        // 메모리 및 저장 용량 관리를 위해 최근 20개까지만 유지
        if (this.gameLogs.size() > 20) {
            this.gameLogs.remove(this.gameLogs.size() - 1);
        }
    }

    /**
     * 상점 진입 (Open)
     */
    public void openShop(String npcId) {
        this.setActiveShopNpcId(npcId);
    }

    /**
     * 상점 종료 (Close)
     */
    public void closeShop() {
        this.setActiveShopNpcId(null);
    }
}