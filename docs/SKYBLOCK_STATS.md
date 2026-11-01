# SkyBlock 玩家数据获取（外部参考 + 落地指南）

> 来源：从另一个 SkyBlock 模组（NoammAddons，MC 26.1.2 / Fabric / Kotlin）的技术文档中**只提取「数据从哪来、怎么抠出来」**。
>
> **已剔除**（对自建数据层没有价值）：作者自建的第三方后端 `api.noamm.org`（端点、keypair 鉴权、缓存 TTL、错误码映射）、`ws.noamm.org` 众包协议、以及全部 UI/呈现逻辑。第三方后端的唯一结论一句话：**不要依赖第三方后端**；真要用，自己做缓存 + 超时 + 降级。
>
> 适用对象：SkyPocket（纯 Java / MC 26.2 / Mojang 映射）。对照代码：`net/SkyBlockDetector`、`skyblock/TabListText`、`chat/MentionAlert`、`mixin/ClientPacketListenerMixin`、`render/TpsEstimator`。

---

## 1. 最重要的一条设计判断

**不要去读客户端实体属性。**

那份实现全仓库**没有一处** `getHealth()` / `getAbsorptionAmount()` / `getActiveEffects()` / `getArmorValue()` / `getFoodData()` / `experienceLevel`。

原因：SkyBlock 的血量、防御、法力是**服务端结算**的结果 —— 护盾、减伤、隐藏修饰符都不体现在客户端实体上，`getHealth()` 拿到的是错的。个人数值一律从 **ActionBar 文本**抠。

- 唯一例外：**速度** `mc.player.abilities.walkingSpeed * 1000`（客户端预测值，**必须**标注为估算）。
- 其它 `mc.player` 访问都属于「操作 / 渲染」，不是取 stat。

---

## 2. 数据源分级（选型表）

| 级别 | 来源 | 能拿到什么 |
|---|---|---|
| 🟢 服务端口径（可当真值） | ActionBar / SystemChat(`overlay=true`) | 血量 / 最大血量 / 防御 / 法力 / 最大法力 / 溢出法力 / 活力 / 有效生命 / 净魂护甲层数 / 弓 T1-T3 / 秘密数 |
| 🟢 | Tab 显示名 + 队伍 prefix/suffix | 队友名字 / 职业 / 等级 / 是否死亡；Crypts / Completed Rooms / Secrets Found / Cleared% / Time Elapsed |
| 🟢 | BossBar `progress` / title / subtitle / 实体元数据 | Boss 血量比例、阶段计数、龙血 |
| 🟢 | **locraw**（服务端下发 JSON） | 当前 server / gameType / location / map |
| 🟡 客户端预测（会漂移，必须标注） | 位置推断 AABB、本地 tick 计时、`abilities`、音效 pitch、背包轮询 | 副本阶段 / 计时 / CD / 高度 |
| 🟠 推断（非直读） | 物品 lore + 服务端 tick | 面具 CD、blessing 层数 |

Hypixel **官方**公共 API（10 分钟轮询，可选）只有三个端点值得记：`/v2/resources/skyblock/election`（市长 → 是否 Paul）、`/v2/skyblock/bazaar`（买卖价）、`/v2/resources/skyblock/items`（name↔id 映射、NPC 卖价）。

---

## 3. 机制 A：ActionBar 解析（个人数值主来源，最值得抄）

### 3.1 触发：两种包都要接

Hypixel 的 actionbar 有时走 `ClientboundSetActionBarTextPacket`，有时走 system chat 的 **`overlay=true`** 消息；只接一种会漏字段。

**我们的落点（零 mixin）** —— 项目里已有同样的写法（`chat/MentionAlert`、`skyblock/MiningAbilityTracker`）：

```java
ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
    if (overlay) { /* actionbar 文本 */ }
});
```

若实测有漏包，再在 `ClientPacketListenerMixin` 里注入 `handleSetActionBarText`（该类已有 `handleTabListCustomisation` 等同类钩子）。门控一律用 `SkyBlockDetector.isActive()`。

线程：Fabric 这两个事件都在客户端主线程，渲染侧读取无竞态。

### 3.2 ⚠️ 必须保留 `§` 颜色码（我们当前的缺口）

那份实现坚持用 **`.formattedText`（带 `§`）** 而不是 `getString()`，因为**颜色码本身就是语义**：

- `§c` = 正常血量，`§6` = **溢出**血量 —— 同一个 `x/y ❤` 文本，靠颜色区分
- `§b` = 法力，`§3` = 溢出法力

**我们目前全项目只用 `message.getString()`**（`MentionAlert`、`MiningAbilityTracker` 都是），颜色码在解析前就丢了 → 这类「同文本、不同含义」的字段**无法区分**。

落地：加一个 `Component → 带 § 字符串` 的助手（递归 `Component.visit((style, text) -> ...)`，把 `Style` 的颜色/粗斜体还原成 `§` 序列），放在 `chat/` 下，所有解析器统一用它。

### 3.3 字段与正则（原样保留）

