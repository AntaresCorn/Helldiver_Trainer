# Helldiver Trainer 离线数据库（Wiki）人工更新指南

本手册用于日常手动新增、修正或随游戏补丁更新战备、武器、敌人与来源数据。所有数据只需改 `wiki-content/`
下的 JSON，构建时会自动校验并同步生成离线 Wiki 数据包与训练器清单。

---

## 1. 目录分工：改哪里，别改哪里

### 可以并应该手动修改的目录

```text
wiki-content/
├── sources.json                # 资料来源登记表（新增出处先来这里登记，wiki_gg 须填 url 与 license）
├── rules/
│   └── damage_rules.json       # 按游戏补丁维护穿甲阶梯系数
├── anatomy/                    # 敌人多视角底图声明与归一化多边形热区（<enemyId>.json）
├── assets/
│   └── anatomy/                # 敌人各视角 WebP 底图（<= 1200x900，<= 350 KB，严禁 SVG）
├── matchups/
│   ├── evidence.json           # 实测事实表 MatchEvidence
│   └── recommendations.json    # 按部位状态与玩家目的分组推荐表 PartRecommendation
├── stratagems/                 # 战备（3层，按 7 个分类拆文件，顶层 {"entries": [...]}）
│   ├── eagle.json              # category = "eagle"（飞鹰）
│   ├── orbital.json            # category = "orbital"（轨道）
│   ├── support_weapon.json     # category = "support_weapon"（支援武器，留在战备目录下）
│   ├── backpack.json           # category = "backpack"（背包）
│   ├── vehicle.json            # category = "vehicle"（载具）
│   ├── defensive.json          # category = "defensive"（防守）
│   └── mission.json            # category = "mission"（任务）
├── weapons/                    # 武器（4层，weapons/<slot>/<weaponClass>.json，顶层 {"entries": [...]}）
│   ├── primary/                # 主武器：assault_rifle / marksman_rifle / submachine_gun / shotgun / explosive / energy_based / special
│   ├── secondary/              # 副武器：pistol / melee / special
│   └── throwable/              # 投掷物：standard / special
└── enemies/                    # 敌人（4层，enemies/<faction>/<sizeClass>.json，顶层 {"entries": [...]}）
    ├── terminid/               # 终结族：small.json / medium.json / heavy.json / massive.json
    ├── automaton/              # 机器人：small.json / medium.json / heavy.json / massive.json
    └── illuminate/             # 光能者：small.json / medium.json / heavy.json / massive.json
```

- **图标文件存放位置**：`composeApp/src/commonMain/moko-resources/images/*.svg`
    - 放入 `my_new_icon.svg` 后，JSON 中的 `"iconKey"` 就填 `"my_new_icon"`（不带 `.svg` 后缀）。

### 绝对不要手改的目录和文件

- `composeApp/src/commonMain/composeResources/files/wiki/`（`manifest.json`、`index.json`、
  `details/*.json`、`rules.json`、`anatomy/*.json`、`anatomy_assets/*.webp`、`matchups/*.json`
  ）：构建时全量覆盖生成，且已加入 `.gitignore`。
- `composeApp/build/generated/wiki/kotlin/`（`GeneratedStratagemCatalog.kt`、`WikiIconRegistry.kt`
  ）：构建时自动生成。
- `composeApp/src/commonMain/kotlin/cn/antares/helldiver_trainer/util/StratagemStore.kt`：只负责读取生成的清单，
  **不再手写战备**。

> **重要提醒**：`WikiValidator` 使用严格 JSON 解析（`ignoreUnknownKeys = false`）， **不要在 JSON
里自行发明未定义的字段名**，也不要把注释写进 JSON，否则构建校验会直接报错。

---

## 2. 字段与合法枚举速查表

