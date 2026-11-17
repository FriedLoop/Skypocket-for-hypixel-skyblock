# 多版本适配（26.2 / 26.3）

同一份源码通过 Bloom 预处理器生成两个 MC 版本的构建。本文记录两条版本差异的表达机制、26.3 的差异清单、Mixin 失配手法，以及新增版本时的步骤。

**核心原则**：能用共享代码就不用分支，能用预处理器指令就不用两份实现。分支只保留给**语义真的不同**的地方。

---
## 目录

### 9.1 目录与构建

```
SkyPocket/
├─ settings.gradle.kts      include :26.2-fabric / :26.3-fabric（projectDir = versions/<名>）
├─ root.gradle.kts          根项目：multiversion-root 插件，声明版本节点
├─ build.gradle.kts         所有版本共享：按版本取依赖 + Bloom 改名规则
├─ gradle.properties        版本化键：fabric_version.26.2 / .26.3、java_version.<ver> …
└─ versions/
   ├─ mainProject           纯文本，内容 = 26.2-fabric（手写源码所在版本）
   ├─ 26.2-fabric/src       唯一手写源码树（可含 //#if）
   └─ 26.3-fabric/build/preprocessed  构建时由 26.2 解析生成；仓库不保留 src 副本
```

- 命令：`gradlew :26.2-fabric:build` / `gradlew :26.3-fabric:build`，产物在 `versions/<名>/build/libs/skypocket-<名>-<mod_version>.jar`（版本号取自 `gradle.properties` 的 `mod_version`）。
- **源码只改 `versions/26.2-fabric/src`**。`versions/<名>/build/preprocessed/` 是配置期生成的解析结果（每次构建重建），不要手改。

### 9.2 两条差异表达机制

| 机制 | 适用 | 位置 |
|---|---|---|
| **Bloom 改名规则** | 纯改名：包搬家、类/字段/方法/常量重命名 | `build.gradle.kts` 的 `bloom { if (atLeast("26.3")) { replacement("旧","新") } }` |
| **`//#if` 条件编译** | 签名变化、结构变化、行为不同 | 手写源码内，构建期解析 |

`//#if` 用法：

```java
//#if MC>=260300
        poseStack.rotateDegrees(axis, degrees);
//#else
        poseStack.mulPose(axis.rotationDegrees(degrees));
//#endif
```

规则（实现见 `build.gradle.kts` 的 `resolveDirectives`）：

- 条件只认 `MC` 与整数字面量，支持 `>= <= == != > <`；支持嵌套与 `//#elseif`；
- 指令必须是**整行**（前面可有缩进），`//#if` 与条件之间必须有空格；
- 两个分支**都可以是真实代码**：26.2 保留 `#else` 分支、26.3 保留 `#if` 分支，因此"同名方法两套实现"合法；
- `MC` 取值 = 版本节点号（`26.2` → `260200`，`26.3` → `260300`）。

> ⚠️ **不要用插件的 `preprocessCode`**：这个 fork 不生成跨版本源码（删掉目标文件后重跑也不会重建，跑主工程直接失败）。解析放在**配置期**自行完成，Gradle 9 也不会报"输出未声明依赖"。
### 9.3 26.3 差异清单（已处理）

