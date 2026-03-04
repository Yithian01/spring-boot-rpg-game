package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.save.InventoryStatus;
import com.example.demo.domain.save.ItemInstance;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.InventoryFileRepository;
import com.example.demo.repository.ItemInstanceRepository;
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
public class InventoryService {
    public static final int BOX_BASE_PRICE = 300;

    private final UserFileRepository userFileRepository;
    private final InventoryFileRepository inventoryFileRepository;
    private final ItemInstanceRepository itemInstanceRepository;
    private final StatCalculationService statCalculationService;
    private final GameDataManager gameDataManager;

    /**
     * 아이템 장착 로직
     */
    public String equipItem(String instanceId, String slot) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();
        ItemInstance itemInstance = itemInstanceRepository.findById(instanceId).orElse(null);

        if (itemInstance == null) return "아이템 정보를 찾을 수 없습니다.";

        // [Step 1] 인벤토리에서 제거
        if (!inventory.getInstanceIds().remove(instanceId)) {
            return "보유하지 않은 아이템입니다.";
        }

        StringBuilder messageBuilder = new StringBuilder();

        // [Step 2] 스탯 비교를 위한 이전 값 저장
        int preMaxHp = user.getCombatStats().getMaxHp();
        int preMaxMp = user.getCombatStats().getMaxMp();
        int preMaxSt = user.getCombatStats().getMaxStamina();

        // [Step 3] 양손 무기 및 슬롯 전처리 (핵심!)
        handleEquipSlotConflict(user, inventory, slot, itemInstance, messageBuilder);

        // [Step 4] 대상 슬롯에 새 아이템 장착
        user.getEquippedItems().put(slot, instanceId);