| 字段                         | 合法值                                                                                                                                                                                                                                                                                                                   | 说明                                                                                                                                             |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `sources[].sourceType`       | `project_migration`、`wiki_gg`、`patch_notes`、`official`                                                                                                                                                                                                                                                                | 来源类型；`wiki_gg` 必须填写 `url` 与 `license`（如 `CC BY-SA 4.0` / `CC BY-NC-SA 4.0`）及 `attribution`                                         |
| `kind`                       | `stratagem`、`weapon`、`enemy`                                                                                                                                                                                                                                                                                           | 条目大类                                                                                                                                         |
| `components[].componentKind` | `direct`、`explosive`、`fire`、`status`                                                                                                                                                                                                                                                                                  | 武器攻击分量类型（用于多分量穿甲与爆炸抗性拆解）                                                                                                 |
| `onBreak[].effectType`       | `expose_part`、`disable_ability`、`bleed_out`、`fatal`                                                                                                                                                                                                                                                                   | 部位破坏效果；`expose_part` 必须填写 `targetPartId`                                                                                              |
| `partState`                  | `intact`（完好）、`broken`（破甲后）                                                                                                                                                                                                                                                                                     | 部位状态（用于 `visibleWhen`、实测记录与推荐分组）                                                                                               |
| `goal`                       | `strip_armor`（破除外甲）、`kill`（直接击杀）、`disable_ability`（限制能力）、`attack_exposed`（攻击暴露肉体）                                                                                                                                                                                                           | 推荐目标分组                                                                                                                                     |
| `verdict`                    | `penetrates`、`reduced`、`ricochet`、`strips_armor`、`kills`、`blocked`、`unknown`                                                                                                                                                                                                                                       | 实测/推算结论枚举                                                                                                                                |
| `stratagem.category`         | `eagle`、`orbital`、`support_weapon`、`backpack`、`vehicle`、`defensive`、`mission`                                                                                                                                                                                                                                      | **必须与所在 `.json` 文件名完全一致**                                                                                                            |
| `stratagem.directions`       | `UP`、`RIGHT`、`DOWN`、`LEFT`                                                                                                                                                                                                                                                                                            | 非空数组，按实际按键顺序填写                                                                                                                     |
| `weapon.slot`                | `primary`、`secondary`、`throwable`                                                                                                                                                                                                                                                                                      | 主武器 / 副武器 / 投掷物（**支援武器在 `stratagems/support_weapon.json` 维护**）；**必须与所在子目录名一致**                                     |
| `weapon.weaponClass`         | `primary`：`assault_rifle`（突击步枪）、`marksman_rifle`（射手步枪）、`submachine_gun`（冲锋枪）、`shotgun`（霰弹枪）、`explosive`（爆炸武器）、`energy_based`（能量武器）、`special`（特殊）<br>`secondary`：`pistol`（手枪）、`melee`（近战）、`special`（特殊）<br>`throwable`：`standard`（标准）、`special`（特殊） | **必须与所在 `.json` 文件名一致**                                                                                                                |
| `enemy.faction`              | `terminid`、`automaton`、`illuminate`                                                                                                                                                                                                                                                                                    | 终结族 / 机器人 / 光能者；**必须与所在子目录名一致**                                                                                             |
| `enemy.sizeClass`            | `small`（小型）、`medium`（中型）、`heavy`（重型）、`massive`（巨型）                                                                                                                                                                                                                                                    | **必须与所在 `.json` 文件名一致**                                                                                                                |
| `relations[].type`           | `calls_in`（仅 `stratagem -> weapon`）、`variant_of`（同类 `weapon -> weapon` 或 `enemy -> enemy`）                                                                                                                                                                                                                      | 禁止自指与成环；**单向录入即可，构建时自动生成反向链接**                                                                                         |
| 数值四态 `status`            | `known`、`unknown`、`not_applicable`、`unlimited`                                                                                                                                                                                                                                                                        | `known` 必须填非负 `value`；其他三态 **不能写 `value`**（或必须为 `null`）；`unlimited` 仅允许用于 `stratagem.uses` 与 `weapon.magazineCapacity` |

### 数值字段常用写法

```json
{ "status": "known", "value": 180.0, "unit": "seconds" }
{ "status": "unknown" }
{ "status": "not_applicable" }
{ "status": "unlimited" }
```

- 常用单位渲染约定：`seconds` 显示为 `秒`，`rounds` 显示为 `发`，`rpm` 显示为 `发/分`，其他字符串原样拼在数值后。
- 若某个具体数值的出处或核验补丁与条目默认不同，可在该数值对象内覆盖 `sourceId`、`verifiedPatch`、
  `condition`：

