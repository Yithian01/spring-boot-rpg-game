package com.example.demo.controller;

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
@RequestMapping("/town")
@RequiredArgsConstructor
public class TownController {

    private final TownService townService;
    private final ValidationService validationService;

    /**
     * 노동 --> 돈
     * @param redirectAttributes town.html의 상태
     * @return 결과 반환
     */
    @PostMapping("/work")
    public String work(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = townService.performWork();
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * 휴식 --> 회복
     * @param redirectAttributes town.html의 상태
     * @return 결과 반환
     */
    @PostMapping("/rest")
    public String rest(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = townService.performRest();
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * 돈 --> 세금납부
     * @param redirectAttributes town.html 상태
     * @return 결과 반환
     */
    @PostMapping("/tax")
    public String payTax(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = townService.payTax();
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * 판돈 --> 도박
     * @param amount 판돈
     * @param redirectAttributes town.html 상태
     * @return 결과 반환
     */
    @PostMapping("/gamble")
    public String gamble(@RequestParam(defaultValue = "100") int amount, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = townService.performGamble(amount);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * 3중1택 --> 수련
     * @param type 힘/민/지
     * @param redirectAttributes town.html 상태
     * @return 결과 반환
     */
    @PostMapping("/train")
    public String train(@RequestParam String type, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }
        // type: STRENGTH, AGILITY, INTELLIGENCE 중 하나가 넘어옴
        String resultMessage = townService.performTrain(type);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

}
