package dev.skypocket.skyblock;

import dev.skypocket.chat.ChatText;
import dev.skypocket.net.SkyBlockDetector;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the ActionBar drill-fuel section and gates it to the currently held drill. */
public final class DrillFuelTracker {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("drillFuel");
    private static long lastProbeLogMs;
    private static final Pattern FUEL = Pattern.compile(
            "(?<fuel>[0-9][0-9,.]*[kmbKMB]?)\\s*/\\s*(?<max>[0-9][0-9,.]*[kmbKMB]?)\\s+Drill(?:\\s+Fuel)?",
            Pattern.CASE_INSENSITIVE);
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0",
            DecimalFormatSymbols.getInstance(Locale.US));

    private static int fuel;
    private static int maxFuel;
    private static boolean hasData;
    private static long lastActionBarMs;

    private DrillFuelTracker() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register(DrillFuelTracker::onMessage);
    }

    private static void onMessage(Component message, boolean overlay) {
        if (!overlay) {
            return;
        }
        if (!SkyBlockDetector.isActive()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SkyBlockItem.isDrill(mc.player.getMainHandItem())) {
            reset();
            return;
        }
        // Replace colour codes with a separator before flattening. Without this, the digit at the
        // end of the previous action-bar section can be glued to the fuel number (2 + 26,921).
        String text = ChatText.formatted(message).replaceAll("§[0-9a-fk-or]", " ");
        long now = System.currentTimeMillis();
        if (text.toLowerCase(Locale.ROOT).contains("drill") && now - lastProbeLogMs > 1000) {
            lastProbeLogMs = now;
            LOGGER.info("actionbar drill: active={} holding={} text='{}'",
                    SkyBlockDetector.isActive(), true, text);
        }
        accept(text);
    }

    static void accept(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = FUEL.matcher(text);
        if (!matcher.find()) {
            return;
        }
        Integer nextFuel = number(matcher.group("fuel"));
        Integer nextMax = number(matcher.group("max"));
        if (nextFuel == null || nextMax == null || nextMax <= 0) {
            return;
        }
        fuel = Math.max(0, nextFuel);
        maxFuel = Math.max(1, nextMax);
        hasData = true;
        lastActionBarMs = System.currentTimeMillis();
    }

    public static void reset() {
        fuel = 0;
        maxFuel = 0;
        hasData = false;
    }

    public static boolean hasData() {
        return hasData;
    }

    /** The HUD is only meaningful while the main-hand item still is a drill. */
    public static boolean holdingDrill() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        ItemStack stack = mc.player.getMainHandItem();
        return SkyBlockItem.isDrill(stack);
    }

    /**
     * ActionBar is the immediate source while it is publishing fuel. Item NBT can lag one or more
     * ticks behind a block break, so it is only a fallback after the ActionBar has gone quiet.
     */
    public static void refreshFromItem() {
        if (hasData && System.currentTimeMillis() - lastActionBarMs < 1000) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (!SkyBlockItem.isDrill(stack)) {
            return;
        }
        int current = SkyBlockItem.intValue(stack, "drill_fuel", -1);
        if (current >= 0) {
            fuel = current;
            if (maxFuel > 0) {
                hasData = true;
            }
        }
    }

    public static boolean visible() {
        refreshFromItem();
        return hasData && holdingDrill();
    }

    public static int fuel() {
        return fuel;
    }

    public static int maxFuel() {
        return maxFuel;
    }

    public static float fraction() {
        return maxFuel <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) fuel / maxFuel));
    }

    /** e.g. {@code 1,798/3,000}. */
    public static String line() {
        return hasData ? NUMBER.format(fuel) + "/" + NUMBER.format(maxFuel) : null;
    }

    public static String previewLine() {
        return "1,798/3,000";
    }

    private static Integer number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace(",", "");
        double multiplier = 1.0;
        char suffix = Character.toLowerCase(value.charAt(value.length() - 1));
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            multiplier = suffix == 'k' ? 1_000.0 : suffix == 'm' ? 1_000_000.0 : 1_000_000_000.0;
            value = value.substring(0, value.length() - 1);
        }
        try {
            return (int) Math.round(Double.parseDouble(value) * multiplier);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
