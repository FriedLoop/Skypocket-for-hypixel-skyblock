package dev.skypocket.crosshair;

import dev.skypocket.config.ConfigManager;
import dev.skypocket.config.ModConfig;
import dev.skypocket.gui.setting.Setting;
import dev.skypocket.i18n.Localization;
import dev.skypocket.ui.Box;
import dev.skypocket.ui.Dimension;
import dev.skypocket.ui.Style;
import dev.skypocket.ui.widget.Button;
import dev.skypocket.ui.widget.Dropdown;
import dev.skypocket.ui.widget.TextField;
import dev.skypocket.ui.widget.WidgetPalette;

import java.util.List;

/**
 * The preset panel that sits to the right of the crosshair editor: name a preset, save the current
 * crosshair, load one back, or delete the selected user preset.
 *
 * <p>State lives here rather than in the {@code Setting} list for two reasons: the page builds its
 * widgets once and a name field is a live string, and the load dropdown owns a popup that has to be
 * registered as a layer - see {@link Setting.V2CustomSetting.Host}.
 *
 * <p>Every action reports through {@link #status}, which the panel draws under its buttons. A silent
 * failure would be indistinguishable from a click that missed.
 */
public final class CrosshairPresetPanel {
    private final ModConfig cfg;
    private final WidgetPalette palette;

    /** What the name field currently holds, kept across rebuilds. */
    private String draftName = "";
    /** Name of the preset most recently loaded or saved, for the delete button. */
    private String selected;

    /** One-line feedback under the buttons; empty when there is nothing to say. */
    private String status = "";

    private TextField nameField;
    private Dropdown loadDropdown;

    /**
     * Set by the host so the panel can ask for a rebuild without importing the page.
     *
     * <p>Deferred by the host through {@code Minecraft.schedule}, because rebuilding replaces the
     * widget whose handler is still running.
     */
    private Runnable rebuildHook = () -> {
    };

    public CrosshairPresetPanel(ModConfig cfg, WidgetPalette palette) {
        this.cfg = cfg;
        this.palette = palette;
        // Start on a real name so Save does something sensible without typing first.
        this.selected = BuiltinCrosshairs.names().get(0);
        this.draftName = "";
    }

    /** Wires the rebuild hook; called once per build. */
    public void rebuildVia(Runnable hook) {
        this.rebuildHook = hook == null ? () -> {
        } : hook;
    }

    /**
     * Builds the panel into {@code parent}.
     *
     * <p>Called from the settings page's custom-row layout, so anything that floats must be handed to
     * {@code host}.
     */
    public void build(Box parent, Setting.V2CustomSetting.Host host) {
        rebuildVia(host::rebuild);

        // Row 1: the name to save under.
        nameField = new TextField(Localization.tr("预设名"), Style.builder()
                .width(Dimension.percent(100f)).height(32f).build(), palette);
        nameField.text(draftName);
        nameField.onChange(text -> {
            // Remembered here, not read back from the widget: the page rebuilds its rows, and a
            // half-typed name would otherwise be lost on every unrelated change.
            draftName = text;
        });
        parent.add(nameField);

        // Row 2: save.
        parent.add(new Button(Localization.tr("保存预设"), this::save, Style.builder()
                .width(Dimension.percent(100f)).height(32f).build(), palette));

        // Row 3: load. The dropdown's list is its own floating layer, hence the host.
        List<String> names = CrosshairStore.names();
        loadDropdown = new Dropdown(Localization.tr("载入预设"), names,
                () -> Math.max(0, names.indexOf(selected)),
                index -> {
                    if (index >= 0 && index < names.size()) {
                        load(names.get(index));
                    }
                }, Style.builder().width(Dimension.percent(100f)).height(32f).build(), palette);
        parent.add(loadDropdown);
        // Handed to the page, which positions it during layout() and keeps it on the layer stack only
        // while it is open. Registering the popup here instead would place it against bounds that the
        // layout pass has not computed yet.
        host.registerDropdown(loadDropdown);

        // Row 4: delete. Built-ins cannot be deleted, so the label says which state it is in.
        boolean deletable = selected != null && !BuiltinCrosshairs.isBuiltin(selected);
        parent.add(new Button(() -> Localization.tr(deletable ? "删除预设" : "内置预设不可删除"),
                this::delete, Style.builder()
                .width(Dimension.percent(100f)).height(32f).build(), palette));

        // Row 5: feedback. A silent failure would look like a click that missed.
        if (!status.isEmpty()) {
            parent.add(new dev.skypocket.ui.Text(status, Style.builder()
                    .width(Dimension.percent(100f)).height(16f).build(), 0.85f,
                    dev.skypocket.ui.TextWeight.REGULAR));
        }
    }

