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

        dungeonService.initDungeon(101);

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

    /**
     * [던전] 쉬기 버튼 클릭 시 (HP/MP/STA 30% 회복)
     */
    @PostMapping("/rest")
    public String rest(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        // 3. 휴식 실행
        dungeonService.rest();
        return "redirect:/game/play";
    }
}