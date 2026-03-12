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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * 미니게임 초기화 핸들러
     * @param gambleType : UNDER_OVER, BLACKJACK
     * @return 에러 메시지 (정상이면 null 반환)
     */
    public String handleGameInit(String gambleType) {
        // 2. 게임 타입별 초기화 로직 분기
        switch (gambleType) {
            case "UNDER_OVER":
                openUnderOverGamble();
                break;
            case "BLACKJACK":
                openBlackJackGamble();
                break;
            default:
                log.error("알 수 없는 도박 타입 요청: {}", gambleType);
                return "존재하지 않는 게임 타입입니다.";
        }
        return "게임을 시작합니다!! 행운을 빕니다.";
    }

    /**
     * 언더/오버 게임 진입 (초기 세팅만 수행)
     */
    public void openUnderOverGamble() {
        GambleStatus status = new GambleStatus();
        status.resetUnderOver();
        gambleFileRepository.saveGambleStatus(status);
        logAndSave("언더/오버 도박판에 자리를 잡았습니다.");
    }

    /**
     * 블랙잭 게임 시작
     */
    public void openBlackJackGamble() {
        GambleStatus status = new GambleStatus();
        status.resetBlackJack();
        gambleFileRepository.saveGambleStatus(status);
        logAndSave("블랙잭 게임에 자리를 잡았습니다. 딜러가 카드를 섞고 있습니다.");
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
        return resultMessage;
    }

    /**
     * [수정] 언더/오버 실제 플레이 로직
     */
    public String playUnderOverGame(String userChoice, int betAmount) {
        UserStatus us = userFileRepository.findGameUser();
        GambleStatus gambleStatus = gambleFileRepository.findGambleStatus();

        // 1. 검증
        if (us.getCurrentGold() < betAmount) {
            return "보유 골드가 부족합니다!";
        }

        // 2. 골드 즉시 차감 (선지불 개념)
        // 중요: 여기서 저장까지 수행하거나, 마지막에 한 번에 저장해야 함
        int currentGold = us.getCurrentGold() - betAmount;
        us.setCurrentGold(currentGold);

        // 3. 주사위 굴리기
        int dice1 = new Random().nextInt(6) + 1;
        int dice2 = new Random().nextInt(6) + 1;
        int sum = dice1 + dice2;

        // 4. 배당 계산
        boolean isWin = false;
        double bonusAdd = us.getLifeStats().getGambleMultiplierBonus() / 100.0;
        double finalMultiplier = 0.0; // 승리 시에만 적용할 배당률

        if ("UNDER".equals(userChoice) && sum < 7) {
            isWin = true;
            finalMultiplier = 2.0 + bonusAdd;
        } else if ("OVER".equals(userChoice) && sum > 7) {
            isWin = true;
            finalMultiplier = 2.0 + bonusAdd;
        } else if ("SEVEN".equals(userChoice) && sum == 7) {
            isWin = true;
            finalMultiplier = 5.0 + bonusAdd;
        }

        // 5. 결과 정산
        int earnedGold = -betAmount;
        if (isWin) {
            earnedGold = (int) (betAmount * finalMultiplier);
            // 이겼을 경우 차감된 금액에 당첨금을 더함
            us.setCurrentGold(us.getCurrentGold() + earnedGold);
        }

        // 메시지 생성
        String resultMsg = generateFantasyMessage(isWin, earnedGold, userChoice);

        // 6. 상태 업데이트 및 파일 저장 (필수!)
        gambleStatus.setBetAmount(betAmount);
        gambleStatus.setUserChoice(userChoice); // [추가] 유저가 뭘 선택했는지 저장해야 함!
        gambleStatus.setResultMessage(resultMsg);
        gambleStatus.setDiceResults(Arrays.asList(dice1, dice2));
        gambleStatus.setWin(isWin);
        gambleStatus.setEarnedGold(earnedGold);
        gambleStatus.setStep("RESULT");

        userFileRepository.saveUserStatus(us);
        gambleFileRepository.saveGambleStatus(gambleStatus);
        return resultMsg;
    }

    /**
     * 판타지 스타일의 결과 메시지 생성
     */
    private String generateFantasyMessage(boolean isWin, int earnedGold, String userChoice) {
        Random rnd = new Random();
        double msgRoll = rnd.nextDouble(); // 0.0 ~ 1.0 사이 확률

        if (isWin) {
            // 1. Lucky 7 전용 메시지 (배당이 크므로 고정 혹은 높은 확률로 노출)
            if ("SEVEN".equals(userChoice)) {
                return "✨ [기적의 예언] 당신의 눈에 무엇이 보인 건가? 주사위가 마치 조종당하듯 7에 멈췄군! 이 황금빛 보상(" + earnedGold + "G)을 기꺼이 누리게!";
            }

            // 2. 일반 승리 메시지 (확률 기반 분기)
            if (msgRoll < 0.3) {
                return "🏆 [전설의 행운] 주사위의 신이 당신의 손을 들어주었군! 짤랑거리는 금화 소리가 들리나? 자그마치 " + earnedGold + "G의 당첨금일세!";
            } else if (msgRoll < 0.7) {
                return "🎲 [운명의 적중] 오오...! 이 숫자가 나올 줄이야! 운명의 실타래가 당신에게 유리하게 꼬였군. 주머니가 묵직해질 " + earnedGold + "G를 챙기게나.";
            } else {
                return "💰 [탐욕의 결실] 눈앞의 황금이 당신을 선택했군. 주사위 눈이 당신의 야망에 응답했네. 자, 여기 " + earnedGold + "G를 받게나.";
            }

        } else {
            // 3. 실패 메시지 (확률 기반 분기)
            if (msgRoll < 0.2) {
                return "💀 [죽음의 눈] 운명의 여신은 변덕쟁이지... 오늘은 자네를 아주 처참하게 외면한 모양이야. 주머니가 가벼워졌군, 쯧쯧.";
            } else if (msgRoll < 0.6) {
                return "🌑 [어둠 속의 패배] 주사위가 차가운 바닥에 무정하게 멈췄네. 자네의 금화는 이제 내 주머니로 들어왔군. 다음 기회를 노려보게나.";
            } else {
                return "🕯️ [꺼져가는 희망] 허망한 꿈이었나... 숫자는 무정하게 비껴갔군. 지하 도박장에선 흔한 일이지, 안 그런가?";
            }
        }
    }

    /**
     * [BLACKJACK] - DEAL 단계 (게임 시작)
     */
    public String startBlackjack(int betAmount) {
        UserStatus us = userFileRepository.findGameUser();
        GambleStatus status = gambleFileRepository.findGambleStatus();

        // 1. 유효성 검사: 골드 부족 여부 및 현재 상태 확인
        if (us.getCurrentGold() < betAmount) {
            return "보유한 골드가 부족하여 베팅할 수 없습니다.";
        }
        if (betAmount <= 0) {
            return "베팅 금액은 0보다 커야 합니다.";
        }
        if (!"BET".equals(status.getStep())) {
            return "이미 게임이 진행 중이거나 종료되었습니다.";
        }

        // 2. 골드 차감 및 베팅 설정
        us.setCurrentGold(us.getCurrentGold() - betAmount);
        status.setBetAmount(betAmount);
        status.setStep("PLAYING");

        // 플레이어 1장 -> 딜러 1장 순서로 분배
        status.getPlayerHand().add(drawCard(status));
        status.getDealerHand().add(drawCard(status));
        status.getPlayerHand().add(drawCard(status));
        status.getDealerHand().add(drawCard(status));

        // 1. 점수 계산
        int pTotal = calculateBlackjackSum(status.getPlayerHand());
        int dTotal = calculateBlackjackSum(status.getDealerHand());
        status.setPlayerTotal(pTotal);
        status.setDealerTotal(dTotal);

        // 2. 시작 즉시 종료 조건 체크 (Blackjack 체크)
        if (pTotal == 21 || dTotal == 21) {
            status.setStep("RESULT"); // 즉시 결과 단계로 이동

            if (pTotal == 21 && dTotal == 21) {
                // 둘 다 블랙잭 (비김)
                status.setWin(false);
                status.setEarnedGold(0);
                us.setCurrentGold(us.getCurrentGold() + betAmount); // 배팅금 돌려줌
                status.setResultMessage("🃏 [무승부] 당신과 딜러 모두 블랙잭입니다! 배팅금을 돌려받습니다.");
            } else if (pTotal == 21) {
                // 플레이어만 블랙잭 (승리)
                double bonus = 1.5 + (us.getLifeStats().getGambleMultiplierBonus() / 100.0);
                int reward = (int) (betAmount * bonus);
                status.setWin(true);
                status.setEarnedGold(reward);
                us.setCurrentGold(us.getCurrentGold() + reward);
                status.setResultMessage("✨ [블랙잭!] 완벽한 승리입니다! 딜러가 당황하며 " + reward + "G를 건넵니다.");
            } else {
                // 딜러만 블랙잭 (패배)
                status.setWin(false);
                status.setEarnedGold(-betAmount);
                status.setResultMessage("💀 [딜러 블랙잭] 딜러가 처음부터 블랙잭을 완성했습니다... 운이 없었군요.");
            }
        } else {
            // 블랙잭이 없으면 평소대로 진행
            status.setStep("PLAYING");
        }

        // 3. 저장
        userFileRepository.saveUserStatus(us);
        gambleFileRepository.saveGambleStatus(status);

        return status.getResultMessage() != null ? status.getResultMessage() : "HIT or STAY??";
    }

    /**
     * 카드 한 장 뽑기 헬퍼 (Service 내부에 위치)
     */
    private String drawCard(GambleStatus status) {
        if (status.getDeck() == null || status.getDeck().isEmpty()) {
            status.setDeck(status.createNewDeck());
        }
        return status.getDeck().remove(0);
    }

    public int calculateBlackjackSum(List<String> hand) {
        int sum = 0;
        int aceCount = 0;

        for (String card : hand) {
            // 문양(첫글자) 제외하고 숫자만 파싱 (예: "♠A" -> "A", "♣10" -> "10")
            String rank = card.substring(1);

            if (rank.equals("A")) {
                aceCount++;
                sum += 11;
            } else if (rank.equals("J") || rank.equals("Q") || rank.equals("K")) {
                sum += 10;
            } else {
                sum += Integer.parseInt(rank);
            }
        }

        // 21을 넘으면 에이스를 1로 취급
        while (sum > 21 && aceCount > 0) {
            sum -= 10;
            aceCount--;
        }
        return sum;
    }

    /**
     * [BLACKJACK] - HIT (카드 한 장 더 받기)
     */
    public String hitBlackjack() {
        GambleStatus status = gambleFileRepository.findGambleStatus();

        // 1. 상태 검증
        if (!"PLAYING".equals(status.getStep())) {
            return "카드를 받을 수 있는 상태가 아닙니다.";
        }

        // 2. 카드 지급 및 점수 계산
        status.getPlayerHand().add(drawCard(status));
        int pTotal = calculateBlackjackSum(status.getPlayerHand());
        status.setPlayerTotal(pTotal);

        // 3. 버스트(Bust) 체크
        if (pTotal > 21) {
            status.setStep("RESULT");
            status.setWin(false);
            status.setEarnedGold(-status.getBetAmount());
            status.setResultMessage("💥 [버스트] 21점을 초과했습니다! 패배하셨습니다.");
        } else if (pTotal == 21) {
            // 21점이면 자동으로 STAY 처리하여 편의성 제공
            return stayBlackjack();
        }

        gambleFileRepository.saveGambleStatus(status);
        return "카드를 한 장 더 받았습니다. (현재 점수: " + pTotal + ")";
    }

    /**
     * [BLACKJACK] - STAY (멈추고 결과 확인)
     */
    public String stayBlackjack() {
        GambleStatus status = gambleFileRepository.findGambleStatus();
        UserStatus us = userFileRepository.findGameUser();

        if (!"PLAYING".equals(status.getStep())) return "잘못된 접근입니다.";

        status.setPlayerStood(true); // 플레이어가 멈췄음을 표시 (UI에서 딜러 카드 공개용)

        // 1. 딜러의 턴 (17점 이상이 될 때까지 카드를 뽑음)
        int dTotal = status.getDealerTotal();
        while (dTotal < 17) {
            status.getDealerHand().add(drawCard(status));
            dTotal = calculateBlackjackSum(status.getDealerHand());
        }
        status.setDealerTotal(dTotal);
        status.setStep("RESULT");

        // 2. 승패 판정
        int pTotal = status.getPlayerTotal();
        int bet = status.getBetAmount();
        double bonus = 2.0 + (us.getLifeStats().getGambleMultiplierBonus() / 100.0); // 기본 2배당

        if (dTotal > 21) {
            // 딜러 버스트
            processWin(status, us, (int)(bet * bonus), "🏆 [딜러 버스트] 딜러가 21점을 넘었습니다! 승리하셨습니다.");
        } else if (pTotal > dTotal) {
            // 플레이어 승리
            processWin(status, us, (int)(bet * bonus), "🏆 [승리] 딜러보다 높은 점수입니다! " + (int)(bet * bonus) + "G를 획득했습니다.");
        } else if (pTotal < dTotal) {
            // 딜러 승리
            status.setWin(false);
            status.setEarnedGold(-bet);
            status.setResultMessage("💀 [패배] 딜러의 점수가 더 높습니다. 다음 기회를 노리세요.");
        } else {
            // 무승부 (Push)
            us.setCurrentGold(us.getCurrentGold() + bet);
            status.setWin(false);
            status.setEarnedGold(0);
            status.setResultMessage("🃏 [무승부] 딜러와 점수가 같습니다. 배팅금을 돌려받습니다.");
        }

        userFileRepository.saveUserStatus(us);
        gambleFileRepository.saveGambleStatus(status);
        return status.getResultMessage();
    }

    /**
     * 승리 처리 공통화 (더블 다운 대비)
     */
    private void processWin(GambleStatus status, UserStatus us, int reward, String msg) {
        status.setWin(true);
        status.setEarnedGold(reward);
        us.setCurrentGold(us.getCurrentGold() + reward);
        status.setResultMessage(msg);
    }

    /**
     * [BLACKJACK] - DOUBLE DOWN
     */
    public String doubleDownBlackjack() {
        GambleStatus status = gambleFileRepository.findGambleStatus();
        UserStatus us = userFileRepository.findGameUser();

        // 1. 상태 및 자금 검증
        if (!"PLAYING".equals(status.getStep())) return "잘못된 접근입니다.";
        if (us.getCurrentGold() < status.getBetAmount()) {
            return "골드가 부족하여 더블 다운을 할 수 없습니다!";
        }

        // 2. 추가 베팅금 차감 및 베팅액 갱신
        us.setCurrentGold(us.getCurrentGold() - status.getBetAmount());
        status.setBetAmount(status.getBetAmount() * 2);
        userFileRepository.saveUserStatus(us);

        // 3. 딱 한 장 더 받기
        status.getPlayerHand().add(drawCard(status));
        int pTotal = calculateBlackjackSum(status.getPlayerHand());
        status.setPlayerTotal(pTotal);

        // 4. 결과 처리 (버스트면 즉시 종료, 아니면 STAY와 동일하게 진행)
        if (pTotal > 21) {
            status.setStep("RESULT");
            status.setWin(false);
            status.setEarnedGold(-status.getBetAmount());
            status.setResultMessage("💥 [더블다운 실패] 21점을 초과했습니다! (최종 점수: " + pTotal + ")");
            gambleFileRepository.saveGambleStatus(status);
            return status.getResultMessage();
        } else {
            // 버스트가 아니면 저장 후 바로 stay 로직 수행
            gambleFileRepository.saveGambleStatus(status);
            return stayBlackjack();
        }
    }
}