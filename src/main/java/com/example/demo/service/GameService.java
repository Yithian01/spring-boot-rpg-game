package com.example.demo.service;

import com.example.demo.domain.enums.LocationType;
import com.example.demo.domain.meta.*;
import com.example.demo.domain.save.*;
import com.example.demo.dto.*;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameDataManager gameDataManager;
    private final UserFileRepository userFileRepository;
    private final TownFileRepository townFileRepository;
    private final InventoryFileRepository inventoryFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final GameRepository gameRepository;
    private final StatCalculationService statCalculationService;
    private final BattleService battleService;

    /**
     * 이어하던 게임 존재하는 지 확인
     * @return 아직까지는 단순히 파일 존재하는 지 확인
     */
    public boolean canContinueGame() {
        return (gameRepository.existsFile()
                && userFileRepository.existsFile()
                && townFileRepository.existsFile()
                && inventoryFileRepository.existsFile());
    }

    /**
     * 새게임 시작 시
     * @return 캐릭터 선택창 반환
     */
    public List<CharacterSelectPageDto> getPlayableCharacterList() {
        Map<Integer, TribeInitialMeta> initialMetaMap = gameDataManager.getTribeInitialMetaMap();
        Map<Integer, TribeMeta> tribeMetaMap = gameDataManager.getTribeMetaMap();

        return initialMetaMap.values().stream()
                .map(meta -> {
                    TribeMeta tribe = tribeMetaMap.get(meta.getTribeId());

                    return CharacterSelectPageDto.builder()
                            .tribeId(meta.getTribeId())
                            .characterName(tribe.getName())
                            .imageUrl(meta.getImage())
                            .description(tribe.getDescription())
                            .initGold(meta.getGold())
                            .build();
                })
                .toList();
    }

    /**
     * [새 게임 생성]
     * - 스탯(Stats): 종족별 고정값 사용 (바바리안은 힘이 세다)
     * - 잠재력(Potentials): 완전 랜덤 생성 (바바리안도 마법 S급 가능)
     */
    public void createNewGame(int tribeId) {

        log.info(">>> 새 게임 시작: 기존 데이터 초기화 중...");
        gameRepository.deleteFile();
        userFileRepository.deleteFile();
        townFileRepository.deleteFile();
        inventoryFileRepository.deleteFile();

        // 1. [종족 정보 조회] - 로직 상단으로 이동하여 데이터를 미리 확보
        TribeInitialMeta initialMeta = gameDataManager.getTribeInitialMetaMap().get(tribeId);
        TribeMeta tribeMeta = gameDataManager.getTribeMetaMap().get(tribeId);

        if (initialMeta == null || tribeMeta == null) {
            throw new RuntimeException("잘못된 종족 ID입니다: " + tribeId);
        }

        // 2. [인벤토리 초기화]
        // 종족 데이터에 정의된 initialItem(회복 아이템 등)을 인벤토리에 추가
        List<InventoryItem> initialInventoryItems = new ArrayList<>();
        if (initialMeta.getInitialItem() != null) {
            for (Integer itemId : initialMeta.getInitialItem()) {
                initialInventoryItems.add(InventoryItem.builder()
                        .id(itemId)
                        .instanceId(UUID.randomUUID().toString())
                        .quantity(1) // 기본 1개, 필요시 메타에 수량 필드 추가 가능
                        .enhancementLevel(0)
                        .build());
            }
        }
        InventoryStatus initialInventory = InventoryStatus.builder()
                .items(initialInventoryItems)
                .build();
        inventoryFileRepository.saveInventoryStatus(initialInventory);

        // 3. [랜덤 잠재력 생성]
        Map<Integer, Integer> randomPotentials = new HashMap<>();
        for (Integer statId : gameDataManager.getStatMetaMap().keySet()) {
            int growthId = gameDataManager.getRandomGrowthId();
            randomPotentials.put(statId, growthId);
        }

        // 4. [장비 및 스킬 설정]
        // 하드코딩을 제거하고 initialMeta(종족 데이터)에서 직접 가져옵니다.
        Map<String, Integer> initialEquippedItems = new HashMap<>(initialMeta.getInitialEquipment());
        List<Integer> learnedSkillIds = new ArrayList<>(initialMeta.getInitialSkill());

        // 5. UserStatus 생성
        UserStatus newUser = UserStatus.builder()
                .id(1)
                .name(tribeMeta.getName())
                .tribeId(tribeId)
                .religionId(0)
                .currentGold(initialMeta.getGold())
                .baseStats(new HashMap<>(initialMeta.getInitialStats()))
                .potentials(randomPotentials)
                .equipmentBonusStats(new HashMap<>())
                .activeStatuses(new ArrayList<>())
                .finalStats(new HashMap<>(initialMeta.getInitialStats()))
                .combatStats(new CombatStats())
                .equippedItems(initialEquippedItems)
                .usedItemIds(new ArrayList<>())
                .learnedSkillIds(learnedSkillIds)
                .saveVersion(1)
                .build();

        // 6. 스탯 계산기 가동 및 장비 레이어 갱신
        // 장착된 초기 장비의 스탯 보너스를 먼저 계산 레이어에 반영합니다.
        statCalculationService.updateEquipmentLayer(newUser, gameDataManager.getItemMetaMap());
        statCalculationService.refreshUserCombatStats(newUser, gameDataManager.getItemMetaMap());

        // 7. 자원 수치 보정
        newUser.setCurrentHp(newUser.getCombatStats().getMaxHp());
        newUser.setCurrentMp(newUser.getCombatStats().getMaxMp());
        newUser.setCurrentStamina(newUser.getCombatStats().getMaxStamina());

        // 8. 데이터 저장
        userFileRepository.saveUserStatus(newUser);

        TownStatus newTown = TownStatus.builder()
                .currentTurn(30)
                .maxTurn(30)
                .day(1)
                .currentTax(100)
                .isTaxPaid(false)
                .build();
        townFileRepository.saveTownStatus(newTown);

        GameStatus gameStatus = GameStatus.builder()
                .location(LocationType.valueOf("TOWN"))
                .dungeonId(null)
                .build();
        gameRepository.saveGameStatus(gameStatus);

        log.info(">>> 새 게임 생성 완료! (종족: {}, 초기 장비 및 스킬 장착됨)", tribeMeta.getName());
    }

    /**
     * 메인 화면에 뿌려줄 모든 게임 데이터를 조립해서 반환
     */
    public GamePageDto getGamePageData() {
        UserStatus user = userFileRepository.findGameUser();
        if (user == null) return null;

        // 랜덤박스 할인률
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();
        int finalPrice = statCalculationService.calculateGambleItemCost(stats, InventoryService.BOX_BASE_PRICE);
        int discountPercent = statCalculationService.calculateGambleItemDiscountPercent(stats);

        // 인벤토리 아이템 리스트 (장착된 것 제외)
        List<ItemPageDto> inventory = getInventoryPageData();

        // 장착 중인 아이템 맵 생성
        Map<String, ItemPageDto> equippedMap = new HashMap<>();
        user.getEquippedItems().forEach((slot, itemId) -> {
            if (itemId != 0) {
                ItemMeta meta = gameDataManager.getItemMetaMap().get(itemId);
                if (meta != null) {
                    equippedMap.put(slot, convertToItemPageDto(meta, 1));
                }
            }
        });

        return GamePageDto.builder()
                .img(String.valueOf(user.getTribeId()))
                .userName(user.getName())
                .tribe(mapUserTribe(user))
                .religion(mapUserReligion(user))

                // 생존 자원 및 골드 (HTML에서 바로 접근 가능)
                .currentHp(user.getCurrentHp())
                .maxHp(user.getCombatStats().getMaxHp())
                .currentMp(user.getCurrentMp())
                .maxMp(user.getCombatStats().getMaxMp())
                .currentStamina(user.getCurrentStamina())
                .maxStamina(user.getCombatStats().getMaxStamina())
                .currentGold(user.getCurrentGold())

                // 데이터 리스트
                .stats(mapUserStats(user))
                .items(inventory)
                .equippedItems(equippedMap) // 장착창 데이터

                //유저 현재 버프/디버프 목록 전달
                .activeStatuses(user.getActiveStatuses())

                // 랜덤 박스 결제 정보
                .boxPrice(finalPrice)
                .boxDiscount(discountPercent)
                .build();
    }

    /**
     * 초기 종족 정보 매칭
     * @param user 유저 정보
     * @return 결과 반환
     */
    private UserTribeDto mapUserTribe(UserStatus user) {
        TribeMeta meta = gameDataManager.getTribeMetaMap().get(user.getTribeId());

        if (meta != null) {
            return UserTribeDto.builder()
                    .id(meta.getId())
                    .name(meta.getName())
                    .description(meta.getDescription())
                    .build();
        }
        return UserTribeDto.builder().name("알 수 없음").description("데이터 없음").build();
    }

    /**
     * 초기 종교 설정 TO-DO
     * @param user 유저 정보
     * @return 결과 반환
     */
    private UserReligionDto mapUserReligion(UserStatus user) {
        int userReligionId = user.getReligionId();

        if (userReligionId == 0) {
            return UserReligionDto.builder()
                    .id(0)
                    .name("무교")
                    .description("신을 믿지 않습니다.")
                    .build();
        }

        ReligionMeta meta = gameDataManager.getReligionMetaMap().get(userReligionId);

        if (meta != null) {
            return UserReligionDto.builder()
                    .id(meta.getId())
                    .name(meta.getName())
                    .description(meta.getDescription())
                    .build();
        }

        return UserReligionDto.builder()
                .id(0)
                .name("무교")
                .description("신을 믿지 않습니다.")
                .build();
    }

    /**
     * 초기 스탯을 처리
     * @param user 유저 스탯
     * @return 결과 반환
     */
    private List<UserStatDto> mapUserStats(UserStatus user) {
        List<UserStatDto> statList = new ArrayList<>();
        Map<Integer, StatMeta> metaMap = gameDataManager.getStatMetaMap();

        // 무조건 finalStats를 보되, 혹시 비어있다면 baseStats를 백업으로 사용
        Map<Integer, Integer> statsToDisplay = (user.getFinalStats() != null && !user.getFinalStats().isEmpty())
                ? user.getFinalStats()
                : user.getBaseStats();

        for (StatMeta meta : metaMap.values()) {
            int key = meta.getId();
            int value = statsToDisplay.getOrDefault(key, 0);
            int potential = user.getPotentials().getOrDefault(key, 0);

            UserStatDto dto = UserStatDto.builder()
                    .id(meta.getId())
                    .name(meta.getName())
                    .description(meta.getDescription())
                    .value(value) // 이 value가 장비+버프가 모두 합쳐진 최종값
                    .growthGrade(calculateGrade(potential))
                    .build();

            statList.add(dto);
        }

        statList.sort(Comparator.comparingInt(UserStatDto::getId));
        return statList;
    }

    private String calculateGrade(int potential) {
        switch (potential) {
            case 1: return "S";
            case 2: return "A";
            case 3: return "B";
            case 4: return "C";
            case 5: return "D";
            case 6: return "E";
            default: return "F";
        }
    }

    /**
     * 화면에 보여질 인벤토리 정보
     * @return 인벤토리 정보 반환
     */
    private List<ItemPageDto> getInventoryPageData() {
        List<InventoryItem> inventoryItems = inventoryFileRepository.findInventoryStatus().getItems();
        List<ItemPageDto> inventoryDtoList = new ArrayList<>();

        for (InventoryItem invItem : inventoryItems) {
            ItemMeta meta = gameDataManager.getItemMetaMap().get(invItem.getId());
            if (meta == null) continue;

            // 장비 아이템(장착 슬롯이 NONE이 아닌 것)은 개별적으로 리스트에 추가
            if (!meta.getSlot().equals("NONE")) {
                for (int i = 0; i < invItem.getQuantity(); i++) {
                    // 수량을 1로 고정하여 각각의 객체로 추가
                    inventoryDtoList.add(convertToItemPageDto(meta, 1));
                }
            } else {
                // 소모품(포션 등)은 기존처럼 겹쳐서 보여주고 싶다면 그대로 유지
                inventoryDtoList.add(convertToItemPageDto(meta, invItem.getQuantity()));
            }
        }
        return inventoryDtoList;
    }

    public TownPageDto getTownData() {
        TownStatus town = townFileRepository.findTownStatus();
        return TownPageDto.builder()
                .day(town.getDay())
                .currentTurn(town.getCurrentTurn())
                .maxTurn(town.getMaxTurn())
                .currentTax(town.getCurrentTax())
                .isTaxPaid(town.isTaxPaid())
                .build();
    }

    public GameStatus getGameStatus() {
        return gameRepository.findGameStatus();
    }

    /**
     * COST TYPE을 반환함
     */
    public List<String> convertCostType(List<Integer> costType){
        List<String> result = new ArrayList<>();
        for (Integer i : costType) {
            switch (i){
                case 1:
                    result.add("LIFE");
                    break;
                case 2:
                    result.add("MANA");
                    break;
                case 3:
                    result.add("STAMINA");
                    break;
                default:
                    result.add("UNKNOWN");
            }
        }
        return result;
    }

    /**
     * StatId -> String 변환
     */
    public List<String> convertStatId(List<Integer> StatIds){
        List<String> result = new ArrayList<>();
        for (Integer i : StatIds) {
            // 메모리에서 스탯 정보 조회
            result.add(gameDataManager.getStatMetaMap().get(i).getName());
        }
        return result;
    }

    /**
     * ItemMeta 데이터를 UI용 ItemPageDto로 변환하는 공통 메서드
     */
    private ItemPageDto convertToItemPageDto(ItemMeta meta, int quantity) {
        List<String> statBonuses = new ArrayList<>();

        if (meta.isTwoHanded()) {
            statBonuses.add("● 양손 무기 (보조장비 착용 불가)");
        }

        // 1. 기초 스탯 보너스 가공
        if (meta.getBaseStatsBonus() != null) {
            meta.getBaseStatsBonus().forEach((statId, value) -> {
                String statName = gameDataManager.getStatMetaMap().get(statId).getName();
                statBonuses.add(statName + " +" + value);
            });
        }

        // 2. 전투 능력치 보너스 가공
        Map<String, Double> cb = meta.getCombatStatsBonus();
        if (cb != null) {
            cb.forEach((key, value) -> {
                if (value != 0) {
                    String displayName = gameDataManager.STAT_NAME_MAP.getOrDefault(key, key);
                    String suffix = (key.contains("Rate") || key.contains("Dmg") || key.contains("Dodge") || key.contains("accuracy")) ? "%" : "";
                    statBonuses.add(displayName + " +" + value + suffix);
                }
            });
        }

        // 3. 소모품 회복 효과 가공
        String recoveryEffect = "";
        Map<String, Integer> rb = meta.getRecoveryBonus();
        if (rb != null) {
            List<String> recoveries = new ArrayList<>();
            rb.forEach((key, value) -> {
                if (value > 0) {
                    recoveries.add(key.toUpperCase() + " " + value + " 회복");
                }
            });
            recoveryEffect = String.join(", ", recoveries);
        }

        return ItemPageDto.builder()
                .id(meta.getId())
                .name(meta.getName())
                .grade(meta.getGrade())
                .icon(meta.getIcon())
                .description(meta.getDescription())
                .gold(meta.getPrice())
                .type(meta.getType())
                .slot(meta.getSlot())
                .subType(meta.getSubType())
                .quantity(quantity)
                .statBonuses(statBonuses)
                .recoveryEffect(recoveryEffect != null ? recoveryEffect : "")
                .twoHanded(meta.isTwoHanded())
                .build();
    }

    /**
     * [조립] 던전 화면 전용 데이터 생성
     */
    public DungeonPageDto getDungeonData() {
        DungeonStatus ds = dungeonFileRepository.findDungeonStatus();
        UserStatus user = userFileRepository.findGameUser();
        if (ds == null || user == null) return null;

        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();

        // 1. 전투/비전투 공통으로 현재 사용 가능한 스킬 리스트(패)를 가져옴
        List<SkillCardDto> skillCards = battleService.getSkillHand(user, ds);

        int calculatedMaxTurns = statCalculationService.calculateCombatTurns(user);
        ds.setActiveMonster(statCalculationService.statCalculationMonster(ds.getActiveMonster()));
        dungeonFileRepository.saveDungeonStatus(ds);

        return DungeonPageDto.builder()
                .dungeonId(ds.getDungeonId())
                .dungeonName(ds.getDungeonName())
                .currentFloor(ds.getCurrentFloor())
                .progress(ds.getProgress())
                .actionCount(ds.getActionCount())
                .maxActionCount(ds.getMaxActionCount())
                .explorationEfficiency(statCalculationService.calculateExplorationEfficiency(stats))
                .restSafetyRate(statCalculationService.calculateRestSafetyRate(stats))
                .isInBattle(ds.isInBattle())
                .activeMonster(ds.getActiveMonster())
                .skillCards(skillCards)
                .playerRemainingTurns(ds.getPlayerRemainingTurns())
                .playerMaxTurns(ds.getPlayerMaxTurns() > 0 ? ds.getPlayerMaxTurns() : calculatedMaxTurns)
                .battleLogs(ds.getBattleLogs())
                .pendingExp(ds.getPendingExp())
                .pendingGold(ds.getPendingGold())
                .build();
    }
}