# [ 스킬 유형별 밸런스 가이드라인 ]

### ■ 1. 즉발 피해 스킬 (DAMAGE Type)
*대상을 즉시 타격하여 강력한 화력을 투사하는 기술입니다.*

|  등급 (Grade)   | 명중률 (Hit)  | 자원 소모 (Cost) | 공격력 계수 (Dmg) | 스탯 계수 합 (Stat) | 관통력(penetration) | 추가 크리 대미지(critDamage) | 추가 크리티컬 확률(critRate) |
|:-------------:|:----------:|:------------:|:------------:|:--------------:|:----------------:|:---------------------:|:--------------------:|
|   **BASE**    |  70 ~ 75%  |   10 ~ 15    |     1.0      |      1.5       |       10%        |          50%          |          5%          |
|  **COMMON**   |  75 ~ 80%  |   12 ~ 20    |     1.2      |      1.5       |       15%        |          50%          |          8%          |
| **UNCOMMON**  |  60 ~ 80%  |   25 ~ 35    |     1.3      |      2.2       |       20%        |          60%          |         10%          |
|   **RARE**    |  60 ~ 80%  |   40 ~ 55    |     1.5      |      3.0       |       25%        |          70%          |         13%          |
|   **EPIC**    |  60 ~ 70%  |   60 ~ 90    |     2.0      |      4.0       |       30%        |          70%          |         15%          |
| **LEGENDARY** |  60 ~ 70%  |  100 ~ 200   |     2.5      |      5.0       |       35%        |         100%          |         20%          |

---

### ■ 2. 지속 피해 스킬 (DOT Type)
*직접 대미지가 아닌 출혈, 독, 화상 등 여러 턴에 걸쳐 총합 데미지를 주는 기술입니다.*

|  등급 (Grade)   | 명중률 (Hit) | 자원 소모 (Cost) | 공격력 계수 (Dmg) | 스탯 계수 합 (Stat) | 관통력(penetration) | 추가 크리티컬 대미지(critDamage) | 추가 크리티컬 확률(critRate)  |
|:-------------:|:---------:|:------------:|:------------:|:--------------:|:----------------:|:-----------------------:|:---------------------:|
|   **BASE**    | 70 ~ 75%  |   10 ~ 15    |     0.3      |      1.0       |        10        |           50%           |          5%           |
|  **COMMON**   | 75 ~ 80%  |   12 ~ 20    |     0.5      |      1.5       |        15        |           50%           |          8%           |
| **UNCOMMON**  | 60 ~ 80%  |   25 ~ 35    |     0.8      |      2.2       |        20        |           60%           |          10%          |
|   **RARE**    | 60 ~ 80%  |   40 ~ 55    |     1.0      |      3.0       |        25        |           70%           |          13%          |
|   **EPIC**    | 60 ~ 70%  |   60 ~ 90    |     1.3      |      4.0       |        30        |           70%           |          15%          |
| **LEGENDARY** | 60 ~ 70%  |  100 ~ 200   |     1.5      |      5.0       |        35        |          100%           |          20%          |

---

# [ 버프 및 디버프 스킬 등급별 효과 상한 가이드라인 ]

버프(BUFF)는 아군에게 이로운 효과를, 디버프(DEBUFF)는 적군에게 해로운 효과를 부여하며, 중첩 여부에 따라 전략적 가치가 결정됩니다.

---

### ■ 1. 버프 스킬 상한표 (BUFF Type)
*아군의 화력을 비율로 증폭하고, 방어 체계(%)를 고정 수치로 보강합니다.*