```json
{
  "status": "known",
  "value": 10,
  "unit": "rounds",
  "sourceId": "wikigg_autocannon",
  "verifiedPatch": "01.001.200",
  "condition": "双发弹夹装填"
}
```

---

## 3. 五类常见更新操作步骤（SOP）

### 场景 1：新增一个战备

1. **放图标**：将 `.svg` 图标放入 `composeApp/src/commonMain/moko-resources/images/`（如
   `sw_new_gun.svg`）。
2. **登记来源**：若来源不是既有 `project_catalog_v1_8_0`，先在 `wiki-content/sources.json` 增加一条
   `sourceId`。
3. **加入对应分类文件**：打开 `wiki-content/stratagems/<category>.json`，在 `"entries"` 数组内追加一条。

**两条关键规则：**

- **想让该战备参与“战备英雄”训练**：填写 `training: { "legacyId": "...", "order": ... }`，且 `iconKey`
  必填、`legacyId` 全局唯一、`order` 必须接在现有最大序号后面（当前为 `114`，下一个是 `115`，必须 `1..N`
  连续不能跳号）。
- **只想在 Wiki 展示、不进训练器**（例如特殊剧情/临时战备）： **直接省略 `training` 字段**即可。

```json
{
  "entryId": "hd2.stratagem.sw_example",
  "kind": "stratagem",
  "nameZh": "示例支援武器",
  "nameEn": "Example Support Weapon",
  "aliases": [
    { "locale": "zh-CN", "text": "示例枪" }
  ],
  "summary": "一句话简介（仅作详情与列表卡片展示，不参与搜索）。",
  "iconKey": "sw_new_gun",
  "sourceId": "wikigg_example",
  "verifiedPatch": "01.002.000",
  "stratagem": {
    "category": "support_weapon",
    "directions": ["DOWN", "LEFT", "RIGHT", "UP"],
    "callInSeconds": { "status": "known", "value": 7.5, "unit": "seconds" },
    "cooldownSeconds": { "status": "known", "value": 480.0, "unit": "seconds" },
    "uses": { "status": "unlimited" },
    "unlockRequirement": "15级 / 10000申购点",
    "training": {
      "legacyId": "sw_34",
      "order": 115
    }
  },
  "relations": []
}
```

---

### 场景 2：新增一把武器（含改型关联）

1. 在 `wiki-content/sources.json` 登记来源（如 `wikigg_sickle`）。
2. 打开对应的 `wiki-content/weapons/<slot>/<weaponClass>.json`（例如主武器能量武器：
   `wiki-content/weapons/primary/energy_based.json`；副武器近战：`weapons/secondary/melee.json`；投掷物标准：
   `weapons/throwable/standard.json`），在 `"entries"` 数组内追加一条：

```json
{
  "entries": [
    {
      "entryId": "hd2.weapon.sickle",
      "kind": "weapon",
      "nameZh": "LAS-16 镰刀",
      "nameEn": "LAS-16 Sickle",
      "aliases": [
        { "locale": "zh-CN", "text": "镰刀" },
        { "locale": "en", "text": "Sickle" }
      ],
      "summary": "激光脉冲突击步枪，控制好散热即可无限续航。",
      "sourceId": "wikigg_sickle",
      "verifiedPatch": "01.002.000",
      "weapon": {
        "slot": "primary",
        "weaponClass": "energy_based",
        "configurations": [
          {
            "configId": "base",
            "labelZh": "基础配置",
            "modelCode": "LAS-16",
            "magazineCapacity": { "status": "not_applicable" },
            "fireRateRpm": { "status": "known", "value": 750, "unit": "rpm" },
            "fireModes": ["全自动"],
            "attacks": [
              {
                "attackId": "laser_pulse",
                "nameZh": "激光脉冲",
                "damage": { "status": "known", "value": 55 },
                "armorPenetration": { "status": "known", "value": 2 }
              }
            ]
          }
        ]
      },
      "relations": []
    }
  ]
}
```

