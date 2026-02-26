package com.example.demo.service;

import com.example.demo.config.GameValidation;
import com.example.demo.domain.enums.LocationType;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.TownStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {
    private final GameFileRepository gameFileRepository;
    private final UserFileRepository userFileRepository;
    private final TownFileRepository townFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final GameValidation gameValidation;

    /**
     * hp <= 0 인경우 체크
     * @return null : Game Over
     */
    public String checkHp() {
        UserStatus user = userFileRepository.findGameUser();
        return gameValidation.validateAlive(user);
    }

    /**
     * 입구 컷 검증 로직
     * 매달 1일에만 입장 가능
     */
    public boolean canEnterDungeon() {
        TownStatus townStatus = townFileRepository.findTownStatus();
        boolean isFirstDay = ((townStatus.getDay() - 1) % 30 + 1) == 1;
        return isFirstDay;
    }

    /**
     * 전투 종료 로직
     */
    public void checkEndBattle() {
        DungeonStatus dungeonStatus = dungeonFileRepository.findDungeonStatus();
        if (dungeonStatus.getActiveMonster() != null && dungeonStatus.getActiveMonster().getCurrentHp() <= 0){
            dungeonFileRepository.clearBattleStatus();
        }
    }

    /**
     * 던전 체류 한계(행동 카운트) 체크
     * @return true: 마을로 강제 귀환해야 함, false: 계속 탐험 가능
     */
    public boolean checkForceReturn() {
        GameStatus gs = gameFileRepository.findGameStatus();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();

        // 1. 하드코딩된 배열 대신 ds에 저장된 maxActionCount 사용
        int limit = ds.getMaxActionCount();
        int current = ds.getActionCount();

        // 2. 제한 수치가 설정되지 않았거나 0인 경우에 대한 방어 로직
        if (limit <= 0) {
            return false;
        }

        // 3. 현재 행동수가 제한을 넘었는지 판단
        boolean isOverLimit = current >= limit;

        if (isOverLimit) {
            gs.addLog(">>> [강제귀환] 던전 체류 한계 도달! 마을로 돌아갑니다.");
            gameFileRepository.saveGameStatus(gs);
            gameFileRepository.updateLocation(LocationType.TOWN, 0);
            log.warn(">>> [강제귀환] 던전 체류 한계 도달! (현재 행동치: {} / 최대 제한: {})", current, limit);
        }

        return isOverLimit;
    }
}
