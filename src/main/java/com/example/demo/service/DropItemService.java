package com.example.demo.service;

import com.example.demo.domain.meta.ItemMeta;
import com.example.demo.domain.meta.MonsterMeta;
import com.example.demo.domain.save.ItemInstance;
import com.example.demo.manager.GameDataManager;
import com.example.demo.repository.InventoryFileRepository;
import com.example.demo.repository.ItemInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DropItemService {
    private final ItemInstanceRepository instanceRepository;
    private final InventoryFileRepository inventoryRepository; // 가방 UUID 리스트 관리용
    private final GameDataManager gameDataManager; // ItemMeta 조회용

    /**
     * TO-DO
     * 전투 종료 후 드랍 아이템들을 생성하고 저장합니다.
     */
    public void processDrops(MonsterMeta monster, List<Integer> dropItemIds) {
    }

}