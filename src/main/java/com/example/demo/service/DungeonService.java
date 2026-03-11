package com.example.demo.service;

import com.example.demo.domain.enums.LocationType;
import com.example.demo.domain.meta.CombatStats;
import com.example.demo.domain.meta.DungeonMeta;
import com.example.demo.domain.meta.MonsterMeta;
import com.example.demo.domain.meta.StatMeta;
import com.example.demo.domain.save.*;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.DungeonFileRepository;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.TownFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DungeonService {

    private final GameFileRepository gameFileRepository;
    private final TownFileRepository townFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final UserFileRepository userFileRepository;
    private final StatCalculationService statCalculationService;
    private final ShopService shopService;
    private final GameDataManager gameDataManager;
    private final EssenceService essenceService;
    private final BattleService battleService;
    private final ValidationService validationService;


    /**
     * 저장 함수
     */
    private void saveAll(UserStatus user, DungeonStatus ds, GameStatus gs) {
        ds.setActionCount(ds.getActionCount() + 1);
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * 행동 카운트를 올리지 않고 현재 모든 상태(로그 포함)를 저장
     */
    private void saveCurrentState(UserStatus user, DungeonStatus ds, GameStatus gs) {
        userFileRepository.saveUserStatus(user);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * [공통 로직] 던전 상태 정보를 세팅하고 저장함
     */
    private void enterFloor(int dungeonId, boolean isFirstEntry) {
        GameStatus gs = gameFileRepository.findGameStatus();
        // 1. 메타 데이터 로드
        DungeonMeta meta = gameDataManager.getDungeonMetaMap().get(dungeonId);
        UserStatus user = userFileRepository.findGameUser();
        int maxTurns = statCalculationService.calculateCombatTurns(user);

        DungeonMeta prevMeta = gameDataManager.getDungeonMetaMap().get(meta.getPrevDungeonId());
        String prevName = (prevMeta != null) ? prevMeta.getName() : null;

        DungeonStatus ds;
        if (isFirstEntry) {
            ds = DungeonStatus.builder()
                    .dungeonId(dungeonId)
                    .parentDungeonId(meta.pickNextDungeonId())
                    .parentDungeonName(prevName)
                    .dungeonName(meta.getName())
                    .currentFloor(meta.getFloor())
                    .actionCount(0)
                    .maxActionCount(meta.getMaxActionCount())
                    .progress(0)
                    .actionCount(0)
                    .floorProgressMap(new HashMap<>())
                    .activeMonster(null)
                    .playerMaxTurns(maxTurns)
                    .build();
            ds.getFloorProgressMap().put(dungeonId, 0);
            gs.addLog("⚔️ <b style='color:#ffd700;'>" + meta.getName() + "</b>에 입장했습니다.");
        } else {
            // 이동이면 기존 데이터를 가져와서 갱신
            ds = dungeonFileRepository.findDungeonStatus();
            ds.setDungeonId(dungeonId);
            ds.setParentDungeonId(meta.pickNextDungeonId());
            ds.setParentDungeonName(prevName);
            ds.setDungeonName(meta.getName());
            ds.setCurrentFloor(meta.getFloor());
            ds.setMaxActionCount(meta.getMaxActionCount());
            // 이전 층으로 돌아올 때 기존 진행도를 기억하고 싶다면 아래 주석 해제
            // int lastProgress = ds.getFloorProgressMap().getOrDefault(dungeonId, 0);
            // ds.setProgress(lastProgress);
            ds.setProgress(0);
            ds.setActiveMonster(null);
            ds.setPlayerMaxTurns(maxTurns);
            gs.addLog("<b style='color:#ffd700;'>" + meta.getName() + "</b>(으)로 이동했습니다.");
        }

        // 3. 던전 효과(디버프/버프)가 있다면 로그에 표시
        if (meta.getEffects() != null && !meta.getEffects().isEmpty()) {
            gs.addLog("<span style='color:#aaaaaa;'>[환경] 이 장소의 특수한 기운이 몸을 감쌉니다...</span>");
        }

        gameFileRepository.saveGameStatus(gs);
        dungeonFileRepository.saveDungeonStatus(ds);
        gameFileRepository.updateLocation(LocationType.DUNGEON, ds.getDungeonId());
    }

    /**
     * 던전 진입 시 초기 설정 및 세이브 파일 생성
     */
    public void initDungeon(int dungeonId) {
        // 1. 전체 게임 상태를 던전으로 변경
        TownStatus townStatus = townFileRepository.findTownStatus();
        int currentDay = townStatus.getDay();

        // [핵심 로직] 1일차(새게임 직후)이거나 30, 60, 90...일차일 때만 입장 허용
        int dayInMonth = ((currentDay - 1) % 30) + 1;
        boolean isPortalOpen = (dayInMonth == 1);

        if (!isPortalOpen) {
            // 남은 일수 계산: 31일(다음 1일)에서 현재 한 달 내 날짜를 뺌
            int daysLeft = 31 - dayInMonth;
            throw new IllegalStateException("현재는 차원문이 닫혀 있습니다. (다음 개방까지 " + daysLeft + "일 남음)");
        }

        gameFileRepository.updateLocation(LocationType.DUNGEON, dungeonId);

        // 2. 마을 상태 업데이트 (날짜 경과, 턴 회복 등)
        townStatus.setDay(townStatus.getDay() + 1);
        townStatus.setCurrentTurn(townStatus.getMaxTurn());
        townFileRepository.saveTownStatus(townStatus);

        // 3. 던전 초기화 로직 실행 (true: 처음 생성)
        enterFloor(dungeonId, true);

        log.info(">>> 던전 최초 진입 완료: DungeonID {}, Day {}", dungeonId, townStatus.getDay());
    }

    /**
     * [다음 층 이동]
     */
    public void goToNextFloor() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        GameStatus gs = gameFileRepository.findGameStatus();
        DungeonMeta currentMeta = gameDataManager.getDungeonMetaMap().get(ds.getDungeonId());

        // 1. 현재 층이 10층인지 확인 (Floor 기준)
        if (currentMeta.getFloor() >= 10) {
            gs.addLog("<span style='color:#ff4d4d;'>[경고] 세상의 끝에서 벗어날 수 없습니다.</span>");
            gameFileRepository.saveGameStatus(gs);
            return;
        }

        int nextId = currentMeta.pickNextDungeonId();

        if (nextId != 0) {
            // 이전에 수동으로 parentId 세팅하던 로직들 전부 삭제 가능
            enterFloor(nextId, true);
        }
    }

    /**
     * [이전 층 이동]
     */
    public void goToPrevFloor() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        DungeonMeta currentMeta = gameDataManager.getDungeonMetaMap().get(ds.getDungeonId());

        int prevId = currentMeta.pickPrevDungeonId();

        if (prevId != 0) {
            // 메타데이터에 정의된 '부모'로 돌아감 (이동이므로 false)
            enterFloor(prevId, false);
            log.info(">>> 이전 층으로 이동: {}", prevId);
        }
    }

    /**
     * [다른 지역 이동]
     */
    public void goToOtherArea() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        DungeonMeta currentMeta = gameDataManager.getDungeonMetaMap().get(ds.getDungeonId());

        int otherArea = currentMeta.pickOtherAreaDungeonId();

        if (otherArea != 0) {
            enterFloor(otherArea, false);
            log.info(">>> 다른 구역으로 이동: {}", otherArea);
        }
    }

    /**
     * [탐사하기] 버튼 클릭 시 호출
     * 몬스터를 조우하거나, 아무 일도 없거나, 탐사율 증가 가거나 결정
     */
    public void explore() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        UserStatus us = userFileRepository.findGameUser();
        GameStatus gs = gameFileRepository.findGameStatus();

        // 현재 던전의 메타 정보 가져오기 (ID 기반)
        DungeonMeta dungeonMeta = gameDataManager.getDungeonMetaMap().get(ds.getDungeonId());

        // 스테미나 소모 및 틱 업데이트
        battleService.updatePlayerStatusTick(us, ds, gs);
        validationService.checkEndBattle();
        battleService.applyPlayerRegeneration(us, gs);
        us.setCurrentStamina(Math.max(0, us.getCurrentStamina() - 3));

        // 1. 메타 데이터에서 확률 추출 (기본단위가 %라면 100.0으로 나누어 소수점화)
        double trapProb = dungeonMeta.getTrapEncounterRate() / 100.0;    // 예: 20% -> 0.2
        double monsterProb = dungeonMeta.getMonsterEncounterRate() / 100.0; // 예: 40% -> 0.4
        double merchantProb = dungeonMeta.getMistEncounterRate() / 100.0;  // 예: 3% -> 0.03

        // 2. 난수 생성
        double roll = Math.random();
        Map<Integer, Integer> stats = (us.getFinalStats() != null) ? us.getFinalStats() : us.getBaseStats();

        // 3. 사건 판정 분기 (누적 확률 방식)
        double currentRange = 0;

        if (roll < (currentRange += trapProb)) {
            // Case A: 함정 조우
            handleTrap(us, gs);
        }
        else if (roll < (currentRange += monsterProb)) {
            // Case B: 몬스터 조우
            handleMonsterEncounter(ds, gs);
        }
        else if (roll < (currentRange + merchantProb)) {
            // Case C: 상인(미스트) 조우
            shopService.dungeonStoreRestock(gs);
        }
        else {
            // Case D: 아무 일도 없음 (순수 탐사 성공)
            // 남은 확률 (100% - 함정 - 몬스터 - 상인)이 일로 들어옵니다.
            handlePureExploration(ds, stats, gs);
        }

        // 4. 상태 저장
        saveAll(us, ds, gs);
    }

    /**
     * 함정 발동 시 이벤트
     * @param user
     */
    private void handleTrap(UserStatus user, GameStatus gs) {
        double dodge = user.getCombatStats().getDodge();

        int displayDodge = (int) dodge;

        // 1. 회피 판정
        if (Math.random() < (dodge / 100.0)) {
            gs.addLog("<span style='color:#70db70;'>[함정]</span> 날렵한 몸놀림으로 함정을 회피했습니다! (회피율: " + displayDodge + "%)");
            return;
        }

        // 2. 피해 계산: 최대 체력의 20%
        int maxHp = user.getCombatStats().getMaxHp();
        int damage = (int) (maxHp * 0.2); // 20% 계산 (정수로 절삭)

        if (damage < 1) damage = 1;

        user.setCurrentHp(Math.max(0, user.getCurrentHp() - damage));

        gs.addLog("<span style='color:#ff4d4d;'>[함정]</span> 치명적인 함정을 밟았습니다! (HP -" + damage + ")");

        if (user.getCurrentHp() <= 0) {
            gs.addLog("<span style='color:#ff0000; font-weight:bold;'>정신이 아득해집니다... 더 이상 움직일 수 없습니다.</span>");
        }
    }

    /**
     * 몬스터 조우 (전투 발생)
     */
    private void handleMonsterEncounter(DungeonStatus ds, GameStatus gs) {
        // 1. 메타 데이터에서 몬스터 추첨
        int currentDungeonId = ds.getDungeonId();
        MonsterMeta monsterMeta = gameDataManager.getRandomMonsterByDungeon(currentDungeonId);

        // 3. ActiveMonster 빌드 (실시간 전투용 스냅샷)
        ActiveMonster activeMonster = ActiveMonster.builder()
                .monsterId(monsterMeta.getId())
                .name(monsterMeta.getName())
                .tier(monsterMeta.getTier())
                .currentHp((int) monsterMeta.getStats().getMaxHp())
                .currentMp((int) monsterMeta.getStats().getMaxMp())
                .baseStats(monsterMeta.getStats().toBuilder().build())
                .activeStats(monsterMeta.getStats().toBuilder().build())
                .activeStatuses(new ArrayList<>())
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
        ds.setPendingEssence(null);

        gs.addLog("<span style='color:#ff9f43;'>[전투]</span> " + activeMonster.getName() + "이(가) 나타났습니다!");
    }

    /**
     * 무탈하게 탐사 진행 (진척도 상승)
     */
    private void handlePureExploration(DungeonStatus ds, Map<Integer, Integer> stats, GameStatus gs) {
        // 1. 스탯 기반 탐사 효율 계산 (기본 5% ~ 최대 20% 등)
        DungeonMeta dungeonMeta = gameDataManager.getDungeonMetaMap().get(ds.getDungeonId());
        int explorationRate = (int) dungeonMeta.getExplorationRate() + statCalculationService.calculateExplorationEfficiency(stats);

        // 2. 진척도 업데이트 (최대 100)
        int currentProgress = ds.getProgress();
        int nextProgress = Math.min(100, currentProgress + explorationRate);
        ds.setProgress(nextProgress);
        ds.getFloorProgressMap().put(ds.getDungeonId(), nextProgress);
        // 3. 로그 남기기
        gs.addLog("주변을 면밀히 조사하며 길을 찾았습니다. (진척도 +" + explorationRate + "%)");
        // 4. 100% 달성 시 안내
        if (nextProgress >= 100) {
            gs.addLog("<span style='color:#ffd700; font-weight:bold;'>[알림] 이 층의 조사가 완료되었습니다! 다음 층으로 내려갈 수 있습니다.</span>");
        }
    }

    public void rest() {
        validationService.checkEndBattle();
        UserStatus user = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        GameStatus gs = gameFileRepository.findGameStatus();

        int hpRecovery = statCalculationService.calculateHpRestoration(user);
        int mpRecovery = statCalculationService.calculateMpRestoration(user);
        int stRecovery = statCalculationService.calculateStRestoration(user);

        user.setCurrentHp(Math.min(user.getCombatStats().getMaxHp(), user.getCurrentHp() + hpRecovery));
        user.setCurrentMp(Math.min(user.getCombatStats().getMaxMp(), user.getCurrentMp() + mpRecovery));
        user.setCurrentStamina(Math.min(user.getCombatStats().getMaxStamina(), user.getCurrentStamina() + stRecovery));

        gs.addLog(String.format("🛌 <b style='color:#70db70;'>휴식을 취했습니다.</b> (HP/MP/STA 회복)"));

        // 2. 기습 판정 (Safety Rate 체크)
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();
        double safetyRate = statCalculationService.calculateRestSafetyRate(stats);

        // 주사위 굴리기 (0~100)
        double roll = Math.random() * 100;

        if (roll > safetyRate) {
            // [판정 실패] 기습 발생!
            gs.addLog("<span style='color:#ff4d4d; font-weight:bold;'>⚠️ 잠결에 기분 나쁜 소름이 돋습니다... 기습입니다!</span>");

            // 기존에 만들어둔 몬스터 조우 로직 재활용
            handleMonsterEncounter(ds, gs);
        } else {
            // [판정 성공] 평온한 휴식
            gs.addLog("<span style='color:#aaaaaa;'>주변이 고요합니다. 충분히 기력을 회복했습니다.</span>");
        }

        // 3. 상태 저장
        saveAll(user, ds, gs);
        log.info(">>> 던전 휴식 완료. 기습 여부: {}", (roll > safetyRate));
    }

    /**
     * [정수 획득 핸들러]
     */
    public void handlePickupEssence() {
        UserStatus us = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        GameStatus gs = gameFileRepository.findGameStatus();

        // 1. 정수 흡수 처리
        essenceService.claimEssence(us, ds, gs);

        if (ds.getPendingEssence() == null) {
            finalizeRewards(us, ds, gs);
        } else {
            saveCurrentState(us, ds, gs);
        }
    }

    /**
     * [정수 버리기 핸들러]
     */
    public void handleDiscardEssence() {
        UserStatus us = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        GameStatus gs = gameFileRepository.findGameStatus();

        gs.addLog("<span style='color:#aaaaaa;'>정수를 흡수하지 않고 버렸습니다.</span>");
        ds.setPendingEssence(null);

        finalizeRewards(us, ds, gs);
    }

    /**
     * [이동/수거 핸들러]
     */
    public void handleMoveOnly() {
        UserStatus us = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        GameStatus gs = gameFileRepository.findGameStatus();

        finalizeRewards(us, ds, gs);
    }

    /**
     * [공통 최종 정리]
     * 이미 BattleService에서 경험치 정산이 끝났으므로, 여기서는 필드 데이터만 초기화합니다.
     */
    private void finalizeRewards(UserStatus us, DungeonStatus ds, GameStatus gs) {
        // 1. 데이터 초기화 (전투 관련 잔재 청소)
        ds.setPendingExp(0);
        ds.setPendingEssence(null);
        ds.setActiveMonster(null);

        // 2. 모든 변화 저장 (이제 '조사' 버튼이 다시 활성화될 준비 완료)
        saveCurrentState(us, ds, gs);
    }
}