> 这些符号（`\uE010❤`、`❈`、`\uE003✎`、`ʬ`、`♨`、`ᝐ⁑҉Ѫ⚶`）是 Hypixel 资源包的**自定义字形**，不能改写成普通字符；版本更新可能变，所以正则要**宽松匹配 + 失败保留旧值**，不要写死到脆断。

| 字段 | 正则 | 说明 |
|---|---|---|
| 血量 / 最大血量 | `§[c6]([\d,]+)/([\d,]+)[\uE010❤]` | `§c` 正常 / `§6` 溢出 |
| 防御 | `§a([\d,]+)(§a)?[❈]( Defense)?` | |
| 法力 / 最大法力 | `§b([\d,]+)/([\d,]+)[\uE003✎]( Mana)?` | |
| 溢出法力 | `§3([\d,]+)[\uE017ʬ]` | **无匹配 → 归零** |
| 活力 / 最大活力 | `§4([\d.,]+)/([\d,.]+)[♨]( Vitality)?` | 可含小数 |
| 净魂护甲层数 | `(?:§6|§6§l)([\d,]+)([ᝐ⁑҉Ѫ⚶])(?:§r)?` | 层数 + 符号形态 |
| 弓 T1-T3 | `(?:§6|§a§l)?T([12]|3!)(?:§r)?` | |
| 秘密数 / 上限 | `\s*§7(\d+)/(\d+) Secrets` | **仅地牢**；非地牢置 `null` |
| 法力扣减 | `§b-([\d,]+) Mana \(§6.+?§b\)` 或 `§c§lNOT ENOUGH MANA` | **扣减现有法力，不是新字段** |

派生值：`有效生命 = 血量 × (1 + 防御/100)`。

### 3.4 四条解析语义（必须照抄，否则数值会在「没变化」时错成 0）

1. **缺值保留旧值**：`match ?: 旧值`。Hypixel **只在数值变化时才带该字段** —— 「没匹配到」**不等于**「为 0」。
2. **派生值必须在同一包内、且依赖字段解析之后再算**（有效生命紧跟防御之后）。
3. **非对称清零**：
   - 溢出法力 → 归零
   - 「活力是否显示」→ `false`
   - 血量 / 防御 / 法力 → **保留**（不要清零）
4. **千分位统一去逗号**；活力是小数，转 `double` 后截断。

### 3.5 生命周期与副作用（他们踩过的坑，我们别重踩）

- ⚠️ 那份实现**离开 SkyBlock 时不重置**（只在 `inSkyblock` 时更新）→ 回主城后 HUD 显示**过期值**。我们自建必须：换世界 / 离开 SkyBlock 时 `reset()`（项目里 `TabListText.clear()` 就是同样的先例）。
- ⚠️ 它解析到「秘密数」后**提前 `return`**，会跳过该包后续所有解析。**别在解析器里提前 return**，要解析完所有字段。
- 数据层形态是「采集一次、全局共享」的静态持有类 —— 和我们 `skyblock/*Tracker` 的写法一致。

---

## 4. 机制 B：TabList（副本统计与队友，第二大来源）

### 4.1 关键洞察

**Hypixel 把副本统计（Crypts / Completed Rooms / Secrets Found / Cleared% / Time Elapsed）放在 Tab 条目的显示名和队伍 prefix/suffix 里**，不在 actionbar。想拿这些数据，唯一办法就是解析 `PlayerInfoUpdate` 的 displayName 与 `SetPlayerTeam` 的 prefix/suffix。

### 4.2 队友行正则

`^\[\d+] (\[..\] )*([A-Za-z0-9_]{1,16}) .*\((\w+)( \w+)?\)$` → 名字 / 职业 / 等级 / `DEAD`。

（拿不到显示名时用队伍 prefix + profile name + suffix 拼接 —— 我们 `TabListText.rawEntry` 已经是这个 fallback，思路一致。）

### 4.3 缓存策略：脏标记 + 懒重算

- **他们**：`listDirty` 由 `PlayerInfoUpdate` 置位，读取时才重算；排序按 tab 顺序；`take(80)` 上限。
- **我们**：`TabListText.all/allLines` **每次调用都现算**（遍历 `getOnlinePlayers()` + `getTabListDisplayName()`），而 `ClientPacketListenerMixin` **只监听了 `handleTabListCustomisation`（header/footer），没有监听 `handlePlayerInfoUpdate`**。
- **落地**（一旦有「每帧读 tab」的功能）：加脏标记缓存 + 在 mixin 注入 `handlePlayerInfoUpdate` 置位。排序已经一致（都用 `PlayerInfo#getTabListOrder()`）。

---

## 5. 其它来源（按需取用）

### 5.1 Sidebar 只当「上下文」

侧边栏主要提供**区域名 / 楼层 / serverId**，**不是**主要 stat 来源。只暴露「原始行文本」，不做解析（`SkyBlockDetector` 已经在直接读 sidebar 标题判断 SkyBlock）。要重复读同样加脏标记；`Set*/Team` 包置位。

### 5.2 物品 NBT / Lore

通用能力：**`skyblockId(ItemStack)`** 把任意物品归一化成 SkyBlock 物品 id（NBT `id` 组件；`PET` / `ENCHANTED_BOOK` / `RUNE` / `POTION` 各有子结构；lore 兜底）。

