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
    private List<String> deck;
    private int playerTotal;          // 매번 계산하기 번거로우므로 합계를 저장
    private int dealerTotal;
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
    public void resetUnderOver() {
        this.currentMode = "UNDER_OVER"; // 도박장 메인 선택창으로 이동
        this.step = "BET";
        this.userChoice = null;
        this.diceResults = new ArrayList<>();
        this.betAmount = 0;

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
     * blackjack 게임 초기화
     */
    public void resetBlackJack() {
        this.currentMode = "BLACKJACK";
        this.step = "BET";
        this.betAmount = 0;

        this.playerHand = new ArrayList<>();
        this.dealerHand = new ArrayList<>();
        this.playerStood = false;

        // 덱 생성 및 셔플
        this.deck = createNewDeck();

        this.playerTotal = 0;
        this.dealerTotal = 0;
        this.isWin = false;
        this.earnedGold = 0;
        this.resultMessage = "";
    }

    /**
     * 52장의 트럼프 카드 생성
     * 문양(S, H, D, C) + 숫자(A, 2~10, J, Q, K)
     */
    public List<String> createNewDeck() {
        List<String> newDeck = new ArrayList<>();
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

        for (String suit : suits) {
            for (String rank : ranks) {
                newDeck.add(suit + rank);
            }
        }
        java.util.Collections.shuffle(newDeck);
        return newDeck;
    }

    /**
     * 단일 카드의 블랙잭 점수를 계산하는 헬퍼 메소드
     * @param card "♠A", "♦10" 등의 카드 문자열
     * @return 카드 점수 (A는 기본 11로 처리)
     */
    public int calculateSingleCardValue(String card) {
        if (card == null || card.length() < 2) return 0;

        String rank = card.substring(1);

        return switch (rank) {
            case "A" -> 11;
            case "J", "Q", "K" -> 10;
            default -> Integer.parseInt(rank);
        };
    }

    /**
     * 주사위 합계 (정상)
     */
    public int getDiceSum() {
        if (diceResults == null || diceResults.isEmpty()) return 0;
        return diceResults.stream().mapToInt(Integer::intValue).sum();
    }
}