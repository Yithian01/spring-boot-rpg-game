package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemInstance {

    // 1. 고유 식별자 (UUID 기반)
    private String instanceId;

    // 2. 메타 데이터 참조 (ItemMeta의 id)
    private int itemMetaId;

    // 3. 인스턴스 전용 커스텀 정보 (몬스터 이름 접두사 등)
    // 예: "슬라임" + "의 마석"
    private String customName;

    // 4. 수량 및 상태 (마석은 수량, 장비는 강화도 등)
    private int quantity;
    private int enhancementLevel;

    // 5. 가격 정보 (몬스터 등급에 따른 가격 보정치 저장 가능)
    // 기본값은 0이며, 0일 경우 ItemMeta의 price를 사용하도록 로직 구성 가능
    private Integer priceOverride;

    /**
     * 새로운 아이템 인스턴스를 생성할 때 사용하는 정적 팩토리 메서드
     */
    public static ItemInstance create(int itemMetaId, String customName, int quantity) {
        return ItemInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .itemMetaId(itemMetaId)
                .customName(customName)
                .quantity(quantity)
                .enhancementLevel(0)
                .build();
    }
}
