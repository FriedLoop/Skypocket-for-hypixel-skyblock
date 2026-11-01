package dev.skypocket.gui.widget;

/**
 * The parts of a colour RGB cannot carry, remembered while the picker is open.
 *
 * <p>Hue is undefined for greys (saturation 0) and saturation is undefined for black (value 0), so a
 * marker derived from the colour alone collapses whenever the user drags into those regions. The owner
 * creates one of these and hands it to the picker, which is why the value lives outside the picker: a
 * picker is recreated when its row scrolls out of view.
 *
 * <p>This used to be a nested class of the widget-based picker; it outlived that widget because the
 * behaviour it protects (keeping the hue while the colour passes through grey) is not about drawing.
 */
public final class PickerMemory {
    private float hue;
    private float saturation = 1f;
    private float value = 1f;
    private boolean seeded;

    /** Whether the memory has been filled from a colour yet. */
    public boolean seeded() {
        return seeded;
    }

    /** Adopts all three components at once (a preset, or a square drag). */
    public void set(float hue, float saturation, float value) {
        this.hue = hue;
        this.saturation = saturation;
        this.value = value;
        this.seeded = true;
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float value() {
        return value;
    }
}