|  등급 (Grade)   | 자원 소모 (Cost) | **공격력 비율 증폭 (Atk 계열)** | **%스탯 고정 수치 증가 (Res, Acc, Dg 등)** | 유지 턴 (Duration) |
|:-------------:|:------------:|:----------------------:|:---------------------------------:|:---------------:|
|  **COMMON**   |    20~30     |  **x 1.10** (10% 증가)   |          **+5%** (합산 보정)          |       2턴        | 
| **UNCOMMON**  |    20~30     |  **x 1.20** (20% 증가)   |         **+10%** (합산 보정)          |       2턴        | 
|   **RARE**    |    25~50     |  **x 1.30** (30% 증가)   |         **+20%** (합산 보정)          |       3턴        | 
|   **EPIC**    |    25~50     |  **x 1.40** (40% 증가)   |         **+35%** (합산 보정)          |       3턴        | 
| **LEGENDARY** |    30~70     |  **x 1.50** (50% 증가)   |         **+50%** (합산 보정)          |       3턴        | 

---

### ■ 2. 디버프 스킬 상한표 (DEBUFF Type)
*적의 화력을 비율로 억제하고, 방어 체계(%)를 고정 수치로 깎아냅니다.*

|  등급 (Grade)   | 자원 소모 (Cost) | **공격력 비율 감소 (Atk 계열)** | **%스탯 고정 수치 감소 (Res, Acc, Dg 등)** | 유지 턴 (Duration)  | 
|:-------------:|:-------------|:----------------------:|:---------------------------------:|:----------------:|
|  **COMMON**   | 20~30        |  **x 0.90** (10% 감소)   |          **-5%** (합산 차감)          |        2턴        | 
| **UNCOMMON**  | 20~30        |  **x 0.80** (20% 감소)   |         **-10%** (합산 차감)          |        2턴        | 
|   **RARE**    | 25~50        |  **x 0.70** (30% 감소)   |         **-20%** (합산 차감)          |        2턴        | 
|   **EPIC**    | 25~50        |  **x 0.60** (40% 감소)   |         **-35%** (합산 차감)          |        2턴        | 
| **LEGENDARY** | 30~70        |  **x 0.50** (50% 감소)   |         **-50%** (합산 차감)          |        2턴        | 

---

###  스킬 생성 가이드 

[즉발 DAMAGE 유형 - A]
```shell
    {
        "id": 409,
        "name": "연쇄 전격",
        "description": "마나 회로를 가속하여 적의 전신에 전류를 흘려보냅니다.",
        "icon": "/images/skills/chain_lightning.png",
        "type": "MAGIC",         <---- PHYSICAL, MAGICAL
        "grade": "RARE",         <---- COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
        "turnCost": 2,
        "hitChance": 70,
        "cost": { "mp": 52 },  <---- hp, mp, stamina 중 다중 가능
        "damageScaling": { "magicAtk": 1.5 },
        "statScaling": { "8": 2.0, "14": 1.0 },
        "effect": {
          "type": "DAMAGE",       <---- DAMAGE
          "element": "LIGHTNING", <---- PHYSICAL, FIRE, ICE, LIGHTNING, EARTH, HOLY, DARK, CHAOS, TOXIC
          "status": "STUN",       <---- BLEED, BURN, FROZEN, STUN, POISON, PAIN
          "duration": 2,
          "chance": 50,
          "penetration": 25,
          "critDamage": 70,
          "critRate": 13
        },
        "requiredWeapons": ["WAND", "STAFF"] <---- NONE, SWORD, AXE, SPEAR, BLUNT, SHIELD, KNUCKLE, DAGGER, BOW, CROSSBOW, WAND, STAFF
    }
```

[즉발 DAMAGE 유형 - B] 
```shell
    {
        "id": 110,
        "name": "교차 베기",
        "description": "적을 빠르게 X자 형태로 베어 넘깁니다.",
        "icon": "/images/skills/cross_slash.png",
        "type": "PHYSICAL",         <---- PHYSICAL, MAGICAL
        "grade": "RARE",         <---- COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
        "turnCost": 1,
        "hitChance": 78,
        "cost": { "stamina": 12, "mp": 0 },  <---- hp, mp, stamina 중 다중 가능
        "damageScaling": { "meleeAtk": 1.2 },
        "statScaling": { "10": 1.0, "21": 0.5 },
        "effect": {
          "type": "DAMAGE",       <---- DAMAGE
          "element": "PHYSICAL", <---- PHYSICAL, FIRE, ICE, LIGHTNING, EARTH, HOLY, DARK, CHAOS, TOXIC
          "status": "BLEED",       <---- BLEED, BURN, FROZEN, STUN, POISON, PAIN
          "duration": 2,
          "chance": 25
        },
        "requiredWeapons": ["WAND", "STAFF"] <---- NONE, SWORD, AXE, SPEAR, BLUNT, SHIELD, KNUCKLE, DAGGER, BOW, CROSSBOW, WAND, STAFF
    }
```

