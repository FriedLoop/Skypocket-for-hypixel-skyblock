# SkyPocket

面向 **Hypixel SkyBlock** 的 Minecraft **客户端**小工具集模组（Fabric）。

> 客户端模组：仅在本地客户端运行，不发送自定义数据包。与 Hypixel Inc.、Mojang Studios 无隶属关系，
> 使用前请自行确认符合服务器规则；因使用本模组导致的账号风险由使用者自行承担。

## 功能概览

- **ClickGUI**：所有功能与子功能设置集中在一个可搜索的 ClickGUI 面板内，全部逻辑在此完成，无需额外界面
- **HUD**：FPS / TPS / Ping、时钟、世界天数、手持物品名、SkyBlock 属性条、挖矿速度与能力冷却、委托追踪
- **渲染**：自定义十字准星、方块描边、实体碰撞箱、挥手动画、后处理（模糊 / 泛光 / FXAA / 锐化 / 动态模糊）
- **聊天**：聊天压缩、提及高亮与自定义提示音（支持导入 MP3）
- **界面**：中英文运行时切换（默认跟随系统）、主题与配色自定义

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.2 / 26.3 |
| Java | 25 |
| Fabric Loader | 见 `gradle.properties`（0.19.x / 0.20.x） |
| Fabric API | 必需 |
| Mod Menu | 可选（提供设置入口） |

## 构建

仓库自带 Gradle Wrapper，无需预装 Gradle：

```bat
:: Windows
gradlew.bat build
```

```bash
# macOS / Linux
./gradlew build
```

产物位于 `versions/<mc 版本>/build/libs/`。首次构建会下载 Minecraft 与映射文件，耗时较长。

## 仓库结构

```
.
├─ build.gradle.kts       # 所有版本子工程共享的构建脚本（含 Bloom 版本重写规则）
├─ root.gradle.kts        # 多版本节点声明（26.2 → 26.3 的预处理链）
├─ settings.gradle.kts    # 子工程列表
├─ versions/
│  ├─ mainProject         # 纯文本：主编辑版本目录名
│  ├─ 26.2-fabric/        # 主版本：源码手写在这里
│  └─ 26.3-fabric/        # 由主版本经预处理器 + Bloom 规则生成
├─ docs/
│  ├─ DEVELOPMENT.md      # 架构与开发约定（改代码前先读）
│  ├─ SKYBLOCK_STATS.md   # SkyBlock 数据来源与解析语义
│  └─ settings-ux-brief.md
└─ tools/
```

源码只在 `versions/<mainProject>/src` 中手写，其他版本由预处理器与 `build.gradle.kts` 的
Bloom 替换规则生成，因此**不要直接改非主版本目录**（会被覆盖）。

## 开发约定（摘要）

完整内容见 `docs/DEVELOPMENT.md`，其中几条硬约束：

- 所有功能（含子功能）的设置必须写在 ClickGUI 面板里；仅 HUD 位置编辑器允许另开 `Screen`
- UI 文案用中文字面量作 key 调用 `Localization.tr()`，英文翻译集中在 `Localization.EN`
- SkyBlock 相关逻辑必须用 `SkyBlockDetector.isActive()` 门控
- 每次改动后执行 `gradlew.bat build` 确认无编译错误

## 许可

Copyright (C) 2026 bitveil

本项目以 **GNU Lesser General Public License v3.0 or later** 发布，全文见 `LICENSE`
（LGPL-3.0 以 GPL-3.0 为基础，其全文见 `LICENSE.GPL-3.0`）。

简而言之：

- 你可以自由使用、修改、再分发本模组，包括将其整合进闭源整合包或以独立 jar 形式分发
- 若你**修改了本模组本身的代码**，改动部分必须以 LGPL-3.0（或更高）公开
- 再分发时须保留版权声明与许可文本，且不得暗示原作者为你背书

第三方组件（JLayer、Inter、Noto Sans SC 等）的许可见 `THIRD-PARTY-NOTICES.md`。
