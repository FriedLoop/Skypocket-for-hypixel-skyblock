package dev.skypocket.skyblock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Jerry's three-part present display: a head-bearing invisible ArmorStand plus
 * "From: ..." and "To: ..."/"CLICK TO OPEN" name stands.
 */
public final class JerryPresentTracker {
    /** Same name shape as SBA: optional rank prefix, then a 1-16 character player name. */
    private static final Pattern FROM_TO = Pattern.compile(
            "(?:From:|To:) (?:\\[.*?\\] )?(?<name>\\w{1,16})");
    private static final Map<UUID, Boolean> HIDDEN = new HashMap<>();
    private static ClientLevel lastLevel;

    private JerryPresentTracker() {
    }

    public static boolean shouldHide(ArmorStand stand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !stand.isInvisible()) {
            return false;
        }
        if (lastLevel != mc.level) {
            HIDDEN.clear();
            lastLevel = mc.level;
        }
        Boolean cached = HIDDEN.get(stand.getUUID());
        if (cached != null) {
            return cached;
        }
        detect(mc, stand);
        return HIDDEN.getOrDefault(stand.getUUID(), false);
    }

    private static void detect(Minecraft mc, ArmorStand origin) {
        AABB box = new AABB(
                origin.getX() - 0.5, origin.getY() - 2.5, origin.getZ() - 0.5,
                origin.getX() + 0.5, origin.getY() + 2.5, origin.getZ() + 0.5);
        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, box);
        ArmorStand present = null;
        ArmorStand from = null;
        ArmorStand to = null;
        for (ArmorStand stand : stands) {
            if (!stand.isInvisible()) {
                continue;
            }
            Component custom = stand.getCustomName();
            if (custom == null) {
                ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
                if (!head.isEmpty() && head.is(Items.PLAYER_HEAD)) {
                    present = stand;
                }
                continue;
            }
            String name = plain(custom.getString()).trim();
            if (name.startsWith("From: ")) {
                from = stand;
            } else if (name.equals("CLICK TO OPEN") || name.startsWith("To: ")) {
                to = stand;
            }
        }
        if (present == null || from == null || to == null
                || present.getY() > from.getY() || from.getY() > to.getY()) {
            return;
        }

        String fromText = plain(from.getCustomName() == null ? "" : from.getCustomName().getString());
        String toText = plain(to.getCustomName() == null ? "" : to.getCustomName().getString());
        String playerName = mc.player.getName().getString();
        boolean forPlayer = toText.equals("CLICK TO OPEN");
        Matcher fromMatch = FROM_TO.matcher(fromText);
        boolean fromPlayer = fromMatch.matches() && playerName.equals(fromMatch.group("name"));
        boolean hide = !forPlayer && !fromPlayer;

        mark(present, hide);
        mark(from, hide);
        mark(to, hide);
    }

    private static void mark(ArmorStand stand, boolean hide) {
        HIDDEN.put(stand.getUUID(), hide);
    }

    private static String plain(String text) {
        return text.replaceAll("§.", "").trim();
    }
}
