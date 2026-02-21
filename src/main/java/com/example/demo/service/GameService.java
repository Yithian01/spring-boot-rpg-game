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
        Map<Integer, TribeMeta> tribeMetaMap = gameDataManager.getTribeMap();

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

        // 1. 초기 아이템 1~10번 설정
        List<InventoryItem> initialItems = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            initialItems.add(InventoryItem.builder()
                    .id(i)                             // 메타 데이터 ID (1~10)
                    .instanceId(UUID.randomUUID().toString()) // 고유 식별자 생성
                    .quantity(1)                       // 수량 1개
                    .enhancementLevel(0)               // 강화 수치 0
                    .build());
        }
        InventoryStatus initialInventory = InventoryStatus.builder()
                .items(initialItems)
                .build();
        inventoryFileRepository.saveInventoryStatus(initialInventory);


        // 2. 메모리에서 종족 초기 정보 조회 (Stats, Gold)
        TribeInitialMeta initialMeta = gameDataManager.getTribeInitialMetaMap().get(tribeId);
        TribeMeta tribeMeta = gameDataManager.getTribeMap().get(tribeId);

        if (initialMeta == null || tribeMeta == null) {
            throw new RuntimeException("잘못된 종족 ID입니다: " + tribeId);
        }

        // 3. [랜덤 잠재력 생성]
        // 모든 스탯 ID(1~24)에 대해 S~F 등급을 랜덤으로 뽑습니다.
        Map<Integer, Integer> randomPotentials = new HashMap<>();

        for (Integer statId : gameDataManager.getStatMap().keySet()) {
            // A. 잠재력(등급) 뽑기 (GameDataManager의 확률 로직 사용)
            int growthId = gameDataManager.getRandomGrowthId();

            // B. 잠재력 맵에 저장 (예: 1번스탯 -> 1(S급))
            randomPotentials.put(statId, growthId);
        }

        Map<Integer, Integer> baseStats = initialMeta.getInitialStats();

        Map<String, Integer> initialEquippedItems = new HashMap<>();
        initialEquippedItems.put("HEAD", 0);
        initialEquippedItems.put("BODY", 0);
        initialEquippedItems.put("BOOTS", 0);
        initialEquippedItems.put("WEAPON", 0);
        initialEquippedItems.put("SUB_WEAPON", 0);
        initialEquippedItems.put("NECKLACE", 0);
        initialEquippedItems.put("RING", 0);

        // 4. UserStatus 생성
        UserStatus newUser = UserStatus.builder()
                .id(1)
                .name(tribeMeta.getName())
                .tribeId(tribeId)
                .religionId(0)
                .currentGold(initialMeta.getGold())
                .baseStats(baseStats) // DNA 주입
                .potentials(randomPotentials)
                .equippedItems(initialEquippedItems)
                .usedItemIds(new ArrayList<>())
                .build();

        // 5. 스탯 계산기 가동 (아이템Map과 함께 호출)
        // 이 한 줄로 maxHp부터 moveSpeed까지 모든 수식이 "디플로이" 됩니다.
        statCalculationService.refreshUserCombatStats(newUser, gameDataManager.getItemMap());

        // 6. 현재 체력 등을 최대치로 보정
        newUser.setCurrentHp(newUser.getMaxHp());
        newUser.setCurrentMp(newUser.getMaxMp());
        newUser.setCurrentStamina(newUser.getMaxStamina());

        // 7. 저장
        userFileRepository.saveUserStatus(newUser);

        TownStatus newTown = TownStatus.builder()
                .currentTurn(30)
                .maxTurn(30)
                .day(1).
                currentTax(100)
                .isTaxPaid(false).build();
        townFileRepository.saveTownStatus(newTown);

        GameStatus gameStatus = GameStatus.builder()
                .location(LocationType.valueOf("TOWN"))
                .dungeonId(null)
                .build();
        gameRepository.saveGameStatus(gameStatus);

        // TO-DO 초기 아이템도 설정??

        log.info(">>> 새 게임 생성 완료! (종족: {}, 잠재력 랜덤 생성됨)", tribeMeta.getName());
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
                ItemMeta meta = gameDataManager.getItemMap().get(itemId);
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
                .maxHp(user.getMaxHp())
                .currentMp(user.getCurrentMp())
                .maxMp(user.getMaxMp())
                .currentStamina(user.getCurrentStamina())
                .maxStamina(user.getMaxStamina())
                .currentGold(user.getCurrentGold())

                // 데이터 리스트
                .stats(mapUserStats(user))
                .items(inventory)
                .equippedItems(equippedMap) // 장착창 데이터

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
        TribeMeta meta = gameDataManager.getTribeMap().get(user.getTribeId());

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

        ReligionMeta meta = gameDataManager.getReligionMap().get(userReligionId);

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
        Map<Integer, StatMeta> metaMap = gameDataManager.getStatMap();

        Map<Integer, Integer> statsToDisplay = (user.getFinalStats() != null)
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
                    .value(value)
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
            ItemMeta meta = gameDataManager.getItemMap().get(invItem.getId());
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
            result.add(gameDataManager.getStatMap().get(i).getName());
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
                String statName = gameDataManager.getStatMap().get(statId).getName();
                statBonuses.add(statName + " +" + value);
            });
        }

        // 2. 전투 능력치 보너스 가공
        ItemMeta.CombatStatsBonus cb = meta.getCombatStatsBonus();
        if (cb != null) {
            if (cb.getMaxHp() != 0) statBonuses.add("최대 체력 +" + cb.getMaxHp());
            if (cb.getMaxMp() != 0) statBonuses.add("최대 마나 +" + cb.getMaxMp());
            if (cb.getMaxStamina() != 0) statBonuses.add("최대 스태미나 +" + cb.getMaxStamina());
            if (cb.getHpRegen() != 0) statBonuses.add("체력 재생 +" + cb.getHpRegen());
            if (cb.getMpRegen() != 0) statBonuses.add("마나 재생 +" + cb.getMpRegen());
            if (cb.getMeleeAtk() != 0) statBonuses.add("물리 공격력 +" + cb.getMeleeAtk());
            if (cb.getMagicAtk() != 0) statBonuses.add("마법 공격력 +" + cb.getMagicAtk());
            if (cb.getCritRate() != 0) statBonuses.add("치명타 확률 +" + cb.getCritRate() + "%");
            if (cb.getCritDmg() != 0) statBonuses.add("치명타 피해 +" + cb.getCritDmg() + "%");
            if (cb.getPenetration() != 0) statBonuses.add("관통력 +" + cb.getPenetration());
            if (cb.getPhysDef() != 0) statBonuses.add("물리 방어 +" + cb.getPhysDef());
            if (cb.getMagRes() != 0) statBonuses.add("마법 저항 +" + cb.getMagRes());
            if (cb.getDodge() != 0) statBonuses.add("회피율 +" + cb.getDodge() + "%");
            if (cb.getAccuracy() != 0) statBonuses.add("명중률 +" + cb.getAccuracy() + "%");
            if (cb.getMoveSpeed() != 0) statBonuses.add("이동 속도 +" + cb.getMoveSpeed());
        }

        // 3. 소모품 회복 효과 가공
        String recoveryEffect = "";
        if (meta.getRecoveryBonus() != null) {
            List<String> recoveries = new ArrayList<>();
            ItemMeta.RecoveryBonus rb = meta.getRecoveryBonus();
            if (rb.getHp() > 0) recoveries.add("HP " + rb.getHp() + " 회복");
            if (rb.getMp() > 0) recoveries.add("MP " + rb.getMp() + " 회복");
            if (rb.getStamina() > 0) recoveries.add("ST " + rb.getStamina() + " 회복");
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

        if (ds == null) return null;

        return DungeonPageDto.builder()
                .currentFloor(ds.getCurrentFloor())
                .dungeonId(ds.getDungeonId())
                .progress(ds.getProgress())
                .isInBattle(ds.getActiveMonster() != null)
                .activeMonster(ds.getActiveMonster())
                .playerRemainingTurns(ds.getPlayerRemainingTurns())
                .playerMaxTurns(ds.getPlayerMaxTurns())
                .battleLogs(ds.getBattleLogs())
                .pendingExp(ds.getPendingExp())
                .pendingGold(ds.getPendingGold())
                .build();
    }

}