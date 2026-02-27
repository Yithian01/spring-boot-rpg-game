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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameDataManager gameDataManager;
    private final UserFileRepository userFileRepository;
    private final TownFileRepository townFileRepository;
    private final InventoryFileRepository inventoryFileRepository;
    private final DungeonFileRepository dungeonFileRepository;
    private final GameFileRepository gameFileRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final StatCalculationService statCalculationService;
    private final BattleService battleService;
    private final InventoryService inventoryService;

    /**
     * 이어하던 게임 존재하는 지 확인
     * @return 아직까지는 단순히 파일 존재하는 지 확인
     */
    public boolean canContinueGame() {
        return (gameFileRepository.existsFile()
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
        gameFileRepository.deleteFile();
        userFileRepository.deleteFile();
        townFileRepository.deleteFile();
        inventoryFileRepository.deleteFile();
        dungeonFileRepository.deleteFile();
        itemInstanceRepository.deleteFile();

        // 1. [종족 정보 조회] - 로직 상단으로 이동하여 데이터를 미리 확보
        TribeInitialMeta initialMeta = gameDataManager.getTribeInitialMetaMap().get(tribeId);
        TribeMeta tribeMeta = gameDataManager.getTribeMetaMap().get(tribeId);

        if (initialMeta == null || tribeMeta == null) {
            throw new RuntimeException("잘못된 종족 ID입니다: " + tribeId);
        }

        // 2. [인벤토리 초기화]
        // 종족 데이터에 정의된 initialItem(회복 아이템 등)을 인벤토리에 추가
        InventoryStatus initialInventory = InventoryStatus.builder()
                .instanceIds(new ArrayList<>())
                .build();

        if (initialMeta.getInitialItem() != null) {
            for (Integer metaId : initialMeta.getInitialItem()) {
                ItemMeta meta = gameDataManager.getItemMetaMap().get(metaId);
                if (meta != null) {
                    ItemInstance newItem = ItemInstance.createConsumable(meta, 3);
                    inventoryService.processAddItem(initialInventory, newItem, true);
                }
            }
        }
        inventoryFileRepository.saveInventoryStatus(initialInventory);

        // 3. [랜덤 잠재력 생성]
        Map<Integer, Integer> randomPotentials = new HashMap<>();
        for (Integer statId : gameDataManager.getStatMetaMap().keySet()) {
            int growthId = gameDataManager.getRandomGrowthId();
            randomPotentials.put(statId, growthId);
        }

        // 4. [장비 설정]
        Map<String, String> equippedUUIDs = new HashMap<>();
        initializeEquippedSlots(equippedUUIDs); // 모든 슬롯 "0"으로 초기화 헬퍼

        if (initialMeta.getInitialEquipment() != null) {
            for (Map.Entry<String, Integer> entry : initialMeta.getInitialEquipment().entrySet()) {
                String slotName = entry.getKey();
                Integer metaId = entry.getValue();

                if (metaId != null && metaId != 0) {
                    ItemMeta meta = gameDataManager.getItemMetaMap().get(metaId);
                    if (meta != null) {
                        // [기존 메서드 활용] 장비로 생성
                        ItemInstance equipment = ItemInstance.createEquipment(meta, null);
                        itemInstanceRepository.save(equipment);
                        equippedUUIDs.put(slotName, equipment.getInstanceId());
                    }
                }
            }
        }

        // 4-2. 스킬 설정
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
                .equippedItems(equippedUUIDs)
                .usedItemIds(new ArrayList<>())
                .learnedSkillIds(learnedSkillIds)
                .saveVersion(1)
                .build();

        // 6. 스탯 계산기 가동 및 장비 레이어 갱신
        // 장착된 초기 장비의 스탯 보너스를 먼저 계산 레이어에 반영합니다.
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
                .currentTax(900000)
                .isTaxPaid(false)
                .build();
        townFileRepository.saveTownStatus(newTown);


        List<String> gameLog = new ArrayList<>();
        GameStatus gs = GameStatus.builder()
                .location(LocationType.valueOf("TOWN"))
                .dungeonId(null)
                .gameLogs(gameLog)
                .build();

        gs.addLog("🏰 마을에 도착했습니다.");
        gameFileRepository.saveGameStatus(gs);

        log.info(">>> 새 게임 생성 완료! (종족: {}, 초기 장비 및 스킬 장착됨)", tribeMeta.getName());
    }

    /**
     * 슬롯 초기화 헬퍼
     */
    private void initializeEquippedSlots(Map<String, String> map) {
        String[] slots = {"WEAPON", "SUB_WEAPON", "BODY", "HEAD", "NECKLACE", "BOOTS", "RING"};
        for (String s : slots) map.put(s, "0");
    }

    /**
     * 메인 화면에 뿌려줄 모든 게임 데이터를 조립해서 반환
     */
    public GamePageDto getGamePageData() {
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        if (us == null) return null;

        // 1. 인벤토리 로드 및 방어적 처리 (비어있을 수 있음)
        InventoryStatus inv = inventoryFileRepository.findInventoryStatus();
        List<ItemPageDto> inventory = new ArrayList<>();

        // 인벤토리가 존재하고, 아이디 리스트가 비어있지 않을 때만 변환
        if (inv != null && inv.getInstanceIds() != null && !inv.getInstanceIds().isEmpty()) {
            inventory = inv.getInstanceIds().stream()
                    .map(uuid -> itemInstanceRepository.findById(uuid).orElse(null))
                    .filter(Objects::nonNull)
                    .map(this::convertToItemPageDto)
                    .collect(Collectors.toList());
        }

        // 2. 할인율 계산
        Map<Integer, Integer> stats = (us.getFinalStats() != null) ? us.getFinalStats() : us.getBaseStats();
        int finalPrice = statCalculationService.calculateGambleItemCost(stats, InventoryService.BOX_BASE_PRICE);
        int discountPercent = statCalculationService.calculateGambleItemDiscountPercent(stats);

        // 3. 장착 중인 아이템 맵 조립 (UUID 기반으로 변경!)
        Map<String, ItemPageDto> equippedMap = new HashMap<>();
        if (us.getEquippedItems() != null) {
            us.getEquippedItems().forEach((slot, instanceId) -> {
                // "0"이나 null이 아닐 때만 실제 인스턴스를 찾음
                if (instanceId != null && !instanceId.equals("0") && !instanceId.equals("")) {
                    itemInstanceRepository.findById(instanceId).ifPresent(ii -> {
                        equippedMap.put(slot, convertToItemPageDto(ii));
                    });
                }
            });
        }

        List<StatCategoryGroupDto> statGroups = buildStatGroups(us);

        return GamePageDto.builder()
                .img(String.valueOf(us.getTribeId()))
                .userName(us.getName())
                .tribe(mapUserTribe(us))
                .religion(mapUserReligion(us))
                .currentHp(us.getCurrentHp())
                .maxHp(us.getCombatStats().getMaxHp())
                .currentMp(us.getCurrentMp())
                .maxMp(us.getCombatStats().getMaxMp())
                .currentStamina(us.getCurrentStamina())
                .maxStamina(us.getCombatStats().getMaxStamina())
                .currentGold(us.getCurrentGold())
                .statGroups(statGroups)
                .combatStats(us.getCombatStats())
                .items(inventory) // 비어있으면 빈 리스트 전달
                .equippedItems(equippedMap)
                .activeStatuses(us.getActiveStatuses())
                .boxPrice(finalPrice)
                .boxDiscount(discountPercent)
                .gameLogs(gs != null ? gs.getGameLogs() : new ArrayList<>())
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
                    .value(value) // 장비+버프가 모두 합쳐진 최종값
                    .growthGrade(gameDataManager.getPotentialGrade(potential))
                    .build();
            statList.add(dto);
        }

        statList.sort(Comparator.comparingInt(UserStatDto::getId));
        return statList;
    }



    /**
     * 화면에 보여질 인벤토리 정보
     * @return 인벤토리 정보 반환
     */
    /**
     * 화면에 보여질 인벤토리 정보 (UUID 인스턴스 기반)
     */
    private List<ItemPageDto> getInventoryPageData() {
        InventoryStatus invStatus = inventoryFileRepository.findInventoryStatus();
        List<ItemPageDto> inventoryDtoList = new ArrayList<>();

        if (invStatus == null || invStatus.getInstanceIds() == null) {
            return inventoryDtoList;
        }

        // 1. 인벤토리에 등록된 모든 UUID를 순회
        for (String uuid : invStatus.getInstanceIds()) {
            // 2. UUID로 실제 아이템 인스턴스(상태 정보)를 가져옴
            ItemInstance instance = itemInstanceRepository.findById(uuid).orElse(null);
            if (instance == null) continue;

            // 3. 인스턴스를 DTO로 변환하여 추가
            // 장비는 애초에 각각의 인스턴스로 존재하므로 별도의 루프가 필요 없음
            inventoryDtoList.add(convertToItemPageDto(instance));
        }

        return inventoryDtoList;
    }

    public TownPageDto getTownData() {
        TownStatus town = townFileRepository.findTownStatus();
        boolean isFirstDayOfMonth = ((town.getDay() - 1) % 30 + 1) == 1;

        return TownPageDto.builder()
                .day(town.getDay())
                .currentTurn(town.getCurrentTurn())
                .maxTurn(town.getMaxTurn())
                .currentTax(town.getCurrentTax())
                .isTaxPaid(town.isTaxPaid())
                .portalOpen(isFirstDayOfMonth)
                .build();
    }

    public GameStatus getGameStatus() {
        return gameFileRepository.findGameStatus();
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
     * ItemMeta 데이터를 UI용 ItemPageDto로 변환하는 공통 메서드
     */
    /**
     * ItemInstance 데이터를 UI용 ItemPageDto로 변환 (UUID 및 강화수치 반영)
     */
    private ItemPageDto convertToItemPageDto(ItemInstance ii) {
        // 0. 기본 메타 데이터 참조 (설명, 아이콘 등 불변 데이터용)
        ItemMeta meta = gameDataManager.getItemMetaMap().get(ii.getItemMetaId());
        List<String> statBonuses = new ArrayList<>();

        if (ii.isTwoHanded()) {
            statBonuses.add("● 양손 무기 (보조장비 착용 불가)");
        }

        // 1. 기초 스탯 보너스 (고정치: +5)
        if (ii.getBaseStatsBonus() != null) {
            ii.getBaseStatsBonus().forEach((statId, value) -> {
                if (value != 0) {
                    String statName = gameDataManager.getStatMetaMap().get(statId).getName();
                    statBonuses.add(statName + " +" + value);
                }
            });
        }

        // 2. 기초 스탯 배율 (퍼센트: +10%) -> 유저님이 원하신 합연산 배율 표시
        if (ii.getBaseStatsBonusModifiers() != null) {
            ii.getBaseStatsBonusModifiers().forEach((statId, modifier) -> {
                if (modifier != 0) {
                    String statName = gameDataManager.getStatMetaMap().get(statId).getName();
                    // 0.1 -> 10% 로 변환
                    statBonuses.add(statName + " +" + (int)(modifier * 100) + "%");
                }
            });
        }

        // 3. 전투 능력치 보너스 (깡스탯 및 배율합)
        if (ii.getCombatStatsBonus() != null) {
            ii.getCombatStatsBonus().forEach((key, value) -> {
                if (value != 0) {
                    String displayName = gameDataManager.STAT_NAME_MAP.getOrDefault(key, key);
                    // 기존 수식 유지하되 % 판단 로직 적용
                    String suffix = (key.toLowerCase().contains("rate") ||
                            key.toLowerCase().contains("dmg") ||
                            key.toLowerCase().contains("dodge") ||
                            key.toLowerCase().contains("accuracy")) ? "%" : "";
                    statBonuses.add(displayName + " +" + value + suffix);
                }
            });
        }

        // 전투 능력치 배율(Modifiers)이 있다면 여기서 추가로 처리 가능
        if (ii.getCombatStatsBonusModifiers() != null) {
            ii.getCombatStatsBonusModifiers().forEach((key, value) -> {
                if (value != 0) {
                    String displayName = gameDataManager.STAT_NAME_MAP.getOrDefault(key, key);
                    statBonuses.add(displayName + " 최종 보정 +" + (int)(value * 100) + "%");
                }
            });
        }

        // 4. 소모품 효과 (메타 데이터 참조)
        String recoveryEffect = "";
        if (meta != null && meta.getRecoveryBonus() != null) {
            List<String> recoveries = new ArrayList<>();
            meta.getRecoveryBonus().forEach((key, value) -> {
                if (value > 0) recoveries.add(key.toUpperCase() + " " + value + " 회복");
            });
            recoveryEffect = String.join(", ", recoveries);
        }

        // 5. DTO 조립 (id 대신 instanceId를 넣어 프론트가 UUID를 쓰게 함)
        return ItemPageDto.builder()
                .id(ii.getInstanceId()) // 이제 이게 식별자가 됨
                .name(ii.getEnhancementLevel() > 0 ?
                        ii.getCustomName() + " (+" + ii.getEnhancementLevel() + ")" :
                        ii.getCustomName())
                .grade(ii.getGrade())
                .icon(meta != null ? meta.getIcon() : "default_icon")
                .description(ii.getDescription())
                .gold(ii.getPrice())
                .type(ii.getType())
                .slot(ii.getSlot())
                .subType(ii.getSubType())
                .quantity(ii.getQuantity())
                .statBonuses(statBonuses)
                .recoveryEffect(recoveryEffect)
                .twoHanded(ii.isTwoHanded())
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
        List<SkillCardDto> skillCards = battleService.getSkillHand(user, ds);

        int calculatedMaxTurns = statCalculationService.calculateCombatTurns(user);
        ds.setActiveMonster(statCalculationService.statCalculationMonster(ds.getActiveMonster()));
        dungeonFileRepository.saveDungeonStatus(ds);

        return DungeonPageDto.builder()
                .dungeonId(ds.getDungeonId())
                .dungeonName(ds.getDungeonName())
                .currentFloor(ds.getCurrentFloor())
                .parentDungeonId(ds.getParentDungeonId())
                .parentDungeonName(ds.getParentDungeonName())
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
                .pendingExp(ds.getPendingExp())
                .pendingGold(ds.getPendingGold())
                .build();
    }

    /**
     * 기본 스탯을 4가지의 것으로 분리
     * @param us 플레이어 정보
     * @return UI로 전달할 스탯 분류 데이터
     */
    private List<StatCategoryGroupDto> buildStatGroups(UserStatus us) {
        Map<Integer, StatMeta> metaMap = gameDataManager.getStatMetaMap();
        Map<Integer, Integer> finalStats = (us.getFinalStats() != null) ? us.getFinalStats() : us.getBaseStats();
        Map<Integer, Integer> potentials = us.getPotentials();

        Map<String, StatCategoryGroupDto> groups = new LinkedHashMap<>();
        // StatCategoryGroupDto 생성자 파라미터에 맞춰 4개(Name, Key, TotalValue, Details) 전달
        groups.put("PHYSIQUE", new StatCategoryGroupDto("육신", "PHYSIQUE", 0, new ArrayList<>()));
        groups.put("AGILITY", new StatCategoryGroupDto("기민", "AGILITY", 0, new ArrayList<>()));
        groups.put("SPIRIT", new StatCategoryGroupDto("정신", "SPIRIT", 0, new ArrayList<>()));
        groups.put("PERCEPTION", new StatCategoryGroupDto("감각", "PERCEPTION", 0, new ArrayList<>()));

        finalStats.forEach((id, value) -> {
            StatMeta meta = metaMap.get(id);
            if (meta != null && groups.containsKey(meta.getCategory())) {
                StatCategoryGroupDto group = groups.get(meta.getCategory());

                // 1. 그룹 총합 수치 갱신
                group.setTotalValue(group.getTotalValue() + value);

                // 2. 잠재력 ID를 등급(S, A, F...)으로 변환
                int potId = potentials.getOrDefault(id, 7); // 기본값 F(7)
                String grade = gameDataManager.getPotentialGrade(potId);

                // 3. 개별 스탯 상세 정보 구성 (UserStatDto 필드에 맞춤)
                group.getDetails().add(UserStatDto.builder()
                        .id(id)
                        .category(meta.getCategory())
                        .name(meta.getName())
                        .description(meta.getDescription())
                        .value(value)
                        .growthGrade(grade) // 변환된 등급 문자열 저장
                        .build());
            }
        });

        return new ArrayList<>(groups.values());
    }
}