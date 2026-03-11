package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.ShopMeta;
import com.example.demo.domain.meta.ShopRandomConfig;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.ShopInstance;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.ShopInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final GameDataManager gameDataManager;
    private final ShopInstanceRepository shopInstanceRepository;
    private final GameFileRepository gameFileRepository;
    private final Random random = new Random();

    /**
     * 현재 상점 화면을 UI에게 열라고 표시
     * @param npcId 상점 NPC ID (String)
     */
    public void openShop(String npcId) {
        GameStatus gs = gameFileRepository.findGameStatus();
        gs.openShop(npcId);
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * 현재 상점 화면을 UI에게 닫으라고 표시
     */
    public void closeShop() {
        GameStatus gs = gameFileRepository.findGameStatus();
        gs.closeShop();
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * 아이템 구매 시 수량 감소 처리 및 저장
     * 구매 성공 여부
     * TO-BE
     * 0 : 성공
     * 1 : 상점 인스턴스 미존재
     * 2 : 재고 부족
     */
    public void decreaseStock(String npcId, int itemMetaId, int quantity) {
        GameStatus gs = gameFileRepository.findGameStatus();
        ShopInstance shop = shopInstanceRepository.findByNpcId(npcId)
                .orElse(null);

        if (shop == null) {
            gs.addLog(String.format("수량 감소 실패: 상점 인스턴스가 존재하지 않습니다."));
        }

        Map<Integer, Integer> itemQtyMap = shop.getItemQty();
        int currentQty = itemQtyMap.getOrDefault(itemMetaId, 0);

        if (currentQty < quantity) {
            gs.addLog(String.format("구매 실패: 재고 부족"));
        }

        int remainQty = currentQty - quantity;
        itemQtyMap.put(itemMetaId, remainQty);

        shopInstanceRepository.save(shop);

        gs.addLog(String.format(" 아이템 구매 완료."));
        gameFileRepository.saveGameStatus(gs);
    }

    /**
     * [핵심 로직] 특정 NPC의 상점 재고를 갱신하고 파일에 저장 (반환값 없음)
     */
    public void refreshStore(String npcId) {
        ShopMeta meta = gameDataManager.getShopMetaMap().get(npcId);
        if (meta == null) {
            log.error("상점 메타 데이터를 찾을 수 없습니다: {}", npcId);
            return;
        }

        Map<Integer, Integer> newItemQty = new HashMap<>();

        // 1. 고정 아이템 추가
        if (meta.getFixedItems() != null && !meta.getFixedItems().isEmpty()) {
            for (Integer itemId : meta.getFixedItems()) {
                newItemQty.put(itemId, 99);
            }
        }

        // 2. 랜덤 설정에 따른 추첨
        if (meta.getRandomConfigs() != null) {
            for (ShopRandomConfig config : meta.getRandomConfigs()) {
                for (int i = 0; i < config.getCount(); i++) {
                    Integer pickedId = pickRandomItem(config);
                    if (pickedId != null) {
                        newItemQty.merge(pickedId, 1, Integer::sum);
                    }
                }
            }
        }

        // 3. 인스턴스 생성 및 즉시 저장
        ShopInstance newInstance = ShopInstance.builder()
                .npcId(npcId)
                .lastRestockTime(System.currentTimeMillis())
                .itemQty(newItemQty)
                .build();

        shopInstanceRepository.save(newInstance); // 여기서 파일 쓰기 발생
        log.info("[{}] 상점 데이터 갱신 및 저장 완료", meta.getNpcName());
    }

    /**
     * 마을 상점 일괄 갱신
     */
    public void townStoreRestock() {
        gameDataManager.getShopMetaMap().keySet().stream()
                .filter(npcId -> !npcId.equals("WANDERING_MERCHANT_MYST"))
                .forEach(this::refreshStore);
        log.info("마을 전체 상점 재입고 및 저장 완료");
    }

    /**
     * 던전 상점 갱신
     */
    public void dungeonStoreRestock() {
        refreshStore("WANDERING_MERCHANT_MYST");
    }

    /**
     * 내부 랜덤 추첨 로직
     */
    private Integer pickRandomItem(ShopRandomConfig config) {
        List<ItemMeta> candidates = gameDataManager.getItemMetaMap().values().stream()
                .filter(item -> config.getType() == null || config.getType().equals(item.getType()))
                .filter(item -> config.getSlot() == null || config.getSlot().equals(item.getSlot()))
                .filter(item -> config.getSubType() == null || config.getSubType().equals(item.getSubType()))
                .filter(item -> config.getGrade() == null || config.getGrade().equals(item.getGrade()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size())).getId();
    }
}