- **如果是改型武器**（例如 `LAS-16` 的派生型）：在 `relations` 中加
  `{ "type": "variant_of", "targetId": "hd2.weapon.sickle" }`，详情页会自动双向显示“基础型号 /
  已收录变种”。
- **支援武器去哪里维护**：支援武器继续在 `wiki-content/stratagems/support_weapon.json` 维护，不放入
  `weapons/` 目录。
- **没有图标怎么办**：`iconKey` 可省略或写 `null`，UI 会自动显示默认武器图标。

---

### 场景 3：新增敌人或敌人变种

#### A. 新增基础型敌人

打开对应 `wiki-content/enemies/<faction>/<sizeClass>.json`（例如终结族巨型：
`enemies/terminid/massive.json`），在 `"entries"` 数组内追加一条：

- 阵营图标可以直接复用现有 `moko-resources` 里的 `"ic_terminid"`、`"ic_automaton"` 或
  `"ic_illuminate"`。

```json
{
  "entries": [
    {
      "entryId": "hd2.enemy.bile_titan",
      "kind": "enemy",
      "nameZh": "胆汁泰坦",
      "nameEn": "Bile Titan",
      "aliases": [
        { "locale": "zh-CN", "text": "泰坦" }
      ],
      "summary": "终结族超重型单位，头部覆盖重甲，腹部毒囊可破坏。",
      "iconKey": "ic_terminid",
      "sourceId": "wikigg_bile_titan",
      "verifiedPatch": "01.002.000",
      "enemy": {
        "faction": "terminid",
        "sizeClass": "massive",
        "familyKey": "bile_titan",
        "parts": [
          {
            "partId": "head",
            "nameZh": "头部甲壳",
            "armor": { "status": "known", "value": 4 },
            "notes": "重穿反坦克武器直击头部可快速击杀。"
          },
          {
            "partId": "bile_sac",
            "nameZh": "腹部毒囊",
            "armor": { "status": "known", "value": 0 },
            "notes": "破坏后失去远程喷吐能力。"
          }
        ]
      },
      "relations": []
    }
  ]
}
```

#### B. 新增变种敌人（推荐用继承 + 局部覆写）

如果变种大部分部位和基础型一样，只需在对应 `enemies/<faction>/<sizeClass>.json` 的 `"entries"` 内写
`extends` + `partOverrides`（构建时会自动展开成独立完整部位表）：

```json
{
  "entryId": "hd2.enemy.charger_spore",
  "kind": "enemy",
  "nameZh": "孢子强袭虫",
  "nameEn": "Spore Charger",
  "aliases": [{ "locale": "zh-CN", "text": "迷雾牛" }],
  "summary": "周身散发孢子迷雾的强袭虫变种。",
  "iconKey": "ic_terminid",
  "sourceId": "wikigg_charger_spore",
  "verifiedPatch": "01.002.000",
  "enemy": {
    "faction": "terminid",
    "sizeClass": "heavy",
    "familyKey": "charger",
    "extends": "hd2.enemy.charger",
    "partOverrides": [
      {
        "partId": "abdomen",
        "notes": "尾部受击或击杀时会产生孢子爆炸伤害。"
      },
      {
        "partId": "spore_sac",
        "nameZh": "背部孢子囊",
        "armor": { "status": "known", "value": 2 },
        "notes": "变种新增部位。",
        "isNewPart": true
      }
    ]
  },
  "relations": [
    { "type": "variant_of", "targetId": "hd2.enemy.charger" }
  ]
}
```

- 覆写基础型已有部位：`partId` 必须存在于基础型，`isNewPart` 默认为 `false`（可省略），未写的字段自动继承基础型。
- 为变种新增全新部位：必须设置 `"isNewPart": true` 且提供 `"nameZh"`。

---

### 场景 4：游戏补丁更新已有数值

1. 在 `wiki-content/sources.json` 更新或新增本次补丁的来源条目（填好 `url`、`verifiedDate`、
   `defaultPatch`）。
2. 打开受影响的战备 / 武器 / 敌人 JSON：
    - 修改变化的数值或方向；
    - **只把已实际核验的条目**推进 `"verifiedPatch"`（如果只有某个别字段先按新补丁核验了，可只在该字段里覆写
      `"sourceId"` 和 `"verifiedPatch"`）；
    - 没有重新核验的条目不要动它的 `"verifiedPatch"`，保留历史标记。