- **稀有度缓存用 `WeakHashMap`**：避免每帧对同一物品重复跑正则 —— 值得照抄的实现思路。
- 几个现成读法：Etherwarp 距离 `= 57 + tuners`（NBT `ethermerge` / `tuned_transmission`）；面具 CD 从 lore `^Cooldown: ([\d.]+)s$` 读、再用 tick 递减；宠物信息在 NBT `petInfo`（JSON：`tier/type/heldItem/uuid/skin`）；背包/末影箱/饰品是 Base64 → NBT 解压。

### 5.3 BossBar / Title / 实体元数据

- Boss 血量 = `BossBar.progress × 硬编码上限` → **把硬编码常量集中成一张表**（版本更新必变，别散落在各功能里）。
- 龙血量在 `ClientboundSetEntityDataPacket.packedItems` 里 `id == 9` 的 Float。
- F7 阶段计数来自 title / subtitle / bossbar。

### 5.4 状态位（buff/debuff）：靠「物品 lore + 服务端 tick」推断

那份实现**从不读 `getActiveEffects()`**，CD/无敌时间一律用「lore 里的 `Cooldown: Xs` + 本地递减」推断。这是可复用模式，但**tick 源要用服务端 tick**（我们已有 `TpsEstimator.onServerTime` 接在 `handleSetTime` 上）。

### 5.5 locraw

服务端下发的 JSON（server / gameType / location / map）—— 做上下文判断**优先用它**，比「位置 + 硬编码 AABB」推断可靠得多。

---

## 6. 写新功能时必须标注可信度

🟢 服务端口径 / 🟡 客户端预测 / 🟠 推断。

B 类（位置推断、本地 tick、`abilities`、音效 pitch、背包轮询）的漂移是**设计上的**：丢包、传送、音效丢失都会误判。所以规则是：

- 用它做**提醒/显示**时，注释里写明「可能误判」；
- **不要**用它做不可逆操作；
- 数据类字段上直接标级别，别让调用方去猜。

---

## 7. 落地到 SkyPocket

### 7.1 缺口清单（按优先级）

1. **`Component` → 带 `§` 字符串**的助手（建议 `chat/ChatText`）—— 目前全项目只有 `getString()`，颜色语义拿不到（见 §3.2）。
2. **`PlayerStats` 快照 + ActionBar 采集器**（`ClientReceiveMessageEvents.GAME` + `overlay`，零 mixin）。
3. **`TabListText` 脏标记缓存** + `handlePlayerInfoUpdate` 钩子（避免每帧遍历 tab）。
4. **`reset()` 语义**：换世界 / 离开 SkyBlock 时清空数据（含 HUD 显示），照 `TabListText.clear()` 的先例。
5. **可信度标注**贯穿数据类注释（见 §6）。

### 7.2 来源 → 我们的落点

| 数据 | 我们的落点 |
|---|---|
| ActionBar 个人数值 | 新增 `skyblock/PlayerStatsTracker`（Fabric GAME 事件 + `SkyBlockDetector.isActive()`） |
| Tab 副本统计 / 队友 | 扩展 `skyblock/TabListText`（脏标记 + PlayerInfo 钩子） |
| 物品 / 宠物 / 稀有度 | 新增 `skyblock/SkyBlockItem`（NBT 归一化 + `WeakHashMap` 缓存） |
| Boss 血量 / 阶段 | 新 mixin 或事件 + **一张集中的常量表** |
| 岛名 / 区域 / 楼层 | `net/SkyBlockDetector` + sidebar 原始行 |
| locraw 上下文 | 解析服务端 JSON（最可靠） |
| 服务端 tick / TPS | 已有 `render/TpsEstimator`（`handleSetTime`） |

### 7.3 建议的数据层形态（采集与呈现彻底切开）

```java
/** 不可变快照：每次数据变化整体替换，渲染只读快照。 */
public final class PlayerStats {
    public final int health, maxHealth, defense, mana, maxMana, overflowMana;
    public final int vitality, maxVitality, effectiveHp;
    public final long capturedAt;      // 新鲜度，用于判断是否过期
}

/** 采集器：不同来源各自实现，互不干扰。 */
public interface StatSource {
    void init();          // 注册事件 / mixin
    void reset();         // 离开 SkyBlock、换世界
    int priority();       // 冲突时的优先级
}
```

三个好处：

1. **呈现随便换** —— HUD / 聊天 / 悬浮窗 / 侧边栏都只是快照的消费者；
2. **数据源可插拔** —— ActionBar 采集器 / Tab 采集器 / NBT 采集器各自独立，某个失效不影响其他；
3. **可测** —— 采集器只依赖「输入字符串 → 字段」，正则能脱离游戏做单元测试。

---

## 8. 一句话总结

这一切都**不做计算，只做「从服务端下发的文本里正则抠数」**：ActionBar 出个人数值（血/蓝/防/活力/秘密），Tab 出副本统计与队友，物品 NBT 出装备与宠物，BossBar/实体元数据出 Boss 血量。**并且刻意不读客户端实体属性** —— 这是最值得抄的一条设计决策。
