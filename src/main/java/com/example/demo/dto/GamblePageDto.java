package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 도박 모달 UI 렌더링을 위한 데이터 전송 객체
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamblePageDto {

    // --- [시스템/상태 정보] ---
    private String currentMode;       // MAIN, UNDER_OVER, BLACKJACK, NONE
    private String step;              // BETTING, PLAYING, RESULT
    private long currentGold;         // 유저의 현재 보유 골드 (금액 부족 체크용)

    // --- [배팅 정보] ---
    private double betAmount;         // 현재 배팅한 금액
    private double betMultiplier;     // 적용된 배당률 (승률 보너스 등 포함)

    // --- [언더/오버 (Dice) 데이터] ---
    private String userChoice;        // 유저가 선택한 값 (UNDER, SEVEN, OVER)
    private List<Integer> diceResults; // 주사위 결과 [n, n]
    private int diceSum;              // 주사위 합계 (화면 출력 편의용)

    // --- [블랙잭 데이터] ---
    private List<String> playerHand;   // 플레이어 카드 ("H_A", "S_10" 등)
    private List<String> dealerHand;   // 딜러 카드
    private int playerTotal;           // 플레이어 카드 합계
    private int dealerTotal;           // 딜러 카드 합계
    private boolean playerStood;       // 플레이어가 멈췄는지 여부
    private boolean playerBlackJack;       // 플레이어가 블랙잭 여부
    private boolean dealerBlackJack;       // 플레이어가 블랙잭 여부

    // --- [결과 데이터] ---
    private boolean isWin;             // 승리 여부
    private int earnedGold;            // 최종 결과 금액 (정산될 금액)
    private String resultMessage;      // 화면에 출력할 텍스트 메시지
}