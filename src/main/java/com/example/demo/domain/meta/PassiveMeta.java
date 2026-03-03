package com.example.demo.domain.meta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassiveMeta {
    private int id;
    private String name;
    private String description;
    private String icon;

    // 1. 트리거 조건 (언제 발동하는가?)
    // ALWAYS: 상시 적용 (스탯업 등)
    // ON_ATTACK: 공격 시
    // ON_HIT: 피격 시
    // ON_KILL: 적 처치 시
    // ON_TURN_START: 턴 시작 시
    private String triggerType;

    // 2. 발동 확률 (0.0 ~ 1.0)
    private double chance;

    // 3. 효과 데이터
    // 기존 SkillEffect를 재사용하거나, 패시브 전용 Effect를 만듭니다.
    // private PassiveEffect effect;
}