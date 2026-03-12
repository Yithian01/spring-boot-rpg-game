package com.example.demo.service;

import com.example.demo.domain.save.GambleStatus;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.TownStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.repository.GambleFileRepository;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class GambleService {
    private final GambleFileRepository gambleFileRepository;
    private final UserFileRepository userFileRepository;
    private final GameFileRepository gameFileRepository;
    private final TownFileRepository townFileRepository;
    private final ShopService shopService;
    private final TownService townService;

    /**
     * 반복되는 로그 기록 및 저장을 위한 헬퍼 메서드
     */
    private void logAndSave(String message) {
        GameStatus gs = gameFileRepository.findGameStatus();
        if (gs != null) {
            gs.addLog(message);
            gameFileRepository.saveGameStatus(gs);
        }
        log.info(message);
    }

    /**
     * 도박장 입장 (메인 선택 화면 열기)
     */
    public void openGamble() {
        GameStatus gs = gameFileRepository.findGameStatus();
        gs.setActiveGamble(true);
        gameFileRepository.saveGameStatus(gs);
        GambleStatus status = gambleFileRepository.findGambleStatus();
        if (status == null) {
            status = new GambleStatus();
        }
        status.reset();
        gambleFileRepository.saveGambleStatus(status);
        logAndSave("지하 도박장에 입장했습니다. 행운을 빕니다!");
    }

    /**
     * 도박장 퇴장 (모달 닫기)
     */
    public void closeGamble() {
        GameStatus gs = gameFileRepository.findGameStatus();
        gs.setActiveGamble(false);
        gameFileRepository.saveGameStatus(gs);
        gambleFileRepository.deleteFile();
        logAndSave("도박장을 나갑니다. 차가운 밤공기가 느껴집니다.");
    }

    /**
     * 언더/오버 게임 시작 (실제 골드 차감 포함)
     */
    public void openUnderOverGamble(int betAmount) {
        deductUserGold(betAmount);

        GambleStatus status = new GambleStatus();
        status.resetUnderOver(betAmount);
        gambleFileRepository.saveGambleStatus(status);

        logAndSave("언더/오버 게임에 " + betAmount + "G를 베팅했습니다!");
    }

    /**
     * 블랙잭 게임 시작 (실제 골드 차감 포함)
     */
    public void openBlackJackGamble(int betAmount) {
        deductUserGold(betAmount);

        GambleStatus status = new GambleStatus();
        status.resetBlackJack(betAmount);
        gambleFileRepository.saveGambleStatus(status);

        logAndSave("블랙잭 게임에 " + betAmount + "G를 베팅했습니다. 딜러가 카드를 섞습니다.");
    }

    /**
     * 공통 골드 차감 로직 (헬퍼 메서드)
     */
    private void deductUserGold(int amount) {
        UserStatus user = userFileRepository.findGameUser();
        if (user.getCurrentGold() < amount) {
            logAndSave("골드가 부족하여 베팅에 실패했습니다.");
            throw new RuntimeException("보유 골드가 부족합니다.");
        }
        user.setCurrentGold(user.getCurrentGold() - amount);
        userFileRepository.saveUserStatus(user);
    }

    /**
     * 행운 기반 스탯에 따라 도박
     * @param bettingGold 도박할 금액
     * @return 도박 결과 반환
     */
    public String luckTestGamble(int bettingGold) {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        // 1. 골드 부족 체크
        if (us.getCurrentGold() < bettingGold) {
            return "보유한 골드가 부족합니다!";
        }

        // 2. 확률 계산
        double winRate = 5.0 + us.getLifeStats().getGambleWinRateBonus();
        double randomValue = new Random().nextDouble() * 100; // 0.0 ~ 100.0

        String resultMessage;
        // 턴 소모
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);
        shopService.townStoreRestock();

        if (randomValue <= winRate) {
            // 승리!
            double multiplier = 2.0 + (us.getLifeStats().getGambleMultiplierBonus() / 100);
            int reward = (int) (bettingGold * multiplier);
            us.setCurrentGold(us.getCurrentGold() + (reward - bettingGold)); // 배팅금 제외 순수익 추가
            resultMessage = "🎲 도박 성공! " + reward + " G를 획득했습니다! (승률: " + String.format("%.1f", winRate) + "%)";
        } else {
            // 패배...
            us.setCurrentGold(us.getCurrentGold() - bettingGold);
            resultMessage = "💸 도박 실패... " + bettingGold + " G를 잃었습니다. (승률: " + String.format("%.1f", winRate) + "%)";
        }

        townService.saveAll(us, ts, gs);
        return townService.checkTurnAndTax(resultMessage);
    }
}