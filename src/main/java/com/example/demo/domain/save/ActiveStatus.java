package com.example.demo.domain.save;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 유저, 몬스터, 화면 전달 등에 사용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveStatus {
    private int skillId;
    private String ownerType; // "PLAYER" 또는 "MONSTER"
    private String name;
    private int remainingTurns;

    private String category;    // BUFF, DEBUFF, CROWD_CONTROL(CC) 등
    private String effectCode;  // STUN, BURN, SILENCE 등 로직 식별용
    private int tickDamage; // 턴당 데미지

    private Map<Integer, Double> statModifiers; // 스탯 보정 퍼센트
    private Map<Integer, Integer> statOffsets; // 스탯 보정 고정치

    private Map<String, Double> combatModifiers; // 전투스탯 보정
}