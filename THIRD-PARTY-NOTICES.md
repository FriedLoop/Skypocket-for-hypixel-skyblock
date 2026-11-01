# Third-Party Notices

SkyPocket 的源码以 GNU Lesser General Public License v3.0（或更高版本）发布，
详见 `LICENSE`。以下是随本仓库源码或构建产物一同分发/依赖的第三方组件及其许可。

> 本文件仅为汇总，不替代各组件自带的许可全文。

## 打包进模组 jar 的组件

### JLayer 1.0.1

- 用途：MP3 解码，用于自定义提及提示音（`javazoom:jlayer:1.0.1`，通过 Loom 的 `include` 以嵌套 jar 形式打进产物）
- 许可：GNU Lesser General Public License v2.1
- 上游：<https://www.javazoom.net/javalayer/javalayer.html>
- 合规说明：JLayer 以独立嵌套 jar 形式分发、未做修改，其许可声明与源码获取途径需随产物保留；
  修改 JLayer 自身时须按 LGPL-2.1 公开改动。

### Inter

- 文件：`assets/skypocket/font/inter.ttf`
- 用途：ClickGUI 矢量字体
- 许可：SIL Open Font License 1.1
- 版权：Copyright (c) 2016 The Inter Project Authors
- 上游：<https://github.com/rsms/inter>

### Noto Sans SC

- 文件：`assets/skypocket/font/notosanssc.ttf`
- 用途：中文字形回退
- 许可：SIL Open Font License 1.1
- 版权：Copyright (c) Google Inc.
- 上游：<https://fonts.google.com/noto/specimen/Noto+Sans+SC>

> OFL 1.1 允许随软件一起分发与嵌入，但不得单独销售字体本身；再分发时须保留上述版权与许可声明。

## 构建期依赖（不打包，除注明外）

| 组件 | 版本 | 许可 | 说明 |
| --- | --- | --- | --- |
| Fabric Loader | 0.19.x / 0.20.x | Apache-2.0 | 运行时由用户自行安装 |
| Fabric API | 见 `gradle.properties` | Apache-2.0 | 运行时依赖 |
| Mod Menu | 见 `gradle.properties` | MIT | 可选，`compileOnly`，不随产物分发 |
| Gradle Wrapper | 见 `gradle/wrapper/` | Apache-2.0 | 构建工具 |

## 不属于本仓库的内容

- Minecraft 及其客户端/服务端代码、资源归 Mojang Studios / Microsoft 所有，本仓库不包含、也不分发其任何文件；
  构建时由 Loom 从官方渠道下载。
- 「Hypixel」「SkyBlock」为 Hypixel Inc. 的商标/服务标识，本项目与 Hypixel Inc.、Mojang Studios 无任何隶属或背书关系。