---

### 场景 5：补充搜索别名与俗称

搜索只匹配 **正式中文名、英文名、型号（`modelCode`）、别名（`aliases`）与战备方向序列**（不搜 `summary`
简介文本）。

- **不需要手写的情况**：大小写差异、全半角、空格、中英文引号（`“”`）、点号（`.`）、连字符（`-`）在搜索时会自动忽略。例如
  `"AR-23"` 搜 `ar23`、`"“飞鹰”空袭"` 搜 `飞鹰空袭`、`"StA-X3 W.A.S.P.发射器"` 搜 `wasp`
  **都能直接命中，不用额外加别名**。
- **需要手写的情况**：玩家常用俗称、缩写或不含原字的昵称（例如 `"500kg"`、`“飞鹰500kg”`、`"大强袭"`、
  `"大牛"`），请加进 `"aliases"`：
  ```json
  "aliases": [
    { "locale": "zh-CN", "text": "500kg" },
    { "locale": "zh-CN", "text": "核弹" }
  ]
  ```

---

### 场景 6：为支援武器补充 `weaponProfile` 与多分量伤害

支援武器继续留在 `wiki-content/stratagems/support_weapon.json`，只需在 `stratagem` 下挂载
`weaponProfile`（内含 `configurations[]` 与 `attacks[].components[]`），即可自动参与搜索型号索引、对位推算与推荐：

```json
"weaponProfile": {
  "configurations": [
    {
      "configId": "aphet_mode",
      "labelZh": "穿甲高爆曳光弹（APHET）",
      "modelCode": "AC-8",
      "attacks": [
        {
          "attackId": "aphet_round",
          "nameZh": "20mm 穿甲高爆弹",
          "components": [
            {
              "componentId": "projectile",
              "labelZh": "弹头直击",
              "componentKind": "direct",
              "normalDamage": { "status": "known", "value": 325 },
              "durableDamage": { "status": "known", "value": 260 },
              "armorPenetration": { "status": "known", "value": 4 }
            },
            {
              "componentId": "blast",
              "labelZh": "命中爆炸（内圈）",
              "componentKind": "explosive",
              "normalDamage": { "status": "known", "value": 150 },
              "durableDamage": { "status": "known", "value": 150 },
              "armorPenetration": { "status": "known", "value": 3 },
              "innerRadiusMeters": 2.0,
              "outerRadiusMeters": 4.0
            }
          ]
        }
      ]
    }
  ]
}
```

---

### 场景 7：新增敌人多视角部位 WebP 与绿色区域映射

1. **准备图片**：将每个角度每个部位的图片压缩为 **`.webp`** 放入
   `wiki-content/assets/anatomy/<enemySlug>_<viewId>_<partId>.webp`（ **严禁 `.svg`**，分辨率
   `<= 1200×900`，单张体积 `<= 350 KB`）。
2. **编写解剖描述**：新建 `wiki-content/anatomy/<enemyId>.json`（文件名必须等于 `enemyId`）：
    - `defaultAssetKey`：默认展示的 WebP `assetKey`；
    - `partAssets`：`<partId> -> <assetKey>` 映射表（切换或点选该部位时直接切换为对应图片；若当前视角无该部位，列表点击会自动切到包含该部位的视角）；
    - `hitMap`（可选）：由离线脚本识别原图亮绿色区域生成的 `width x height` 调色板与 `intactRle`/
      `brokenRle` 透明点击映射层；若主体图仅含 `MAIN` 文字而无具体绿色高亮区域，则不写入 `hitMap`，仅保留
      `partAssets["main"]` 供列表点击切换。

---

### 场景 8：维护补丁规则表、实测记录与分组推荐

1. **补丁规则**：在 `wiki-content/rules/damage_rules.json` 按 `patchVersion` 维护
   `penetrateMultiplier`、`equalArmorMultiplier`、`ricochetMultiplier`。
2. **实测事实**：在 `wiki-content/matchups/evidence.json` 登记 `MatchEvidence`（`attackRef` 可指向
   `weapon` 或挂载 `weaponProfile` 的 `stratagem`；`patchVersion` 必须已在 `damage_rules.json` 中定义）。
