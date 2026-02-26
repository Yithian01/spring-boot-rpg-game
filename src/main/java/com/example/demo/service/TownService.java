package com.example.demo.service;

import java.text.DecimalFormat;
import com.example.demo.domain.meta.GrowthMeta;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.TownStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class TownService {

    private final GameFileRepository gameFileRepository;
    private final UserFileRepository userFileRepository;
    private final TownFileRepository townFileRepository;
    private final StatCalculationService statCalculationService;
    private final GameDataManager gameDataManager;

    /**
     * 게임 저장 & 로그 저장 용도
     * @param us 플레이어 정보
     * @param ts 마을 정보
     * @param gs 게임 정보(로그 저장용)
     */
    private void saveAll(UserStatus us, TownStatus ts, GameStatus gs) {
        userFileRepository.saveUserStatus(us);
        townFileRepository.saveTownStatus(ts);
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * 육체 스탯 기반 노동
     * @return 노동 결과 반환
     */
    public String performWork() {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        if (ts.getCurrentTurn() <= 0) return "남은 턴이 없습니다. 던전에 입장하세요!";

        int staminaCost = statCalculationService.calculateWorkStaminaCost(us.getBaseStats());
        if (us.getCurrentStamina() < staminaCost) return "스태미나가 부족합니다! (필요: " + staminaCost + ")";

        int earnedGold = statCalculationService.calculateEarnedGold(us.getBaseStats());
        earnedGold += new Random().nextInt(11);

        us.setCurrentGold(us.getCurrentGold() + earnedGold);
        us.setCurrentStamina(us.getCurrentStamina() - staminaCost);
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);

        String resultMsg = "⛏️ 일당으로 " + earnedGold + " G를 벌었습니다! (남은 턴: " + ts.getCurrentTurn() + ")";
        saveAll(us, ts, gs);
        return checkTurnAndTax(resultMsg);
    }

    /**
     * 재생 스탯 기반 휴식 한다.
     * @return 휴식 결과 반환
     */
    public String performRest() {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        if (ts.getCurrentTurn() <= 0) return "남은 턴이 없어 휴식할 수 없습니다.";

        int hpRecovery = statCalculationService.calculateHpRestoration(us);
        int mpRecovery = statCalculationService.calculateMpRestoration(us);
        int stRecovery = statCalculationService.calculateStRestoration(us);

        us.setCurrentHp(Math.min(us.getCombatStats().getMaxHp(), us.getCurrentHp() + hpRecovery));
        us.setCurrentMp(Math.min(us.getCombatStats().getMaxMp(), us.getCurrentMp() + mpRecovery));
        us.setCurrentStamina(Math.min(us.getCombatStats().getMaxStamina(), us.getCurrentStamina() + stRecovery));
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);

        saveAll(us, ts, gs);
        return checkTurnAndTax("🛌 편안한 휴식을 취했습니다. (HP/MP/ST 회복)");
    }

    /**
     * 현재 세금 납부 후 10% 인상
     * @return 납부 결과 반환
     */
    public String payTax() {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        // 숫자에 콤마를 찍기 위한 포맷터 선언
        DecimalFormat df = new DecimalFormat("#,###");

        if (ts.isTaxPaid()) return "이미 세금을 납부했습니다.";

        int currentTax = ts.getCurrentTax();
        int currentGold = us.getCurrentGold();

        if (currentGold < currentTax) {
            int leftTax = currentTax - currentGold;
            // 부족한 금액에도 콤마 적용
            return "골드가 부족합니다! (부족: " + df.format(leftTax) + " G)";
        }

        int amountToPay = currentTax;
        us.setCurrentGold(currentGold - amountToPay);
        ts.setTaxPaid(true);

        saveAll(us, ts, gs);

        // 납부 금액에 콤마 적용하여 리턴
        return checkTurnAndTax(df.format(amountToPay) + " G를 세금으로 납부했습니다.");
    }

    /**
     * 행운 기반 스탯에 따라 도박
     * @param bettingGold 도박할 금액
     * @return 도박 결과 반환
     */
    public String performGamble(int bettingGold) {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        if (ts.getCurrentTurn() <= 0) return "남은 턴이 없습니다.";

        // 1. 골드 부족 체크
        if (us.getCurrentGold() < bettingGold) {
            return "보유한 골드가 부족합니다!";
        }

        // 2. 확률 계산
        double winRate = statCalculationService.calculateGambleWinRate(us.getBaseStats());
        double randomValue = new Random().nextDouble() * 100; // 0.0 ~ 100.0

        String resultMessage;
        // 턴 소모
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);

        if (randomValue <= winRate) {
            // 승리!
            double multiplier = statCalculationService.calculateGambleMultiplier(us.getBaseStats());
            int reward = (int) (bettingGold * multiplier);
            us.setCurrentGold(us.getCurrentGold() + (reward - bettingGold)); // 배팅금 제외 순수익 추가
            resultMessage = "🎲 도박 성공! " + reward + " G를 획득했습니다! (승률: " + String.format("%.1f", winRate) + "%)";
        } else {
            // 패배...
            us.setCurrentGold(us.getCurrentGold() - bettingGold);
            resultMessage = "💸 도박 실패... " + bettingGold + " G를 잃었습니다. (승률: " + String.format("%.1f", winRate) + "%)";
        }

        saveAll(us, ts, gs);
        return checkTurnAndTax(resultMessage);
    }

    /**
     * 특정 타입(근력/민첩/지능)에 따른 스탯 수련
     * @param type 훈련 종류 (STRENGTH, AGILITY, INTELLIGENCE)
     * @return 수련 결과 메시지
     */
    /**
     * 스탯 수련 (Potentials 등급 기반 성장)
     */
    public String performTrain(String type) {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        // 1. 기본 검증
        if (ts.getCurrentTurn() <= 0) return "남은 턴이 없습니다.";

        int trainCost = 25; // 수련은 노동보다 힘듭니다.
        if (us.getCurrentStamina() < trainCost) return "스태미나가 부족합니다! (필요: " + trainCost + ")";

        // 2. 훈련 타입별 스탯 ID 리스트 (주신 24개 스탯 분류)
        List<Integer> targetIds = switch (type) {
            case "STRENGTH" -> List.of(1, 2, 3, 10, 11, 12); // 체력/근력 관련
            case "AGILITY" -> List.of(6, 7, 15, 16, 17, 20, 21, 22, 23, 24); // 민첩/치명/회피 관련
            case "INTELLIGENCE" -> List.of(4, 5, 8, 9, 13, 14, 18, 19); // 마나/마법 관련
            default -> List.of();
        };

        // 3. 대상 스탯 중 랜덤하게 하나 선택
        int randomStatId = targetIds.get(new Random().nextInt(targetIds.size()));
        String statName = gameDataManager.getStatMetaMap().get(randomStatId).getName();

        // 4. 잠재력(Potential)에 따른 성장치 계산
        // potentials 맵에서 해당 스탯의 성장 등급 ID를 가져옴 (기본값 7: F등급)
        int growthId = us.getPotentials().getOrDefault(randomStatId, 7);
        GrowthMeta growthMeta = gameDataManager.getGrowthMetaMap().get(growthId);

        // 성장 등급이 높을수록(S=1, A=2...) 더 많이 오를 확률 부여
        // 예: S등급은 3~5, F등급은 1~2 상승
        int baseGain = switch (growthId) {
            case 1 -> 3 + new Random().nextInt(3); // S: 3~5
            case 2 -> 2 + new Random().nextInt(3); // A: 2~4
            case 3, 4 -> 1 + new Random().nextInt(3); // B, C: 1~3
            default -> 1 + new Random().nextInt(2); // D, E, F: 1~2
        };

        // 5. 실제 데이터 반영
        Map<Integer, Integer> baseStats = us.getBaseStats();
        int currentVal = baseStats.getOrDefault(randomStatId, 0);
        baseStats.put(randomStatId, currentVal + baseGain);

        // 자원 소모
        us.setCurrentStamina(us.getCurrentStamina() - trainCost);
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);

        // 6. 스탯 변화가 전투 능력치에 영향을 주므로 재계산 필요
        statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());

        saveAll(us, ts, gs);

        String message = String.format("🧘 [%s] 수련 완료!\n[%s] 스탯이 %d 상승했습니다. (성장등급: %s)",
                translateType(type), statName, baseGain, growthMeta.getGrade());
        return checkTurnAndTax(message);
    }

    private String translateType(String type) {
        return Map.of(
                "STRENGTH", "근력",
                "AGILITY", "민첩",
                "INTELLIGENCE", "지능")
                .getOrDefault(type, type);
    }

    /**
     * 공통 턴 종료 및 세금 체크 로직
     * 초기 세금 900,000G
     * 1.1 -> 10% 복리 적용
     * 1,000,000G 상한선
     * - 마을 행동 1회 = 1일 경과
     * - 30일 주기: 던전 개방
     * - 360일 주기: 세금 징수 (못 내면 처형)
     */
    private String checkTurnAndTax(String actionMessage) {
        TownStatus ts = townFileRepository.findTownStatus();
        UserStatus us = userFileRepository.findGameUser();
        GameStatus gs = gameFileRepository.findGameStatus();

        // 1. 로그 기록
        gs.addLog(actionMessage);

        // 2. 날짜 경과 (마을 행동 1번 = 1일 경과)
        ts.setDay(ts.getDay() + 1);

        // 3. 세금 징수 체크 (1년 = 360일 주기)
        if (ts.getDay() % 360 == 0) {
            if (us.getCurrentGold() >= ts.getCurrentTax()) {
                // [세금 납부 성공]
                int paidTax = ts.getCurrentTax();
                us.setCurrentGold(us.getCurrentGold() - paidTax);

                // 납부 상태 업데이트 (UI 표시용)
                ts.setTaxPaid(true);

                // 다음 해 세금 계산: 10% 복리 적용 (상한선 1,000,000G)
                int nextTax = (int) (paidTax * 1.1);
                ts.setCurrentTax(Math.min(nextTax, 1000000));

                String taxMsg = String.format("\n💰 [연말 세금 징수] %d년차 세금 %d G를 납부했습니다. (다음 세율 반영: %d G)",
                        (ts.getDay() / 360), paidTax, ts.getCurrentTax());
                gs.addLog(taxMsg);
                actionMessage += taxMsg;
            } else {
                // [세금 미납 - 처형 엔딩]
                String deathMsg = String.format("\n🚨 [처형] %d년차 세금 %d G를 내지 못했습니다. 국왕의 기사단이 들이닥쳐 당신을 처형했습니다...",
                        (ts.getDay() / 360), ts.getCurrentTax());

                // 시스템적으로 완전히 사망 처리
                us.setCurrentHp(0);
                us.setCurrentMp(0);
                us.setCurrentStamina(0);
                // 필요 시 보유 골드도 몰수
                us.setCurrentGold(0);

                gs.addLog(deathMsg);
                saveAll(us, ts, gs);

                // 프론트엔드에서 GameOver 화면으로 튕기기 위한 키워드 반환
                return "GameOver:EXECUTED";
            }
        } else {
            // 세금 납부일이 아닌 평소에는 납부 상태를 false로 유지 (신규 연도 시작 시 초기화 개념)
            if (ts.getDay() % 360 == 1) {
                ts.setTaxPaid(false);
            }
        }

        // 4. 던전 개방 알림 (30일 주기)
        if (ts.getDay() % 30 == 0) {
            String dungeonMsg = "\n🌌 [차원문] 마력이 소용돌이칩니다. 던전 입장이 가능해졌습니다!";
            gs.addLog(dungeonMsg);
            actionMessage += dungeonMsg;
        }

        saveAll(us, ts, gs);
        return actionMessage;
    }
    /**
     * 다음 날로 진행 (턴 회복 및 납부 상태 초기화)
     */
    private void nextDay(TownStatus town) {
        town.setDay(town.getDay() + 1);
        town.setCurrentTurn(town.getMaxTurn()); // 턴 풀 회복
        town.setTaxPaid(false); // 다시 미납 상태로
        // payTax에서 인상하지 않았다면 여기서 인상 로직을 넣어도 됩니다.
    }
}