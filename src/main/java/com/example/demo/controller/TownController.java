package com.example.demo.controller;

import com.example.demo.service.GambleService;
import com.example.demo.service.TownService;
import com.example.demo.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/town")
@RequiredArgsConstructor
public class TownController {

    private final TownService townService;
    private final GambleService gambleService;
    private final ValidationService validationService;

    /**
     * 노동 --> 돈
     * @param redirectAttributes town.html의 상태
     * @return 결과 반환
     */
    @PostMapping("/work")
    public String work(@RequestParam String workType,RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        String resultMessage = townService.performWork(workType);
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
    @PostMapping("/old_gamble")
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
     * 4대 계통(육신, 기민, 정신, 감각) 중 하나를 선택하여 영혼 단련 수행
     * * @param type 수련 종류 (전달되는 값: "육신", "기민", "정신", "감각")
     * @param redirectAttributes 결과 메시지 및 게임 오버 상태 전달
     * @return 마을 화면으로 리다이렉트
     */
    @PostMapping("/train")
    public String train(@RequestParam String type, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }


        String resultMessage = townService.performTrain(type);
        redirectAttributes.addFlashAttribute("message", resultMessage);
        return "redirect:/game/play";
    }

    /**
     * 특정 마석 인스턴스를 사용하여 연성 가능한 스킬 카드 리스트를 생성 (Step 1)
     * @param instanceId 유저가 클릭한 마석의 고유 ID (UUID)
     */
    @PostMapping("/extract-prepare")
    public String prepareExtraction(@RequestParam String instanceId, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        townService.skillExtractionOptions(instanceId);
        return "redirect:/game/play";
    }

    /**
     * 유저가 선택한 스킬을 최종적으로 각인 (Step 2)
     * JS에서 location.href = '/town/learn-skill/' + selectedSkillId; 로 호출함
     * @param skillId 선택한 스킬의 고유 ID
     */
    @GetMapping("/learn-skill/{skillId}")
    public String learnSkill(@PathVariable String skillId, RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        log.info("스킬 각인 요청 수신 - 스킬 ID: {}", skillId);

        townService.confirmSkillExtraction(skillId);
        return "redirect:/game/play";
    }

    /**
     * [도박장 입장]
     * 단순히 도박장 메인 선택창(모달)을 활성화합니다.
     */
    @GetMapping("/gamble/open")
    public String openGambleModal(RedirectAttributes redirectAttributes) {
        // HP 체크 공통 로직 (생략 가능하면 서비스로 밀어도 됨)
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        gambleService.openGamble();

        return "redirect:/game/play";
    }

    /**
     * [도박장 나가기]
     * 단순히 도박장 메인 선택창(모달)을 활성화합니다.
     */
    @GetMapping("/gamble/close")
    public String closeGambleModal(RedirectAttributes redirectAttributes) {
        String checkMessage = validationService.checkHp();
        if (checkMessage != null && checkMessage.startsWith("GameOver")) {
            redirectAttributes.addFlashAttribute("gameOver", true);
            redirectAttributes.addFlashAttribute("message", checkMessage.split(":")[1]);
            return "redirect:/game/play";
        }

        gambleService.closeGamble();

        return "redirect:/game/play";
    }
}
