package dev.skypocket.sound;

import dev.skypocket.i18n.Localization;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Custom alert sounds: MP3 files the user drops into
 * {@code <gameDir>/skypocket/sound/}, decoded with JLayer and played on a
 * background thread (never the render thread). Vanilla sound-event playback with
 * volume/pitch lives in {@link SoundManager}.
 */
public final class MentionSoundManager {
    private static final dev.skypocket.SkyPocketLog LOGGER =
            dev.skypocket.SkyPocketLog.of("sound");

    /** Single background thread for sound playback + file dialogs. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "skypocket-sound");
        t.setDaemon(true);
        return t;
    });

    private MentionSoundManager() {
    }

    /** Creates the sound folder (once, at mod init). */
    public static void init() {
        try {
            Files.createDirectories(soundDir());
        } catch (IOException e) {
            LOGGER.warn("Failed to create sound folder", e);
        }
    }

    /** {@code <gameDir>/skypocket/sound/} — the user-facing custom-sound folder. */
    public static Path soundDir() {
        return FabricLoader.getInstance().getGameDir().resolve("skypocket").resolve("sound");
    }

    /** The folder's MP3 file names, sorted. */
    public static List<String> listCustomSounds() {
        List<String> sounds = new ArrayList<>();
        File dir = soundDir().toFile();
        File[] files = dir.listFiles((d, n) -> n != null && n.toLowerCase().endsWith(".mp3"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File f : files) {
                sounds.add(f.getName());
            }
        }
        return sounds;
    }

    /** Opens a native file dialog and copies the chosen MP3 into the sound folder. */
    public static void pickAndImport() {
        EXEC.submit(() -> {
            try {
                File[] selected = new File[1];
                EventQueue.invokeAndWait(() -> {
                    FileDialog dialog = new FileDialog((Frame) null, Localization.tr("选择音频文件"), FileDialog.LOAD);
                    dialog.setFilenameFilter((d, name) -> name != null && name.toLowerCase().endsWith(".mp3"));
                    dialog.setVisible(true);
                    String dir = dialog.getDirectory();
                    String file = dialog.getFile();
                    dialog.dispose();
                    selected[0] = (dir != null && file != null) ? new File(dir, file) : null;
                });
                if (selected[0] != null && selected[0].isFile()) {
                    Files.copy(selected[0].toPath(), soundDir().resolve(selected[0].getName()),
                            StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Imported mention sound {}", selected[0].getName());
                }
            } catch (Exception e) {
                LOGGER.warn("Sound import failed", e);
            }
        });
    }

    /** Plays a custom MP3 file (by file name) asynchronously. */
    public static void playCustom(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        Path file = soundDir().resolve(fileName);
        EXEC.submit(() -> playMp3(file));
    }

    /** Plays an MP3 file via JLayer's high-level player (decodes + streams to the line). */
    private static void playMp3(Path file) {
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            new javazoom.jl.player.Player(in).play();
        } catch (Exception e) {
            LOGGER.warn("Failed to play sound {}", file, e);
        }
    }
}
