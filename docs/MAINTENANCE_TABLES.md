# SkyPocket 本地维护表

> 这份文档列出 SkyPocket 里需要随游戏版本/数据变化手动维护的表。
> 修改任何一张表时，先改手写源 `versions/26.2-fabric/src`，再按仓库规则构建 26.3；不要手改生成的 26.3 源码。
> 最后更新：2026-11-10。

## 总览

| 表 | 位置 | 类型 | 何时更新 |
|---|---|---|---|
| 技能累计 XP | `skyblock/SkillXpTable.java` | 静态累计 XP 数组 | Hypixel 调整技能等级上限或 XP 曲线 |
| 技能 TabList 等级 | `skyblock/SkillLevelTracker.java` | 正则 | TabList 技能行格式变化 |
| 传奇/神话海怪消息 | `skyblock/SeaCreatureMessages.java` | 精确字符串集合 | 新增/修改海怪刷新文案 |
| SkyBlock 物品字段 | `skyblock/SkyBlockItem.java` | `ExtraAttributes` 读取 | 新增物品字段/组件变更 |
| 雷瓶容量 | `skyblock/ThunderBottleTracker.java` | 固定容量表 | Hypixel 调整容量或新瓶种 |
| 矿物伪装强度 | `skyblock/MiningBlocks.java` | 精确值 / 候选值 / 类别 | 新增矿物、区域或伪装方块 |
| 矿物强度校准 | `skyblock/MiningStrengthTable.java` | 运行时投票 + 常量 | 服务器破块规则或校准策略变化 |
| 采矿工具白名单 | `skyblock/MiningTools.java` | `Set<Item>` | 新钻头/新工具材质 |
| 采矿技能名 | `skyblock/MiningAbility.java` | 枚举 | 新增/改名 pickaxe ability |
| 钓鱼热点类型 | `render/HotspotType.java` | 枚举 + 匹配字符串 | 新增 hotspot 属性行 |
| 用户颜色默认值 | `config/ColorDefaults.java` | 颜色常量 | 调整默认配色或 reset 行为 |
| 后处理预设 | `render/ColorGradeRenderer.java` | `Map<String,float[]>` | 调整 Light/Standard/Cinematic/Warm 预设 |

## 1. 技能累计 XP

- 文件：`skyblock/SkillXpTable.java`
- 六个技能：`Farming / Mining / Combat / Foraging / Fishing / Enchanting`
- 数组语义：`array[level]` 是该等级起点的累计 XP；下一级需求 = `array[level + 1] - array[level]`
- 数组长度 = 最大等级 + 1：
  - Farming / Mining / Combat / Enchanting：61 项，最高 60
  - Foraging：56 项，最高 55
  - Fishing：51 项，最高 50
- 使用范围：**只在 ActionBar 给百分比、没有 current/total 时**，结合 TabList 等级反推 current/total
- 正常 ActionBar 已给 `current/total` 时不读这张表
- 更新规则：
  1. 先确认 Hypixel 当前等级上限与累计 XP
  2. 只追加/修正数组，不要把增量表混进累计表
  3. 检查最大等级、`total = 0` 满级分支和百分比反推
  4. 六级技能都测一次普通、满级、百分比三种输入

## 2. 技能 TabList 等级

- 文件：`skyblock/SkillLevelTracker.java`
- 当前匹配：`<SkillName> <level>: <percent>%` 或 `<SkillName> <level>: MAX`
- 只在百分比-only 的 ActionBar 路径调用，正常路径不扫 TabList
- TabList 行格式变化时更新正则；不要在多个类里复制这套正则

## 2.1 传奇/神话海怪消息

- 文件：`skyblock/SeaCreatureMessages.java`
- 当前只存 **LEGENDARY + MYTHIC** 两条稀有度及以上的精确 spawnMessage
- 匹配规则：`Component.getString()` 后**整条字符串 equals**，大小写、标点、弯引号 `’`、长破折号 `—` 都必须一致
- 不做关键词匹配，不做 trim/lowercase；`Legendary`/`Sea Creature` 等词通常根本不在消息里
- 更新场景：Hypixel 新增/改写传奇或神话海怪刷新消息
- 更新时直接从游戏聊天原文复制整句，保留 Unicode 标点，并在 `LegendarySeaCreatureWarningHudElement` 只做消息表匹配

## 2.2 SkyBlock 物品字段与雷瓶容量

- `SkyBlockItem` 从 `custom_data` → `ExtraAttributes` 读取 `id`、`drill_fuel`、`thunder_charge` 等字段；当前只读，不写回物品。
- 雷瓶容量表：
  - `THUNDER_IN_A_BOTTLE_EMPTY` = 50,000
  - `STORM_IN_A_BOTTLE_EMPTY` = 500,000
  - `HURRICANE_IN_A_BOTTLE_EMPTY` = 5,000,000
