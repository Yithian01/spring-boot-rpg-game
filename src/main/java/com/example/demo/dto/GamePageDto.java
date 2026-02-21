package com.example.demo.dto;

import com.example.demo.domain.save.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamePageDto {
    // 1. 유저 기본 정보
    private String img;
    private String userName;        // 유저 이름
    private UserTribeDto tribe;     // 종족 정보 (id, name, description)
    private UserReligionDto religion; // 종교 정보 (id, name, description)

    // 2. 생존 자원 (Health, Mana, Stamina)
    private int currentHp;
    private int maxHp;
    private int currentMp;          // 추가
    private int maxMp;              // 추가
    private int currentStamina;     // 추가
    private int maxStamina;         // 추가

    // 3. 재화
    private int currentGold;        // 추가

    // 4. 리스트 데이터 (스탯, 인벤토리)
    private List<UserStatDto> stats;
    private List<ItemPageDto> items;

    // 5. 장착 장비 (Key: 슬롯명, Value: 아이템DTO)
    // HTML에서 장착창을 구현할 때 필수로 필요합니다.
    private Map<String, ItemPageDto> equippedItems;

    private int boxPrice;
    private int boxDiscount;
}