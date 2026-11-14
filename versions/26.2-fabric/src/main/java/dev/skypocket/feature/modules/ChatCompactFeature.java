package dev.skypocket.feature.modules;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.feature.FeatureModule;
import dev.skypocket.gui.Category;
import dev.skypocket.gui.setting.Setting;

import java.util.List;

/** Merges identical chat messages received within a time window. */
public final class ChatCompactFeature implements FeatureModule {

    @Override
    public String id() {
        return "chat_compact";
    }

    @Override
    public String name() {
        return "聊天消息压缩";
    }

    @Override
    public Category category() {
        return Category.NOTIFICATION;
    }
    @Override
    public Object config() {
        return ConfigManager.get().chatCompactBlock;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.get().chatCompactBlock.chatCompactEnabled;
    }

    @Override
    public void setEnabled(boolean value) {
        ConfigManager.get().chatCompactBlock.chatCompactEnabled = value;
    }

    @Override
    public List<Setting> settings() {
        var cfg = ConfigManager.get();
        return List.of(
                new Setting.Header("行为", "设置重复聊天消息的合并窗口"),
                // The field is an int; the slider still reports a double, so it round-trips through
                // Math.round exactly as the hand-written registration did.
                new Setting.SliderSetting("压缩窗口(秒)", 1, 120,
                        () -> cfg.chatCompactBlock.chatCompactTimeoutSeconds,
                        v -> cfg.chatCompactBlock.chatCompactTimeoutSeconds = (int) Math.round(v), "%.0f", 1));
    }
}
