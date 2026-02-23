package com.example.demo.service;

import com.example.demo.config.GameValidation;
import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.TownStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {
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
     */
    public boolean canEnterDungeon() {
        TownStatus townStatus = townFileRepository.findTownStatus();
        return townStatus.getCurrentTurn() <= 0; // 0이하일 때만 true
    }

    /**
     * 전투 종료 로직
     */
    public void checkEndBattle() {
        System.out.println("asdsadasdsdsadsad");

        DungeonStatus dungeonStatus = dungeonFileRepository.findDungeonStatus();
        if (dungeonStatus.getActiveMonster() != null && dungeonStatus.getActiveMonster().getCurrentHp() <= 0){
            dungeonFileRepository.clearBattleStatus();
        }
    }
}
