package dev.skypocket.gui.widget;

import dev.skypocket.render.ClickGuiFonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The expanded option list of a dropdown setting: renders the options matching the
 * live search filter (polled from the search field each frame, so typing never
 * needs a rebuild) and selects the clicked row. The visible window scrolls via
 * {@link #listScroll}, which {@code OneClickScreen} forwards from its wheel handler.
 */
public class DropdownListWidget extends BaseWidget {
    public static final int ITEM_H = 16;
    private static final int BG = 0xFF1E1E1E;
    private static final int ROW_HOVER = ClickGuiStyle.listRowHover();
    private static final int ROW_CURRENT = ClickGuiStyle.listRowCurrent();

    private final Supplier<List<String>> optionsSupplier;
    private final Supplier<String> currentSupplier;
    private final Supplier<String> filterSupplier;
    private final Consumer<String> onSelect;
    private int scrollIndex;
    private List<String> filtered = List.of();
    private String lastFilter = null;

    public DropdownListWidget(int x, int y, int width, int height,
                              Supplier<List<String>> optionsSupplier,
                              Supplier<String> currentSupplier,
                              Supplier<String> filterSupplier,
                              Consumer<String> onSelect) {
        super(x, y, width, height);
        this.optionsSupplier = optionsSupplier;
        this.currentSupplier = currentSupplier;
        this.filterSupplier = filterSupplier;
        this.onSelect = onSelect;
    }

    /** Whether the point is inside the list (public for the screen's wheel dispatch). */
    public boolean containsPoint(double x, double y) {
        return inBounds(x, y);
    }

    /** Wheel handler; returns true when the cursor was over the list and it scrolled. */
    public boolean listScroll(double mouseX, double mouseY, double amount) {
        if (!inBounds(mouseX, mouseY)) {
            return false;
        }
        scrollIndex += amount > 0 ? -1 : 1;
        clampScroll();
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return;
        }
        int row = (int) ((event.y() - getY()) / ITEM_H);
        int index = scrollIndex + row;
        if (index >= 0 && index < filtered.size()) {
            onSelect.accept(filtered.get(index));
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        refilter();
        ClickGuiStyle.surface(graphics, getX(), getY(), width, height, BG, 0);

        int visible = height / ITEM_H;
        clampScroll();
        String current = currentSupplier.get();
        for (int row = 0; row < visible; row++) {
            int index = scrollIndex + row;
            if (index >= filtered.size()) {
                break;
            }
            String option = filtered.get(index);
            int ry = getY() + row * ITEM_H;
            if (option.equals(current)) {
                graphics.fill(getX() + 1, ry, getX() + width - 1, ry + ITEM_H, ROW_CURRENT);
            } else if (inBounds(mouseX, mouseY)
                    && mouseY >= ry && mouseY < ry + ITEM_H) {
                graphics.fill(getX() + 1, ry, getX() + width - 1, ry + ITEM_H, ROW_HOVER);
            }
            ClickGuiFonts.drawText(graphics, DropdownWidget.fit(option, width - 12),
                    getX() + 6, ry + (ITEM_H - ClickGuiFonts.lineHeight()) / 2, 0xFFE0E0E0, true);
        }
    }

    private void refilter() {
        String filter = filterSupplier.get();
        String key = filter == null ? "" : filter.trim().toLowerCase();
        if (key.equals(lastFilter)) {
            return;
        }
        lastFilter = key;
        List<String> all = optionsSupplier.get();
        if (key.isEmpty()) {
            filtered = all;
        } else {
            List<String> out = new ArrayList<>();
            for (String option : all) {
                if (option.toLowerCase().contains(key)) {
                    out.add(option);
                }
            }
            filtered = out;
        }
        scrollIndex = 0;
    }

    private void clampScroll() {
        int visible = Math.max(1, height / ITEM_H);
        int max = Math.max(0, filtered.size() - visible);
        if (scrollIndex > max) {
            scrollIndex = max;
        }
        if (scrollIndex < 0) {
            scrollIndex = 0;
        }
    }
}
