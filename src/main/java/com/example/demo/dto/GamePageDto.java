package com.example.demo.dto;

import com.example.demo.domain.meta.CombatStats;
import com.example.demo.domain.save.ActiveStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Primary;

import java.security.PrivateKey;
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
    private int currentMp;
    private int maxMp;
    private int currentStamina;
    private int maxStamina;

    // 3. 재화
    private int currentGold;

    // 4. 리스트 데이터 (스탯, 전투 스탯)
    private List<StatCategoryGroupDto> statGroups;
    private CombatStats combatStats;
    
    // 5. 인벤토리 정보
    private List<ItemPageDto> items;

    // 6. 장착 장비 (Key: 슬롯명, Value: 아이템DTO)
    private Map<String, ItemPageDto> equippedItems;

    // 7. [추가] 현재 적용 중인 상태 효과 (버프/디버프)
    // 유저가 "아, 내가 지금 독에 걸렸구나" 또는 "힘 물약을 먹었구나"를 알게 해줍니다.
    private List<ActiveStatus> activeStatuses;

    private int boxPrice;
    private int boxDiscount;

    private List<String> gameLogs;
}