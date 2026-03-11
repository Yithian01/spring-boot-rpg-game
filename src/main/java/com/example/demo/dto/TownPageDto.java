package com.example.demo.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TownPageDto {
    private int day;            // 현재 며칠 차인지
    private int currentTurn;    // 현재 턴
    private int maxTurn;        // 최대 턴 (보통 30)
    private int currentTax;     // 이번 년도 낼 세금
    private boolean isTaxPaid;  // 세금 납부 여부
    private boolean portalOpen; // 매달 1일에 열린 여부

    // 마석 등급별 보유 수량 (Key: 등급(1~9), Value: 보유 수량)
    // 예: {8: 5, 7: 2} -> 8등급 5개, 7등급 2개 보유 중
    private List<MagicStoneDto> magicStoneList;

    // 마석 판매 시에 ts를 저장해야 하므로 세이브에는 저장하지 X DTO에서만 사용
    private int totalMagicStoneCount;

    // 현재 연성 창에 떠 있는 스킬 카드들 (연성 버튼 클릭 시점에 채워짐)
    private List<RandomSkillCardDto> skillOptions;

    // 노동 모달 전용 정보 추가
    private List<WorkDetailDto> workOptions;
}