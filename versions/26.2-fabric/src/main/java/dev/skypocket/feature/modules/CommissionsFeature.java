package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/** Lists the player's mining commissions, with sorting and colours. SkyBlock-only. */
public final class CommissionsFeature implements FeatureModule {

    @Override
    public String id() {
        return "commissions";
    }

    @Override
    public String name() {
        return "挖矿委托";
    }

    @Override
    public Category category() {
        return Category.MINING;
    }
    @Override
    public Object config() {
        return ConfigManager.get().commissionsBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().commissionsBlock.commissionsEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().commissionsBlock.commissionsEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        List<Setting> settings = new ArrayList<>();
        settings.add(new Setting.Header("显示", "调整委托列表的排序、缩放与颜色"));
        settings.add(new Setting.BooleanSetting("隐藏已完成的委托",
                () -> cfg.commissionsBlock.commissionsHideDone, v -> cfg.commissionsBlock.commissionsHideDone = v));
        settings.add(new Setting.BooleanSetting("未完成的置顶",
                () -> cfg.commissionsBlock.commissionsPendingFirst, v -> cfg.commissionsBlock.commissionsPendingFirst = v));
        settings.add(new Setting.SliderSetting("缩放", 0.25, 6.0,
                () -> cfg.commissionsBlock.commissionsScale, v -> cfg.commissionsBlock.commissionsScale = (float) v,
                "%.2f", ModConfig.SCALE_GRID));
        settings.add(new Setting.ColorSetting("标题颜色",
                () -> cfg.commissionsBlock.commissionsHeaderColor, v -> cfg.commissionsBlock.commissionsHeaderColor = v));
        settings.add(new Setting.ColorSetting("未完成颜色",
                () -> cfg.commissionsBlock.commissionsStartColor, v -> cfg.commissionsBlock.commissionsStartColor = v));
        settings.add(new Setting.ColorSetting("完成颜色",
                () -> cfg.commissionsBlock.commissionsDoneColor, v -> cfg.commissionsBlock.commissionsDoneColor = v));
        return settings;
    }
}
