package com.example.demo.service;

import com.example.demo.domain.enums.LocationType;
import com.example.demo.domain.meta.DungeonMeta;
import com.example.demo.domain.meta.MonsterMeta;
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
    private final GameDataManager gameDataManager;

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
                    .parentDungeonId(meta.getPrevDungeonId())
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
            ds.setParentDungeonId(meta.getPrevDungeonId());
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
        gameFileRepository.updateLocation(LocationType.DUNGEON, dungeonId);

        // 2. 마을 상태 업데이트 (날짜 경과, 턴 회복 등)
        TownStatus townStatus = townFileRepository.findTownStatus();
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
        DungeonMeta currentMeta = gameDataManager.getDungeonMetaMap().get(ds.getDungeonId());

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

        int prevId = currentMeta.getPrevDungeonId();

        if (prevId != 0) {
            // 메타데이터에 정의된 '부모'로 돌아감 (이동이므로 false)
            enterFloor(prevId, false);
            log.info(">>> 이전 층으로 이동: {}", prevId);
        }
    }

    /**
     * [탐사하기] 버튼 클릭 시 호출
     * 몬스터를 조우하거나, 아무 일도 없거나, 탐사율 증가 가거나 결정
     */
    public void explore() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        UserStatus user = userFileRepository.findGameUser();
        GameStatus gs = gameFileRepository.findGameStatus();

        // 1. 기본 비용 소모 (스태미나 감소 등)
        user.setCurrentStamina(Math.max(0, user.getCurrentStamina() - 3));

        // 2. 난수 생성 (0.0 ~ 1.0)
        double roll = Math.random();
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();

        // 3. 사건 판정 분기
        if (roll < 0.15) {
            // Case A: 함정 조우 (15% 확률)
            handleTrap(user, stats, gs);
        }
        else if (roll < 0.45) {
            // Case B: 몬스터 조우 (30% 확률)
            handleMonsterEncounter(ds, gs);
        }
        else {
            // Case C: 무탈하게 탐사 성공 (55% 확률)
            handlePureExploration(ds, stats, gs);
        }

        // 4. 상태 저장
        saveAll(user, ds, gs);
    }

    /**
     * 함정 발동 시 이벤트
     * @param user
     * @param stats
     */
    private void handleTrap(UserStatus user, Map<Integer, Integer> stats, GameStatus gs) {
        int intuition = stats.getOrDefault(24, 0); // 직관

        // 1. 회피 판정 (직관 스탯 기반)
        if (Math.random() < (intuition * 0.005)) {
            gs.addLog("<span style='color:#70db70;'>[함정]</span> 감각적으로 함정을 눈치채고 피해갔습니다.");
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

        // 2. 골드 랜덤 결정 (goldMin ~ goldMax)
        int rewardGold = monsterMeta.getGoldMin() +
                (int)(Math.random() * (monsterMeta.getGoldMax() - monsterMeta.getGoldMin() + 1));

        // 3. ActiveMonster 빌드 (실시간 전투용 스냅샷)
        ActiveMonster activeMonster = ActiveMonster.builder()
                .monsterId(monsterMeta.getId())
                .name(monsterMeta.getName())
                .tier(monsterMeta.getTier())
                .currentHp((int) monsterMeta.getStats().getMaxHp())
                .currentMp((int) monsterMeta.getStats().getMaxMp())
                .baseStats(monsterMeta.getStats())
                .activeStats(monsterMeta.getStats())
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

        gs.addLog("<span style='color:#ff9f43;'>[전투]</span> " + activeMonster.getName() + "이(가) 나타났습니다!");
    }

    /**
     * 무탈하게 탐사 진행 (진척도 상승)
     */
    private void handlePureExploration(DungeonStatus ds, Map<Integer, Integer> stats, GameStatus gs) {
        // 1. 스탯 기반 탐사 효율 계산 (기본 5% ~ 최대 20% 등)
        int efficiency = statCalculationService.calculateExplorationEfficiency(stats);

        // 2. 진척도 업데이트 (최대 100)
        int currentProgress = ds.getProgress();
        int nextProgress = Math.min(100, currentProgress + efficiency);
        ds.setProgress(nextProgress);
        ds.getFloorProgressMap().put(ds.getDungeonId(), nextProgress);
        // 3. 로그 남기기
        gs.addLog("주변을 면밀히 조사하며 길을 찾았습니다. (진척도 +" + efficiency + "%)");
        // 4. 100% 달성 시 안내
        if (nextProgress >= 100) {
            gs.addLog("<span style='color:#ffd700; font-weight:bold;'>[알림] 이 층의 조사가 완료되었습니다! 다음 층으로 내려갈 수 있습니다.</span>");
        }
    }

    public void rest() {
        UserStatus user = userFileRepository.findGameUser();
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        GameStatus gs = gameFileRepository.findGameStatus();

        int hpRecovery = statCalculationService.calculateHpRestoration(user);
        int mpRecovery = statCalculationService.calculateMpRestoration(user);
        int stRecovery = statCalculationService.calculateStRestoration(user);

        user.setCurrentHp(Math.min(user.getCombatStats().getMaxHp(), user.getCurrentHp() + hpRecovery));
        user.setCurrentMp(Math.min(user.getCombatStats().getMaxMp(), user.getCurrentMp() + mpRecovery));
        user.setCurrentStamina(Math.min(user.getCombatStats().getMaxStamina(), user.getCurrentStamina() + stRecovery));

        saveAll(user, ds, gs);

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
}