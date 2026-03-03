package com.example.demo.controller;

import com.example.demo.config.GameValidation;
import com.example.demo.service.InventoryService;
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
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ValidationService validationService;

    /**
     * 장비 장착
     */
    @PostMapping("/equip")
    public String equip(@RequestParam String slot, @RequestParam String itemId, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }
        // slot: HEAD, WEAPON, BODY 등
        // itemId: 메타데이터 상의 아이템 ID --> UUID
        String message = inventoryService.equipItem(itemId, slot);

        redirectAttributes.addFlashAttribute("message", message);
        return "redirect:/game/play";
    }

    /**
     * 장비 해제
     */
    @PostMapping("/unequip")
    public String unequip(@RequestParam String slot, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String message = inventoryService.unequipItem(slot);
        redirectAttributes.addFlashAttribute("message", message);
        return "redirect:/game/play";
    }

    /**
     * 아이템 소모 (포션, 음식 등)
     */
    @PostMapping("/consume")
    public String consume(@RequestParam String itemId, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String message = inventoryService.consumeItem(itemId);
        redirectAttributes.addFlashAttribute("message", message);
        return "redirect:/game/play";
    }

    /**
     * 아이템 판매 (수량 선택 반영)
     */
    @PostMapping("/sell")
    public String sell(@RequestParam String itemId,
                       @RequestParam(defaultValue = "1") int quantity, // 수량 파라미터 추가
                       RedirectAttributes redirectAttributes) {

        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        log.info(">>> 아이템 판매 요청: itemId={}, quantity={}", itemId, quantity);

        // 서비스 메서드에도 quantity를 전달하도록 수정이 필요합니다.
        String message = inventoryService.sellItem(itemId, quantity);

        redirectAttributes.addFlashAttribute("message", message);
        return "redirect:/game/play";
    }

    /**
     * 아이템 뽑기
     * @param redirectAttributes
     * @return
     */
    @PostMapping("/gamble-item")
    public String gambleItem(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String message = inventoryService.pullRandomItem();
        redirectAttributes.addFlashAttribute("message", message);
        return "redirect:/game/play";
    }
}