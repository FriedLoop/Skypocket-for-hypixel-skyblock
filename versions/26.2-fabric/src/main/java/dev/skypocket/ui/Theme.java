package dev.skypocket.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named design tokens. It intentionally stores no widget state and no MC types; a theme can be
 * swapped without rebuilding the element tree. Colours are ARGB ints, metrics are design units.
 */
public final class Theme {
    public static final Theme EMPTY = builder().build();

    private final Map<String, Integer> colors;
    private final Map<String, Float> metrics;

    private Theme(Builder builder) {
        colors = Collections.unmodifiableMap(new LinkedHashMap<>(builder.colors));
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metrics));
    }

    public static Builder builder() {
        return new Builder();
    }

    public int color(String name, int fallback) {
        return colors.getOrDefault(name, fallback);
    }

    public float metric(String name, float fallback) {
        return metrics.getOrDefault(name, fallback);
    }

    public Map<String, Integer> colors() {
        return colors;
    }

    public Map<String, Float> metrics() {
        return metrics;
    }

    public static final class Builder {
        private final Map<String, Integer> colors = new LinkedHashMap<>();
        private final Map<String, Float> metrics = new LinkedHashMap<>();

        public Builder color(String name, int argb) {
            colors.put(name, argb);
            return this;
        }

        public Builder metric(String name, float value) {
            metrics.put(name, value);
            return this;
        }

        public Theme build() {
            return new Theme(this);
        }
    }
}