    // ---- actions ------------------------------------------------------------------------------

    /**
     * Saves the live crosshair under the typed name.
     *
     * <p>The name falls back to the selected preset when the field is empty, so "overwrite what I just
     * loaded" needs no typing. That fallback is skipped for built-ins - their identifiers are Chinese
     * display literals, so falling back to one would either be refused by the guard below or, worse,
     * create a file named after a translated string.
     */
    private void save() {
        String name = draftName == null ? "" : draftName.trim();
        if (name.isEmpty() && selected != null && !BuiltinCrosshairs.isBuiltin(selected)) {
            name = selected;
        }
        if (name.isEmpty()) {
            status = Localization.tr("请输入预设名");
            rebuild();
            return;
        }
        if (BuiltinCrosshairs.isBuiltin(name)) {
            status = Localization.tr("内置预设不可覆盖");
            rebuild();
            return;
        }
        CrosshairPreset preset = CrosshairPreset.fromConfig(name, cfg);
        if (CrosshairStore.save(preset)) {
            selected = name;
            draftName = name;
            status = Localization.tr("已保存");
        } else {
            status = Localization.tr("保存失败，见日志");
        }
        ConfigManager.touch();
        rebuild();
    }

    /**
     * Loads a preset into the live config.
     *
     * <p>No confirmation: this is a preview surface, and the player can re-select the previous preset
     * to get back. Deleting is the irreversible one, and that one is guarded.
     */
    private void load(String name) {
        CrosshairPreset preset = CrosshairStore.load(name);
        if (preset == null) {
            status = Localization.tr("读取失败，见日志");
            rebuild();
            return;
        }
        if (!preset.applyTo(cfg)) {
            // Length mismatch means the file was written for a different grid size.
            status = Localization.tr("预设与当前网格不兼容");
            rebuild();
            return;
        }
        selected = name;
        // The name field is only filled for user presets. A built-in's identifier is its display
        // name, and that one is a Chinese literal translated at draw time by the dropdown; putting it
        // in the field would show the untranslated literal instead, because the field deliberately
        // draws raw text (it must not translate what the player types). Leaving the field alone also
        // matches what the button does: built-ins cannot be saved over, so there is nothing useful to
        // pre-fill.
        if (!BuiltinCrosshairs.isBuiltin(name)) {
            draftName = name;
        }
        status = Localization.tr("已载入");
        ConfigManager.touch();
        rebuild();
    }

    /** Deletes the selected preset, refusing built-ins. */
    private void delete() {
        if (selected == null || selected.isEmpty()) {
            status = Localization.tr("请先选择预设");
            rebuild();
            return;
        }
        if (BuiltinCrosshairs.isBuiltin(selected)) {
            status = Localization.tr("内置预设不可删除");
            rebuild();
            return;
        }
        if (CrosshairStore.delete(selected)) {
            status = Localization.tr("已删除");
            selected = BuiltinCrosshairs.names().get(0);
            draftName = "";
        } else {
            status = Localization.tr("删除失败，见日志");
        }
        ConfigManager.touch();
        rebuild();
    }

    /**
     * Rebuilds the settings page.
     *
     * <p>Deferred through {@code Minecraft.schedule} by the host so the rebuild does not replace the
     * widget being clicked while it is still handling the event.
     */
    private void rebuild() {
        rebuildHook.run();
    }
}
