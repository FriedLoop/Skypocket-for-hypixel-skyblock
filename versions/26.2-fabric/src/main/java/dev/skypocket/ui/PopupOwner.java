package dev.skypocket.ui;

/**
 * A control whose popup is a separate floating layer.
 *
 * <p>Dropdown option lists and colour pickers are not children of their widget: they are Cards that
 * have to be positioned against the widget that opened them, registered for input while open, and
 * painted after the page. Every host that used one had to do those steps itself, and
 * {@code SettingsPage} ended up with three near-identical loops - one per kind of popup it grew.
 *
 * <p>The three members here are exactly what a host needs, and they were already the shape of both
 * {@code Dropdown} and {@code ColorField}. Naming them lets {@link UiPage} drive any popup the same
 * way, so a new floating widget costs a class that implements this and nothing in the page.
 */
public interface PopupOwner {
    /** The floating element, which must already be invisible until the owner opens. */
    UiElement popup();

    /** Whether the popup should currently be on screen and receiving input. */
    boolean isOpen();

    /**
     * Positions and lays out the popup.
     *
     * <p>The owner does this itself because the anchor differs per widget: a dropdown opens below or
     * above its field and flips at the viewport edge, while a colour picker is centred under its row.
     * Both also snap to design pixels so a live popup does not sit a pixel off the cached row it
     * belongs to. A generic host cannot reproduce that from the outside.
     */
    void layoutPopup(TextMeasurer measurer, float viewWidth, float viewHeight);

    /**
     * Refreshes the popup's contents from the value before it is painted.
     *
     * <p>Only the colour picker needs this - its hex and RGBA fields mirror a colour that can also
     * change from the square or the bars. Dropdowns keep the default.
     */
    default void syncBeforePaint() {
    }
}
