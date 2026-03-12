package com.example.demo.controller;

import com.example.demo.service.GambleService;
import com.example.demo.service.TownService;
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
@RequestMapping("/gamble")
@RequiredArgsConstructor
public class GambleController {

    private final GambleService gambleService;
    private final ValidationService validationService;

    /**
     * [운명에 맡기기] - 메인 모달에서 즉시 실행
     * @param amount 사용자가 입력한 판돈
     */
    @PostMapping("/luck-test")
    public String luckTest(@RequestParam(defaultValue = "100") int amount,
                           RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = gambleService.luckTestGamble(amount);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    // 추후 여기에 @PostMapping("/under-over/start") 등을 별도로 분리하면 됩니다.
}