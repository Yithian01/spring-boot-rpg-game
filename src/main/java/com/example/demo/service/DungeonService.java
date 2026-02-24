package com.example.demo.service;

import com.example.demo.domain.enums.LocationType;
import com.example.demo.domain.meta.MonsterMeta;
import com.example.demo.domain.save.*;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.GameRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DungeonService {

    private final GameRepository gameRepository;
    private final TownFileRepository townFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final UserFileRepository userFileRepository;
    private final StatCalculationService statCalculationService;
    private final GameDataManager gameDataManager;

    /**
     * 저장 함수
     */
    private void saveAll(UserStatus user, DungeonStatus ds) {
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
    }

    /**
     * 던전 진입 시 초기 설정 및 세이브 파일 생성
     */
    public void initDungeon() {
        // 1. 전체 게임 상태 변경 (마을 -> 던전)
        GameStatus gameStatus = gameRepository.findGameStatus();
        gameStatus.setLocation(LocationType.DUNGEON);
        gameStatus.setDungeonId(1); // 기본 던전 ID
        gameRepository.saveGameStatus(gameStatus);

        // 2. 마을 상태 업데이트 (날짜 경과 및 턴 회복)
        TownStatus townStatus = townFileRepository.findTownStatus();
        townStatus.setDay(townStatus.getDay() + 1);
        townStatus.setCurrentTurn(townStatus.getMaxTurn());
        townStatus.setTaxPaid(false);
        townFileRepository.saveTownStatus(townStatus);

        UserStatus user = userFileRepository.findGameUser();
        int initialMaxTurns = statCalculationService.calculateCombatTurns(user);

        // 3. ★ 던전 상태 파일 초기 생성 ★
        // 처음 진입 시에는 1층, 몬스터는 없는(null) 상태로 시작
        DungeonStatus newDungeonStatus = DungeonStatus.builder()
                .currentFloor(1)
                .dungeonId("GRAY_MINE") // 혹은 gameStatus에서 가져온 ID
                .progress(0) // 혹은 gameStatus에서 가져온 ID
                .activeMonster(null)    // 아직 조우 전
                .playerMaxTurns(initialMaxTurns)      // 전투 돌입 시 계산
                .playerRemainingTurns(0)
                .battleLogs(new ArrayList<>())
                .build();

        newDungeonStatus.getBattleLogs().add("어두운 던전에 발을 들였습니다... (1층)");

        dungeonFileRepository.saveDungeonStatus(newDungeonStatus);

        log.info(">>> 던전 파일 생성 완료 및 진입: Day {}, Floor 1", townStatus.getDay());
    }

    /**
     * [탐사하기] 버튼 클릭 시 호출
     * 몬스터를 조우하거나, 아무 일도 없거나, 탐사율 증가 가거나 결정
     */
    public void explore() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        UserStatus user = userFileRepository.findGameUser();

        // 1. 기본 비용 소모 (스태미나 감소 등)
        user.setCurrentStamina(Math.max(0, user.getCurrentStamina() - 3));

        // 2. 난수 생성 (0.0 ~ 1.0)
        double roll = Math.random();
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();

        // 3. 사건 판정 분기
        if (roll < 0.15) {
            // Case A: 함정 조우 (15% 확률)
            handleTrap(user, ds, stats);
        }
        else if (roll < 0.45) {
            // Case B: 몬스터 조우 (30% 확률)
            handleMonsterEncounter(ds);
        }
        else {
            // Case C: 무탈하게 탐사 성공 (55% 확률)
            handlePureExploration(ds, stats);
        }

        // 4. 상태 저장
        saveAll(user, ds);
    }

    /**
     * 함정 발동 시 이벤트
     * @param user
     * @param ds
     * @param stats
     */
    private void handleTrap(UserStatus user, DungeonStatus ds, Map<Integer, Integer> stats) {
        int intuition = stats.getOrDefault(24, 0); // 직관

        // 1. 회피 판정 (직관 스탯 기반)
        if (Math.random() < (intuition * 0.005)) {
            ds.addLog("<span style='color:#70db70;'>[함정]</span> 감각적으로 함정을 눈치채고 피해갔습니다.");
            return;
        }

        // 2. 피해 계산: 최대 체력의 20%
        int maxHp = user.getCombatStats().getMaxHp();
        int damage = (int) (maxHp * 0.2); // 20% 계산 (정수로 절삭)

        if (damage < 1) damage = 1;

        user.setCurrentHp(Math.max(0, user.getCurrentHp() - damage));

        ds.addLog("<span style='color:#ff4d4d;'>[함정]</span> 치명적인 함정을 밟았습니다! (HP -" + damage + ")");

        if (user.getCurrentHp() <= 0) {
            ds.addLog("<span style='color:#ff0000; font-weight:bold;'>정신이 아득해집니다... 더 이상 움직일 수 없습니다.</span>");
        }
    }

    /**
     * 몬스터 조우 (전투 발생)
     */
    private void handleMonsterEncounter(DungeonStatus ds) {
        // 1. 메타 데이터에서 몬스터 추첨
        MonsterMeta monsterMeta = gameDataManager.getRandomMonsterByFloor(ds.getCurrentFloor());

        // 2. 골드 랜덤 결정 (goldMin ~ goldMax)
        int rewardGold = monsterMeta.getGoldMin() +
                (int)(Math.random() * (monsterMeta.getGoldMax() - monsterMeta.getGoldMin() + 1));

        // 3. ActiveMonster 빌드 (실시간 전투용 스냅샷)
        ActiveMonster activeMonster = ActiveMonster.builder()
                .monsterId(monsterMeta.getId())
                .name(monsterMeta.getName())
                .tier(monsterMeta.getTier())
                .currentHp((int) monsterMeta.getStats().getMaxHp())
                .maxHp((int) monsterMeta.getStats().getMaxHp())
                .currentMp((int) monsterMeta.getStats().getMaxMp())
                .maxMp((int) monsterMeta.getStats().getMaxMp())
                .stats(monsterMeta.getStats()) // 복사본 전달
                .activeStatuses(new ArrayList<>())
                .build();
        ds.setActiveMonster(activeMonster);

        // 4. 유저 전투 턴 계산 (민첩 스탯 기반)
        UserStatus user = userFileRepository.findGameUser();
        int turns = statCalculationService.calculateCombatTurns(user);
        ds.setPlayerMaxTurns(turns);
        ds.setPlayerRemainingTurns(turns);

        // 5. 보상 예치 (전투 승리 시 지급용)
        ds.setPendingExp(monsterMeta.getExpReward());
        ds.setPendingGold(rewardGold);

        ds.addLog("<span style='color:#ff9f43;'>[전투]</span> " + activeMonster.getName() + "이(가) 나타났습니다!");
    }

    /**
     * 무탈하게 탐사 진행 (진척도 상승)
     */
    private void handlePureExploration(DungeonStatus ds, Map<Integer, Integer> stats) {
        // 1. 스탯 기반 탐사 효율 계산 (기본 5% ~ 최대 20% 등)
        int efficiency = statCalculationService.calculateExplorationEfficiency(stats);

        // 2. 진척도 업데이트 (최대 100)
        int currentProgress = ds.getProgress();
        int nextProgress = Math.min(100, currentProgress + efficiency);
        ds.setProgress(nextProgress);

        // 3. 로그 남기기
        ds.addLog("주변을 면밀히 조사하며 길을 찾았습니다. (진척도 +" + efficiency + "%)");

        // 4. 100% 달성 시 안내
        if (nextProgress >= 100) {
            ds.addLog("<span style='color:#ffd700; font-weight:bold;'>[알림] 이 층의 조사가 완료되었습니다! 다음 층으로 내려갈 수 있습니다.</span>");
        }
    }

    public void rest() {
        UserStatus user = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();

        if (user == null || user.getCurrentHp() <= 0) return;

        // 1. 자원 회복 처리 (기존 로직 유지)
        double restoreRate = 0.3;
        int hpGain = (int) (user.getCombatStats().getMaxHp() * restoreRate);
        int mpGain = (int) (user.getCombatStats().getMaxMp() * restoreRate);
        int stmGain = (int) (user.getCombatStats().getMaxStamina() * restoreRate);

        user.setCurrentHp(Math.min(user.getCombatStats().getMaxHp(), user.getCurrentHp() + hpGain));
        user.setCurrentMp(Math.min(user.getCombatStats().getMaxMp(), user.getCurrentMp() + mpGain));
        user.setCurrentStamina(Math.min(user.getCombatStats().getMaxStamina(), user.getCurrentStamina() + stmGain));

        ds.addLog(String.format("🛌 <b style='color:#70db70;'>휴식을 취했습니다.</b> (HP/MP/STA +30%%)"));

        // 2. 기습 판정 (Safety Rate 체크)
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();
        double safetyRate = statCalculationService.calculateRestSafetyRate(stats);

        // 주사위 굴리기 (0~100)
        double roll = Math.random() * 100;

        if (roll > safetyRate) {
            // [판정 실패] 기습 발생!
            ds.addLog("<span style='color:#ff4d4d; font-weight:bold;'>⚠️ 잠결에 기분 나쁜 소름이 돋습니다... 기습입니다!</span>");

            // 기존에 만들어둔 몬스터 조우 로직 재활용
            handleMonsterEncounter(ds);
        } else {
            // [판정 성공] 평온한 휴식
            ds.addLog("<span style='color:#aaaaaa;'>주변이 고요합니다. 충분히 기력을 회복했습니다.</span>");
        }

        // 3. 상태 저장
        saveAll(user, ds);
        log.info(">>> 던전 휴식 완료. 기습 여부: {}", (roll > safetyRate));
    }
}