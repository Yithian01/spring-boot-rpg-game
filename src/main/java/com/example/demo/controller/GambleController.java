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

    /**
     * [UNDER_OVER, BLACKJACK] - 초기 설정 분기
     */
    @PostMapping("/start")
    public String startGamble(@RequestParam String gameType,
                              RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = gambleService.handleGameInit(gameType);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * [UNDER_OVER] - 실제 주사위 굴리기 및 결과 처리
     */
    @PostMapping("/under-over/play")
    public String playUnderOver(@RequestParam("userChoice") String userChoice,
                                @RequestParam("batAmount") int amount,
                                RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        // 2. 서비스 호출: 실제 주사위 굴리기 로직
        String resultMessage = gambleService.playUnderOverGame(userChoice, amount);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * [BLACKJACK] - 카드 받기 (게임 시작 및 첫 카드 지급)
     */
    @PostMapping("/blackjack/deal")
    public String blackjackDeal(@RequestParam(defaultValue = "100") int amount,
                                RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        String resultMessage = gambleService.startBlackjack(amount);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * [BLACKJACK] - HIT (카드 한 장 더 받기)
     */
    @PostMapping("/blackjack/hit")
    public String blackjackHit(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        String resultMessage = gambleService.hitBlackjack();
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * [BLACKJACK] - STAY (멈추고 결과 확인)
     */
    @PostMapping("/blackjack/stay")
    public String blackjackStay(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        String resultMessage = gambleService.stayBlackjack(null);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * [BLACKJACK] - DOUBLE (베팅금 2배 후 한 장만 더 받기)
     */
    @PostMapping("/blackjack/double")
    public String blackjackDouble(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            return "redirect:/game/play";
        }

        String resultMessage = gambleService.doubleDownBlackjack();
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }
}