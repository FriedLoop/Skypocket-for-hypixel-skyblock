package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/**
 * Outlines dropped items by rarity.
 *
 * <p>Phase 4. SkyBlock-only, and its header-only settings list is kept exactly as it was: the
 * header carries the explanation, so the card still opens a page rather than showing "no settings".
 */
public final class ItemGlowFeature implements FeatureModule {

    @Override
    public String id() {
        return "item_glow";
    }

    @Override
    public String name() {
        return "掉落物发光";
    }

    @Override
    public Category category() {
        return Category.ESP;
    }
    @Override
    public Object config() {
        return ConfigManager.get().itemGlowBlock;
    }

    @Override
    public boolean skyBlockOnly() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().itemGlowBlock.itemGlowEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().itemGlowBlock.itemGlowEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        return List.of(
                new Setting.Header("外观", "按物品稀有度给掉落物添加发光轮廓"));
    }
}
