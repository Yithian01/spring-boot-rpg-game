package com.example.demo.repository;

import com.example.demo.domain.save.UserStatus;
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
 * user-status.json 파일 관리하는 레포지토리
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserFileRepository {

    private final ObjectMapper objectMapper;

    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    private final String USER_FILE = "user-status.json";


    /**
     * [저장] 유저 상태(UserStatus)를 JSON 파일로 저장
     * Service에서 createNewGame()이나 게임 중간 저장 시 호출
     */
    public void saveUserStatus(UserStatus userStatus) {
        try {
            // 1. 폴더가 없으면 생성
            File folder = new File(DATA_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 2. 파일 객체 생성
            File file = new File(folder, USER_FILE);

            // 3. JSON 쓰기 (INDENT_OUTPUT 옵션: 줄바꿈/들여쓰기 적용해서 보기 좋게 저장)
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, userStatus);

        } catch (IOException e) {
            log.error("유저 데이터 저장 실패", e);
            throw new RuntimeException("유저 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * JSON 파일을 읽어서 UserStatus 객체로 반환
     * (Service에서 불러야 하니까 public으로 변경!)
     */
    public UserStatus findGameUser() {
        try {
            File file = new File(DATA_DIR, USER_FILE);
            if (!file.exists()) {
                throw new RuntimeException("유저 데이터 파일이 없습니다: " + file.getAbsolutePath());
            }
            return objectMapper.readValue(file, UserStatus.class);
        } catch (IOException e) {
            log.error("유저 파일 로드 실패", e);
            throw new RuntimeException("게임 데이터를 읽을 수 없습니다.", e);
        }
    }

    /**
     * 세이브 파일 존재하는지 확인
     * @return
     */
    public boolean existsFile() {
        File file = new File(DATA_DIR, USER_FILE);
        return file.exists() && file.isFile();
    }

    /**
     * [삭제] 기존 유저 데이터 파일 삭제 (추가된 부분)
     * 새 게임 시작 시 초기화를 위해 사용
     */
    public void deleteFile() {
        try {
            // Path 객체 생성
            Path filePath = Paths.get(DATA_DIR, USER_FILE);

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