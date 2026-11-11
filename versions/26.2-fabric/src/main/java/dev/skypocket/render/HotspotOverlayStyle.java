package dev.skypocket.render;

import java.util.List;

/** How a fishing hotspot is drawn. */
public enum HotspotOverlayStyle {
    CYLINDER("无底圆柱（侧面）"),
    FILLED_CIRCLE("实心圆"),
    HOLLOW_CIRCLE("空心圆");

    private final String label;

    HotspotOverlayStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isCylinder() {
        return this == CYLINDER;
    }

    public static List<String> labels() {
        return List.of(CYLINDER.label, FILLED_CIRCLE.label, HOLLOW_CIRCLE.label);
    }

    public static HotspotOverlayStyle byLabel(String label) {
        for (HotspotOverlayStyle style : values()) {
            if (style.label.equals(label)) {
                return style;
            }
        }
        return CYLINDER;
    }
}
