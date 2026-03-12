package com.example.demo.service;

import java.text.DecimalFormat;
import com.example.demo.domain.meta.GrowthMeta;
import com.example.demo.domain.meta.LifeStats;
import com.example.demo.domain.meta.SkillMeta;
import com.example.demo.domain.meta.StatMeta;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.ItemInstance;
import com.example.demo.domain.save.TownStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.dto.RandomSkillCardDto;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.ShopInstanceRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TownService {

    private final GameFileRepository gameFileRepository;
    private final UserFileRepository userFileRepository;
    private final TownFileRepository townFileRepository;
    private final StatCalculationService statCalculationService;
    private final ShopService shopService;
    private final InventoryService inventoryService;
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

    public String performWork(String workType) {
        // 1. 데이터 로드
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        TownStatus ts = townFileRepository.findTownStatus();
        LifeStats ls = us.getLifeStats();

        Map<Integer, Integer> stats = us.getFinalStats();

        // 배율 계산 (실수 연산)
        double staminaMultiplier = 1.0 - (ls.getWorkStaminaBonus() / 100.0);
        double goldBonusMultiplier = 1.0 + (ls.getWorkGoldBonus() / 100.0); // 보수 금액 증가 15% -> 1.15

        int staminaCost = 0;
        String jobName;
        int basePay;
        double avgType;
        double multiplier;

        // 3. 업무 설정 매핑 (basePay에서 보너스를 미리 곱하지 말고, 순수 기본값만 세팅)
        switch (workType) {
            case "PHYSIQUE" -> {
                jobName = "🏗️ 성벽 보수 작업";
                basePay = 25;
                avgType = gameDataManager.getCategoryAverage(stats, "PHYSIQUE");
                multiplier = 0.8;
                staminaCost = (int) (10 * staminaMultiplier);
            }
            case "SPIRIT" -> {
                jobName = "📜 마법 도서관 서기";
                basePay = 10;
                avgType = gameDataManager.getCategoryAverage(stats, "SPIRIT");
                multiplier = 1.1;
                staminaCost = (int) (15 * staminaMultiplier);
            }
            case "AGILITY" -> {
                jobName = "🏃 긴급 서신 배달";
                basePay = 15;
                avgType = gameDataManager.getCategoryAverage(stats, "AGILITY");
                multiplier = 0.7;
                staminaCost = (int) (5 * staminaMultiplier);
            }
            case "PERCEPTION" -> {
                jobName = "🔍 유물 파편 분류"; // 화면과 명칭 통일
                basePay = 5;
                avgType = gameDataManager.getCategoryAverage(stats, "PERCEPTION");
                multiplier = 0.9;
                staminaCost = (int) (10 * staminaMultiplier);
            }
            default -> { return "잘못된 업무 타입입니다."; }
        }

        // 2. 스태미나 체크
        if (us.getCurrentStamina() < staminaCost) {
            return "스태미나가 부족합니다! (필요: " + staminaCost + ")";
        }

        // 4. 골드 계산
        // [중요] statCalculationService 결과값에 최종적으로 goldBonusMultiplier를 곱해줍니다.
        int earnedGold = (int) (statCalculationService.calculateWorkGold(basePay, (int) avgType, multiplier) * goldBonusMultiplier);

        // [특수] PERCEPTION 타입 대박 로직 (오타 수정: EXPERT -> PERCEPTION)
        // 확률 또한 화면과 일치시키기 위해 (10 + 보너스확률) 적용
        double totalSuccessChance = (10.0 + ls.getWorkSuccessBonus()) / 100.0;
        if ("PERCEPTION".equals(workType) && new Random().nextDouble() < totalSuccessChance) {
            earnedGold *= 2; // 대박 시 최종 금액의 2배
            jobName = "🌟 희귀 유물 발견!";
        }

        // 5. 보상 지급 및 상태 업데이트
        us.setCurrentGold(us.getCurrentGold() + earnedGold);
        us.setCurrentStamina(us.getCurrentStamina() - staminaCost);
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);

        shopService.townStoreRestock();

        String resultMsg = String.format("%s 업무를 완료하여 %d G를 벌었습니다! (남은 턴: %d)",
                jobName, earnedGold, ts.getCurrentTurn());

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

        // if (ts.getCurrentTurn() <= 0) return "남은 턴이 없어 휴식할 수 없습니다.";

        int hpRecovery = statCalculationService.calculateHpRestoration(us);
        int mpRecovery = statCalculationService.calculateMpRestoration(us);
        int stRecovery = statCalculationService.calculateStRestoration(us);

        us.setCurrentHp(Math.min(us.getCombatStats().getMaxHp(), us.getCurrentHp() + hpRecovery));
        us.setCurrentMp(Math.min(us.getCombatStats().getMaxMp(), us.getCurrentMp() + mpRecovery));
        us.setCurrentStamina(Math.min(us.getCombatStats().getMaxStamina(), us.getCurrentStamina() + stRecovery));
        ts.setCurrentTurn(ts.getCurrentTurn() - 1);
        shopService.townStoreRestock();

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

        // if (ts.getCurrentTurn() <= 0) return "남은 턴이 없습니다.";

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

        // 3. 해당 카테고리 스탯 1개 ID를 가져옴
        int randomStatId = gameDataManager.getStatIdByStatCategory(categoryKey);

        // 스탯ID = 1부터 시작
        if (randomStatId <= 0) {
            System.out.println("매칭 실패: UI입력[" + type + "] -> 변환키[" + categoryKey + "]");
            return "수련 가능한 스탯을 찾을 수 없습니다. (계통: " + type + ")";
        }

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
        shopService.townStoreRestock();


        // 7. 스탯 재계산 및 저장
        statCalculationService.refreshUserCombatStats(us, gameDataManager.getItemMetaMap());
        saveAll(us, ts, gs);

        String statName = gameDataManager.getStatMetaMap().get(randomStatId).getName();


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

    public void skillExtractionOptions(String instanceId) {
        ItemInstance stone = inventoryService.getMagicStone(instanceId);
        if (stone == null) throw new IllegalStateException("마석 정보가 없습니다.");

        // 먼저 차감(제거)
        inventoryService.consumeStone(stone);

        TownStatus town = townFileRepository.findTownStatus();
        town.getMagicStoneList().stream()
                .filter(ms -> instanceId.equals(ms.getInstanceId()))
                .findFirst()
                .ifPresent(targetStone -> {
                    int newQty = targetStone.getQuantity() - 1;
                    if (newQty <= 0) {
                        // 수량이 0 이하면 리스트에서 아예 제거
                        town.getMagicStoneList().remove(targetStone);
                    } else {
                        targetStone.setQuantity(newQty);
                    }
                });

        UserStatus us = userFileRepository.findGameUser();
        int stoneGrade = stone.getItemMetaId();
        int cardCount = (us.getTribeId() == 1) ? 4 : 3;

        // 등급 리스트 먼저 확정 (ex: [COMMON, UNCOMMON, COMMON])
        List<String> gradesToPull = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            gradesToPull.add(gameDataManager.rollSkillGrade(stoneGrade));
        }

        // 비복원 추출을 위한 임시 스킬 풀 준비 (메모리 원본 보호를 위해 새 리스트로 복사)
        Map<String, List<SkillMeta>> tempPools = new HashMap<>();
        for (String grade : gradesToPull.stream().distinct().toList()) {
            tempPools.put(grade, new ArrayList<>(gameDataManager.getSkillsByGrade(grade)));
        }

        List<RandomSkillCardDto> options = new ArrayList<>();
        Random rnd = new Random();

        for (String targetGrade : gradesToPull) {
            List<SkillMeta> currentPool = tempPools.get(targetGrade);

            // 만약 해당 등급 풀에 스킬이 부족하면 COMMON 등급에서 보충 (안전장치)
            if (currentPool.isEmpty()) {
                if (!tempPools.containsKey("COMMON")) {
                    tempPools.put("COMMON", new ArrayList<>(gameDataManager.getSkillsByGrade("COMMON")));
                }
                currentPool = tempPools.get("COMMON");
            }

            // 중복 방지: 리스트에서 하나를 무작위로 꺼내고(remove) 결과에 담기
            SkillMeta picked = currentPool.remove(rnd.nextInt(currentPool.size()));

            // 유저가 이미 배운 스킬인지 확인
            boolean alreadyLearned = us.getLearnedSkillIds().contains(picked.getId());
            int primaryStatId = picked.getStatScaling().keySet().stream()
                    .findFirst()
                    .orElse(0);

            options.add(RandomSkillCardDto.builder()
                    .id(picked.getId())
                    .name(picked.getName())
                    .icon(picked.getIcon())
                    .description(picked.getDescription())
                    .grade(picked.getGrade())

                    .turnCost(picked.getTurnCost())
                    .staminaCost(picked.getCost().getOrDefault("stamina", 0))
                    .mpCost(picked.getCost().getOrDefault("mp", 0))
                    .hpCost(picked.getCost().getOrDefault("hp", 0))

                    .type(picked.getType())
                    .element(picked.getEffect().getElement())
                    .requiredWeapons(picked.getRequiredWeapons())

                    .effectType(picked.getEffect().getType())
                    .statusName(gameDataManager.getStatusName(picked.getEffect().getStatus()))
                    .duration(picked.getEffect().getDuration())
                    .effectChance(picked.getEffect().getChance())

                    .scalingInfo(gameDataManager.createSkillScalingInfo(picked, us.getFinalStats()))
                    .modifierDetails(gameDataManager.createSkillModifierInfo(picked))

                    .alreadyLearned(alreadyLearned)
                    .bonusStatType(gameDataManager.getStatBaseCategory(primaryStatId)) // 스킬 메타에 정의된 대표 스탯 (육신, 기민 등)
                    .bonusStatValue(gameDataManager.calculateBonusValue(stoneGrade))
                    .build());
        }

        town.setSkillOptions(options);
        townFileRepository.saveTownStatus(town);
    }

    public void confirmSkillExtraction(String skillId) {
        int targetId = Integer.parseInt(skillId);
        SkillMeta skillMeta = gameDataManager.getSkillMetaMap().get(targetId);
        if (skillMeta == null) throw new IllegalStateException("존재하지 않는 스킬 데이터입니다.");

        TownStatus ts = townFileRepository.findTownStatus();
        UserStatus us = userFileRepository.findGameUser();
        GameStatus gs = gameFileRepository.findGameStatus();

        // 1. 이미 배운 스킬인지 확인
        boolean isAlreadyLearned = us.getLearnedSkillIds().contains(targetId);

        if (isAlreadyLearned) {
            // 2. 중복 시: TownStatus의 임시 옵션 리스트에서 해당 스킬의 보너스 정보를 찾음
            RandomSkillCardDto selectedOption = ts.getSkillOptions().stream()
                    .filter(opt -> opt.getId() == targetId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("선택한 스킬이 옵션 목록에 없습니다."));

            // 보너스 스탯 종류(ex: "육신")와 기본 보너스 수치(ex: 2) 가져오기
            String categoryKey = selectedOption.getBonusStatType();
            int baseBonusValue = selectedOption.getBonusStatValue();

            // 2-1. 수련 로직과 동일하게 해당 카테고리의 랜덤 스탯 결정
            int randomStatId = gameDataManager.getStatIdByStatCategory(categoryKey);
            String randomStatName = gameDataManager.getStatName(randomStatId);

            if (randomStatId > 0) {
                // 2-2. 잠재력 가중치 적용 계산
                int growthId = us.getPotentials().getOrDefault(randomStatId, 7);
                GrowthMeta growthMeta = gameDataManager.getGrowthMetaMap().get(growthId);
                double weight = (growthMeta != null) ? growthMeta.getWeight() : 1.0;

                // 최종 증가량 = (마석 보너스 기본치) * (잠재력 가중치)
                int finalGain = (int) Math.max(1, Math.round(baseBonusValue * weight));

                // 스탯 반영
                int currentVal = us.getBaseStats().getOrDefault(randomStatId, 0);
                us.getBaseStats().put(randomStatId, currentVal + finalGain);

                gs.addLog(String.format("영혼이 공명하여 [%s] 계통의 [%s]능력이 %d만큼 상승했습니다!",
                        categoryKey, randomStatName, finalGain));
            } else {
                gs.addLog("각인 보너스 스탯 대상을 찾을 수 없습니다.");
            }
        } else {
            // 3. 새로 배우는 경우
            us.getLearnedSkillIds().add(targetId);
            gs.addLog(String.format("새로운 기술 [%s]을(를) 성공적으로 습득했습니다!", skillMeta.getName()));
        }

        // 4. 공통 사후 처리: 옵션 초기화 및 데이터 저장
        ts.setSkillOptions(null); // 연성 완료 후 리스트 초기화
        userFileRepository.saveUserStatus(us);
        townFileRepository.saveTownStatus(ts);
        gameFileRepository.saveGameStatus(gs);
    }

}