package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.save.InventoryItem;
import com.example.demo.domain.save.InventoryStatus;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.InventoryFileRepository;
import com.example.demo.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class InventoryService {
    public static final int BOX_BASE_PRICE = 300;

    private final UserFileRepository userFileRepository;
    private final InventoryFileRepository inventoryFileRepository;
    private final StatCalculationService statCalculationService;
    private final GameDataManager gameDataManager;

    /**
     * 아이템 장착 로직
     */
    public String equipItem(int itemId, String slot) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();
        ItemMeta meta = gameDataManager.getItemMap().get(itemId);

        if (meta == null) return "아이템 정보를 찾을 수 없습니다.";

        // 1. 인벤토리 보유 확인
        InventoryItem targetItem = inventory.getItems().stream()
                .filter(item -> item.getId() == itemId).findFirst().orElse(null);
        if (targetItem == null || targetItem.getQuantity() <= 0) return "보유하지 않은 아이템입니다.";

        // [변경 포인트 1] 현재 자원 비율 유지를 위해 이전 Max 값 저장
        int preMaxHp = user.getCombatStats().getMaxHp();
        int preMaxMp = user.getCombatStats().getMaxMp();
        int preMaxSt = user.getCombatStats().getMaxStamina();

        // 2. 장착 슬롯 처리 (양손 무기 등 기존 로직 유지)
        StringBuilder messageBuilder = new StringBuilder();
        handleWeaponSlot(user, inventory, slot, meta, messageBuilder); // 헬퍼 메서드로 분리 추천

        // 기존 장착템 해제 및 새 아이템 등록
        Integer currentEquippedId = user.getEquippedItems().get(slot);
        if (currentEquippedId != null && currentEquippedId != 0) {
            addInventoryItem(inventory, currentEquippedId);
        }
        user.getEquippedItems().put(slot, itemId);
        targetItem.setQuantity(targetItem.getQuantity() - 1);
        if (targetItem.getQuantity() <= 0) inventory.getItems().remove(targetItem);

        // [변경 포인트 2] 계층형 스탯 업데이트
        // (중요) StatCalculationService에 새로 만든 장비 레이어 전용 업데이트 호출
        statCalculationService.updateEquipmentLayer(user, gameDataManager.getItemMap());

        // [변경 포인트 3] 최종 스탯 리프레시 (Base + Equip + ActiveStatus)
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());

        // 3. 자원 보정 (최대치가 늘어난 만큼 현재치도 자연스럽게 보정)
        adjustCurrentResources(user, preMaxHp, preMaxMp, preMaxSt);

        // 4. 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        messageBuilder.append(meta.getName()).append("을(를) 장착했습니다.");
        return messageBuilder.toString();
    }

    /**
     * 아이템 해제 로직 (개선 버전)
     */
    public String unequipItem(String slot) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();

        Integer equippedId = user.getEquippedItems().get(slot);
        if (equippedId == null || equippedId == 0) return "해당 슬롯에 장착된 아이템이 없습니다.";

        // 1. 장착 해제
        user.getEquippedItems().put(slot, 0);
        addInventoryItem(inventory, equippedId);

        // [변경 포인트] 레이어 업데이트 후 리프레시
        statCalculationService.updateEquipmentLayer(user, gameDataManager.getItemMap());
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());

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
     * 주무기/보조무기 장착 시 양손 무기 규칙을 처리하는 헬퍼 메서드
     */
    private void handleWeaponSlot(UserStatus user, InventoryStatus inventory, String slot, ItemMeta meta, StringBuilder messageBuilder) {
        // A. 주무기(WEAPON) 슬롯에 장착하려는 경우
        if ("WEAPON".equals(slot)) {
            // 지금 끼려는 무기가 양손 무기라면 보조무기를 강제 해제
            if (meta.isTwoHanded()) {
                Integer subId = user.getEquippedItems().get("SUB_WEAPON");
                if (subId != null && subId != 0) {
                    addInventoryItem(inventory, subId);
                    user.getEquippedItems().put("SUB_WEAPON", 0);
                    messageBuilder.append("(양손 무기로 인해 보조장비 해제) ");
                }
            }
        }
        // B. 보조무기(SUB_WEAPON) 슬롯에 장착하려는 경우
        else if ("SUB_WEAPON".equals(slot)) {
            Integer mainId = user.getEquippedItems().get("WEAPON");
            if (mainId != null && mainId != 0) {
                ItemMeta mainMeta = gameDataManager.getItemMap().get(mainId);
                // 현재 끼고 있는 주무기가 양손 무기라면 보조무기를 낄 수 없으므로 주무기를 해제
                if (mainMeta != null && mainMeta.isTwoHanded()) {
                    addInventoryItem(inventory, mainId);
                    user.getEquippedItems().put("WEAPON", 0);
                    messageBuilder.append("(양손 무기를 사용 중이라 주무기 해제) ");
                }
            }
        }
    }

    /**
     * 장비 변경 시 스탯 레이어를 갱신하고 최종 수치를 리프레시하는 공통 로직
     */
    private void refreshUserStatsAfterEquipmentChange(UserStatus user) {
        // 1. 장비 스탯 레이어(equipmentBonusStats)만 따로 계산해서 유저 객체에 저장
        statCalculationService.updateEquipmentLayer(user, gameDataManager.getItemMap());

        // 2. 최종 스탯(finalStats) 및 전투 능력치(MaxHp, Atk 등) 계산
        // 이 메서드 내부에서 baseStats + equipmentBonusStats + activeStatuses가 합산됨
        statCalculationService.refreshUserCombatStats(user, gameDataManager.getItemMap());
    }

    /**
     * 인벤토리에 아이템을 안전하게 추가하는 공통 메서드
     */
    private void addInventoryItem(InventoryStatus inventory, int itemId) {
        inventory.getItems().stream()
                .filter(i -> i.getId() == itemId)
                .findFirst()
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + 1),
                        () -> inventory.getItems().add(InventoryItem.builder()
                                .id(itemId)
                                .quantity(1)
                                .build())
                );
    }

    /**
     * 아이템 소모 로직 (포션, 음식 등)
     */
    public String consumeItem(int itemId) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();
        ItemMeta item = gameDataManager.getItemMap().get(itemId);

        if (item == null) return "아이템 정보를 찾을 수 없습니다.";

        // 1. 소모품 타입 검증
        if (!"CONSUMABLE".equals(item.getType())) {
            return "소모할 수 없는 아이템입니다.";
        }

        // 2. 인벤토리에서 해당 아이템 찾기
        // 장비와 달리 소모품은 itemId로 묶여있으므로 findFirst로 충분합니다.
        InventoryItem invItem = inventory.getItems().stream()
                .filter(i -> i.getId() == itemId)
                .findFirst()
                .orElse(null);

        if (invItem == null || invItem.getQuantity() <= 0) {
            return "아이템이 부족합니다.";
        }

        // 3. 효과 적용 및 메시지 생성
        ItemMeta.RecoveryBonus rb = item.getRecoveryBonus();
        List<String> recoveryResults = new ArrayList<>();

        if (rb != null) {
            if (rb.getHp() > 0) {
                user.setCurrentHp(Math.min(user.getCombatStats().getMaxHp(), user.getCurrentHp() + rb.getHp()));
                recoveryResults.add("HP +" + rb.getHp());
            }
            if (rb.getMp() > 0) {
                user.setCurrentMp(Math.min(user.getCombatStats().getMaxMp(), user.getCurrentMp() + rb.getMp()));
                recoveryResults.add("MP +" + rb.getMp());
            }
            if (rb.getStamina() > 0) {
                user.setCurrentStamina(Math.min(user.getCombatStats().getMaxStamina(), user.getCurrentStamina() + rb.getStamina()));
                recoveryResults.add("ST +" + rb.getStamina());
            }
        }

        // 4. 수량 차감 및 리스트 정리
        invItem.setQuantity(invItem.getQuantity() - 1);
        if (invItem.getQuantity() <= 0) {
            inventory.getItems().remove(invItem);
        }

        // 5. 변경사항 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        // 결과 피드백: "빨간 포션을 사용했습니다. (HP +30)"
        String effectMsg = recoveryResults.isEmpty() ? "" : " (" + String.join(", ", recoveryResults) + ")";
        return item.getName() + "을(를) 사용했습니다." + effectMsg;
    }

    /**
     * 아이템 판매 로직
     */
    public String sellItem(int itemId) {
        UserStatus user = userFileRepository.findGameUser();
        InventoryStatus inventory = inventoryFileRepository.findInventoryStatus();
        ItemMeta meta = gameDataManager.getItemMap().get(itemId);

        if (meta == null) return "아이템 정보를 찾을 수 없습니다.";

        // 1. 인벤토리에서 아이템 찾기
        InventoryItem targetItem = inventory.getItems().stream()
                .filter(item -> item.getId() == itemId)
                .findFirst()
                .orElse(null);

        if (targetItem == null || targetItem.getQuantity() <= 0) {
            return "판매할 아이템이 없습니다.";
        }

        // 2. 골드 추가 및 수량 차감
        int sellPrice = meta.getPrice();
        user.setCurrentGold(user.getCurrentGold() + sellPrice);

        targetItem.setQuantity(targetItem.getQuantity() - 1);
        if (targetItem.getQuantity() <= 0) {
            inventory.getItems().remove(targetItem);
        }

        // 3. 저장
        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return meta.getName() + "을(를) " + sellPrice + "G에 판매했습니다.";
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

        // 2. 등급 가중치 가져오기 및 등급 결정
        Map<String, Double> weights = statCalculationService.calculateGambleGradeWeights(stats);
        String selectedGrade = rollGrade(weights);

        // 3. 해당 등급의 아이템 리스트 필터링
        List<ItemMeta> candidateItems = gameDataManager.getItemMap().values().stream()
                .filter(item -> item.getGrade().equals(selectedGrade))
                .toList();

        // (안전장치) 만약 해당 등급 아이템이 없으면 Common으로 강제 전환
        if (candidateItems.isEmpty()) {
            candidateItems = gameDataManager.getItemMap().values().stream()
                    .filter(item -> item.getGrade().equals("COMMON"))
                    .toList();
        }

        // 4. 최종 아이템 선택 및 저장
        ItemMeta pulledItem = candidateItems.get(new java.util.Random().nextInt(candidateItems.size()));

        user.setCurrentGold(user.getCurrentGold() - finalPrice);
        addInventoryItem(inventory, pulledItem.getId());

        userFileRepository.saveUserStatus(user);
        inventoryFileRepository.saveInventoryStatus(inventory);

        return String.format("[%s] 등급! [%s]을(를) 획득했습니다. (%dG 소모)",
                pulledItem.getGrade(), pulledItem.getName(), finalPrice);
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