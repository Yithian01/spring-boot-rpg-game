package com.example.demo.config;

import com.example.demo.domain.save.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class GameValidation {

    /**
     * 유저의 생존 여부 및 행동 가능 상태를 통합 검증합니다.
     * @return 게임오버 사유 메시지 (정상이면 null)
     */
    public String validateAlive(UserStatus user) {
        if (user.getCurrentHp() <= 0) {
            return "GameOver:이미 기력을 다해 쓰러졌습니다. 더 이상 움직일 수 없습니다.";
        }
        return null;
    }

    /**
     * (확장 예시) 특정 스탯이나 상태값에 따른 추가 검증이 필요할 때 활용
     */
    public String validateStamina(UserStatus user, int cost) {
        if (user.getCurrentStamina() < cost) {
            return "스태미나가 부족합니다!";
        }
        return null;
    }
}