        // [Step 5] 스탯 갱신 및 자원 보정
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());
        adjustCurrentResources(user, preMaxHp, preMaxMp, preMaxSt);

        // [Step 6] 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return messageBuilder.append(itemInstance.getCustomName()).append("을(를) 장착했습니다.").toString();
    }

    /**
     * 아이템 해제 로직
     */
    public String unequipItem(String slot) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();

        String equippedId = user.getEquippedItems().get(slot);
        if (equippedId == null || equippedId.equals("0")) return "장착된 아이템이 없습니다.";

        // 1. 장착 해제
        user.getEquippedItems().put(slot, "0");

        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMetaMap());

        // 2. 가방으로 돌려보내기
        ItemInstance oldItem = itemInstanceRepository.findById(equippedId).orElse(null);
        if (oldItem != null) {
            processAddItem(inventory, oldItem, false);
        }

        // 3. 리프레시 및 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return "장비를 해제했습니다.";
    }

    /**
     * 최대 생명력이 늘어난 만큼 현재 생명력을 채워주는 편의 로직
     */
    private void adjustCurrentResources(UserStatus user, int oldHp, int oldMp, int oldSt) {
        if (user.getCombatStats().getMaxHp() > oldHp) user.setCurrentHp(user.getCurrentHp() + (user.getCombatStats().getMaxHp() - oldHp));
        if (user.getCombatStats().getMaxMp() > oldMp) user.setCurrentMp(user.getCurrentMp() + (user.getCombatStats().getMaxMp() - oldMp));
        if (user.getCombatStats().getMaxStamina() > oldSt) user.setCurrentStamina(user.getCurrentStamina() + (user.getCombatStats().getMaxStamina() - oldSt));

        user.setCurrentHp(Math.min(user.getCurrentHp(), user.getCombatStats().getMaxHp()));
        user.setCurrentMp(Math.min(user.getCurrentMp(), user.getCombatStats().getMaxMp()));
        user.setCurrentStamina(Math.min(user.getCurrentStamina(), user.getCombatStats().getMaxStamina()));
    }

    /**
     * 슬롯 충돌 해결 (양손 무기 규칙 적용)
     */
    private void handleEquipSlotConflict(UserStatus user, InventoryStatus inventory, String slot, ItemInstance newItem, StringBuilder msg) {

        // 1. 공통: 해당 슬롯에 이미 아이템이 있다면 해제
        String oldId = user.getEquippedItems().get(slot);
        if (oldId != null && !"0".equals(oldId) && !oldId.isEmpty()) {
            unequipToInventory(inventory, oldId);
        }

        // 2. 무기 특수 규칙
        if ("WEAPON".equals(slot)) {
            // 내가 양손 무기를 장착하려는데 보조무기에 뭐가 있다면? 해제!
            if (newItem.isTwoHanded()) {
                String subId = user.getEquippedItems().get("SUB_WEAPON");
                if (subId != null && !subId.equals("0")) {
                    unequipToInventory(inventory, subId);
                    user.getEquippedItems().put("SUB_WEAPON", "0");
                    msg.append("(양손 무기 장착으로 보조장비 해제) ");
                }
            }
        }
        else if ("SUB_WEAPON".equals(slot)) {
            // 보조무기를 장착하려는데 주무기가 양손 무기라면? 주무기 해제!
            String mainId = user.getEquippedItems().get("WEAPON");
            if (mainId != null && !mainId.equals("0")) {
                ItemInstance mainItem = itemInstanceRepository.findById(mainId).orElse(null);
                if (mainItem != null && mainItem.isTwoHanded()) {
                    unequipToInventory(inventory, mainId);
                    user.getEquippedItems().put("WEAPON", "0");
                    msg.append("(양손 무기 해제 후 보조장비 장착) ");
                }
            }
        }
    }

    /**
     * 장착 해제된 아이템을 인벤토리로 안전하게 돌려보내는 헬퍼
     */
    private void unequipToInventory(InventoryStatus inventory, String instanceId) {
        ItemInstance item = itemInstanceRepository.findById(instanceId).orElse(null);
        if (item != null) {
            processAddItem(inventory, item, false); // 기존 존재하던 템이므로 false
        }
    }

    /**
     * 인벤토리에 아이템을 추가하는 통합 게이트웨이
     * @param isNewInstance : 완전히 새로 생성된 템이면 true, 기존에 존재하던 템의 이동이면 false
     */
    public void processAddItem(InventoryStatus inventory, ItemInstance item, boolean isNewInstance) {
        if (isNewInstance) {
            // [2번 로직] 신규 획득: 중첩 확인 후 수량 합치기 혹은 신규 등록
            acquireItem(inventory, item);
        } else {
            // [1번 로직] 단순 이동: 이미 존재하는 UUID를 가방 명단에 추가
            registerInstanceToInventory(inventory, item.getInstanceId());
        }
    }

    /**
     * [1] 기존 인스턴스 등록 (장착 해제 등)
     * 이미 존재하는 UUID를 인벤토리 리스트에 추가만 합니다.
     */
    private void registerInstanceToInventory(InventoryStatus inventory, String instanceId) {
        if (!inventory.getInstanceIds().contains(instanceId)) {
            inventory.getInstanceIds().add(instanceId);
        }
    }

    /**
     * [2] 새로운 아이템 획득 (드랍, 보상 등)
     * 소모품/재료라면 중첩 처리하고, 아니면 새로 생성합니다.
     * 소모품/재료/마석 중첩 처리 로직
     */
    private void acquireItem(InventoryStatus inventory, ItemInstance newItem) {
        // 1. 중첩 대상 확인 (소모품, 재료, 그리고 마석(ID 1~9))
        boolean isStackableType = "CONSUMABLE".equals(newItem.getType()) || "MATERIAL".equals(newItem.getType());
        boolean isMagicStone = newItem.getItemMetaId() >= 1 && newItem.getItemMetaId() <= 9;

        if (isStackableType || isMagicStone) {
            for (String id : inventory.getInstanceIds()) {
                ItemInstance existing = itemInstanceRepository.findById(id).orElse(null);

                if (existing != null && existing.getItemMetaId() == newItem.getItemMetaId()) {

                    // [핵심 추가] 마석인 경우 이름(customName)까지 같아야 수량 증가
                    if (isMagicStone) {
                        if (existing.getCustomName().equals(newItem.getCustomName())) {
                            existing.setQuantity(existing.getQuantity() + newItem.getQuantity());
                            itemInstanceRepository.save(existing);
                            return;
                        }
                        continue;
                    }

                    // 일반 소모품/재료는 MetaId만 같으면 바로 수량 증가
                    existing.setQuantity(existing.getQuantity() + newItem.getQuantity());
                    itemInstanceRepository.save(existing);
                    return;
                }
            }
        }

        // 2. 중첩 대상이 없거나 장비인 경우: 신규 등록
        itemInstanceRepository.save(newItem);
        inventory.getInstanceIds().add(newItem.getInstanceId());
    }

    /**
     * 아이템 소모 로직 (UUID 기반)
     */
    public String consumeItem(String instanceId) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();

        // 1. 인벤토리 명단에 있는지 확인 (1차 검증)
        if (!inventory.getInstanceIds().contains(instanceId)) {
            return "인벤토리에 해당 아이템이 없습니다.";
        }

        // 2. 실체(Instance) 조회 (2차 검증)
        ItemInstance ii = itemInstanceRepository.findById(instanceId).orElse(null);
        if (ii == null) {
            inventory.getInstanceIds().remove(instanceId); // 유령 아이템 청소
            inventoryFileRepository.saveInventoryStatus(inventory);
            return "아이템 정보를 찾을 수 없습니다.";
        }

        // 3. 소모 가능 타입 검증
        if (!"CONSUMABLE".equals(ii.getType())) {
            return "소모할 수 없는 아이템입니다.";
        }

        // 4. 효과 적용 (기존 로직 유지)
        Map<String, Integer> rb = ii.getRecoveryBonus();
        StringBuilder effectMsg = new StringBuilder();
        if (rb != null && !rb.isEmpty()) {
            rb.forEach((key, value) -> {
                if (value > 0) {
                    applyRecovery(user, key, value);
                    effectMsg.append(key.toUpperCase()).append(" +").append(value).append(" ");
                }
            });
        }

        // 5. [중요] 수량 차감 및 데이터 동기화
        ii.setQuantity(ii.getQuantity() - 1);

        if (ii.getQuantity() <= 0) {
            // 수량이 다 되면 인벤토리 명단에서 제거 및 인스턴스 파일 삭제
            inventory.getInstanceIds().remove(instanceId);
            itemInstanceRepository.deleteByInstanceId(instanceId);
        } else {
            // 수량이 남았다면 인스턴스 정보만 업데이트
            itemInstanceRepository.save(ii);
        }

        // 6. 결과 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return ii.getCustomName() + "을(를) 사용했습니다. (" + effectMsg.toString().trim() + ")";
    }

    /**
     * 실제로 UserStatus의 현재 자원(HP, MP 등)을 올려주는 로직
     */
    private void applyRecovery(UserStatus user, String statType, int amount) {
        switch (statType.toUpperCase()) {
            case "HP":
                int newHp = user.getCurrentHp() + amount;
                // 최대 체력을 넘지 않도록 보정
                user.setCurrentHp(Math.min(newHp, user.getCombatStats().getMaxHp()));
                break;

            case "MP":
                int newMp = user.getCurrentMp() + amount;
                user.setCurrentMp(Math.min(newMp, user.getCombatStats().getMaxMp()));
                break;

            case "STAMINA":
            case "ST":
                int newSt = user.getCurrentStamina() + amount;
                user.setCurrentStamina(Math.min(newSt, user.getCombatStats().getMaxStamina()));
                break;

            default:
                log.warn("알 수 없는 회복 스탯 타입입니다: {}", statType);
                break;
        }
    }

    /**
     * 아이템 판매 로직 (선택 수량 반영)
     */
    public String sellItem(String instanceId, int sellQty) { // 파라미터에 sellQty 추가
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();

        // 1. 보유 확인
        if (!inventory.getInstanceIds().contains(instanceId)) {
            return "판매할 아이템이 인벤토리에 없습니다.";
        }

        ItemInstance ii = itemInstanceRepository.findById(instanceId).orElse(null);
        if (ii == null) {
            inventory.getInstanceIds().remove(instanceId);
            inventoryFileRepository.saveInventoryStatus(inventory); // 정보 없으면 리스트에서 정리
            return "아이템 정보를 찾을 수 없습니다.";
        }

        // [검증] 보유 수량보다 많이 팔려고 하는지 확인
        if (ii.getQuantity() < sellQty) {
            return "보유 수량이 부족합니다. (현재: " + ii.getQuantity() + "개)";
        }

        // 2. 골드 추가 (개당 가격 * 판매 수량)
        int unitPrice = ii.getPrice();
        int totalSellPrice = unitPrice * sellQty;
        user.setCurrentGold(user.getCurrentGold() + totalSellPrice);

        // 3. 수량 차감
        ii.setQuantity(ii.getQuantity() - sellQty);

        if (ii.getQuantity() <= 0) {
            // 수량이 0 이하면 인벤토리 목록에서 제거 및 인스턴스 삭제
            inventory.getInstanceIds().remove(instanceId);
            itemInstanceRepository.deleteByInstanceId(instanceId);
        } else {
            // 수량이 남았으면 변경된 수량 저장
            itemInstanceRepository.save(ii);
        }

        // 4. 상태 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return String.format("%s %d개를 %dG에 판매했습니다.", ii.getCustomName(), sellQty, totalSellPrice);
    }

    /**
     * 랜덤 아이템 뽑기 (상점 대신 사용)
     */
    public String pullRandomItem() {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();
        Map<Integer, Integer> stats = (user.getFinalStats() != null) ? user.getFinalStats() : user.getBaseStats();

        // 1. 가격 체크
        int finalPrice = statCalculationService.calculateGambleItemCost(stats, BOX_BASE_PRICE);
        if (user.getCurrentGold() < finalPrice) return "골드가 부족합니다!";

        // 2. 등급 결정
        Map<String, Double> weights = statCalculationService.calculateGambleGradeWeights(stats);
        String selectedGrade = rollGrade(weights);

        // 3. 아이템 필터링 (마석 ID 1~9 제외 조건 추가)
        List<ItemMeta> candidateItems = gameDataManager.getItemMetaMap().values().stream()
                .filter(item -> item.getGrade().equals(selectedGrade))
                .filter(item -> item.getId() < 1 || item.getId() > 9) // [추가] 1~9번 마석 제외
                .toList();

        // 안전장치: 해당 등급에 뽑을 아이템이 없으면 COMMON에서 다시 찾음 (역시 마석 제외)
        if (candidateItems.isEmpty()) {
            candidateItems = gameDataManager.getItemMetaMap().values().stream()
                    .filter(item -> item.getGrade().equals("COMMON"))
                    .filter(item -> item.getId() < 1 || item.getId() > 9)
                    .toList();
        }

        ItemMeta pulledMeta = candidateItems.get(new java.util.Random().nextInt(candidateItems.size()));
        System.out.println("pulledMeta = " + pulledMeta );

        // 4. 아이템 객체 생성 (일단 정보만 담은 객체)
        ItemInstance newItem = ItemInstance.builder()
                .instanceId(java.util.UUID.randomUUID().toString())
                .itemMetaId(pulledMeta.getId())
                .customName(pulledMeta.getName())
                .grade(pulledMeta.getGrade())
                .type(pulledMeta.getType())
                .slot(pulledMeta.getSlot())
                .subType(pulledMeta.getSubType())
                .twoHanded(pulledMeta.isTwoHanded())
                .description(pulledMeta.getDescription())
                .quantity(1)
                .enhancementLevel(0)
                .price(pulledMeta.getPrice())
                .baseStatsBonus(pulledMeta.getBaseStatsBonus())
                .combatStatsBonus(pulledMeta.getCombatStatsBonus())
                .baseStatsBonusModifiers(pulledMeta.getBaseStatsBonusModifiers())
                .combatStatsBonusModifiers(pulledMeta.getCombatStatsBonusModifiers())
                .recoveryBonus(pulledMeta.getRecoveryBonus())
                .grantedSkillIds(pulledMeta.getGrantedSkillIds())
                .build();

        // 5. 통합 게이트웨이 호출 (여기서 유저님이 말씀하신 '깊은 확인'이 일어납니다)
        processAddItem(inventory, newItem, true);

        // 6. 상태 업데이트 및 저장
        user.setCurrentGold(user.getCurrentGold() - finalPrice);
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return String.format("[%s] 등급! [%s]을(를) 획득했습니다. (%dG 소모)",
                pulledMeta.getGrade(), pulledMeta.getName(), finalPrice);
    }

    /**
     * 가중치 맵을 기반으로 주사위를 굴려 등급을 결정하는 헬퍼 메서드
     */
    private String rollGrade(Map<String, Double> weights) {
        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = new java.util.Random().nextDouble() * totalWeight;

        double currentSum = 0;
        // LEGENDARY -> EPIC -> RARE -> COMMON 순으로 검사
        String[] grades = {"LEGENDARY", "EPIC", "RARE", "COMMON"};
        for (String grade : grades) {
            currentSum += weights.get(grade);
            if (randomValue <= currentSum) return grade;
        }
        return "COMMON";
    }
}