| 面 | 26.2 | 26.3 | 处理 |
|---|---|---|---|
| 窗口/输入 | GLFW | SDL（依赖里没有 glfw） | `GLFW.GLFW_KEY_*` → `InputConstants.KEY_*`；`glfwGetKey(h,k)` → `InputConstants.isKeyDown(k)` |
| 修饰键/回车 | `KEY_LEFT_SHIFT` / `KEY_ENTER` / `KEY_KP_ENTER` | `KEY_LSHIFT` / `KEY_RETURN`（无 KP_ENTER） | Bloom 改名 |
| 纹理包 | `blaze3d.textures.*` | `renderpearl.api.textures.*` | Bloom 整包改名 |
| buffer | `blaze3d.buffers.GpuBuffer` / `TransientMemory` | `renderpearl.api.buffers.*`（`Std140Builder` **不搬**） | Bloom **精确到类名**，不要整包替换 |
| 渲染管线 | `blaze3d.pipeline.RenderPipeline` / `DepthStencilState`、`blaze3d.platform.CompareOp` | `renderpearl.api.pipeline.*` | Bloom 改名 |
| 鼠标按键号 | 左 0 / 右 1 | 左 **1** / 中 **2** / 右 **3** | `MouseButtons.isLeft/isRight`（`//#if` 各一份），Bloom 把 18 处字面量判断改走它 |
| 实体类 | `EnderMan` | `Enderman` | Bloom 两条（**全限定名 + 裸名**，只改 import 对 usages 无效） |
| 手持渲染类 | `ItemInHandRenderer` | `FirstPersonHandsAndItemsRenderer`（`applyItemArmTransform`/`swingArm` 仍是私有同名方法） | Bloom 只改 `import` 与 `@Mixin(...)`，**不要改自己的 Mixin 类名**（类名必须等于文件名） |
| 粒子包 | `getParticle()/getX()/getY()/getZ()` | record 访问器 `particle()/x()/y()/z()` | `//#if` |
| `NativeImage.read` | `read(Format, InputStream)` | `read(InputStream)` | `//#if` |
| `TextureTarget` 构造 | `(String,w,h,boolean,format)` | `(String,w,h,GpuFormat color,GpuFormat depth)` | `//#if`，深度取 `main.getDepthTexture().getFormat()` |
| `PoseStack` 旋转 | `mulPose(Axis.X.rotationDegrees(v))` | `rotateDegrees(Axis.X, v)` | 收敛成 `rotate(...)` helper，`//#if` 只写一次 |
| 挥动 API | `swingingArm` + `getAttackAnim` + `ItemStack.getSwingAnimation()` | `getCurrentSwing()` + `getSwingAnimation()` + `ItemStack.getAttackAnimation()` | 见 9.5 |

### 9.4 Mixin 的三种失配与对应手法

| 失配 | 报错 | 手法 |
|---|---|---|
| 目标改名 / 返回类型变 | `could not find any targets matching 'swing(...;Z)V'` | 整个 `@Inject` 用 `//#if` 分版本（handler 签名一起改） |
| 目标**多了**参数 | `Invalid descriptor ... Expected (...DDDF...) but found (...DDD...)` | 只给 handler **参数表**加 `//#if`，方法体共用 |
| 目标**少了**参数 | `Expected (CallbackInfo)V but found (DeltaTracker,Z,CallbackInfo)V` | 同上，把多余参数在 26.3 分支去掉 |

参数表分版本（`//#if` 夹在参数列表中间，解析后就是合法参数表）：

```java
    private void sky$hideNearbyPlayers(Entity entity, Frustum frustum, double x, double y, double z,
//#if MC>=260300
                                      float partialTick,
//#endif
                                      CallbackInfoReturnable<Boolean> cir) {
```

- 能共用方法体就共用，只分派**参数表或目标描述符**；
- 注入目标被删（如 `getAttackAnim`）时，通常要换到新入口，而不是硬改描述符。
### 9.5 手持挥动：两个版本是两套实现

- **26.2**：`getAttackAnim(float)` 是唯一入口，没法改时长 ⇒ 只能**自跑时钟**（`SwingSpeedAnimator`）并在 `RETURN` 覆盖返回值，`swing()` 里 `restart()`。
- **26.3**：`swing(hand, SwingAnimation, updateSelf)` **把动画作为参数传入**，状态与计数器都按这个时长建立 ⇒ 只要在入口把时长改掉，原版自己走完：

```java
    @ModifyVariable(method = "swing", at = @At("HEAD"), argsOnly = true, index = 2)
    private SwingAnimation sky$lengthenSwing(SwingAnimation animation) {
        int ticks = SwingSpeedAnimator.customDurationTicks();   // 原版时长 / 速度
        return ticks <= animation.duration() ? animation
                : new SwingAnimation(animation.type(), ticks);
    }
```

- **不要在 26.3 再覆盖 `getSwingAnimation` / `getCurrentSwing`**：那是读侧补丁，会和原版计数器形成**两条时间线**（相位差约 1 tick），表现为**手抖**与末段跳变（实测 `vanilla=0.85 / custom=0.76` 交错）。
- 结论：**时长在写侧改一次，进度交给原版**；`SwingSpeedAnimator` 在 26.3 只负责"记录手 + 算时长"。

### 9.6 着色器（26.3 编译到 SPIR-V）

自定义后处理着色器（`assets/skypocket/shaders/post/*.fsh`）必须同时满足两条，否则 `PostChain` 加载失败、效果**静默不生效**：

```glsl
#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
```

