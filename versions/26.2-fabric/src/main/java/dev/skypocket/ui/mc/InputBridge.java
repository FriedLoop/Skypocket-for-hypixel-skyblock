package dev.skypocket.ui.mc;

import dev.skypocket.ui.input.InputKeys;
import dev.skypocket.ui.input.Modifiers;

/**
 * The one place platform key events are translated into the kernel's event model.
 *
 * <p>This exists because five hosts - the ClickGUI screen, the theme drawer, the HUD editor, its
 * settings card, and the dev test screen - each forwarded keyboard input to an {@code InputRouter}
 * themselves, and no two of them did it the same way:
 *
 * <ul>
 *   <li>two never converted the platform key code to the kernel's canonical one, so Backspace and
 *       Escape did nothing on 26.3, where the key codes differ from 26.2;
 *   <li>two dropped the Ctrl modifier, so Ctrl+A/C/V/X did nothing in their text fields;
 *   <li>four never forwarded {@code preeditUpdated}, so IME composition was broken everywhere except
 *       the one screen that remembered.
 * </ul>
 *
 * <p>None of those are copy-paste slips. They are what happens when a job has several required
 * steps, five implementers, and no shared definition of the job. This class is that definition for
 * the steps that were being forgotten; which host receives an event first, and how its coordinates
 * are converted, still live in each host and still need a real {@code UiHost}.
 *
 * <p>Static and free of state so a host can adopt it without changing its structure. Lives in
 * {@code ui/mc} because it names the platform's event type - this is the boundary package.
 */
public final class InputBridge {
    private InputBridge() {
    }

    /**
     * Converts a platform key event into the kernel's canonical form, carrying the modifiers.
     *
     * <p>Both halves are required. The key code must go through {@link InputKeys#canonical} because
     * the platform reports raw codes that differ between 26.2 and 26.3; the modifiers must be carried
     * because the router hands them to the focused control, and text fields read Ctrl to decide
     * whether a letter means "select all" or "type an A".
     */
    public static dev.skypocket.ui.input.KeyEvent toKernel(
            net.minecraft.client.input.KeyEvent event, boolean down) {
        return new dev.skypocket.ui.input.KeyEvent(
                InputKeys.canonical(event), 0, modifiersOf(event), down);
    }

    /** Modifier bits in the kernel's representation, read from a platform event. */
    public static int modifiersOf(net.minecraft.client.input.KeyEvent event) {
        int bits = 0;
        if (event.hasShiftDown()) {
            bits |= Modifiers.SHIFT;
        }
        if (event.hasControlDown()) {
            bits |= Modifiers.CONTROL;
        }
        if (event.hasAltDown()) {
            bits |= Modifiers.ALT;
        }
        return bits;
    }

    /**
     * Converts modifiers that arrived as booleans rather than on the event.
     *
     * <p>For hosts whose signatures predate the event type: the HUD editor and the theme drawer
     * receive plain ints. Kept here so they do not each build the bitfield differently - one of them
     * used to build it without Ctrl at all.
     */
    public static int modifiersOf(boolean shift, boolean ctrl, boolean alt) {
        int bits = 0;
        if (shift) {
            bits |= Modifiers.SHIFT;
        }
        if (ctrl) {
            bits |= Modifiers.CONTROL;
        }
        if (alt) {
            bits |= Modifiers.ALT;
        }
        return bits;
    }

    /**
     * The text a character event carries, or {@code null} when it should not be inserted.
     *
     * <p>Filtering here rather than at each call site is what stops a host from forwarding a raw
     * control character into a text field.
     */
    public static String textOf(net.minecraft.client.input.CharacterEvent event) {
        if (event == null || !event.isAllowedChatCharacter()) {
            return null;
        }
        String text = event.codepointAsString();
        return text.isEmpty() ? null : text;
    }
}
