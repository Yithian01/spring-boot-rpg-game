package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
@Order(1)
public class GameDataInitializer implements ApplicationRunner {

    // 프로젝트 실행 경로 하위 /data
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== [System] 데이터 저장소 초기화 점검 ==========");

        File folder = new File(DATA_DIR);

        // 1. data 폴더가 아예 없으면 나중에 에러나니까 폴더만 생성
        if (!folder.exists()) {
            folder.mkdirs();
            log.info(" -> /data 폴더가 없어 새로 생성했습니다.");
        } else {
            log.info(" -> /data 폴더가 정상적으로 존재합니다.");
        }

        // 2. 파일 체크 (복구 로직 삭제)
        // 파일이 있든 없든 신경 쓰지 않습니다.
        // 없으면 Service에서 canContinueGame()이 false를 뱉을 것이고,
        // 유저는 자연스럽게 '새 게임' 화면으로 진입하게 됩니다.
        File userFile = new File(folder, "user-status.json");
        if (userFile.exists()) {
            log.info(" -> 기존 세이브 파일이 발견되었습니다.");
        } else {
            log.info(" -> 세이브 파일이 없습니다. (새 게임 대기 중)");
        }

        log.info("=====================================================");
    }
}