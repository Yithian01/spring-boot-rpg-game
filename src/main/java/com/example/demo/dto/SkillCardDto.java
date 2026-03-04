package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCardDto {
    private int id;
    private String name;
    private String icon;
    private String description;
    private String skillType; // PLAYER, MONSTER

    // [1] 비용 및 기본 제약
    private int turnCost;
    private int staminaCost;
    private int mpCost;
    private int hpCost;
    private boolean canAct;
    private String message;     // 사용 불가 사유 (ex: "마력 부족", "검 필요")

    // [2] 전투 메타데이터
    private String type;        // PHYSICAL, MAGIC, BUFF, DEBUFF, HEAL, PASS, ESCAPE
    private String element;     // FIRE, ICE, PHYSICAL, DARK, HOLY 등
    private List<String> requiredWeapons; // 필요한 무기군 명칭

    // [3] 효과 및 상태이상 상세
    private String effectType;  // DAMAGE, DOT, BUFF, HEAL 등
    private String status;      // 부여하는 상태이상 코드 (BURN, STUN, PAIN 등)
    private String statusName;  // UI용 상태이상 이름 (ex: "화상", "기절")
    private Integer duration;   // 지속 시간
    private Integer effectChance; // 효과 발동 확률 (%)

    // [4] 스케일링 정보 (UI에서 "공격력의 130% + 힘의 2.5배" 같은 문구 생성용)
    private List<String> scalingInfo;

    // [5] 수치 보정 정보 (버프/디버프 상세 내용)
    // 예: "공격력 1.5배 증가", "명중률 30% 감소" 등의 문자열 리스트
    private List<String> modifierDetails;

    private int realHitChance;   // 최종 계산된 명중률 (%)
    private int expectedPower;   // 현재 스탯 기준 예상 위력 (데미지/힐량)
}