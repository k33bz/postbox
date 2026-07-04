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

    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("postbox.json");
    }

    public static PostboxConfig load() {
        PostboxConfig cfg = null;
        try {
            if (Files.exists(path())) {
                cfg = GSON.fromJson(Files.readString(path()), PostboxConfig.class);
            }
        } catch (Exception e) {
            Postbox.LOGGER.warn("[postbox] could not read config, using defaults", e);
        }
        if (cfg == null) {
            cfg = new PostboxConfig();
        }
        cfg.save(); // write back so new knobs appear in the file
        return cfg;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            Postbox.LOGGER.warn("[postbox] could not save config", e);
        }
    }
}
