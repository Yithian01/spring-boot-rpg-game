package com.example.demo.controller;

import com.example.demo.service.DungeonService;
import com.example.demo.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/dungeon")
@RequiredArgsConstructor
public class DungeonController {

    private final DungeonService dungeonService;
    private final ValidationService validationService;

    /**
     * [던전 진입] 마을에서 입장 버튼 클릭 시
     */
    @PostMapping("/enter")
    public String enterDungeon(RedirectAttributes redirectAttributes) {
        // 1. 체력 검증 (이미 있는 로직)
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        if (!validationService.canEnterDungeon()) {
            redirectAttributes.addFlashAttribute("message", "🌀 아직 차원문이 열리지 않았습니다!");
            return "redirect:/game/play";
        }

        dungeonService.initDungeon();

        log.info(">>> 던전 입장 완료. 위치 전환: TOWN -> DUNGEON");
        return "redirect:/game/play";
    }

    /**
     * [던전] 던전에서 조사 버튼 클릭 시
     */
    @PostMapping("/explore")
    public String exploreDungeon(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }
        validationService.checkEndBattle();

        dungeonService.explore();
        return "redirect:/game/play";
    }

//    /**
//     * [스킬 사용] 던전 액션 패널에서 스킬 클릭 시
//     */
//    @PostMapping("/skill")
//    public String useSkill(@RequestParam int skillId, RedirectAttributes redirectAttributes) {
//        // 전투 결과 메시지 처리 (예: "슬라임에게 15의 데미지!")
//        String resultMessage = dungeonService.processSkill(skillId);
//
//        redirectAttributes.addFlashAttribute("message", resultMessage);
//        return "redirect:/game/play";
//    }

//    /**
//     * [도망치기] 전투 이탈
//     */
//    @PostMapping("/escape")
//    public String escape(RedirectAttributes redirectAttributes) {
//        boolean success = dungeonService.processEscape();
//
//        if (success) {
//            redirectAttributes.addFlashAttribute("message", "전투에서 무사히 도망쳤습니다!");
//            return "redirect:/game/play"; // 마을로 돌아감
//        } else {
//            redirectAttributes.addFlashAttribute("message", "도망에 실패했습니다! 적의 공격이 이어집니다.");
//            return "redirect:/game/play"; // 여전히 던전
//        }
//    }
}