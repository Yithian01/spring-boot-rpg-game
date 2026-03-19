package com.example.demo.repository;

import com.example.demo.domain.save.ShopInstance;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * shop_instance.json 파일을 관리하는 레포지토리
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ShopInstanceRepository {
    private final ObjectMapper objectMapper;
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    private final String SHOP_FILE = "shop_instance.json";

    /**
     * [로드] 전체 상점 상태 Map 가져오기
     */
    public Map<String, ShopInstance> findAll() {
        try {
            File file = new File(DATA_DIR, SHOP_FILE);
            if (!file.exists() || file.length() == 0) {
                return new HashMap<>();
            }
            return objectMapper.readValue(file, new TypeReference<Map<String, ShopInstance>>() {});
        } catch (IOException e) {
            log.error("상점 인스턴스 로드 실패", e);
            return new HashMap<>();
        }
    }

    /**
     * [조회] NPC ID로 특정 상점 상태 가져오기
     */
    public Optional<ShopInstance> findByNpcId(String npcId) {
        return Optional.ofNullable(findAll().get(npcId));
    }

    /**
     * [추가/수정] 단일 상점 상태 저장
     */
    public void save(ShopInstance instance) {
        Map<String, ShopInstance> all = findAll();
        all.put(instance.getNpcId(), instance);
        saveMap(all);
    }

    /**
     * [저장] Map 전체를 파일에 쓰기
     */
    private void saveMap(Map<String, ShopInstance> instances) {
        try {
            File folder = new File(DATA_DIR);
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, SHOP_FILE);
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, instances);
        } catch (IOException e) {
            log.error("상점 데이터 파일 쓰기 실패", e);
            throw new RuntimeException("상점 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * 상점 데이터 초기화 (파일 삭제)
     */
    public void deleteFile() {
        try {
            Path filePath = Paths.get(DATA_DIR, SHOP_FILE);
            Files.deleteIfExists(filePath);
            log.info("상점 데이터 파일 초기화 완료");
        } catch (IOException e) {
            log.error("상점 파일 삭제 중 오류 발생", e);
        }
    }

    /**
     * 세이브 파일 존재하는지 확인
     * @return true/false
     */
    public boolean existsFile() {
        File file = new File(DATA_DIR, SHOP_FILE);
        return file.exists() && file.isFile();
    }
}