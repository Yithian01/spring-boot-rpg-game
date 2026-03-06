# 📊 User Stats & Mechanics Design Guide

## 1. 🧬 24종 세부 스탯 분류 (Sub-Stats)

| 분류 | ID | 스탯 명칭 | 주요 영향 범위 |
| :--- | :---: | :--- | :--- |
| **PHYSIQUE** | 1, 2 | 척추 코어 / 골밀도 | 최대 HP, 물리 방어 |
| (신체) | 3 | 대사 효율 / 면역 | HP 재생, 상태이상 저항, 휴식 효율 |
| | 10, 11, 12 | 상완 / 대흉근 / 전완근 | 물리 공격력, 노동 파워 |
| **SPIRIT** | 4, 5 | 마나 혈관 / 기억 용량 | 최대 MP |
| (정신) | 8, 9 | 마나 회로 / 계율 준수 | MP 재생, 마나 회복 효율, 상태이상 저항 |
| | 13, 14 | 에너지 응집 / 논리 연산 | 마법 공격력, 방어 관통 |
| **AGILITY** | 6, 7 | 심폐 지구력 / 대퇴사두 | 최대 Stamina, 이동 속도, 노동 효율 |
| (민첩) | 20, 21 | 신경 반응 / 비복근 | 회피율, 이동 속도, 도박 승률 |
| | 22, 23 | 관절 가동 / 공간 지각 | 회피율, 명중률 |
| **PERCEPTION** | 15, 16, 17 | 인과율 / 동체 시력 / 수지 조작 | 치명타 확률, 치명타 피해, 명중률 |
| (인지) | 18, 19 | 부패 저항 / 원소 공명 | 마법 저항 |
| | 24 | 직관 | 명중률, 도박 보너스 |
---

## 2. ⚔️ 전투 수식 (Combat Formulas)

### 🔴 생존 및 회복 (Survival)
* **Max HP**: $100 + (Stat_1 \times 10) + (Stat_2 \times 5) + (Stat_3 \times 2)$
* **Max MP**: $50 + (Stat_4 \times 10) + (Stat_5 \times 2)$
* **Max Stamina**: $100 + (Stat_6 \times 10) + (Stat_7 \times 3)$
* **HP/MP Regen**:
    * HP: $Stat_3 \times 0.5$
    * MP: $(Stat_8 \times 0.5) + (Stat_9 \times 0.2)$
* **Status Resist**: $Min(80.0, 5.0 + (Stat_3 \times 0.8) + (Stat_9 \times 0.8))$

### ⚔️ 공격 메커니즘 (Offense)
* **Melee ATK**: $(Stat_{10} \times 2.0) + (Stat_{11} \times 1.5) + (Stat_{12} \times 0.5)$
* **Magic ATK**: $(Stat_{13} \times 2.5) + (Stat_{14} \times 1.0)$
* **Crit Rate (%)**: $(Stat_{15} \times 1.0) + (Stat_{16} \times 0.5)$
* **Crit DMG (%)**: $150 + (Stat_{17} \times 2.0)$
* **Accuracy**: $(Stat_{16} \times 1.0) + (Stat_{23} \times 0.5) + (Stat_{24} \times 0.2)$

### 🛡️ 방어 메커니즘 (Defense)
* **Phys Def**: $(Stat_2 \times 1.5) + (Stat_1 \times 0.5)$
* **Mag Res**: $(Stat_{18} \times 1.0) + (Stat_{19} \times 0.5)$
* **Dodge Rate**: $(Stat_{20} \times 0.8) + (Stat_{21} \times 0.5) + (Stat_{22} \times 0.3)$

---

## 3. 🏘️ 마을 및 시스템 수식 (Town & Utility)

### ⚒️ 노동 및 이동 (Work & Move)
* **Move Speed**: $Round((Stat_{21} \times 0.1) + (Stat_7 \times 0.05), 1)$
* **Work Power**: $(Stat_{10} + Stat_{11} + Stat_{12}) / 2$
* **Work Gold**: $10 + Power$
* **Work Stamina Cost**: $Max(5, 25 - (Stat_6 + Stat_7) / 2)$

### 💤 휴식 회복량 (Restoration)
* **HP Recovery**: $(MaxHP \times 0.2) + (Stat_3 \times 5)$
* **MP Recovery**: $(MaxMP \times 0.2) + (Stat_8 \times 5)$
* **ST Recovery**: $(MaxST \times 0.3) + (Stat_3 \times 5)$

### 🎲 도박 (Gamble)
* **Win Rate (%)**: $Min(85.0, 10.0 + (Stat_{20} \times 1.0) + (Stat_{16} \times 0.5))$
* **Multiplier**: $2.0 + (Stat_{23} \times 0.5)$

---

## 💡 개발 핵심 요약 (Insights)
1. **복합 기여**: 대부분의 핵심 지표는 단일 스탯이 아닌 2~3개 스탯의 조합으로 결정됩니다.
2. **신체 부위별 특화**: 팔 근육(10~12)은 공격, 다리 근육(7, 21)은 기동성과 스태미나에 집중되어 있어 직관적인 성장이 가능합니다.
3. **방어의 세분화**: 물리 방어는 골격(2) 중심, 마법 저항은 인지/저항력(18, 19) 중심으로 이원화되어 있습니다.
4. **리스크 관리**: `Status Resist`와 `Stamina Cost` 수식은 후반부 밸런스 붕괴를 막기 위해 **상한(Cap)**과 **하한(Min)**이 설정되어 있습니다.