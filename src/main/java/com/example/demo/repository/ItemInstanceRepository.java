package com.example.demo.repository;

import com.example.demo.domain.save.ItemInstance;
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
import java.util.List;
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
     * 아이템 인스턴스를 추가하거나 업데이트합니다.
     * (기존에 UUID가 있으면 덮어쓰고, 없으면 새로 생성됩니다.)
     */
    public void save(ItemInstance itemInstance) {
        Map<String, ItemInstance> allItems = findAll();
        allItems.put(itemInstance.getInstanceId(), itemInstance);
        saveMap(allItems);
    }

    /**
     * 여러 개의 아이템을 한 번에 추가/수정합니다. (드랍 시 유용)
     */
    public void saveAll(List<ItemInstance> itemInstances) {
        Map<String, ItemInstance> allItems = findAll();
        for (ItemInstance item : itemInstances) {
            allItems.put(item.getInstanceId(), item);
        }
        saveMap(allItems);
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
     * UUID로 특정 아이템 하나만 조회합니다.
     */
    public Optional<ItemInstance> findByInstanceId(String instanceId) {
        return Optional.ofNullable(findAll().get(instanceId));
    }

    /**
     * 모든 아이템 인스턴스 맵을 로드합니다.
     */
    public Map<String, ItemInstance> findAll() {
        try {
            File file = new File(DATA_DIR, ITEM_FILE);
            if (!file.exists() || file.length() == 0) {
                return new HashMap<>();
            }
            // Jackson의 TypeReference를 사용하여 Map<String, ItemInstance> 구조로 읽기
            return objectMapper.readValue(file, new com.fasterxml.jackson.core.type.TypeReference<Map<String, ItemInstance>>() {});
        } catch (IOException e) {
            log.error("아이템 데이터 로드 실패", e);
            return new HashMap<>();
        }
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