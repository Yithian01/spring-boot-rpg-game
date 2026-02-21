package com.example.demo.repository;

import com.example.demo.domain.save.DungeonStatus;
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
 * dungeon-status.json 파일을 관리하는 레포지토리
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DungeonFileRepository {

    private final ObjectMapper objectMapper;

    // 데이터 저장 경로 (Town과 동일한 data 폴더 사용)
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    // 던전 상태 파일명
    private final String DUNGEON_FILE = "dungeon-status.json";

    /**
     * [저장] 던전 상태(DungeonStatus) 저장
     */
    public void saveDungeonStatus(DungeonStatus dungeonStatus) {
        try {
            File folder = new File(DATA_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File file = new File(folder, DUNGEON_FILE);

            // 예쁘게 저장 (줄바꿈 적용)
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, dungeonStatus);

            log.info("던전 데이터 저장 성공: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("던전 데이터 저장 실패", e);
            throw new RuntimeException("던전 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * [조회] 던전 상태 로드
     */
    public DungeonStatus findDungeonStatus() {
        try {
            File file = new File(DATA_DIR, DUNGEON_FILE);
            if (!file.exists()) {
                log.warn("던전 데이터 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
                return null; // 또는 빈 객체 반환 정책에 따라 결정
            }
            return objectMapper.readValue(file, DungeonStatus.class);
        } catch (IOException e) {
            log.error("던전 데이터 로드 실패", e);
            throw new RuntimeException("던전 데이터를 읽을 수 없습니다.", e);
        }
    }

    /**
     * [확인] 파일 존재 여부
     */
    public boolean existsFile() {
        File file = new File(DATA_DIR, DUNGEON_FILE);
        return file.exists() && file.isFile();
    }

    /**
     * [삭제] 던전 데이터 파일 삭제
     * 새 게임 시작 시나 던전 클리어/탈출 시 초기화를 위해 사용
     */
    public void deleteFile() {
        try {
            Path filePath = Paths.get(DATA_DIR, DUNGEON_FILE);
            boolean deleted = Files.deleteIfExists(filePath);

            if (deleted) {
                log.info("던전 데이터 삭제 완료: {}", filePath);
            }
        } catch (IOException e) {
            log.error("던전 데이터 삭제 중 오류 발생", e);
            throw new RuntimeException("던전 데이터 초기화 실패", e);
        }
    }

    /**
     * 현재 던전에서 전투(몬스터와 대치) 중인지 확인합니다.
     * @return true: 전투 중, false: 탐사 중 혹은 데이터 없음
     */
    public boolean isInBattle() {
        try {
            File file = new File(DATA_DIR, DUNGEON_FILE);
            if (!file.exists()) {
                return false;
            }

            // 파일을 읽어 상태 확인
            DungeonStatus status = objectMapper.readValue(file, DungeonStatus.class);

            // activeMonster가 null이 아니면 전투 중인 것으로 간주
            return status != null && status.getActiveMonster() != null;

        } catch (IOException e) {
            log.error("전투 상태 확인 중 오류 발생", e);
            return false;
        }
    }

    /**
     * [전투 종료] 현재 대치 중인 몬스터 정보를 제거하고 상태를 저장합니다.
     * 전투 승리/도망 성공 시 호출합니다.
     */
    public void clearBattleStatus() {
        try {
            DungeonStatus status = findDungeonStatus();
            if (status != null) {
                // 1. 전투 관련 데이터 초기화
                status.setActiveMonster(null);
                status.setPlayerRemainingTurns(0);
                status.setPlayerMaxTurns(0);

                // 2. 보상 정보 초기화 (이미 지급 완료되었다고 가정)
                status.setPendingExp(0);
                status.setPendingGold(0);

                // 3. 변경된 상태 덮어쓰기
                saveDungeonStatus(status);
                log.info("전투 종료: 몬스터 정보가 초기화되었습니다.");
            }
        } catch (Exception e) {
            log.error("전투 상태 초기화 중 오류 발생", e);
            throw new RuntimeException("전투 상태를 종료할 수 없습니다.");
        }
    }
}