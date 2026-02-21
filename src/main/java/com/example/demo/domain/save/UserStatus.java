package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserStatus {
    /* =========================
     * 0. 세이브 메타
     * ========================= */
    private int saveVersion;

    /* =========================
     * 1. 기본 정보
     * ========================= */
    private int id;
    private String name;

    private int tribeId;
    private int religionId;

    /* =========================
     * 2. 현재 자원 상태
     * ========================= */
    private int currentHp;
    private int currentMp;
    private int currentStamina;

    private int currentGold;

    /* =========================
     * 3. 전투 능력치 (현재 적용값)
     * ========================= */
    private int maxHp;
    private int maxMp;
    private int maxStamina;

    private double hpRegen;
    private double mpRegen;

    private double meleeAtk;
    private double magicAtk;

    private double critRate;
    private double critDmg;
    private double penetration;

    private double physDef;
    private double magRes;

    private double dodge;
    private double accuracy;
    private double moveSpeed;

    /* =========================
     * 4. Base Stats (B1~B24)
     * ========================= */
    private Map<Integer, Integer> baseStats;

    /* =========================
     * 4-1. Final Stats (아이템/버프가 적용된 최종값)
     * ========================= */
    // 이 필드를 추가해서 refresh 할 때마다 여기에 계산 결과(순수+보너스)를 써줍니다.
    private Map<Integer, Integer> finalStats;

    /* =========================
     * 5. 성장 / 잠재력
     * ========================= */
    private Map<Integer, Integer> potentials;

    /* =========================
     * 6. 기록
     * ========================= */
    private List<Integer> usedItemIds;

    /* =========================
     * 7. 장착 장비 (SlotType : ItemId)
     * ========================= */
    private Map<String, Integer> equippedItems = new HashMap<>() {{
        put("HEAD", 0);
        put("BODY", 0);
        put("BOOTS", 0);
        put("WEAPON", 0);
        put("SUB_WEAPON", 0);
        put("NECKLACE", 0);
        put("RING", 0);
    }};
}