- SPIR-V 要求显式 `location`；而 GLSL 330 的 **fragment input** 只有在该扩展下才允许写 `layout(location=)`。缺扩展会报 `SPIR-V requires location`，加扩展前先加 location 又报 `location qualifier on input not supported`。
- 排查依据：日志出现 `com.mojang.renderpearl.util.ShaderCompileException` 与 `[SkyPocket/postChain] failed to load post chain`。

### 9.7 新增一个 MC 版本（checklist）

1. `settings.gradle.kts` 的 `listOf(...)` 加 `"<ver>-fabric"`；
2. `root.gradle.kts` 加 `createNode("<ver>-fabric", <verCode>, null)` 并 `link()` 到相邻版本。**mappings 必须传 `null`**：本工程用官方映射（非混淆），传 `"srg"` 会抛 `Project appears to not be obfuscated but ... mappings set`；
3. `gradle.properties` 补齐该版本全套键（`java_version.<ver>`、`loader_version.<ver>`、`fabric_version.<ver>`、`modmenu_version.<ver>`），缺哪个都会在配置期报 `gradle.properties needs ...`；
4. `gradlew :<ver>-fabric:compileJava`，按 9.4 处理 mixin 失配、按 9.3 补 Bloom 规则与 `//#if`；
5. 运行期重点验：mixin 是否全部应用、Click GUI 能否点击、后处理是否生效、手持动画是否完整。

> 教训：多版本下**编译通过 ≠ 能跑**。mixin 描述符、鼠标按键编号、着色器 location 这三类问题都只在运行时暴露，必须实机过一遍。
### 9.8 26.3 运动模糊顿挫：定位、修复与遗留

**现象**：26.3 下关闭 vsync 时运动模糊"一顿一顿"，26.2 不明显；开 vsync 则正常。

**测量**（临时 250ms 窗口日志，已移除）：

| 场景 | calls/s | 帧时长 | `scale` 区间 |
|---|---|---|---|
| vsync On 静止/甩动 | 320 | 3–4ms | 4.0 ~ 6.9 |
| vsync Off 静止 | 2084 | 0.5ms | 4.5 ~ 8.0 |
| vsync Off 甩动 | 1447 ~ 1730 | 0.6ms（含尖峰） | **1.24 ~ 8.0（6.5 倍）** |

`scale = 曝光(16ms) / 单帧时长`，所以 `scale` 反推即帧时长：`1.24` ⇒ **12.9ms 的尖峰**混在 0.5ms 的帧里。

**根因一（已修）**：强度与**瞬时**帧时长成反比 ⇒ 一次尖峰就让模糊从"很糊"塌到"几乎不糊"，下一帧弹回。

- `frameTime` 走 EMA（`FRAME_TIME_SMOOTHING = 0.15`，≈7 帧时间常数）；
- 参与计算的帧时长设 1ms 下限（`MIN_FRAME_SECONDS`）；
- `scale` 每帧最多变化 ±15%（`SCALE_STEP`），残留台阶也不会闪。

修后：Off 甩动 `7.86 ~ 8.00`、On `4.9 ~ 5.2`，**脉动消失**。

**根因二（部分修）**：无位移时也在每帧跑 10-tap 全屏 pass。

- `maxVelocity < MIN_VELOCITY (0.0015)` ⇒ 直接跳过 `CHAIN.apply` 与 `process`；
- 但 `PREVIOUS` / `lastCameraPos` / `hasHistory` **仍每帧更新**，否则"静止→转身"会攒出一次突兀强模糊。

**遗留问题（未解决，留档）**：

1. **2000fps 无 vsync 时仍有帧尖峰**（calls/s 从 2084 掉到 1447）。0.48ms/帧的预算下，任何 1ms 额外开销都等于"这一帧翻倍"，属渲染循环/驱动/GC 层，模组只能降低自身开销，无法根除。实际运行环境帧率远低于此，**暂不处理**。
2. 降开销的可选项（未做）：`parameters(...)` 每帧新建 `List<UniformValue>` 可复用；高帧率（`frameTime < 1ms`）时隔帧执行 pass（方案 C，会轻微改变时基）。
3. 未验证：uniform 是否晚一帧生效（`PREVIOUS` 的采样时间点）。若将来在正常帧率下看到拖影方向错位，**先查这里**，而不是调强度常数。

**教训**：后处理强度**不要直接用瞬时帧时长**。反比关系会把任何帧尖峰放大成可见的强度跳变；必须平滑 + 钳位，否则调参永远调不稳。
