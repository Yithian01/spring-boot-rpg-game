package com.example.demo.repository;

import com.example.demo.domain.save.GambleStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * gamble_status.json 파일을 관리하는 레포지토리
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GambleFileRepository {

    private final ObjectMapper objectMapper;

    // 데이터 저장 경로 (data 폴더 사용)
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    // 도박 상태 파일명
    private final String GAMBLE_FILE = "gamble_status.json";

    /**
     * [저장] 도박 상태(GambleStatus) 저장
     */
    public void saveGambleStatus(GambleStatus gambleStatus) {
        try {
            File folder = new File(DATA_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File file = new File(folder, GAMBLE_FILE);

            // 예쁘게 저장 (줄바꿈 적용)
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, gambleStatus);

            log.info("도박 데이터 저장 성공: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("도박 데이터 저장 실패", e);
            throw new RuntimeException("도박 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * [조회] 도박 상태 로드
     */
    public GambleStatus findGambleStatus() {
        try {
            File file = new File(DATA_DIR, GAMBLE_FILE);
            if (!file.exists()) {
                log.warn("도박 데이터 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
                return null;
            }
            return objectMapper.readValue(file, GambleStatus.class);
        } catch (IOException e) {
            log.error("도박 데이터 로드 실패", e);
            throw new RuntimeException("도박 데이터를 읽을 수 없습니다.", e);
        }
    }

    /**
     * [확인] 파일 존재 여부
     */
    public boolean existsFile() {
        File file = new File(DATA_DIR, GAMBLE_FILE);
        return file.exists() && file.isFile();
    }

    /**
     * [삭제] 도박 데이터 파일 삭제 (초기화 시 사용)
     */
    public void deleteFile() {
        try {
            Path filePath = Paths.get(DATA_DIR, GAMBLE_FILE);
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("도박 데이터 삭제 완료: {}", filePath);
            }
        } catch (IOException e) {
            log.error("도박 데이터 삭제 중 오류 발생", e);
            throw new RuntimeException("도박 데이터 초기화 실패", e);
        }
    }

    /**
     * 현재 어떤 게임 모드인지 확인합니다.
     * @return "NONE", "MAIN", "UNDER_OVER", "BLACKJACK" 등
     */
    public String getCurrentMode() {
        GambleStatus status = findGambleStatus();
        return (status != null) ? status.getCurrentMode() : "NONE";
    }

    /**
     * 현재 게임이 결과 화면 상태인지 확인합니다.
     * @return true: 결과 출력 중, false: 진행 중 혹은 데이터 없음
     */
    public boolean isResultStep() {
        GambleStatus status = findGambleStatus();
        return status != null && "RESULT".equals(status.getStep());
    }

    /**
     * [상태 초기화] 게임이 완전히 끝났을 때(정산 완료 후) 호출하여 초기 상태(MAIN)로 되돌립니다.
     */
    public void resetToMain() {
        try {
            GambleStatus status = findGambleStatus();
            if (status != null) {
                status.reset(); // 도메인에 정의한 reset 메소드 호출
                saveGambleStatus(status);
                log.info("도박 상태 초기화: 메인 화면으로 변경되었습니다.");
            }
        } catch (Exception e) {
            log.error("도박 상태 초기화 중 오류 발생", e);
            throw new RuntimeException("도박 상태를 초기화할 수 없습니다.");
        }
    }

    /**
     * 현재 블랙잭 게임이 진행 중(카드 배분 후 대기)인지 확인합니다.
     */
    public boolean isPlayingBlackjack() {
        GambleStatus status = findGambleStatus();
        return status != null
                && "BLACKJACK".equals(status.getCurrentMode())
                && "PLAYING".equals(status.getStep());
    }

    /**
     * 현재 걸려있는 배팅 금액을 가져옵니다.
     */
    public int getBetAmount() {
        GambleStatus status = findGambleStatus();
        return (status != null) ? status.getBetAmount() : 0;
    }
}