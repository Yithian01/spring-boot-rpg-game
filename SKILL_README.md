# 🎮 통합 스킬 시스템 및 데미지 엔진 규격서 (v1.5)

본 문서는 게임 내 스킬 데이터 구조, 플레이어와 몬스터의 차별화된 데미지 산출 방식, 그리고 상태 이상 및 회복 메커니즘의 최종 규격을 정의합니다.

---

## 1. 스킬 기본 속성 및 시스템 구조

### 1.1 스킬 메타데이터
| 필드명 | 설명 | 비고 |
| :--- | :--- | :--- |
| `type` | 스킬 메커니즘 분류 | `PHYSICAL`, `MAGIC`, `HEAL`, `BUFF`, `DEBUFF`, `PASS`, `ESCAPE` |
| `element` | 속성 분류 | `NONE`, `PHYSICAL`, `FIRE`, `ICE`, `LIGHTNING`, `EARTH`, `HOLY`, `DARK`, `CHAOS`, `TOXIC` |
| `turnCost` | 소모 턴 수 | 스킬 시전에 필요한 턴 자원 |
| `hitChance` | 기본 명중률 | 최종 명중 판정의 기초값 |
| `cost` | 소모 자원 | `stamina`, `mp` 기반 |
| `requiredWeapons`| 무기 제한 | `SWORD`, `BOW`, `NONE`(맨손/공용) 등 |

### 1.2 속성 대응 및 방어 메커니즘
| 공격 속성 | 대응 저항 스탯 (Res) | 대응 관통 스탯 (Pen) | 방어력 (Def) 적용 여부 |
| :--- | :--- | :--- | :--- |
| **PHYSICAL** | `physRes` | `physPen` | **✅ physDef (고정치 감쇄)** |
| **ELEMENTAL**| `fireRes`, `iceRes` 등 | `firePen`, `icePen` 등 | **❌ 미적용 (비율 저항만)** |

---

## 2. 데미지 산출 엔진 (Monster vs Player)

### 2.1 대상별 데미지 원천
* **플레이어:** 장비 + 전투 스탯(`Atk`) 기반. 스탯 투자에 따른 **스케일링 배율**이 핵심 성장 요소.
* **몬스터:** 개체별 고정 **기본 공격력(`BaseAtk`)** 기반. 스탯 스케일링 없이 기본 위력 위주.

### 2.2 최종 배율 (`FinalMultiplier`) 계산
* **플레이어:** $SkillBaseMultiplier + \sum \left( \frac{getEffectiveStat300(StatID) \times Factor}{500.0} \right)$
* **몬스터:** $SkillBaseMultiplier$ (추가 배수 없음)

### 2.3 최종 데미지 공식 (Unified)
$$FinalDamage = \max\left(0, (BaseAtk \times FinalMultiplier) - TargetDef\right) \times \left( 1 - \frac{TargetRes - AttackerPen}{100} \right)$$
1. **기초 피해:** 공격력에 최종 배율을 곱함 (플레이어는 스탯 효율 합산).
2. **물리 방어(`Def`):** 물리 공격 시에만 피격자의 `physDef`를 먼저 차감 (최소 0).
3. **관통 및 저항:** `(TargetRes - AttackerPen)`을 계산하여 최종 데미지에 비율로 적용.

---

## 3. 피해 유형별 세부 메커니즘

### 3.1 즉시 피해와 상태 이상 (DAMAGE + STATUS)
* **메커니즘:** 적중 시 즉시 피해를 주고 확률적으로 상태 이상 부여.
* **상태 이상 데미지:** 틱당 대미지는 **최종 즉시 피해량의 1/3**로 보정.

### 3.2 순수 지속 피해 (DOT Type)
* **메커니즘:** 즉시 피해 없이(또는 매우 낮게) 상태 이상 부여가 주 목적.
* **상태 이상 데미지:** 보정 없이 **기술의 순수 계산 위력(100%)**이 틱당 대미지가 됨.

### 3.3 회복 메커니즘 (HEAL)
* **공식:** $\lceil (BaseStatValue \times (Scaling + StatBonusMultiplier)) \rceil$
* **특징:** 방어력이나 관통/저항을 무시하며, `magicAtk` + `holyAtk` 등을 베이스로 사용. 결과값은 무조건 올림 처리.

---

## 4. 상태 이상 중첩 및 갱신 (BURN, POISON, BLEED 등)

상태 이상이 중복 적용될 경우 아래의 로직을 따릅니다.

* **대미지 (Damage):** **누적합(Accumulated Sum)** 방식. 기존 틱 대미지에 새로운 틱 대미지를 합산.
* **지속시간 (Duration):** **최대치 갱신(Refresh to Max)** 방식. 현재 남은 시간과 스킬의 지속시간 중 더 긴 값으로 설정.

> **예시:** 화상(틱 30, 2턴 남음) 상태의 적에게 화상(틱 30, 3턴) 스킬 적중 시
> → **최종 상태: 화상(틱 60, 3턴)**

---

## 5. 최종 요약 테이블

| 스킬 분류 | 베이스 스탯 | 소프트캡 | 관통/저항 | 방어력(Def) | 특이 사항 |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **PHYSICAL** | `meleeAtk` + `속성` | ✅ | ✅ | ✅ | 고정치 감쇄 후 비율 저항 |
| **MAGIC** | `magicAtk` + `속성` | ✅ | ✅ | ❌ | 비율 저항만 적용 |
| **HEAL** | `magicAtk` + `holy` | ✅ | ❌ | ❌ | 자신/아군 대상, 올림 처리 |
| **STATUS** | 즉시 피해량 기반 | - | - | - | 즉시 피해의 1/3 데미지 |
| **DOT** | 기술 계수 기반 | ✅ | ✅ | ❌ | 감쇄 없는 순수 위력 틱딜 |

---

## 6. 밸런스 설계 가이드 (소프트캡)
* **적용 함수:** `getEffectiveStat300(stat)`
* **의도:** 플레이어의 스탯이 일정 수치(300 등)를 넘어서면 효율을 완만하게 꺾어 데미지 인플레이션을 방지하고, 몬스터의 `BaseAtk` 설계와 조화를 이룸.