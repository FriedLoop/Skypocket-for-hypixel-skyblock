package dev.skypocket.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified sound playback for alert features. A sound id is either a vanilla
 * {@code minecraft:...} sound-event key (played through the game's sound manager,
 * with configurable volume/pitch) or a custom MP3 file name in
 * {@code skypocket/sound/} (played by {@link MentionSoundManager}).
 */
public final class SoundManager {
    /** Default alert sound for new configs. */
    public static final String DEFAULT_SOUND = "minecraft:entity.experience_orb.pickup";

    private SoundManager() {
    }

    /** Whether {@code id} refers to a vanilla sound event. */
    public static boolean isVanilla(String id) {
        return id != null && id.startsWith("minecraft:");
    }

    /** All vanilla sound ids, sorted alphabetically. */
    public static List<String> listVanillaSounds() {
        List<String> out = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            out.add(id.toString());
        }
        Collections.sort(out);
        return out;
    }

    /** Every selectable id: vanilla sounds first, then the user's MP3 files. */
    public static List<String> listAllSounds() {
        List<String> out = new ArrayList<>(listVanillaSounds());
        out.addAll(MentionSoundManager.listCustomSounds());
        return out;
    }

    /**
     * Plays a sound id with the given volume [0..2] and pitch [0.5..2]. Must be
     * called on the render thread (alert features trigger there anyway).
     *
     * <p>Above 1.0 the vanilla gain is amplified rather than clamped - that is what the
     * "volume up to 200%" setting does - so loud sources can clip. Custom MP3s ignore both
     * volume and pitch: they play on the background thread at their native values.
     */
    public static void play(String id, float volume, float pitch) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (isVanilla(id)) {
            playVanilla(id, volume, pitch);
        } else {
            MentionSoundManager.playCustom(id);
        }
    }

    private static void playVanilla(String id, float volume, float pitch) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(id))
                .map(holder -> holder.value())
                .orElse(null);
        if (event == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
