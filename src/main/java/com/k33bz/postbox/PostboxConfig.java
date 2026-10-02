package com.k33bz.postbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GSON-backed config, written to {@code config/postbox.json} on first run.
 * Every postage / delivery / courier knob lives here so the economy can be tuned
 * without recompiling. File-only for v1 (no live-set command).
 */
public class PostboxConfig {

    // --- mailboxes ---
    /** How many mailboxes one player may raise (creative bypasses). */
    public int maxBoxesPerPlayer = 1;

    // --- postage (emeralds; emerald blocks count as 9) ---
    /** Flat base cost of any non-hand delivery. */
    public int postageBase = 1;
    /** One emerald per this many message characters (ceil). */
    public int postageCharsPerEmerald = 150;
    /** One emerald per this many blocks of mailbox-to-mailbox distance (ceil). */
    public int postageBlocksPerEmerald = 512;
    /** Flat surcharge when the recipient has NO mailbox (poste restante, queue-only). */
    public int posteRestanteSurcharge = 3;
    /** Distance charged when sender and recipient mailboxes are in different dimensions. */
    public double crossDimensionBlocks = 1024.0;

    // --- delivery time ---
    /** Seconds of travel per 100 blocks of distance. */
    public double secondsPer100Blocks = 1.0;
    /** Floor on any traveling delivery, seconds. */
    public int minDeliverySeconds = 10;
    /** Fixed slow delay for recipients with no mailbox, seconds. */
    public int posteRestanteDeliverySeconds = 600;

    // --- queue ---
    /** Per-player queue cap; sends beyond it are rejected up front (mail is never dropped). */
    public int maxQueued = 100;

    // --- express courier scene ---
    /** Master switch for the wandering-trader delivery theater (never gates real delivery). */
    public boolean courierSceneEnabled = true;

    // --- trader loot ---
    /** Chance a killed wandering trader (vanilla spawns included) drops a Lost Mail bundle. */
    public double traderMailChance = 0.35;

    // --- engine ---
    /** Server-tick interval between delivery sweeps. */
    public int sweepIntervalTicks = 20;
    /** Transit delay for system mail dropped in via the {@code config/postbox_outbox/} spool
     *  ({@link Outbox}) — short by default so a courier still animates but the note lands promptly. */
    public long systemMailDelayMs = 3000L;

    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("postbox.json");
    }

    public static PostboxConfig load() {
        Path file = path();
        if (Files.exists(file)) {
            try {
                PostboxConfig cfg = GSON.fromJson(Files.readString(file), PostboxConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    cfg.save(); // write back so new knobs appear in the file
                    return cfg;
                }
            } catch (Exception e) {
                // Never overwrite a file we could not read: an admin's typo must not silently reset
                // the whole economy to defaults. Keep the broken file, run on defaults in memory.
                Postbox.LOGGER.error("[postbox] could not read {}; running on defaults until it is fixed "
                        + "and the server restarts. The file was left untouched.", file, e);
                PostboxConfig cfg = new PostboxConfig();
                cfg.sanitize();
                return cfg;
            }
        }
        PostboxConfig cfg = new PostboxConfig();
        cfg.save();
        return cfg;
    }

    /**
     * Pull every knob into a range the mail engine can run on. The file is hand-edited and was used
     * as read: {@code postageCharsPerEmerald: 0} made letters free of the length charge, a negative
     * {@code maxQueued} rejected all mail, a chance above 1 or NaN distance broke the math.
     */
    public void sanitize() {
        maxBoxesPerPlayer = Math.clamp(maxBoxesPerPlayer, 0, 1000);
        postageBase = Math.clamp(postageBase, 0, 10_000);
        postageCharsPerEmerald = Math.clamp(postageCharsPerEmerald, 1, 1_000_000);
        postageBlocksPerEmerald = Math.clamp(postageBlocksPerEmerald, 1, 1_000_000);
        posteRestanteSurcharge = Math.clamp(posteRestanteSurcharge, 0, 10_000);
        crossDimensionBlocks = clamp(crossDimensionBlocks, 0, 10_000_000, 1024.0);
        secondsPer100Blocks = clamp(secondsPer100Blocks, 0, 3600, 1.0);
        minDeliverySeconds = Math.clamp(minDeliverySeconds, 0, WEEK_SECONDS);
        posteRestanteDeliverySeconds = Math.clamp(posteRestanteDeliverySeconds, 0, WEEK_SECONDS);
        maxQueued = Math.clamp(maxQueued, 1, 10_000);
        traderMailChance = clamp(traderMailChance, 0, 1, 0.35);
        sweepIntervalTicks = Math.clamp(sweepIntervalTicks, 1, 1200);
        systemMailDelayMs = Math.clamp(systemMailDelayMs, 0L, WEEK_SECONDS * 1000L);
    }

    private static final int WEEK_SECONDS = 7 * 24 * 3600;

    // NaN (only reachable from the file) falls back to the default
    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isNaN(value) ? fallback : Math.clamp(value, min, max);
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            Postbox.LOGGER.warn("[postbox] could not save config", e);
        }
    }
}
