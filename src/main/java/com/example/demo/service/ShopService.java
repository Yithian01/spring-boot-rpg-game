package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.ShopMeta;
import com.example.demo.domain.meta.ShopRandomConfig;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.domain.save.ShopInstance;
import com.example.demo.domain.save.UserStatus;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.GameFileRepository;
import com.example.demo.repository.ShopInstanceRepository;
import com.example.demo.repository.UserFileRepository;
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
    private final UserFileRepository userFileRepository;
    private final GameFileRepository gameFileRepository;
    private final Random random = new Random();

    /**
     * 세이브 파일 저장
     * @param gs 게임 상태
     * @param us 유저 상태
     * @param si 상점 상태
     */
    private void saveAll(GameStatus gs, UserStatus us, ShopInstance si){
        gameFileRepository.saveGameStatus(gs);
        userFileRepository.saveUserStatus(us);
        shopInstanceRepository.save(si);
    }

    /**
     * 반복되는 로그 기록 및 저장을 위한 헬퍼 메서드
     */
    private void logAndSave(GameStatus gs, String message) {
        gs.addLog(message);
        gameFileRepository.saveGameStatus(gs);
        log.warn(message);
    }


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
        // 1. 초기 데이터 로드
        GameStatus gs = gameFileRepository.findGameStatus();
        UserStatus us = userFileRepository.findGameUser();
        ShopInstance si = shopInstanceRepository.findByNpcId(npcId).orElse(null);

        ItemMeta itemMeta = gameDataManager.getItemMetaMap().get(itemMetaId);
        ShopMeta shopMeta = gameDataManager.getShopMetaMap().get(npcId);

        // 2. 유효성 검사 (Validation Phase)
        if (si == null || itemMeta == null || shopMeta == null) {
            logAndSave(gs, "구매 실패: 상점 또는 아이템 정보가 유효하지 않습니다.");
            return;
        }

        // 3. 재고 및 금액 계산
        int currentQty = si.getItemQty().getOrDefault(itemMetaId, 0);
        int totalPrice = (int) (itemMeta.getPrice() * shopMeta.getPriceModifier()) * quantity;

        // 4. 구매 가능 여부 체크 (Business Guard Clauses)
        if (currentQty < quantity) {
            logAndSave(gs, String.format("구매 실패: [%s]의 재고가 부족합니다.", itemMeta.getName()));
            return;
        }

        if (us.getCurrentGold() < totalPrice) {
            logAndSave(gs, String.format("구매 실패: 골드가 부족합니다. (필요: %d G)", totalPrice));
            return;
        }

        // 5. 상태 변경 (State Mutation)
        us.setCurrentGold(us.getCurrentGold() - totalPrice);
        si.getItemQty().put(itemMetaId, currentQty - quantity);

        // 6. 결과 기록 및 저장
        String successMsg = String.format("✨ [%s] %d개 구매 완료! (잔액: %d G)",
                itemMeta.getName(), quantity, us.getCurrentGold());
        gs.addLog(successMsg);

        saveAll(gs, us, si); // 한 번에 저장
        log.info("Purchase Success: {} | Remaining Stock: {}", itemMeta.getName(), currentQty - quantity);
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