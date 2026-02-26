package com.example.demo.service;

import java.text.DecimalFormat;
import com.example.demo.domain.meta.GrowthMeta;
import com.example.demo.domain.meta.StatMeta;
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
import java.util.stream.Collectors;

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
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();
        GameStatus gs = gameFileRepository.findGameStatus();
        DecimalFormat df = new DecimalFormat("#,###");

        if (ts.isTaxPaid()) return "이미 이번 주기의 세금을 납부했습니다.";

        if (us.getCurrentGold() < ts.getCurrentTax()) {
            return "골드가 부족합니다! (부족: " + df.format(ts.getCurrentTax() - us.getCurrentGold()) + " G)";
        }

        int amount = ts.getCurrentTax(); // 메시지용 기록

        // 공통 로직 호출
        processTaxPayment(us, ts);

        saveAll(us, ts, gs);

        String message = df.format(amount) + " G를 세금으로 납부했습니다. (다음 예정: " + df.format(ts.getCurrentTax()) + " G)";
        return checkTurnAndTax(message);
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
     * 사용자의 선택에 따른 계통별 스탯 수련을 수행합니다.
     * @param type UI 버튼에서 전달된 훈련 종류 (육신, 기민, 정신, 감각)
     * @return 수련 결과 및 스탯 상승치 메시지
     */
    public String performTrain(String type) {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();

        int trainCost = 25;
        if (us.getCurrentStamina() < trainCost) return "스태미나가 부족합니다! (필요: " + trainCost + ")";

        // 2. UI 한글 타입을 데이터의 영문 카테고리로 변환
        // 로그에 찍힌 [PHYSIQUE, SPIRIT, AGILITY, PERCEPTION]와 매핑합니다.
        String categoryKey = switch (type) {
            case "육신" -> "PHYSIQUE";
            case "기민" -> "AGILITY";
            case "정신" -> "SPIRIT";
            case "감각" -> "PERCEPTION";
            default -> type; // 이미 영문이거나 잘못된 값일 경우 그대로 전달
        };

        // 3. Meta 데이터 필터링 (가져온 categoryKey와 비교)
        List<Integer> targetIds = gameDataManager.getStatMetaMap().values().stream()
                .filter(meta -> categoryKey.equals(meta.getCategory()))
                .map(StatMeta::getId)
                .collect(Collectors.toList());

        // [중요] 리스트가 비어있으면 뒤에서 랜덤 에러가 발생하므로 여기서 차단
        if (targetIds.isEmpty()) {
            System.out.println("매칭 실패: UI입력[" + type + "] -> 변환키[" + categoryKey + "]");
            return "수련 가능한 스탯을 찾을 수 없습니다. (계통: " + type + ")";
        }

        // 4. 대상 스탯 중 랜덤하게 하나 선택 (이제 안전함)
        int randomStatId = targetIds.get(new Random().nextInt(targetIds.size()));
        String statName = gameDataManager.getStatMetaMap().get(randomStatId).getName();

        // 5. 잠재력(Potential) 가중치 기반 성장치 계산
        int growthId = us.getPotentials().getOrDefault(randomStatId, 7);
        GrowthMeta growthMeta = gameDataManager.getGrowthMetaMap().get(growthId);

        // 가중치 곱 적용 후 반올림 (최소값 1)
        double weight = growthMeta.getWeight();
        double baseValue = 1.2;
        int finalGain = (int) Math.max(1, Math.round(baseValue * weight));

        // 6. 실제 데이터 반영
        Map<Integer, Integer> baseStats = us.getBaseStats();
        int currentVal = baseStats.getOrDefault(randomStatId, 0);
        baseStats.put(randomStatId, currentVal + finalGain);

        // 자원 소모
        us.setCurrentStamina(us.getCurrentStamina() - trainCost);
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);

        // 7. 스탯 재계산 및 저장
        statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());
        saveAll(us, ts, gs);

        // UI 컨셉에 맞춘 결과 메시지
        String message = String.format("🌌 [%s] 계통 수련 완료!\n [%s] 스탯이 %d 상승했습니다.",
                type, statName, finalGain, weight);

        return checkTurnAndTax(message);
    }

    /**
     * 실제 세금 차감 및 다음 세율 계산 (공통 로직)
     */
    private void processTaxPayment(UserStatus us, TownStatus ts) {
        int paidTax = ts.getCurrentTax();

        // 1. 골드 차감
        us.setCurrentGold(us.getCurrentGold() - paidTax);

        // 2. 납부 상태 업데이트
        ts.setTaxPaid(true);

        // 3. 다음 세금 계산: 10% 복리 적용 (상한선 1,000,000G)
        int nextTax = (int) (paidTax * 1.1);
        ts.setCurrentTax(Math.min(nextTax, 1000000));

        log.info(">>> 납세 완료: 납부액 {}, 다음 세금 {}", paidTax, ts.getCurrentTax());
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

        gs.addLog(actionMessage);
        ts.setDay(ts.getDay() + 1);

        // 3. 세금 징수 체크 (1년 = 360일 주기)
        if (ts.getDay() % 360 == 0) {
            if (ts.isTaxPaid()) {
                // [추가] 이미 수동으로 납부했다면 메시지만 출력하고 통과
                String taxMsg = "\n✅ 올해 세금은 이미 납부되었습니다.";
                gs.addLog(taxMsg);
                actionMessage += taxMsg;
            } else if (us.getCurrentGold() >= ts.getCurrentTax()) {
                // [수정] 자동 징수 시에도 공통 로직 사용
                int paidTax = ts.getCurrentTax();
                processTaxPayment(us, ts);

                String taxMsg = String.format("\n💰 [연말 세금 징수] %d년차 세금 %d G를 납부했습니다. (다음 세율 반영: %d G)",
                        (ts.getDay() / 360), paidTax, ts.getCurrentTax());
                gs.addLog(taxMsg);
                actionMessage += taxMsg;
            } else {
                String deathMsg = String.format("\n🚨 [처형] %d년차 세금 %d G를 내지 못했습니다...", (ts.getDay() / 360), ts.getCurrentTax());
                us.setCurrentHp(0);
                us.setCurrentMp(0);
                us.setCurrentStamina(0);
                us.setCurrentGold(0);
                gs.addLog(deathMsg);
                saveAll(us, ts, gs);
                return "GameOver:EXECUTED";
            }
        }

        if (ts.getDay() % 360 == 1) {
            ts.setTaxPaid(false);
        }

        // 4. 던전 개방 알림 유지...
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