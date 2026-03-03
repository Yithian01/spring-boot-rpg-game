package com.example.demo.controller;

import com.example.demo.service.DungeonService;
import com.example.demo.service.EssenceService;
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
    private final EssenceService essenceService;

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

    /**
     * [던전] 다음 층 이동
     */
    @PostMapping("/next-floor")
    public String nextFloor(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        // 2. 서비스에 모든 로직 위임
        dungeonService.goToNextFloor();
        return "redirect:/game/play";
    }

    /**
     * [던전] 이전 층 이동
     */
    @PostMapping("/prev-floor")
    public String prevFloor(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        dungeonService.goToPrevFloor();
        return "redirect:/game/play";
    }

    /**
     * [던전] 전투 승리 후 전리품 처리
     */
    @PostMapping("/collect")
    public String collectReward(@RequestParam(name = "action") String action, RedirectAttributes redirectAttributes) {
        // 공통 HP 검증
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        switch (action) {
            case "pickup":
                dungeonService.handlePickupEssence();
                break;

            case "discard":
                dungeonService.handleDiscardEssence();
                break;

            case "move":
                dungeonService.handleMoveOnly();
                break;
        }

        return "redirect:/game/play";
    }
}