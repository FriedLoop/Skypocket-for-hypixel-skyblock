package dev.skypocket.feature;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * What the framework needs to know about a feature's HUD element in order to place, edit and migrate
 * it without a hand-written table entry.
 *
 * <p>Today these facts are spread over three places, and adding an element means editing all of
 * them: an enum constant plus a {@code SPECS} entry plus two {@code switch} arms in
 * {@code HudPositionEditorScreen} (22 targets, 17 specs, 68 switch arms), and a position mapping in
 * {@code HudPositionSync}. Declaring them once here lets each of those be derived.
 *
 * <h2>Completeness is enforced by the builder</h2>
 *
 * <p>The editor's {@code computeLines} and {@code label} are {@code switch} <em>expressions</em>,
 * which the compiler refuses to accept when a constant is missing - that is the existing safety net.
 * A derived table loses it, so the equivalent guarantee moves here: {@link Builder#build()} rejects
 * a spec that has not supplied everything the editor will ask for. A feature that forgets one fails
 * at registration (startup), not when the player opens the editor.
 *
 * <h2>Status</h2>
 *
 * <p>Phase 0: defined but not yet consumed. {@code HudPositionEditorScreen} still owns its own
 * tables; features migrate onto this in Phase 2.
 */
public final class HudSpec {

    private final String label;
    private final Supplier<List<String>> lines;
    private final ToIntFunction<Object> x;
    private final ObjIntConsumer<Object> setX;
    private final ToIntFunction<Object> y;
    private final ObjIntConsumer<Object> setY;
    private final ToDoubleFunction<Object> scale;
    private final ObjDoubleConsumer<Object> setScale;
    private final int defaultX;
    private final int defaultY;
    private final Visibility visibility;
    private final String moduleId;
    private final String targetName;

    private HudSpec(Builder builder) {
        this.label = builder.label;
        this.lines = builder.lines;
        this.x = builder.x;
        this.setX = builder.setX;
        this.y = builder.y;
        this.setY = builder.setY;
        this.scale = builder.scale;
        this.setScale = builder.setScale;
        this.defaultX = builder.defaultX;
        this.defaultY = builder.defaultY;
        this.visibility = builder.visibility;
        this.moduleId = builder.moduleId;
        this.targetName = builder.targetName;
    }

    /**
     * Whether the editor shows a handle for this element right now.
     *
     * <p>Only the editor consults this. Runtime conditions (no data yet, not on SkyBlock) must NOT
     * take part: the player has to be able to position an element before it has anything to show.
     */
    @FunctionalInterface
    public interface Visibility {
        boolean isVisible(Object config);
    }

    /** The name shown in the editor's selected-target readout and settings card title. */
    public String label() {
        return label;
    }

    /**
     * The id of the feature that owns this element, which the editor's settings card opens.
     *
     * <p>Defaults to the feature's own id, because the common case is one element per feature.
     * Elements that belong to a shared feature (the six stat bars all belong to
     * {@code stats_display}) override it.
     */
    public String moduleId() {
        return moduleId;
    }

    /**
     * The {@code HudPositionEditorScreen.Target} constant this element maps to.
     *
     * <p>Must match the enum constant name exactly. It is a string rather than the enum because the
     * enum is private to the editor: exposing it would make every feature depend on screen
     * internals, and the name is already the stable key the editor uses elsewhere.
     */
    public String targetName() {
        return targetName;
    }

    /** The element's current text lines, used to measure its bounding box while dragging. */
    public List<String> lines() {
        return lines.get();
    }

    public int x(Object config) {
        return x.applyAsInt(config);
    }

    public void setX(Object config, int value) {
        setX.accept(config, value);
    }

    public int y(Object config) {
        return y.applyAsInt(config);
    }

    public void setY(Object config, int value) {
        setY.accept(config, value);
    }

    public double scale(Object config) {
        return scale.applyAsDouble(config);
    }

    public void setScale(Object config, double value) {
        setScale.accept(config, value);
    }

    /** Where {@code HudPositionSync} puts the element when it is found off-screen. */
    public int defaultX() {
        return defaultX;
    }

    public int defaultY() {
        return defaultY;
    }

    public boolean visible(Object config) {
        return visibility == null || visibility.isVisible(config);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link HudSpec}, rejecting anything the editor would later fail to answer.
     *
     * <p>Deliberately strict: a missing accessor here would otherwise become a null dereference in
     * the editor, which is exactly the class of failure the switch expressions used to prevent.
     */
    public static final class Builder {
        private String label;
        private Supplier<List<String>> lines;
        private ToIntFunction<Object> x;
        private ObjIntConsumer<Object> setX;
        private ToIntFunction<Object> y;
        private ObjIntConsumer<Object> setY;
        private ToDoubleFunction<Object> scale;
        private ObjDoubleConsumer<Object> setScale;
        private int defaultX;
        private int defaultY;
        private Visibility visibility;
        private String moduleId;
        private String targetName;

        private Builder() {
        }

        public Builder label(String value) {
            this.label = value;
            return this;
        }

        /**
         * Supplies the element's current text lines, used to measure its box while dragging.
         *
         * <p>Called from the editor's mouse path as well as its draw path, so it must be cheap and
         * must not have side effects. Elements that cache their measurement per frame keep doing so
         * inside this supplier.
         */
        public Builder lines(Supplier<List<String>> value) {
            this.lines = value;
            return this;
        }

        public Builder x(ToIntFunction<Object> getter, ObjIntConsumer<Object> setter) {
            this.x = getter;
            this.setX = setter;
            return this;
        }

        public Builder y(ToIntFunction<Object> getter, ObjIntConsumer<Object> setter) {
            this.y = getter;
            this.setY = setter;
            return this;
        }

        /**
         * Position scale.
         *
         * <p>{@code double} because that is what the existing {@code Spec} record carries: the editor
         * formats it with two decimals and the slider steps in 0.05, so an {@code int} would lose the
         * fraction. Implementations store a {@code float} and cast, exactly as they do today.
         */
        public Builder scale(ToDoubleFunction<Object> getter, ObjDoubleConsumer<Object> setter) {
            this.scale = getter;
            this.setScale = setter;
            return this;
        }

        /** Fallback position used when the stored one ends up entirely off-screen. */
        public Builder defaults(int x, int y) {
            this.defaultX = x;
            this.defaultY = y;
            return this;
        }

        /**
         * The owning feature's id, when it differs from the element's own name.
         *
         * <p>Defaults to {@link #label} being unused for this: a spec with no explicit module id
         * reports the feature id the caller passes to {@code build(moduleId)}. Kept as a builder
         * option so the shared-feature case stays declarative.
         */
        public Builder moduleId(String value) {
            this.moduleId = value;
            return this;
        }

        /**
         * The editor target this element maps to; must equal a {@code Target} constant name.
         *
         * <p>Not defaulted to the label: labels are localized Chinese text, target names are
         * identifiers, and silently deriving one from the other would turn a typo into a
         * wrong-element binding rather than a visible failure.
         */
        public Builder target(String value) {
            this.targetName = value;
            return this;
        }

        /** Editor visibility; omit to always show a handle. */
        public Builder visibleWhen(Visibility value) {
            this.visibility = value;
            return this;
        }

        public HudSpec build() {
            // Each check names the missing part, because a registration failure at startup is the
            // only chance to say which feature is incomplete.
            Objects.requireNonNull(label, "HudSpec.label is required");
            Objects.requireNonNull(lines, "HudSpec.lines is required");
            Objects.requireNonNull(x, "HudSpec.x is required");
            Objects.requireNonNull(setX, "HudSpec.setX is required");
            Objects.requireNonNull(y, "HudSpec.y is required");
            Objects.requireNonNull(setY, "HudSpec.setY is required");
            Objects.requireNonNull(scale, "HudSpec.scale is required");
            Objects.requireNonNull(setScale, "HudSpec.setScale is required");
            Objects.requireNonNull(moduleId, "HudSpec.moduleId is required");
            Objects.requireNonNull(targetName, "HudSpec.target is required");
            return new HudSpec(this);
        }
    }
}