3. **分组推荐**：在 `wiki-content/matchups/recommendations.json` 按
   `(enemyId, partId, partState, goal)` 登记编辑实证推荐与 `priorityOrder`
   （若同补丁同条件下实测与规则推算冲突，对位引擎会自动标记“待复核”并从推荐列表中剔除）。

---

## 4. 改完后如何校验与预览

1. **快速校验 JSON 并重新生成离线包**（改完内容源后必跑，几秒内即可暴露格式/引用/图标/重复错误）：
   ```powershell
   .\gradlew.bat :composeApp:generateWikiAssets
   ```
2. **跑全量单测与双端编译**：
   ```powershell
   .\gradlew.bat :build-logic:test :composeApp:desktopTest :composeApp:testAndroidHostTest :desktopApp:compileKotlin :androidApp:compileDebugKotlin
   ```
3. **启动桌面端直观检查 UI 效果**：
   ```powershell
   .\gradlew.bat :desktopApp:hotRun --auto
   ```

---

## 5. 常见构建报错排查

| 报错关键词                                                             | 常见原因                                                                                                    | 解决方法                                                                          |
|------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `Encountered an unknown key '...'`                                     | JSON 里写了未定义的字段名或拼错了字段名                                                                     | 对照第 2/3 节模板核对拼写                                                         |
| `条目的 category (...) 与所在文件 (...json) 不一致`                    | 战备分类与文件名不匹配（同样适用于武器的 `slot`/`weaponClass` 与敌人的 `faction`/`sizeClass` 目录及文件名） | 移到对应的目录/文件或修正字段值                                                   |
| `武器子类文件名 (...) 不属于槽位 ...`                                  | 槽位与子类组合非法（例如把 `melee.json` 放进 `throwable/`）                                                 | 参照第 2 节各槽位合法子类列表调整                                                 |
| `未登记的 sourceId: ...`                                               | 引用了 `sources.json` 中不存在的 `sourceId`                                                                 | 先在 `wiki-content/sources.json` 登记该 `sourceId`                                |
| `moko-resources 中不存在图标: ...`                                     | 拼错了 `iconKey` 或忘记把 `.svg` 放进资源目录                                                               | 核对 `composeApp/src/commonMain/moko-resources/images/` 下的文件名（不含 `.svg`） |
| `training.order 必须从 1 连续编号至 N`                                 | 删除或插入可训练战备后序号断号或重复                                                                        | 检查所有带 `training` 的战备，确保 `order` 为连续的 `1..N`                        |
| `status=known 时 value 不能为空` / `非 known 状态时 value 必须为 null` | `known` 忘了填 `value`，或 `unknown` 多写了 `value`                                                         | 检查 `WikiFact` 四态结构                                                          |
| `覆写目标部位在基础型中不存在`                                         | `partOverrides` 写错了 `partId`，或新增部位漏了 `"isNewPart": true`                                         | 核对基础型 `parts[].partId` 或补充 `"isNewPart": true`                            |
| `严禁使用 SVG 作为解剖底图` / `WebP 分辨率 ... 超出上限`               | `assets/anatomy/` 放入了 `.svg` 或分辨率/体积超过 `1200×900` / `350 KB`                                     | 转换为 `<= 1200×800` 的 `.webp` 并压缩体积                                        |
| `检测到非相邻边 ... 自交或重叠` / `坐标超出归一化区间`                 | 多边形顶点自交或未归一化到 `[0.0, 1.0]`                                                                     | 检查多边形顶点顺序并将坐标除以底图宽高归一化                                      |
| `wiki_gg 来源必须填写协议 license`                                     | `sources.json` 中 `wiki_gg` 条目缺少 `license` 或 `url`                                                     | 补齐 `url` 与 `license`（如 `CC BY-SA 4.0`）                                      |
| `patchVersion 未在 damage_rules.json 中定义`                           | `evidence.json` 或 `recommendations.json` 使用了未登记的补丁号                                              | 先在 `wiki-content/rules/damage_rules.json` 增加对应 `patchVersion`               |
