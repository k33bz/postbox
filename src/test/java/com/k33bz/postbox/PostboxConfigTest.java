package com.k33bz.postbox;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * config/postbox.json is hand-edited and every knob feeds the economy or the server tick directly,
 * so sanitize() must leave values the engine can run on, whatever the file says.
 */
class PostboxConfigTest {

    private static PostboxConfig parse(String json) {
        PostboxConfig cfg = new Gson().fromJson(json, PostboxConfig.class);
        cfg.sanitize();
        return cfg;
    }

    @Test
    void defaultsSurviveSanitizeUnchanged() {
        PostboxConfig cfg = new PostboxConfig();
        cfg.sanitize();
        assertEquals(1, cfg.maxBoxesPerPlayer);
        assertEquals(150, cfg.postageCharsPerEmerald);
        assertEquals(512, cfg.postageBlocksPerEmerald);
        assertEquals(100, cfg.maxQueued);
        assertEquals(0.35, cfg.traderMailChance);
        assertEquals(20, cfg.sweepIntervalTicks);
        assertEquals(3000L, cfg.systemMailDelayMs);
    }

    @Test
    void zeroDivisorsCannotMakeMailFree() {
        PostboxConfig cfg = parse("{\"postageCharsPerEmerald\": 0, \"postageBlocksPerEmerald\": -5}");
        assertEquals(1, cfg.postageCharsPerEmerald);
        assertEquals(1, cfg.postageBlocksPerEmerald);
        // 150 chars at 1 per emerald costs 150 + base, not just the base
        assertEquals(1 + 150, Postage.letterCost(false, false, 150, 0, cfg.postageBase,
                cfg.postageCharsPerEmerald, cfg.postageBlocksPerEmerald, 0));
    }

    @Test
    void negativeQueueCapNoLongerRejectsAllMail() {
        assertEquals(1, parse("{\"maxQueued\": -5}").maxQueued);
    }

    @Test
    void chanceAndTimesAreClamped() {
        PostboxConfig cfg = parse("{\"traderMailChance\": 7.5, \"sweepIntervalTicks\": 0,"
                + " \"minDeliverySeconds\": -10, \"systemMailDelayMs\": -1, \"secondsPer100Blocks\": -2}");
        assertEquals(1.0, cfg.traderMailChance);
        assertEquals(1, cfg.sweepIntervalTicks);
        assertEquals(0, cfg.minDeliverySeconds);
        assertEquals(0L, cfg.systemMailDelayMs);
        assertEquals(0.0, cfg.secondsPer100Blocks);
    }

    @Test
    void nanFallsBackToTheDefault() {
        PostboxConfig cfg = new PostboxConfig();
        cfg.crossDimensionBlocks = Double.NaN;
        cfg.traderMailChance = Double.NaN;
        cfg.secondsPer100Blocks = Double.NaN;
        cfg.sanitize();
        assertEquals(1024.0, cfg.crossDimensionBlocks);
        assertEquals(0.35, cfg.traderMailChance);
        assertEquals(1.0, cfg.secondsPer100Blocks);
    }

    @Test
    void missingKeysKeepTheirDefaults() {
        PostboxConfig cfg = parse("{\"maxQueued\": 50}");
        assertEquals(50, cfg.maxQueued);
        assertEquals(1, cfg.postageBase);
    }
}
