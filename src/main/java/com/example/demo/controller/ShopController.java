package com.example.demo.controller;

import com.example.demo.domain.save.GameStatus;
import com.example.demo.service.GameService;
import com.example.demo.service.ShopService;
import com.example.demo.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ValidationService validationService;

    /**
     * 상점 열기
     * @param npcId 상점 주인 NPC ID
     */
    @GetMapping("/open")
    public String openShop(@RequestParam String npcId, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        shopService.openShop(npcId);

        return "redirect:/game/play";
    }

    /**
     * 상점 닫기 (나가기)
     */
    @GetMapping("/close")
    public String closeShop( RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        shopService.closeShop();

        return "redirect:/game/play";
    }

    /**
     * 아이템 구매
     * @param itemId 메타데이터상의 아이템 ID (Integer)
     * @param quantity 구매 수량
     */
    @PostMapping("/buy")
    public String buyItem( @RequestParam String npcId,
                          @RequestParam int itemId,
                          @RequestParam(defaultValue = "1") int quantity,
                          RedirectAttributes redirectAttributes) {

        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        log.info(">>> 아이템 구매 요청: itemId={}, quantity={}", itemId, quantity);
        shopService.decreaseStock(npcId, itemId, quantity);

        return "redirect:/game/play";
    }
}