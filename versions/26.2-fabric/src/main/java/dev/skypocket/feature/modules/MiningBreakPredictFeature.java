package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.i18n.Localization;
import dev.skypocket.skyblock.MiningStrengthTable;
import dev.skypocket.sound.SoundManager;

import java.util.ArrayList;
import java.util.List;

/** Predicts when the block being mined will break. SkyBlock-only. */
public final class MiningBreakPredictFeature implements FeatureModule {

    @Override
    public String id() {
        return "mining_break_predict";
    }

    @Override
    public String name() {
        return "挖矿破坏预测";
    }

    @Override
    public Category category() {
        return Category.MINING;
    }
    @Override
    public Object config() {
        return ConfigManager.get().miningBreakPredictBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().miningBreakPredictBlock.miningBreakPredictEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().miningBreakPredictBlock.miningBreakPredictEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        ModConfig cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.Header("提醒", "调整方块轮廓、提示偏移与提醒音效"));
        settings.add(new Setting.BooleanSetting("方块轮廓",
                () -> cfg.miningBreakPredictBlock.miningBreakOutlineEnabled, v -> cfg.miningBreakPredictBlock.miningBreakOutlineEnabled = v));
        settings.add(new Setting.ColorSetting("轮廓颜色",
                () -> cfg.miningBreakPredictBlock.miningBreakOutlineColor, v -> cfg.miningBreakPredictBlock.miningBreakOutlineColor = v, true));
        // Negative values predict early (the break happens before the client would show it).
        settings.add(new Setting.SliderSetting("提示偏移(Tick)", -6, 3,
                () -> cfg.miningBreakPredictBlock.miningBreakBiasTicks,
                v -> cfg.miningBreakPredictBlock.miningBreakBiasTicks = (int) Math.round(v), "%.0f", 1));
        settings.add(new Setting.DropdownSetting("提醒音效", SoundManager::listAllSounds,
                () -> cfg.miningBreakPredictBlock.miningBreakSound, v -> cfg.miningBreakPredictBlock.miningBreakSound = v));
        settings.add(new Setting.SliderSetting("音量", 0, 200,
                () -> cfg.miningBreakPredictBlock.miningBreakSoundVolume * 100,
                v -> cfg.miningBreakPredictBlock.miningBreakSoundVolume = (float) (v / 100.0), "%.0f%%", 1));
        settings.add(new Setting.SliderSetting("音高", 0.5, 2.0,
                () -> cfg.miningBreakPredictBlock.miningBreakSoundPitch,
                v -> cfg.miningBreakPredictBlock.miningBreakSoundPitch = (float) v, "%.2f", 0.05));

        // Calibration and diagnostics are not everyday settings, so they fold into one group. It has
        // no switch of its own - there is nothing meaningful to turn off as a unit - which is why this
        // is a plain GroupSetting rather than one with a getter/setter pair.
        List<Setting> advanced = new ArrayList<>();
        advanced.add(new Setting.ActionSetting(
                () -> Localization.tr("重置矿物强度识别") + " (" + MiningStrengthTable.size() + ")",
                MiningStrengthTable::clear));
        advanced.add(new Setting.DropdownSetting("取整方式",
                () -> List.of("ceil", "floor", "round"),
                () -> cfg.miningBreakPredictBlock.miningTickRounding, v -> cfg.miningBreakPredictBlock.miningTickRounding = v));
        advanced.add(new Setting.BooleanSetting("自动校准速度刻度",
                () -> cfg.miningBreakPredictBlock.miningAutoScale, v -> cfg.miningBreakPredictBlock.miningAutoScale = v));
        advanced.add(new Setting.BooleanSetting("相位对齐",
                () -> cfg.miningBreakPredictBlock.miningPhaseAlign, v -> cfg.miningBreakPredictBlock.miningPhaseAlign = v));
        advanced.add(new Setting.DropdownSetting("对准时刻",
                () -> List.of("zero", "server", "client"),
                () -> cfg.miningBreakPredictBlock.miningAlignTarget, v -> cfg.miningBreakPredictBlock.miningAlignTarget = v));
        advanced.add(new Setting.SliderSetting("最小破坏 tick", 1, 20,
                () -> cfg.miningBreakPredictBlock.miningMinBreakTicks,
                v -> cfg.miningBreakPredictBlock.miningMinBreakTicks = (int) Math.round(v), "%.0f", 1));
        advanced.add(new Setting.BooleanSetting("调试信息",
                () -> cfg.miningBreakPredictBlock.miningBreakDebugHud, v -> cfg.miningBreakPredictBlock.miningBreakDebugHud = v));
        settings.add(new Setting.GroupSetting("校准与调试", advanced)
                .subtitle("调试信息与高级显示选项"));
        return settings;
    }
}
