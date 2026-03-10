package com.example.demo.repository;

import com.example.demo.domain.save.EssenceInstance;
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
 * essence-instance.json 파일을 관리하는 레포지토리
 * 획득한 정수의 상세 데이터(스탯 보너스, 스킬 리스트 등)를 영속화합니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EssenceRepository {
    private final ObjectMapper objectMapper;
    private final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    private final String ESSENCE_FILE = "essence_instance.json";

    /**
     * [로드] 보유중인 정수 Map 가져오기
     */
    public Map<String, EssenceInstance> findAll() {
        try {
            File file = new File(DATA_DIR, ESSENCE_FILE);
            if (!file.exists() || file.length() == 0) {
                return new HashMap<>();
            }
            return objectMapper.readValue(file, new TypeReference<Map<String, EssenceInstance>>() {});
        } catch (IOException e) {
            log.error("정수 인스턴스 로드 실패", e);
            return new HashMap<>();
        }
    }

    /**
     * [추가/수정] 단일 정수 저장
     */
    public void save(EssenceInstance instance) {
        Map<String, EssenceInstance> all = findAll();
        all.put(instance.getInstanceId(), instance);
        saveMap(all);
    }

    /**
     * [조회] UUID로 특정 정수 가져오기
     */
    public Optional<EssenceInstance> findById(String uuid) {
        return Optional.ofNullable(findAll().get(uuid));
    }

    /**
     * [삭제] UUID 기반 정수 파기
     * 유저가 정수를 지울 때 호출됩니다.
     */
    public void deleteByInstanceId(String instanceId) {
        Map<String, EssenceInstance> all = findAll();
        if (all.containsKey(instanceId)) {
            all.remove(instanceId);
            saveMap(all);
            log.info("정수 인스턴스 삭제 완료: {}", instanceId);
        } else {
            log.warn("삭제하려는 정수가 존재하지 않습니다: {}", instanceId);
        }
    }

    /**
     * 실제 파일 저장 로직
     */
    private void saveMap(Map<String, EssenceInstance> instances) {
        try {
            File folder = new File(DATA_DIR);
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, ESSENCE_FILE);
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(file, instances);
        } catch (IOException e) {
            log.error("정수 데이터 파일 쓰기 실패", e);
            throw new RuntimeException("정수 데이터를 저장하는 중 오류가 발생했습니다.");
        }
    }

    /**
     * 정수 파일 초기화 (새 게임 등)
     */
    public void deleteFile() {
        try {
            Path filePath = Paths.get(DATA_DIR, ESSENCE_FILE);
            Files.deleteIfExists(filePath);
            log.info("정수 데이터 파일 초기화 완료");
        } catch (IOException e) {
            log.error("정수 파일 삭제 중 오류 발생", e);
        }
    }
}