[도트 DAMAGE 유형]
```shell
    {
        "id": 17,
        "name": "고통의 낙인",
        "description": "적의 영혼에 고통스러운 낙인을 새깁니다. 지속적인 피해를 입힙니다.",
        "icon": "/images/skills/17.png",
        "type": "MAGIC",       <---- PHYSICAL, MAGICAL
        "grade": "COMMON",     <---- COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
        "turnCost": 1,
        "hitChance": 75,
        "cost": { "hp": 20, "mp": 10 },
        "damageScaling": { "magicAtk": 0.5},
        "statScaling": {"13": 1.5},
        "effect": { 
          "type": "DOT",       <---- DOT
          "element": "DARK",   <---- PHYSICAL, FIRE, ICE, LIGHTNING, EARTH, HOLY, DARK, CHAOS, TOXIC
          "status": "PAIN",    <---- BLEED, BURN, FROZEN, STUN, POISON, PAIN
          "duration": 2,
          "chance": 100
          "penetration": 15,
          "critRate": 8
        },
        "requiredWeapons": ["NONE"]  <---- NONE, SWORD, AXE, SPEAR, BLUNT, SHIELD, KNUCKLE, DAGGER, BOW, CROSSBOW, WAND, STAFF
    }

```

[BUFF 유형]
```shell
    {
    "id": 1062,
    "name": "성령의 가호",  
    "description": "신성한 기운으로 몸을 감싸 신성 공격력과 마법 저항력을 보강합니다."
    "icon": "/images/skills/16.png",
    "grade": "RARE",  <---- COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    "type": "BUFF",   <---- BUFF
    "turnCost": 1,
    "cost": { "mp": 40, "stamina": 10 },  <---- hp, mp, stamina 중 다중 가능
    "effect": {
      "type": "BUFF",  <---- BUFF 
      "element": "HOLY",  <---- PHYSICAL, FIRE, ICE, LIGHTNING, EARTH, HOLY, DARK, CHAOS, TOXIC
      "status": "STRENGTHEN", <--- BLEED, BURN, FROZEN, STUN, POISON
      "duration": 3,  
      "chance": 100,
      "combatStatModifiers": {  <--- 배수 *연산
        "holyAtk": 1.30
        },
      "combatStatOffsets": {   <--- 합연산
        "holyPen": 10,
        "magicRes": 10
         }
      }
    }
```

[DEBUFF 유형]
```shell
    {
        "id": 16,
        "name": "쇠약의 저주",
        "description": "대상에게 어둠의 굴레를 씌워 물리 공격력을 저하시킵니다.",
        "icon": "/images/skills/16.png",
        "grade": "COMMON",      <---- COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
        "type": "DEBUFF",       <---- DEBUFF
        "turnCost": 1,
        "hitChance": 70,
        "cost": { "hp": 20, "mp": 10 },   <---- hp, mp, stamina 중 다중 가능
        "damageScaling": {},
        "statScaling": {},
        "effect": {
          "type": "DEBUFF",     <---- DEBUFF
          "element": "DARK",    <---- PHYSICAL, FIRE, ICE, LIGHTNING, EARTH, HOLY, DARK, CHAOS, TOXIC
          "status": "WEAKNESS", <---  WEAKNESS
          "duration": 2,
          "chance": 100,
          "combatStatModifiers": {  <--- 배수 *연산
            "meleeAtk": 0.9
          },
          "combatStatOffsets": { } <--- 합연산
        },
        "requiredWeapons": ["NONE"]
    }
```