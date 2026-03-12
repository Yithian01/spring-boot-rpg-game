package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 도박 시스템의 현재 진행 상태를 저장하는 도메인
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GambleStatus {

    // 1. 현재 어떤 모달이 활성화되어야 하는가? (NONE, MAIN, UNDER_OVER, BLACKJACK)
    private String currentMode;

    // 2. 현재 게임의 단계 (BETTING: 배팅 중, PLAYING: 게임 진행 중, RESULT: 결과 확인 중)
    private String step;

    // 3. 베팅한 금액 게임 진행 시 데이터 휘발성 블록
    private int betAmount;

    // --- [언더/오버 전용 데이터] ---
    private String userChoice;      // 유저의 선택 (UNDER, SEVEN, OVER)
    private List<Integer> diceResults; // 주사위 결과 (예: [3, 4])

    // --- [블랙잭 전용 데이터] ---
    private List<String> playerHand;   // 플레이어 카드 리스트 (예: ["H_A", "D_10"])
    private List<String> dealerHand;   // 딜러 카드 리스트
    private boolean playerStood;       // 플레이어가 STAY(멈춤)를 눌렀는지 여부
    private boolean playerBlackJack; // 플레이어 블랙잭 여부
    private boolean dealerBlackJack; // 딜러 블랙잭 여부


    // --- [결과 처리 데이터] ---
    private boolean isWin;             // 승리 여부
    private int earnedGold;            // 최종 획득/손실 금액 (양수면 획득, 음수면 손실)
    private String resultMessage;      // 화면에 띄워줄 결과 메시지 ("블랙잭 승리!", "7이 나와 패배했습니다.")

    /**
     * 게임 초기화 (초기 상태로 복구할 때 사용)
     */
    public void reset() {
        this.currentMode = "MAIN"; // 도박장 메인 선택창으로 이동
        this.step = "BETTING";
        this.betAmount = 0;

        this.userChoice = null;
        this.diceResults = null;

        this.playerHand = null;
        this.dealerHand = null;
        this.playerStood = false;
        this.playerBlackJack = false;
        this.dealerBlackJack = false;

        this.isWin = false;
        this.earnedGold = 0;
        this.resultMessage = "";
    }

    /**
     * underOver 게임 초기화 (초기 상태로 복구할 때 사용)
     */
    public void resetUnderOver(int betAmount) {
        this.currentMode = "UNDER_OVER"; // 도박장 메인 선택창으로 이동
        this.step = "PLAYING";
        this.userChoice = null;
        this.diceResults = new ArrayList<>();
        this.betAmount = betAmount;

        this.playerHand = null;
        this.dealerHand = null;
        this.playerStood = false;
        this.playerBlackJack = false;
        this.dealerBlackJack = false;

        this.isWin = false;
        this.earnedGold = 0;
        this.resultMessage = "";
    }

    /**
     * balckJack 게임 초기화 (초기 상태로 복구할 때 사용)
     */
    public void resetBlackJack(int betAmount) {
        this.currentMode = "BLACKJACK"; // 도박장 메인 선택창으로 이동
        this.step = "PLAYING";
        this.userChoice = null;
        this.diceResults = null;

        this.betAmount = betAmount;

        this.playerHand = new ArrayList<>();
        this.dealerHand = new ArrayList<>();
        this.playerStood = false;
        this.playerBlackJack = false;
        this.dealerBlackJack = false;

        this.isWin = false;
        this.earnedGold = 0;
        this.resultMessage = "";
    }

    /**
     * 주사위 합계를 계산하여 반환하는 헬퍼 메소드
     */
    public int getDiceSum() {
        if (diceResults == null || diceResults.isEmpty()) return 0;
        return diceResults.stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 플레이어 패 합계를 계산하여 반환하는 헬퍼 메소드
     */
    public int getPlayerHandSum() {
        if (diceResults == null || diceResults.isEmpty()) return 0;
        return diceResults.stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 딜러 패 합계를 계산하여 반환하는 헬퍼 메소드
     */
    public int getDealerHandSum() {
        if (diceResults == null || diceResults.isEmpty()) return 0;
        return diceResults.stream().mapToInt(Integer::intValue).sum();
    }
}