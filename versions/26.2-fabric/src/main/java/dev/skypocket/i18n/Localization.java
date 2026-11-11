package dev.skypocket.i18n;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Minimal runtime i18n. UI strings are written as Chinese literals (which double
 * as the lookup key); {@link #tr(String)} returns the English translation when the
 * current language is English, otherwise the Chinese original.
 */
public final class Localization {

    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("i18n");

    private static final Map<String, String> EN = new HashMap<>();

    static {
        // category tabs
        EN.put("渲染", "Render");
        EN.put("界面", "HUD");
        EN.put("移动", "Movement");
        EN.put("通知", "Notifications");
        // skyblock sub-categories
        EN.put("地牢", "Dungeons");
        EN.put("挖矿", "Mining");
        EN.put("农耕", "Farming");
        EN.put("战斗", "Combat");
        EN.put("钓鱼", "Fishing");
        EN.put("事件", "Events");
        EN.put("钓鱼热点覆盖层", "Fishing Hotspot Overlay");
        EN.put("无底圆柱（侧面）", "Bottomless Cylinder (Side)");
        EN.put("实心圆", "Filled Circle");
        EN.put("空心圆", "Hollow Circle");
        EN.put("钓鱼速度", "Fishing Speed");
        EN.put("海洋生物几率", "Sea Creature Chance");
        EN.put("双重上钩几率", "Double Hook Chance");
        EN.put("奖杯几率", "Trophy Chance");
        EN.put("宝藏几率", "Treasure Chance");
        EN.put("未知类型", "Unknown Type");
        EN.put("高度", "Height");
        EN.put("显示距离", "Render Distance");
        // mining ability reminder
        EN.put("挖矿技能提醒", "Mining Ability Reminder");
        EN.put("挖矿破坏预测", "Mining Break Prediction");
    EN.put("抗锯齿", "Anti-Aliasing");
    EN.put("画面调色", "Color Grading");
    EN.put("预设", "Preset");
    EN.put("套用预设", "Apply Preset");
    EN.put("曝光", "Exposure");
    EN.put("对比度", "Contrast");
    EN.put("饱和度", "Saturation");
    EN.put("伽马", "Gamma");
    EN.put("黑位", "Lift");
    EN.put("色温", "Temperature");
    EN.put("暗角", "Vignette");
    EN.put("锐化", "Sharpen");
    EN.put("动态模糊", "Motion Blur");
    EN.put("调试：显示速度", "Debug: Show Velocity");
    EN.put("曝光", "Exposure");
    EN.put("最大半径", "Max Radius");
    EN.put("泛光", "Bloom");
    EN.put("阈值", "Threshold");
    EN.put("半径", "Radius");
    EN.put("玩家数值显示", "Player Stats");
    EN.put("关闭", "Close");
    EN.put("启用", "Enabled");
    EN.put("找不到设置模块", "Settings module not found");
    EN.put("切换到现代风格", "Switch to Modern Style");
    EN.put("切换到经典风格", "Switch to Classic Style");
    EN.put("法力不足时闪烁", "Blink When Mana Low");
    EN.put("生命数值", "Health Readout");
    EN.put("法力数值", "Mana Readout");
    EN.put("生命数值大小", "Health Readout Size");
    EN.put("法力数值大小", "Mana Readout Size");
    EN.put("原版 HUD", "Vanilla HUD");
    EN.put("隐藏原版元素，避免和本模块重复绘制",
            "Hide vanilla elements so they do not draw twice with this module");
    EN.put("风格", "Style");
    EN.put("经典", "Classic");
    EN.put("现代", "Modern");
    EN.put("经典沿用原版图标，现代改用条；切换后只显示对应风格的设置",
            "Classic keeps the vanilla icons, modern uses bars; only the matching settings show");
    EN.put("经典风格", "Classic Style");
    EN.put("原版图标 + 精确数值；两行文字的位置在「HUD 位置调整」页里拖",
            "Vanilla icons plus exact numbers; drag the two rows in the HUD position editor");
    EN.put("现代风格 · 条", "Modern Style · Bars");
    EN.put("位置在「HUD 位置调整」页里逐条拖拽与缩放，这里只管粗细与开关",
            "Each bar is dragged and sized in the HUD position editor; these rows only switch them");
    EN.put("本模块会自己画血量，留着会两层叠在一起", "This module draws its own; keeping them stacks two");
    EN.put("饥饿那一行被法力占用", "The hunger row is where mana goes");
    EN.put("它会正好落在法力的第二行上", "It would land exactly on mana's second row");
    EN.put("它和本模块的布局不冲突，所以默认留着", "It does not clash with this layout, so it stays");
    EN.put("只隐藏绘制：数值仍然在读取，显示照常更新",
            "Drawing only: the values keep updating");
    EN.put("剩余不足两格时按原版节奏闪烁", "Blinks on vanilla's cadence at two slots or less");
    EN.put("20 格图标表达不了一个点；这里给出精确值与吸收量",
            "Twenty icons cannot show a point; this gives the value and absorption");
    EN.put("精确值与溢出法力", "The value and overflow mana");
    EN.put("六条共用，保证一组条的粗细一致", "Shared by all six, so they read as a set");
    EN.put("吸收血量作为第二层叠在它上面", "Absorption is drawn as a second layer over it");
    EN.put("高级", "Advanced");
    EN.put("重置条位置与恢复默认颜色", "Reset bar positions and restore the default colours");
    EN.put("重置条位置", "Reset Bar Positions");
    EN.put("恢复默认颜色", "Reset Colours");
    EN.put("颜色", "Colours");
    EN.put("现代风格的条与经典风格的数值文字共用同一套",
            "Shared by the modern bars and the classic readouts");
    EN.put("文字行", "Text Rows");
    EN.put("仅经典风格；现代风格的条已经直接显示这些数值",
            "Classic only; the modern bars already display these values");
    EN.put("防御 / 速度 / 活力：两套风格下都显示，位置同样在「HUD 位置调整」页里拖",
            "Defense / speed / vitality: shown in both styles, positioned in the HUD editor");
    EN.put("隐藏原版生命条", "Hide Vanilla Hearts");
    EN.put("隐藏原版饥饿条", "Hide Vanilla Hunger");
    EN.put("隐藏原版空气泡", "Hide Vanilla Air Bubbles");
    EN.put("隐藏原版盔甲条", "Hide Vanilla Armor");
    EN.put("隐藏原版动作栏", "Hide Vanilla Action Bar");
    EN.put("条宽", "Bar Width");
    EN.put("条高", "Bar Height");
    EN.put("条间距", "Bar Spacing");
    EN.put("距底部", "Bottom Offset");
    EN.put("条上显示数值", "Show Values on Bars");
    EN.put("生命条", "Health Bar");
    EN.put("法力条", "Mana Bar");
    EN.put("防御条", "Defense Bar");
    EN.put("速度条", "Speed Bar");
    EN.put("活力条", "Vitality Bar");
    EN.put("空气条", "Air Bar");
    EN.put("生命条大小", "Health Bar Size");
    EN.put("法力条大小", "Mana Bar Size");
    EN.put("防御条大小", "Defense Bar Size");
    EN.put("速度条大小", "Speed Bar Size");
    EN.put("活力条大小", "Vitality Bar Size");
    EN.put("空气条大小", "Air Bar Size");
    EN.put("重置条位置", "Reset Bar Positions");
    EN.put("生命颜色", "Health Colour");
    EN.put("吸收颜色", "Absorption Colour");
    EN.put("法力颜色", "Mana Colour");
    EN.put("溢出法力颜色", "Overflow Mana Colour");
    EN.put("空气颜色", "Air Colour");
    EN.put("防御颜色", "Defense Colour");
    EN.put("速度颜色", "Speed Colour");
    EN.put("活力颜色", "Vitality Colour");
    EN.put("防御文字", "Defense Row");
    EN.put("速度文字", "Speed Row");
    EN.put("活力文字", "Vitality Row");
    EN.put("防御文字大小", "Defense Row Size");
    EN.put("速度文字大小", "Speed Row Size");
    EN.put("活力文字大小", "Vitality Row Size");
    EN.put("防御", "Defense");
    EN.put("速度", "Speed");
    EN.put("活力", "Vitality");
    EN.put("（暂无数据）", "(no data)");
    EN.put("强度", "Strength");
    EN.put("光影下也生效", "Also With Shaders");
    EN.put("开界面时跳过", "Skip With GUI Open");
    EN.put("挖矿委托", "Commissions");
    EN.put("隐藏已完成的委托", "Hide Completed");
    EN.put("未完成的置顶", "Pending First");
    EN.put("标题颜色", "Header Color");
    EN.put("未完成颜色", "Pending Color");
    EN.put("完成颜色", "Done Color");
        EN.put("方块轮廓", "Block Outline");
        EN.put("轮廓颜色", "Outline Color");
        EN.put("延后 Tick", "Assert Delay");
        EN.put("提示偏移(Tick)", "Alert Offset (ticks)");
        EN.put("重置破坏时长学习", "Reset Break-Time Learning");
        EN.put("重置矿物强度识别", "Reset Block Identification");
        EN.put("伤害飘字", "Damage splash");
    EN.put("归属半径", "Attach radius");
    EN.put("合并窗口(毫秒)", "Merge window (ms)");
    EN.put("合并", "Compact");
    EN.put("大写格式化", "Uppercase formatting");
    EN.put("显示次数", "Show hit count");
    EN.put("显示时长(毫秒)", "Display time (ms)");
    EN.put("淡出(毫秒)", "Fade (ms)");
    EN.put("右侧偏移", "Right offset");
    EN.put("行高", "Row height");
    EN.put("聊天消息压缩", "Chat compacting");
    EN.put("压缩窗口(秒)", "Compact window (s)");
    EN.put("调试信息", "Debug Info");

    EN.put("取整方式", "Rounding");
    EN.put("自动校准速度刻度", "Auto-calibrate speed");
    EN.put("相位对齐", "Tick phase alignment");
    EN.put("对准时刻", "Align to");
    EN.put("最小破坏 tick", "Min break ticks");
        EN.put("冷却倒计时", "Ability Cooldown");
        EN.put("技能提醒", "Ability Alert");
        EN.put("可用!", "available!");
        EN.put("技能名颜色", "Ability Name Color");
        EN.put("可用颜色", "Available Color");
        EN.put("冷却缩放", "Cooldown Scale");
        EN.put("提醒缩放", "Reminder Scale");
        EN.put("提醒音效", "Alert Sound");
        EN.put("音量", "Volume");
        EN.put("音高", "Pitch");
        EN.put("搜索", "Search");
        EN.put("动画时长", "Animation Duration");
        EN.put("缓动曲线", "Easing");
        EN.put("文字阴影", "Text Shadow");
        EN.put("面板渲染缓存", "Panel Render Cache");
    EN.put("面板缓存统计", "Panel Cache Stats");
    EN.put("调试日志", "Debug Logging");
    EN.put("字体实验", "Font Experiments");
    EN.put("实验：整数 advance", "Exp: Integer Advance");
    EN.put("实验：原点定位", "Exp: Origin Placement");
    EN.put("实验：Hinting", "Exp: Hinting");
    EN.put("实验：字重", "Exp: Weight");
    EN.put("实验：光栅倍率", "Exp: Raster Scale");
    EN.put("实验：线性字体采样", "Exp: Linear Font Sampling");
    EN.put("实验：自研字形图集", "Exp: Custom Glyph Atlas");
    EN.put("实验：覆盖率 Gamma", "Exp: Coverage Gamma");
    EN.put("实验：应用最佳预设", "Exp: Apply Best Preset");
    EN.put("实验：应用 2x 平滑", "Exp: Apply 2x Smooth");
    EN.put("实验：恢复原生字体", "Exp: Restore Native Font");
    EN.put("主题", "Theme");
    EN.put("删除主题 ", "Delete theme ");
    EN.put("删除", "Delete");
    EN.put("取消", "Cancel");
    EN.put("已删除 ", "Deleted ");
    EN.put("删除失败，内置主题不可删除", "Delete failed - built-in themes cannot be removed");
    EN.put("内置主题不能删除", "Built-in themes cannot be deleted");
    EN.put("已切换到 ", "Switched to ");
    EN.put("，未保存的改动已丢弃", " - unsaved changes discarded");
    EN.put("请输入名字", "Enter a name");
    EN.put("名字已存在", "That name already exists");
    EN.put("已还原未保存的改动", "Discarded unsaved changes");

    // Theme studio token help.
    EN.put("面板主体背景，透明度由“背景透明度”统一控制",
            "Panel body background; opacity is controlled by Background Opacity");
    EN.put("顶部栏、侧栏、footer 与 tab 的底色",
            "Top bar, sidebar, footer and tab base");
    EN.put("卡片和页面主体表面", "Card and page surfaces");
    EN.put("按钮、普通控件和列表行的底色", "Buttons, normal controls and list rows");
    EN.put("输入框、下拉框和搜索框", "Text fields, dropdowns and search boxes");
    EN.put("输入框内部的数值框", "Value box inside a text field");
    EN.put("主色填充：选中、开启、滑块和重点元素",
            "Primary fill: selection, enabled states, sliders and emphasis");
    EN.put("主色填充的 hover 状态", "Primary fill hover state");
    EN.put("主色背景上的文字和开关滑块", "Text and switch knobs on primary backgrounds");
    EN.put("主要文字", "Primary text");
    EN.put("说明文字和次级信息", "Descriptions and secondary text");
    EN.put("弱化文字、禁用文字和占位符", "Muted text, disabled text and placeholders");
    EN.put("开关开启时的轨道颜色", "Switch track when on");
    EN.put("开关关闭时的轨道颜色", "Switch track when off");
    EN.put("每一行设置项的高度", "Height of each settings row");
    EN.put("行内控件的高度", "Height of controls inside a row");
    EN.put("标签距离行左侧的距离", "Label inset from the row's left edge");
    EN.put("滑条和下拉框的控件列宽度", "Width of the slider/dropdown control column");
    EN.put("设置卡片标题上方的间距", "Gap above a card title");
    EN.put("设置卡片标题行的高度", "Height of the card title row");
    EN.put("卡片和控件圆角半径", "Card and control corner radius");
    EN.put("开关轨道宽度", "Switch track width");
    EN.put("开关轨道高度", "Switch track height");
    EN.put("滑条轨道厚度", "Slider track thickness");
    EN.put("滑条圆点直径", "Slider knob diameter");

    // Module settings card headers and descriptions.
    EN.put("布局与动画", "Layout & Animation");
    EN.put("调整 ClickGUI 缩放和打开/关闭动画",
            "Adjust ClickGUI scale and open/close animation");
    EN.put("控制模块生效范围和界面语言",
            "Control module availability and interface language");
    EN.put("性能", "Performance");
    EN.put("控制面板渲染缓存", "Control panel render caching");
    EN.put("调整主题、毛玻璃、透明度和文字阴影",
            "Adjust theme, blur, opacity and text shadows");
    EN.put("HUD 字体", "HUD Font");
    EN.put("选择 HUD 文本使用的字体；切换后立即重新测量",
            "Choose the HUD text font; switching re-measures immediately");
    EN.put("使用 SkyPocket 字体", "Use SkyPocket Font");
    EN.put("预览", "Preview");
    EN.put("实时预览准星形状与像素网格", "Live preview of the crosshair shape and pixel grid");
    EN.put("外观", "Appearance");
    EN.put("目标变色", "Target Tint");
    EN.put("行为", "Behavior");
    EN.put("显示", "Display");
    EN.put("提醒", "Alerts");
    EN.put("过滤", "Filter");
    EN.put("动作", "Actions");
    EN.put("位置与旋转", "Position & Rotation");
    EN.put("位置与大小", "Position & Size");
    EN.put("校准与调试", "Calibration & Debug");
    EN.put("显示风格", "Display Style");
    EN.put("原版元素", "Vanilla Elements");
    EN.put("经典文字行", "Classic Text Rows");
    EN.put("经典布局", "Classic Layout");
    EN.put("现代条", "Modern Bars");
    EN.put("重置与恢复", "Reset & Restore");
    EN.put("控制伤害飘字的合并、归属与显示次数",
            "Control damage splash merging, attribution and count display");
    EN.put("调整方块轮廓、提示偏移与提醒音效",
            "Adjust the block outline, cue offset and alert sound");
    EN.put("调整描边颜色、厚度与方块面填充",
            "Adjust outline colour, thickness and block-face fill");
    EN.put("设置提及音效、文本与 ID 匹配长度",
            "Set the mention sound, text and ID match length");
    EN.put("设置重复聊天消息的合并窗口", "Set the merge window for repeated chat messages");
    EN.put("调整技能名与可用颜色、缩放和提醒音效",
            "Adjust ability name/available colours, scale and alert sound");
    EN.put("调整委托列表的排序、缩放与颜色",
            "Adjust commission ordering, scale and colours");
    EN.put("调整玩家发光的颜色", "Adjust the player glow colour");
    EN.put("调整末影龙发光的颜色", "Adjust the ender dragon glow colour");
    EN.put("设置隐藏附近玩家的范围", "Set the range for hiding nearby players");
    EN.put("调整碰撞箱颜色、线宽和视线",
            "Adjust hitbox colour, line width and line of sight");
    EN.put("选择要显示碰撞箱的实体类型",
            "Choose which entity types show hitboxes");
    EN.put("控制碰撞箱的显示距离", "Control the hitbox display distance");
    EN.put("样式", "Style");
    EN.put("调整热点样式、颜色、高度、线宽与显示距离",
            "Adjust hotspot style, colours, height, line width and range");
    EN.put("调整热点类型颜色、高度、线宽与显示距离",
            "Adjust hotspot colours, height, line width and range");
    EN.put("设置提示框的滚动方式与灵敏度",
            "Set tooltip scrolling behaviour and sensitivity");
    EN.put("调整手持物品的大小", "Adjust the held item size");
    EN.put("控制挥动、切换和挥砍速度",
            "Control swing, re-equip and swing speed");
    EN.put("调整第一人称手部的偏移与旋转",
            "Adjust first-person hand offset and rotation");
    EN.put("设置时间制式与时钟大小", "Set the time format and clock size");
    EN.put("调整世界天数文字大小", "Adjust the world day text size");
    EN.put("选择显示项目、大小与排版", "Choose displayed values, size and layout");
    EN.put("调整手持物品名的缩放与屏幕偏移",
            "Adjust held item name scale and screen offset");

    // Theme studio.
    EN.put("主题工作室", "Theme Studio");
    EN.put("碰撞箱", "Collision boxes");
    EN.put("玩家数值", "Player stats");
    EN.put("重载", "Reload");
    EN.put("保存", "Save");
    EN.put("还原", "Restore");
    EN.put("导出", "Export");
    EN.put("颜色", "Colours");
    EN.put("度量", "Metrics");
    EN.put("按钮", "Buttons");
    EN.put("开关", "Switches");
    EN.put("滑块 / 输入", "Sliders / Fields");
    EN.put("列表 / 卡片", "Lists / Cards");
    EN.put("文字层级", "Text");
    EN.put("常态", "Normal");
    EN.put("悬停", "Hover");
    EN.put("强调", "Accent");
    EN.put("禁用", "Disabled");
    EN.put("悬停行", "Hovered row");
    EN.put("当前行", "Current row");
    EN.put("模块卡片", "Module card");
    EN.put("主要文字", "Primary");
    EN.put("次要文字", "Secondary");
    EN.put("弱化文字", "Muted");
    EN.put("拖拽取色 / 输入 HEX", "Drag, or type HEX");
    EN.put("新建", "New");
    EN.put("已加载 ", "Loaded ");
    EN.put("已重载 ", "Reloaded ");
    EN.put("已保存到 ", "Saved to ");
    EN.put("保存失败，见日志", "Save failed - see log");
    EN.put("新建失败，见日志", "Create failed - see log");
    EN.put("已新建 ", "Created ");
    EN.put("新主题名字", "New theme name");
    EN.put("名字", "Name");
    EN.put("回车确认 / ESC 取消", "Enter to confirm / ESC to cancel");
    EN.put("点击左列颜色项打开拾色器", "Pick a colour on the left to open the picker");
    EN.put("调试", "Debug");
    // Floating card in the HUD position editor.
    EN.put("当前: ", "Selected: ");
    EN.put("位置 X=%d Y=%d", "Position X=%d Y=%d");
    EN.put("尺寸 %d×%d  缩放 %.2f", "Size %d×%d  Scale %.2f");
    EN.put("缩放 %.2f", "Scale %.2f");
    // Two rows, not one: the card's width is measured from its widest row, so a single long hint line
    // made it wider than necessary. Keep these keys and the strings in HudPositionEditorScreen#panelRows
    // in step - changing one without the other is what left these two rows in Chinese.
    EN.put("拖动移动 · 把手改尺寸", "Drag to move · handles to resize");
    EN.put("方向键微调 · ESC 退出", "Arrows to nudge · ESC to exit");
        // modules
        EN.put("自定义准星", "Crosshair");
        EN.put("方块描边", "Block Outline");
        EN.put("第三人称穿墙", "Third-Person No-Clip");
        EN.put("受伤镜头不抖动", "No Hurt Cam");
        EN.put("聊天提及提醒", "Chat Mention Alert");
        EN.put("音效", "Sound");
        EN.put("内置提示音", "Ding");
        EN.put("快捷上传自定义音频", "Upload Custom Sound");
        EN.put("自定义检索文本", "Custom Search Text");
    EN.put("自定义提醒文本", "Custom Alert Text");
    EN.put("在此输入文本", "Type your text here");
        EN.put("ID 检索长度", "ID Match Length");
        EN.put("选择音频文件", "Select Audio File");
        EN.put("(留空则用游戏ID)", "(empty = game ID)");
        EN.put("人物发光", "Player Glow");
        EN.put("末影龙发光", "Ender Dragon Glow");
        EN.put("发光颜色", "Glow Color");
        EN.put("隐藏附近玩家", "Hide Nearby Players");
        EN.put("隐藏出生点玩家", "Hide Spawn Point Players");
        EN.put("隐藏 NPC 附近玩家", "Hide Players Near NPCs");
        EN.put("出生点范围", "Spawn Point Radius");
        EN.put("NPC 附近范围", "NPC Radius");
        EN.put("隐藏站在 Hub 出生点的真实玩家", "Hide real players standing at the Hub spawn point");
        EN.put("隐藏站在 SkyBlock NPC 附近的真实玩家",
                "Hide real players standing near a SkyBlock NPC");
        EN.put("传奇海怪警告", "Legendary Sea Creature Warning");
        EN.put("警告", "Warning");
        EN.put("警告颜色", "Warning Colour");
        EN.put("显示时长", "Display Duration");
        EN.put("检测传奇/神话海怪刷新并显示中央警告",
                "Detect Legendary/Mythic sea creature spawns and show a centre warning");
        EN.put("传奇海怪出现!", "Legendary Sea Creature!");
        EN.put("技能进度显示", "Skill Progress Display");
        EN.put("显示最近获得经验的技能、当前 XP 和下一级需求，图标使用原版物品",
                "Show the most recently gained skill, current XP and next-level requirement");
        EN.put("仅显示百分比", "Percentage Only");
        EN.put("文字颜色", "Text Colour");
        EN.put("隐藏落地箭矢", "Hide Landed Arrows");
        EN.put("隐藏插体箭矢", "Hide Stuck Arrows");
        EN.put("范围", "Range");
        EN.put("手持物品动画", "Hand Item");
        EN.put("滚动提示框", "Scrollable Tooltips");
        EN.put("始终可滚动", "Always Scrollable");
        EN.put("滚动灵敏度", "Scroll Sensitivity");
        EN.put("时钟", "Clock");
        EN.put("自动疾跑", "Auto Sprint");
        // common
        EN.put("开", "ON");
        EN.put("关", "OFF");
        EN.put("完成", "Done");
        EN.put("设置", "Settings");
        EN.put("无设置", "No Settings");
        EN.put("HUD 位置调整", "HUD Positions");
        EN.put("返回", "Back");
        EN.put("全部", "All");
        EN.put("搜索...", "Search...");
        EN.put("语言", "Language");
    EN.put("跟随系统", "System");
    EN.put("中文", "Chinese");
        // crosshair
        EN.put("描边", "Stroke");
        EN.put("描边宽度", "Stroke Width");
        EN.put("像素大小", "Pixel Size");
        EN.put("缩放", "Scale");
    EN.put("手持物品名", "Held Item Name");
    EN.put("物品名", "Item Name");
    EN.put("水平偏移", "Horizontal Offset");
    EN.put("垂直偏移", "Vertical Offset");
        EN.put("准星颜色", "Crosshair Color");
        EN.put("目标变色设置", "Target Colors");
        // block outline
        EN.put("描边-红", "Edge-R");
        EN.put("描边-绿", "Edge-G");
        EN.put("描边-蓝", "Edge-B");
        EN.put("描边-透明度", "Edge-A");
        EN.put("描边厚度", "Thickness");
        EN.put("方块面调色", "Face Tint");
        EN.put("面-红", "Face-R");
        EN.put("面-绿", "Face-G");
        EN.put("面-蓝", "Face-B");
        EN.put("面-透明度", "Face-A");
        EN.put("描边颜色", "Edge Color");
        EN.put("面颜色", "Face Color");
        // hand item
        EN.put("物品大小", "Item Scale");
        EN.put("禁用挥动动画", "Disable Swing");
        EN.put("挥砍速度", "Swing Speed");
        EN.put("忽略急迫等效果", "Ignore Haste");
        EN.put("位置 X", "Offset X");
        EN.put("位置 Y", "Offset Y");
        EN.put("位置 Z", "Offset Z");
        EN.put("禁用切换动画", "Disable Reequip");
        EN.put("旋转 X", "Rotate X");
        EN.put("旋转 Y", "Rotate Y");
        EN.put("旋转 Z", "Rotate Z");
        // clock
        EN.put("24 小时制", "24-Hour Clock");
        EN.put("时钟大小", "Clock Scale");
        EN.put("位置: X=%d, Y=%d", "Position: X=%d, Y=%d");
        // color picker
        EN.put("红", "Red");
        EN.put("绿", "Green");
        EN.put("蓝", "Blue");
        EN.put("透明度", "Alpha");
        // panel settings
        EN.put("快捷面板设置", "Panel Settings");
        EN.put("面板设置", "Panel Settings");
        EN.put("功能", "Modules");
        EN.put("ClickGUI 缩放", "ClickGUI Scale");
        EN.put("背景透明度", "Background Opacity");

        EN.put("SkyBlock 模块仅在 SkyBlock 生效", "SkyBlock Modules Only in SkyBlock");
        EN.put("毛玻璃效果", "Blur");
        EN.put("毛玻璃强度", "Blur Strength");
        EN.put("拖动面板标题栏可自定义位置", "Drag the title bar to move");
        // entity colors
        EN.put("准星目标变色", "Crosshair Target Colors");
        EN.put("准星外观", "Crosshair Appearance");
    EN.put("瞄准目标变色", "Tint On Target");
    EN.put("后处理", "Post-processing");
        EN.put("友好", "Friendly");
        EN.put("中立", "Neutral");
        EN.put("敌对", "Hostile");
        EN.put("箱子", "Container");
        EN.put("友好颜色", "Friendly Color");
        EN.put("中立颜色", "Neutral Color");
        EN.put("敌对颜色", "Hostile Color");
        EN.put("箱子颜色", "Container Color");
        // fps/tps/ping + hitbox
        EN.put("拖动调整位置，方向键微调（每次 1px），滚轮/滑块调整大小",
                "Drag to move, arrow keys nudge (1px), scroll/slider to resize");
        EN.put("大小", "Scale");
        EN.put("颜色", "Color");
        EN.put("世界天数", "World Day");
        EN.put("第 %d 天", "Day %d");
        EN.put("碰撞箱", "Hitbox");
        EN.put("碰撞箱设置", "Hitbox Settings");
        EN.put("玩家", "Players");
        EN.put("箭矢", "Projectiles");
        EN.put("敌对生物", "Hostile Mobs");
        EN.put("中立生物", "Neutral Mobs");
        EN.put("其他", "Others");
        EN.put("实体视线", "Look Ray");
        EN.put("视线颜色", "Look Ray Color");
        EN.put("视线长度", "Look Ray Length");
        EN.put("线宽", "Line Width");
        EN.put("竖向排版", "Vertical Layout");
    EN.put("横向排版", "Horizontal Layout");
    EN.put("排版", "Layout");
        EN.put("切换到横向排版", "Switch to Horizontal");
        EN.put("切换到竖向排版", "Switch to Vertical");
        EN.put("不限距离", "Unlimited Range");
        EN.put("调整此卡片的选项", "Adjust the options on this card");
        EN.put("调整本模块的主要选项", "Adjust the module's main options");
        EN.put("调整准星线条、颜色、像素大小与缩放", "Tune crosshair lines, colors, pixel size and scale");
        EN.put("按目标类型切换准星颜色", "Tint the crosshair by target type");
        EN.put("平滑边缘，并按强度调整锐化", "Smooth edges and sharpen by strength");
        EN.put("移动视角时加入方向性模糊", "Add directional blur when moving the camera");
        EN.put("调整画面亮度、对比度、饱和度与色调", "Adjust brightness, contrast, saturation and tint");
        EN.put("让高亮区域产生柔和光晕", "Add a soft glow around bright areas");
        EN.put("调试信息与高级显示选项", "Debug information and advanced display options");
        EN.put("字体与渲染调试选项", "Font and rendering debug options");
        EN.put("字体栅格、字重与渲染实验", "Font raster, weight and rendering experiments");
        EN.put("控制伤害飘字的显示时长、淡出与排版", "Control damage popup lifetime, fade and layout");
    }

    private Localization() {
    }

    /** Keys already reported as untranslated, so a missing entry logs once instead of every frame. */
    private static final Set<String> MISSING = new HashSet<>();

    public static String tr(String zh) {
        if (zh == null || zh.isEmpty() || !hasCjk(zh)) {
            // Already-localized English (or a non-translatable token) must not be looked up again.
            return zh;
        }
        if (!isEnglish()) {
            return zh;
        }
        String en = EN.get(zh);
        if (en == null) {
            // Falling back to the key keeps the UI readable, but it also hides the omission - which is
            // exactly how two hint rows stayed Chinese while everything else followed the language.
            if (MISSING.add(zh)) {
                LOGGER.warn("missing English translation: {}", zh);
            }
            return zh;
        }
        return en;
    }

    /**
     * Drawing-boundary translation: UI text is localized even when a constructor forgot to call
     * {@link #tr}. Already-English text contains no CJK and returns immediately; explicit tr() calls
     * are therefore safe to double-pass.
     */
    public static String auto(String text) {
        if (text == null || text.isEmpty() || !hasCjk(text)) {
            return text;
        }
        if (!isEnglish()) {
            return text;
        }
        String en = EN.get(text);
        if (en == null) {
            if (MISSING.add(text)) {
                LOGGER.warn("missing English translation: {}", text);
            }
            return text;
        }
        return en;
    }

    private static boolean hasCjk(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if ((cp >= 0x3400 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isEnglish() {
        ModConfig cfg = ConfigManager.get();
        String lang = cfg.language;
        if (lang == null || "system".equals(lang)) {
            String code = Minecraft.getInstance().options.languageCode;
            return code == null || !code.startsWith("zh");
        }
        return "en_us".equals(lang);
    }

    /** Human-readable name of the currently selected language (shown in its own language). */
    public static String displayName() {
        String lang = ConfigManager.get().language;
        return switch (lang == null ? "system" : lang) {
            case "zh_cn" -> "中文";
            case "en_us" -> "English";
            default -> "跟随系统";
        };
    }

}
