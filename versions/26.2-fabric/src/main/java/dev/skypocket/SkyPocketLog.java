package dev.skypocket;

import dev.skypocket.config.ConfigManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's logging entry point: a level switch plus a searchable prefix.
 *
 * <p><b>Finding our lines.</b> Minecraft's log pattern is {@code [thread/LEVEL] message} - it does not
 * print the logger name, so every mod's output looks like {@code [Render thread/INFO]} and cannot be
 * picked out. Changing that pattern would rewrite the format of every line in the log, including
 * vanilla's, which is not ours to do. Instead each logger prints its own prefix:
 *
 * <pre>[22:26:46] [Render thread/INFO]: [SkyPocket/panelCache] panel cache (5s): ...</pre>
 *
 * <p>Usage is one declaration per class, after which the call sites read exactly as before:
 *
 * <pre>private static final SkyPocketLog LOGGER = SkyPocketLog.of("panelCache");
 * LOGGER.info("panel cache (5s): {} frames", frames);</pre>
 *
 * <p><b>The level switch.</b> Filtering happens at the logger level, not at the call sites:
 * {@code Configurator} sets the level of the {@value #ROOT} logger and every logger is named
 * {@code skypocket.<area>}, so they all inherit it. That is what makes the switch apply to log
 * statements written later as well. With the switch on, DEBUG and above (so INFO too) are printed; with
 * it off the level drops to WARN, which silences information but keeps warnings and errors - when a
 * player reports a problem the log has to contain the failures even though nobody turned a debug option
 * on.
 *
 * <p>The naming rule is a hard requirement: a logger called {@code skypocket:panel} (colon) or
 * {@code SkyPocketPanel} would sit outside the hierarchy and ignore the switch.
 */
public final class SkyPocketLog {
    /** Root of the mod's logger hierarchy - see the class comment. */
    public static final String ROOT = "skypocket";

    /** Used only by {@link #apply}, which must be able to report a failure whatever the level is. */
    private static final Logger BOOT = LoggerFactory.getLogger(ROOT);

    private final Logger delegate;
    private final String prefix;

    private SkyPocketLog(String area) {
        boolean named = area != null && !area.isEmpty();
        this.delegate = LoggerFactory.getLogger(named ? ROOT + "." + area : ROOT);
        this.prefix = named ? "[SkyPocket/" + area + "] " : "[SkyPocket] ";
    }

    /**
     * A logger for one area of the mod; its messages are prefixed with {@code [SkyPocket/<area>]} so they
     * can be found at a glance in a shared log.
     */
    public static SkyPocketLog of(String area) {
        return new SkyPocketLog(area);
    }

    /** Applies the level implied by the current config setting. */
    public static void apply() {
        apply(ConfigManager.get().debugLog);
    }

    /** Applies the level: {@code debug} true means DEBUG and above, false means WARN and above. */
    public static void apply(boolean debug) {
        try {
            Configurator.setLevel(ROOT, debug ? Level.DEBUG : Level.WARN);
        } catch (Throwable t) {
            // Logging must never take the game down; report it through the level that is always allowed.
            BOOT.warn("could not apply the log level (debug={})", debug, t);
        }
    }

    public void info(String format, Object... args) {
        delegate.info(prefix + format, args);
    }

    public void info(String message, Throwable t) {
        delegate.info(prefix + message, t);
    }

    public void warn(String format, Object... args) {
        delegate.warn(prefix + format, args);
    }

    public void warn(String message, Throwable t) {
        delegate.warn(prefix + message, t);
    }

    public void error(String format, Object... args) {
        delegate.error(prefix + format, args);
    }

    public void error(String message, Throwable t) {
        delegate.error(prefix + message, t);
    }

    public void debug(String format, Object... args) {
        delegate.debug(prefix + format, args);
    }

    public void debug(String message, Throwable t) {
        delegate.debug(prefix + message, t);
    }
}
