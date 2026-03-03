package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.MonsterMeta;
import com.example.demo.domain.save.ItemInstance;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.InventoryFileRepository;
import com.example.demo.repository.ItemInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class DropItemService {
    private final ItemInstanceRepository instanceRepository;
    private final InventoryFileRepository inventoryRepository;
    private final GameDataManager gameDataManager;
    private final InventoryService inventoryService;
    private final Random random = new Random();

    /**
     * 전투 종료 후 드롭 테이블을 참조하여 아이템을 생성하고 인벤토리에 저장합니다.
     */
    public void processDrops(MonsterMeta monster) {
        var inventory = inventoryRepository.findInventoryStatus();
        List<ItemInstance> rewards = new ArrayList<>();

        // 1. 마석 생성 (확정 드랍 혹은 티어 기반)
        ItemInstance magicStone = createMonsterMagicStone(monster);
        if (magicStone != null) {
            rewards.add(magicStone);
        }

        // 2. 확률 드랍 아이템 계산 (dropTableId가 null인 경우 자연스럽게 스킵)
        String tableId = monster.getDropTableId();
        if (tableId != null) {
            var dropTable = gameDataManager.getDropTableMetaMap().get(tableId);

            if (dropTable != null && dropTable.getDrops() != null) {
                for (var dropInfo : dropTable.getDrops()) {
                    if (random.nextDouble() * 100.0 < dropInfo.getDropRate()) {
                        var meta = gameDataManager.getItemMetaMap().get(dropInfo.getItemId());
                        if (meta != null) {
                            int qty = calculateQuantity(dropInfo.getMinQty(), dropInfo.getMaxQty());
                            rewards.add(createInstanceFromMeta(meta, qty));
                        }
                    }
                }
            } else {
                log.warn("몬스터({})의 드랍 테이블 ID({})가 정의되어 있으나 데이터를 찾을 수 없습니다.", monster.getName(), tableId);
            }
        }

        // 3. 인벤토리에 일괄 추가
        // inventoryService.processAddItem 내부에서 수량 합치기(Stacking)가 처리된다고 가정합니다.
        for (ItemInstance item : rewards) {
            inventoryService.processAddItem(inventory, item, true);
        }

        // 4. 가방 상태 최종 저장
        inventoryRepository.saveInventoryStatus(inventory);
        log.info("{} 처치 보상 지급 완료: {}종 획득", monster.getName(), rewards.size());
    }

    /**
     * 수량 결정 메소드
     * @param min 최소수량
     * @param max 최대수량
     * @return 수량 반환
     */
    private int calculateQuantity(int min, int max) {
        if (min >= max) return min;
        return random.nextInt((max - min) + 1) + min;
    }

    /**
     * 몬스터 정보를 바탕으로 커스텀 마석 인스턴스를 생성합니다.
     */
    private ItemInstance createMonsterMagicStone(MonsterMeta monster) {
        ItemMeta stoneMeta = gameDataManager.getItemMetaMap().get(monster.getTier());

        if (stoneMeta == null || !"MAGIC_STONE".equals(stoneMeta.getSubType())) {
            log.warn("티어 {}에 해당하는 마석 메타를 찾을 수 없습니다.", monster.getTier());
            return null;
        }

        return ItemInstance.builder()
                .instanceId(java.util.UUID.randomUUID().toString())
                .itemMetaId(stoneMeta.getId())
                .customName(monster.getName() + "의 " + stoneMeta.getName())
                .grade(stoneMeta.getGrade())
                .type(stoneMeta.getType())
                .subType(stoneMeta.getSubType())
                .description(stoneMeta.getDescription())
                .price(stoneMeta.getPrice())
                .quantity(1)
                .build();
    }

    /**
     * 일반 아이템 메타로부터 인스턴스를 생성합니다.
     */
    private ItemInstance createInstanceFromMeta(ItemMeta meta, int qty) { // qty 추가
        return ItemInstance.builder()
                .instanceId(java.util.UUID.randomUUID().toString())
                .itemMetaId(meta.getId())
                .customName(meta.getName())
                .grade(meta.getGrade())
                .type(meta.getType())
                .slot(meta.getSlot())
                .subType(meta.getSubType())
                .twoHanded(meta.isTwoHanded())
                .description(meta.getDescription())
                .price(meta.getPrice())
                .quantity(qty)
                .baseStatsBonus(meta.getBaseStatsBonus())
                .combatStatsBonus(meta.getCombatStatsBonus())
                .recoveryBonus(meta.getRecoveryBonus())
                .build();
    }
}