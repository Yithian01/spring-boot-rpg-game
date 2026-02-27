package com.example.demo.repository;

import com.example.demo.domain.save.ItemInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * item-instance.json 파일을 관리하는 레포지토리
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ItemInstanceRepository {
    private final ObjectMapper objectMapper;
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    private final String ITEM_FILE = "item-instance.json";

    /**
     * [로드] 전체 아이템 Map 가져오기
     */
    public Map<String, ItemInstance> findAll() {
        try {
            File file = new File(DATA_DIR, ITEM_FILE);
            if (!file.exists() || file.length() == 0) {
                return new HashMap<>();
            }
            // JSON 객체를 Map<String, ItemInstance>으로 바로 변환
            return objectMapper.readValue(file, new TypeReference<Map<String, ItemInstance>>() {});
        } catch (IOException e) {
            log.error("아이템 인스턴스 로드 실패", e);
            return new HashMap<>();
        }
    }

    /**
     * [추가/수정] 단일 아이템 저장
     */
    public void save(ItemInstance instance) {
        Map<String, ItemInstance> all = findAll();
        all.put(instance.getInstanceId(), instance);
        saveAll(all);
    }

    /**
     * [저장] Map 전체를 파일에 쓰기
     */
    public void saveAll(Map<String, ItemInstance> instances) {
        saveMap(instances);
    }

    /**
     * --- [UUID 기반 삭제] ---
     * UUID(instanceId)를 기반으로 아이템을 삭제합니다.
     */
    public void deleteByInstanceId(String instanceId) {
        Map<String, ItemInstance> allItems = findAll();
        if (allItems.containsKey(instanceId)) {
            allItems.remove(instanceId);
            saveMap(allItems);
            log.info("아이템 인스턴스 삭제 완료: {}", instanceId);
        } else {
            log.warn("삭제하려는 아이템이 존재하지 않습니다: {}", instanceId);
        }
    }

    /**
     * --- [UUID 기반 조회] ---
     * [조회] UUID로 하나만 가져오기
     */
    public Optional<ItemInstance> findById(String uuid) {
        return Optional.ofNullable(findAll().get(uuid));
    }

    /**
     * 아이템 인스턴스 파일 저장
     * @param instances
     */
    private void saveMap(Map<String, ItemInstance> instances) {
        try {
            File folder = new File(DATA_DIR);
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, ITEM_FILE);
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, instances);
        } catch (IOException e) {
            log.error("아이템 데이터 파일 쓰기 실패", e);
            throw new RuntimeException("아이템 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * 아이템 인스턴스 파일 존재 메소드
     * @return 존재 여부
     */
    public boolean existsFile() {
        File file = new File(DATA_DIR, ITEM_FILE);
        return file.exists() && file.isFile();
    }

    /**
     * 아이템 인스턴스 파일 삭제
     * 초기화 시 사용
     */
    public void deleteFile() {
        try {
            Path filePath = Paths.get(DATA_DIR, ITEM_FILE);
            Files.deleteIfExists(filePath);
            log.info("아이템 데이터 파일 초기화 완료");
        } catch (IOException e) {
            log.error("파일 삭제 중 오류 발생", e);
        }
    }
}