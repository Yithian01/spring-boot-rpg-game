package com.example.demo.domain.save;

import com.example.demo.domain.meta.CombatStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

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
    private String img;

    private int tribeId;
    private int religionId;

    /* =========================
     * 1-1. 레벨 정보
     * ========================= */
    private int level;
    private int currentExp;
    private int requiredExp;

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
    private CombatStats combatStats;

    /* =========================
     * 스탯 계층 관리 (Layered Stats)
     * ========================= */
    // 1. 순수 태생 스탯
    private Map<Integer, Integer> baseStats;

    // 2. 장비로 인한 추가 수치 (고정치 합산)
    private Map<Integer, Integer> equipmentBonusStats;

    // 3. 현재 적용 중인 버프/디버프 목록 (동적 계산용)
    private List<ActiveStatus> activeStatuses = new ArrayList<>();

    // 4. 최종 결과물 (Base + Equip) * Buff 가 적용된 결과
    // UI에서는 오직 이 finalStats만 봅니다.
    private Map<Integer, Integer> finalStats;

    /* =========================
     * 5. 성장 / 잠재력
     * ========================= */
    private Map<Integer, Integer> potentials;

    /* =========================
     * 6. 기록: 사용 아이템/잡은 몬스터 메타 ID
     * ========================= */
    private List<Integer> usedItemIds;
    private Set<Integer> defeatedMonsterIds = new HashSet<>();

    /* =========================
     * 7. 장착 장비 (SlotType : ItemId)
     * ========================= */
    private Map<String, String> equippedItems = new HashMap<>() {{
        put("HEAD", "0");
        put("BODY", "0");
        put("GLOVES", "0");
        put("BOOTS", "0");
        put("WEAPON", "0");
        put("SUB_WEAPON", "0");
        put("NECKLACE", "0");
        put("RING", "0");
        put("ARTIFACT", "0");
    }};

    /* =========================
     * 8. 스킬 정보
     * ========================= */
    private List<Integer> learnedSkillIds = new ArrayList<>(); // 배운 마법/스킬 ID 리스트

    /* =========================
     * 9. 정수 시스템 (Essence System)
     * ========================= */
    // 획득한 순간부터 바로 효과가 적용되는 정수 ID 리스트
    // 이 리스트에 들어있는 ID들은 모두 '장착' 상태로 간주합니다.
    private List<String> activeEssenceIds = new ArrayList<>();

    public Set<Integer> getDefeatedMonsterIds() {
        if (this.defeatedMonsterIds == null) {
            this.defeatedMonsterIds = new HashSet<>();
        }
        return defeatedMonsterIds;
    }
}