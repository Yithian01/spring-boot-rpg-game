package com.example.demo.service;

import com.example.demo.domain.enums.LocationType;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.TownStatus;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.GameRepository;
import com.example.demo.repository.TownFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class DungeonService {

    private final GameRepository gameRepository;
    private final TownFileRepository townFileRepository;
    private final DungeonFileRepository dungeonFileRepository;

    /**
     * 던전 진입 시 초기 설정 및 세이브 파일 생성
     */
    public void initDungeon() {
        // 1. 전체 게임 상태 변경 (마을 -> 던전)
        GameStatus gameStatus = gameRepository.findGameStatus();
        gameStatus.setLocation(LocationType.DUNGEON);
        gameStatus.setDungeonId(1); // 기본 던전 ID
        gameRepository.saveGameStatus(gameStatus);

        // 2. 마을 상태 업데이트 (날짜 경과 및 턴 회복)
        TownStatus townStatus = townFileRepository.findTownStatus();
        townStatus.setDay(townStatus.getDay() + 1);
        townStatus.setCurrentTurn(townStatus.getMaxTurn());
        townStatus.setTaxPaid(false);
        townFileRepository.saveTownStatus(townStatus);

        // 3. ★ 던전 상태 파일 초기 생성 ★
        // 처음 진입 시에는 1층, 몬스터는 없는(null) 상태로 시작
        DungeonStatus newDungeonStatus = DungeonStatus.builder()
                .currentFloor(1)
                .dungeonId("GRAY_MINE") // 혹은 gameStatus에서 가져온 ID
                .progress(0) // 혹은 gameStatus에서 가져온 ID
                .activeMonster(null)    // 아직 조우 전
                .playerMaxTurns(0)      // 전투 돌입 시 계산
                .playerRemainingTurns(0)
                .battleLogs(new ArrayList<>())
                .build();

        newDungeonStatus.getBattleLogs().add("어두운 던전에 발을 들였습니다... (1층)");

        dungeonFileRepository.saveDungeonStatus(newDungeonStatus);

        log.info(">>> 던전 파일 생성 완료 및 진입: Day {}, Floor 1", townStatus.getDay());
    }

    /**
     * [탐사하기] 버튼 클릭 시 호출
     * 몬스터를 조우하거나, 아무 일도 없거나, 다음 층으로 가거나 결정
     */
    public void explore() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();

        // 여기에 아까 만든 몬스터 추첨 로직 + 턴 계산 로직을 넣으면 됩니다.
        // 1. 몬스터 뽑기
        // 2. 유저 스탯 기반 턴 계산
        // 3. ds.setActiveMonster(...)
        // 4. dungeonFileRepository.saveDungeonStatus(ds);
    }
}