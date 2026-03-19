package com.example.demo.controller;

import com.example.demo.service.BattleService;
import com.example.demo.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/battle")
@RequiredArgsConstructor
public class BattleController {

    private final BattleService battleService;
    private final ValidationService validationService;

    @PostMapping("/skill/{skillId}")
    public String useSkill(@PathVariable int skillId, @RequestParam String skillCardType, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        // 2. BattleService를 통한 스킬 로직 실행
        String resultMessage = battleService.executeSkill(skillId, skillCardType);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }
}