- 更新场景：Hypixel 新增瓶种、调整容量或改写 ExtraAttributes 字段名。
- Drill Fuel 的当前/最大值来自 ActionBar 的 `x/y Drill Fuel` 段，主手必须存在 `drill_fuel` 字段。

## 3. 矿物伪装强度

- 文件：`skyblock/MiningBlocks.java`
- `EXACT`：伪装方块 → 确定强度
- `CATEGORY`：伪装方块 → `BLOCK / ORE / GEMSTONE / DWARVEN`
- `CANDIDATES`：同一伪装可能对应多个强度时，由 `MiningStrengthTable` 观测解析
- `ALL`：没有候选时的完整强度集合，新增强度要同步加入
- 即时挖掘阈值：

```text
BLOCK     30 × strength + 1
ORE       60 × strength
GEMSTONE  60 × strength + 1
DWARVEN   60 × strength + 1
```

- 更新场景：新增矿物/区域、Hypixel 改伪装方块、发现新的歧义伪装
- 约束：确定值用 `exact/ore/metal/gemstone`；有歧义必须用 `candidates`，不要为了“看起来完整”硬编码单一强度

## 4. 矿物强度校准

- 文件：`skyblock/MiningStrengthTable.java`
- 不是静态游戏数据表，而是运行时从破块时长反推 `CANDIDATES`
- 关键常量：
  - `DEFAULT_MIN_BREAK_TICKS = 4`
  - `VOTES_NEEDED = 2`
  - `MIN_TICKS_FOR_VOTE = 4`
  - `MISMATCHES_ALLOWED = 2`
  - `SAVE_INTERVAL_MS = 20000`
- 更新场景：服务器改变 4 tick 下限、破块公式或校准策略
- 注意：这是**运行时状态**，不要把它当成可随意清空的缓存；解析结果会持久化到配置

## 5. 采矿工具白名单

- 文件：`skyblock/MiningTools.java`
- 当前：木/石/金/铁/钻石镐、`PLAYER_HEAD`（SkyBlock 钻头）、`PRISMARINE_SHARD`
- 更新场景：新钻头使用新的物品类型/材质
- 约束：白名单同时影响 Mining Speed 接受和 Mining Break Prediction；漏掉会把非工具期间的 Tab 值当成有效采矿速度

## 6. 采矿技能名

- 文件：`skyblock/MiningAbility.java`
- 当前六项：`Mining Speed Boost / Pickobulus / Maniac Miner / Tunnel Vision / Sheer Force / Gemstone Infusion`
- 更新场景：新增 pickaxe ability 或改名
- 约束：`matchName` 必须与服务器 Tab/Chat 的英文名完全一致；技能名不翻译
- 冷却总时长由首次观测写入 `MiningAbilityTracker.TOTAL_SECONDS`，不是静态表

## 7. 钓鱼热点类型

- 文件：`render/HotspotType.java`
- 当前：`Fishing Speed / Sea Creature Chance / Double Hook Chance / Trophy Chance / Treasure Chance`
- 更新场景：新增 hotspot 属性行
- 约束：`matchKey` 是服务器统计行中的英文字符串，必须与资源包/服务器文案一致；同时补默认色和设置项

## 8. 用户颜色默认值

- 文件：`config/ColorDefaults.java`
- 这些是功能的用户数据默认值，不是主题 token
- `ModConfig` 初始值和设置页 reset 共用这一份，避免两处漂移
- 更新场景：调整发光/碰撞箱/数值条默认颜色

## 9. 后处理预设

- 文件：`render/ColorGradeRenderer.java`
- `PRESETS` 字段顺序：`exposure, contrast, saturation, gamma, lift, temperature, vignette`
- 更新场景：调整 Light/Standard/Cinematic/Warm 一键预设
- `custom` 不是预设，是“滑条已偏离预设”的标记，不要塞进 `PRESETS`

## 维护规则

1. **单一来源**：同一数据只在一个类里维护；不要在 26.3 快照、设置页或测试代码里复制第二份。
2. **先改 26.2**：手写源是 `versions/26.2-fabric/src`，26.3 由构建生成。
3. **表与测试一起改**：新增等级/矿物/热点时同步补对应边界测试。
4. **歧义优先候选**：不确定的矿物伪装不要硬编码，交给 `MiningStrengthTable` 观测。
5. **不要静默改表**：改动累计 XP、强度阈值、白名单时在提交说明里写清楚数据来源和验证方式。
