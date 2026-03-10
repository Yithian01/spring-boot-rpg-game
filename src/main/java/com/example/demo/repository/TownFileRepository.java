package com.example.demo.repository;

import com.example.demo.domain.save.TownStatus;
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
 * town-status.json 파일을 관리하는 레포지토리
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TownFileRepository {

    private final ObjectMapper objectMapper;
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    private final String TOWN_FILE = "town_status.json";

    /**
     * [저장] 마을 상태(TownStatus) 저장
     */
    public void saveTownStatus(TownStatus townStatus) {
        try {
            File folder = new File(DATA_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File file = new File(folder, TOWN_FILE);

            // 예쁘게 저장 (줄바꿈 적용)
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, townStatus);

        } catch (IOException e) {
            log.error("마을 데이터 저장 실패", e);
            throw new RuntimeException("마을 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * [조회] 마을 상태 로드
     */
    public TownStatus findTownStatus() {
        try {
            File file = new File(DATA_DIR, TOWN_FILE);
            if (!file.exists()) {
                throw new RuntimeException("마을 데이터 파일이 없습니다: " + file.getAbsolutePath());
            }
            return objectMapper.readValue(file, TownStatus.class);
        } catch (IOException e) {
            log.error("마을 데이터 로드 실패", e);
            throw new RuntimeException("마을 데이터를 읽을 수 없습니다.", e);
        }
    }

    /**
     * [확인] 파일 존재 여부
     */
    public boolean existsFile() {
        File file = new File(DATA_DIR, TOWN_FILE);
        return file.exists() && file.isFile();
    }

    /**
     * [삭제] 기존 유저 데이터 파일 삭제 (추가된 부분)
     * 새 게임 시작 시 초기화를 위해 사용
     */
    public void deleteFile() {
        try {
            // Path 객체 생성
            Path filePath = Paths.get(DATA_DIR, TOWN_FILE);

            // 파일이 존재하면 삭제 (없으면 아무것도 안 함 -> 에러 안 남)
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("기존 유저 데이터 삭제 완료: {}", filePath);
            } else {
                log.info("삭제할 기존 유저 데이터가 없습니다.");
            }

        } catch (IOException e) {
            log.error("유저 데이터 삭제 중 오류 발생", e);
            throw new RuntimeException("게임 초기화(파일 삭제) 실패", e);
        }
    }
}