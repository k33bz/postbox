package com.k33bz.postbox;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Outbox spool — lets OTHER server-side mods post mail without a compile-time dependency on postbox.
 * A sender drops one JSON request file per letter into {@code config/postbox_outbox/} (fields:
 * {@code toUuid, toName, from, body}); this ingest sweep turns each into a normal system letter (a
 * postcard placed into transit, delivered by the usual {@link Delivery} path with the courier scene),
 * then deletes the request file. One file per request means no shared-file write race with postbox's
 * own {@code postbox_mail.json} saves. Ingest runs on the same interval as the delivery sweep.
 */
public final class Outbox {
    private Outbox() {
    }

    private static final Gson GSON = new Gson();

    /** A drop-file mail request from another mod. */
    static final class Request {
        String toUuid;   // recipient UUID (required)
        String toName;   // recipient display name (for the letter header / poste restante)
        String from;     // sender label shown on the letter (e.g. "The Gravekeeper")
        String body;     // the message text
    }

    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("postbox_outbox");
    }

    /** Ingest every pending request file into transit, then delete it. Best-effort per file. */
    public static void ingest(MinecraftServer server, PostboxConfig cfg) {
        Path dir = dir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (Exception e) {
            return;
        }
        if (files.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        for (Path f : files) {
            String content;
            try {
                content = Files.readString(f);
            } catch (Exception e) {
                continue; // unreadable right now; retry next sweep
            }
            // CLAIM the file by removing it FIRST, and only enqueue if the claim succeeds. A file we
            // can't delete (e.g. a wrong-owner drop) is thus skipped, not re-ingested every sweep —
            // otherwise a single stuck request would mail the recipient on repeat forever.
            try {
                Files.delete(f);
            } catch (Exception e) {
                Postbox.LOGGER.warn("[postbox] can't claim outbox request {} — skipping to avoid duplicate mail", f, e);
                continue;
            }
            try {
                Request r = GSON.fromJson(content, Request.class);
                if (r != null && r.toUuid != null && !r.toUuid.isBlank()) {
                    enqueue(level, r, cfg);
                }
            } catch (Exception e) {
                Postbox.LOGGER.warn("[postbox] bad outbox request {}", f, e);
            }
        }
    }

    private static void enqueue(ServerLevel level, Request r, PostboxConfig cfg) {
        // Respect the recipient's queue cap; system mail is dropped silently rather than piling up.
        if (Mail.pendingCountFor(r.toUuid) >= cfg.maxQueued) {
            return;
        }
        String from = (r.from == null || r.from.isBlank()) ? "Postmaster" : r.from;
        String body = r.body == null ? "" : r.body;

        Mail.Letter letter = new Mail.Letter();
        letter.from = from;
        letter.fromUuid = "";                 // system sender, no player uuid
        letter.toUuid = r.toUuid;
        letter.toName = r.toName;
        letter.sentAtMs = System.currentTimeMillis();
        letter.postagePaid = 0;
        letter.express = false;
        Mail.Box toBox = Mail.firstBoxOf(r.toUuid);
        letter.boxId = toBox == null ? null : toBox.id;
        letter.stack = Mail.encodeStack(level, Letters.postcard(from, body));
        letter.arriveAtMs = letter.sentAtMs + Math.max(0L, cfg.systemMailDelayMs);

        Mail.store().inTransit.add(letter);
        Mail.save();
    }
}
