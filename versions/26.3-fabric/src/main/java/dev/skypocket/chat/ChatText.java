package dev.skypocket.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * Turns a {@link Component} back into a legacy {@code §}-coded string.
 *
 * <p>{@code Component.getString()} throws the styling away, and on Hypixel the styling <em>is</em> part
 * of the data: SkyBlock's action bar writes normal health as {@code §c} and overflow/absorption as
 * {@code §6}, mana as {@code §b} and overflow mana as {@code §3} - the same "1234/1234" text means two
 * different numbers depending on the colour, so a parser that only has the plain text cannot tell them
 * apart.
 *
 * <p>Each styled run is emitted as {@code §r} followed by that run's codes. The reset makes the output
 * independent of what came before it (a run that dropped a colour would otherwise inherit the previous
 * one), and it costs nothing for the parsers: their patterns anchor on the colour code that sits
 * immediately before the value, e.g. {@code §b([\d,]+)}.
 *
 * <p>The colour is matched to the nearest of the sixteen legacy colours rather than looked up by RGB.
 * {@code ChatFormatting} no longer exposes a code character or an RGB value directly, and the nearest
 * match is also the more robust choice: it still produces a usable code if the server sends a colour
 * that is only approximately one of the sixteen, which is what any custom resource pack does.
 */
public final class ChatText {
    /** The sixteen legacy colours, indexed the same as {@link #LEGACY_CODE}. */
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final char[] LEGACY_CODE = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private ChatText() {
    }

    /** The text with its colours and formats as legacy codes, or {@code ""} for null. */
    public static String formatted(Component text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        text.visit((style, content) -> {
            appendCodes(out, style);
            out.append(content);
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    private static void appendCodes(StringBuilder out, Style style) {
        out.append('§').append('r');
        TextColor colour = style.getColor();
        if (colour != null) {
            out.append('§').append(legacyCode(colour.getValue()));
        }
        if (style.isBold()) {
            out.append('§').append('l');
        }
        if (style.isItalic()) {
            out.append('§').append('o');
        }
        if (style.isUnderlined()) {
            out.append('§').append('n');
        }
        if (style.isStrikethrough()) {
            out.append('§').append('m');
        }
        if (style.isObfuscated()) {
            out.append('§').append('k');
        }
    }

    /** The legacy code of the colour closest to {@code rgb} (alpha ignored). */
    private static char legacyCode(int rgb) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int distance = distance(rgb & 0xFFFFFF, LEGACY_RGB[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return LEGACY_CODE[best];
    }

    private static int distance(int a, int b) {
        int red = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int green = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int blue = (a & 0xFF) - (b & 0xFF);
        return red * red + green * green + blue * blue;
    }
}
