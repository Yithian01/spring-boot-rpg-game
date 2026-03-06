package com.example.demo.controller;

import com.example.demo.domain.save.DungeonStatus;
import com.example.demo.domain.save.GameStatus;
import com.example.demo.dto.CharacterSelectPageDto;
import com.example.demo.dto.DungeonPageDto;
import com.example.demo.dto.GamePageDto;
import com.example.demo.dto.TownPageDto;
import com.example.demo.service.BattleService;
import com.example.demo.service.GameService;
import com.example.demo.service.ValidationService;
import jdk.javadoc.doclet.Taglet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/game")
@RequiredArgsConstructor
public class MainController {
    private final GameService gameService;
    private final ValidationService validationService;

    /**
     * 메인 페이지 반환
     * @return
     */
    @GetMapping("/")
    public String title(Model model) {
        boolean canContinue = gameService.canContinueGame();
        model.addAttribute("canContinue", canContinue);
        return "title";
    }

    /**
     * 캐릭터 선택 페이지 반환
     * @param model
     * @return
     */
    @GetMapping("/character-select")
    public String characterSelect(Model model) {
        List<CharacterSelectPageDto> characters =
                gameService.getPlayableCharacterList();

        model.addAttribute("characters", characters);
        return "characterSelect";
    }

    /**
     * [1단계] 게임 시작 파일 생성
     * 게임 시작 요청 처리 (POST)
     */
    @PostMapping("/new")
    public String newGame(@RequestParam("tribeId") int tribeId) {
        gameService.createNewGame(tribeId);

        return "redirect:/game/play";
    }

    /**
     * [2단계] 메인 게임 화면 (GET)
     * - 리다이렉트되어 들어오거나, 나중에 '이어하기'로 들어오는 곳
     * - 여기서 파일을 읽어서 DTO를 만들고 화면에 뿌림
     */
    @GetMapping("/play")
    public String playGame(Model model) {

        // 1. 게임 파일이 존재하는지 안전 장치 (없으면 첫 화면으로 쫓아냄)
        if (!gameService.canContinueGame()) {
            return "redirect:/";
        }

        GameStatus status = gameService.getGameStatus();

        switch (status.getLocation()) {
            case TOWN:
                GamePageDto gameData = gameService.getGamePageData();

                TownPageDto townData = gameService.getTownData();

                // 3. 모델에 데이터 담기 (HTML로 전달)
                // HTML에서 ${game.xxx}, ${town.xxx} 로 쓰려면 이름("game", "town")을 맞춰야 함
                model.addAttribute("game", gameData);
                model.addAttribute("town", townData);

                log.info(">>> 게임 데이터 로딩 완료: {}", gameData.getUserName());

                System.out.println("gameData = " + gameData );
                System.out.println("townData = " + townData );

                return "town"; // town.html 템플릿 반환
            case DUNGEON:
                // 1. 공통 유저 데이터 (HP, MP, 스탯, 인벤토리, 장착템 등)
                GamePageDto gameDataForDungeon = gameService.getGamePageData();

                // 2. 던전 전용 데이터 (현재 층, 몬스터 정보, 로그 등)
                DungeonPageDto dungeonData = gameService.getDungeonData();


                if (validationService.checkForceReturn()) {
                    return "redirect:/game/play";
                }

                // 3. 모델에 각각 담기
                model.addAttribute("game", gameDataForDungeon); // 상단바, 사이드바용
                model.addAttribute("dungeon", dungeonData);   // 중앙 전투창/탐사창용

                // 4. (선택) 현재 무기에 따른 사용 가능 스킬
                //model.addAttribute("skills", gameService.getAvailableSkills());
                System.out.println("gameData = " + gameDataForDungeon );
                System.out.println("dungeonData = " + dungeonData );

                log.info(">>> 던전 화면 진입: {}층", dungeonData.getCurrentFloor());
                return "dungeon";
            default:
                throw new IllegalStateException("알 수 없는 위치");
        }
    }

}
