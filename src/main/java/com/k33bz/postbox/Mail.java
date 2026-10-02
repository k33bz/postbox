package com.k33bz.postbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mail store — {@code config/postbox_mail.json} (gson, pretty). Everything lives in JSON,
 * not in-world: mailboxes, per-player FIFO queues, and letters in transit. Letter item stacks
 * are serialized via {@code ItemStack.CODEC} + registry ops, the same despawn-proof pattern
 * as sanctuary's graves.
 *
 * <p>Schema (also consumed by the future web inbox / Discord bridge):
 * {@code boxes[]}, {@code queues{uuid:[letters]}}, {@code inTransit[]},
 * letter = {from, fromUuid, sentAtMs, arriveAtMs, postagePaid, express, stack}.</p>
 */
public final class Mail {
    private Mail() {
    }

    /** Cluster tag on every display/interaction entity of every mailbox. */
    public static final String BOX_TAG = "postbox_box";
    /** Per-box tag: {@code postbox_box_<id>}. */
    public static final String FLAG_TAG = "postbox_flag";
    /** Tag on express-courier entities (trader + llamas): interaction-blocked, invulnerable. */
    public static final String COURIER_TAG = "postbox_courier";

    public static final int INBOX_SIZE = 9;

    public static final class Letter {
        public String from;       // sender display name
        public String fromUuid;
        public String toUuid;
        public String toName;
        public long sentAtMs;
        public long arriveAtMs;
        public int postagePaid;   // emeralds actually consumed
        public boolean express;   // any overpay (intentional or block split)
        public String boxId;      // destination box at send time; null = poste restante
        public JsonElement stack; // the letter item, ItemStack.CODEC JSON
    }

    public static final class Box {
        public String id;
        public String owner;      // uuid
        public String ownerName;
        public String dim;
        public int x, y, z;       // the END ROD position (the head was consumed into displays)
        public List<Letter> inbox = new ArrayList<>();
    }

    public static final class Store {
        public List<Box> boxes = new ArrayList<>();
        public Map<String, List<Letter>> queues = new HashMap<>();
        public List<Letter> inTransit = new ArrayList<>();
    }

    private static Store store;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("postbox_mail.json");
    }

    public static Store store() {
        if (store == null) {
            Path file = path();
            if (Files.exists(file)) {
                try {
                    store = GSON.fromJson(Files.readString(file), new TypeToken<Store>() { }.getType());
                } catch (Exception e) {
                    // Never start over on top of the only copy: back it up first (MailFiles).
                    Path backup = MailFiles.backUpCorrupt(file, System.currentTimeMillis());
                    Postbox.LOGGER.error("[postbox] could not read the mail store {}. It was copied to {} and "
                            + "postbox is starting with an EMPTY store; repair the copy and put it back "
                            + "(with the server stopped) to restore mailboxes and letters.", file, backup, e);
                }
            }
            if (store == null) {
                store = new Store();
            }
            if (store.boxes == null) {
                store.boxes = new ArrayList<>();
            }
            store.boxes.removeIf(b -> b == null); // a stray `null` in the array would NPE every lookup
            for (Box b : store.boxes) {
                if (b.inbox == null) {
                    b.inbox = new ArrayList<>();
                }
            }
            if (store.queues == null) {
                store.queues = new HashMap<>();
            }
            store.queues.replaceAll((uuid, letters) -> letters == null ? new ArrayList<>() : letters);
            if (store.inTransit == null) {
                store.inTransit = new ArrayList<>();
            }
            store.inTransit.removeIf(l -> l == null);
        }
        return store;
    }

    /** Persist the store atomically (temp file + move), so a crash mid-save can't corrupt it. */
    public static void save() {
        try {
            MailFiles.writeAtomically(path(), GSON.toJson(store()));
        } catch (IOException e) {
            Postbox.LOGGER.warn("[postbox] could not save mail store", e);
        }
    }

    // --- lookups ---

    public static Box boxById(String id) {
        for (Box b : store().boxes) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    /** The recipient's mailbox (players are capped at 1 by default; first wins otherwise). */
    public static Box firstBoxOf(String uuid) {
        for (Box b : store().boxes) {
            if (b.owner.equals(uuid)) {
                return b;
            }
        }
        return null;
    }

    public static Box boxAt(String dim, int x, int y, int z) {
        for (Box b : store().boxes) {
            if (b.x == x && b.y == y && b.z == z && b.dim.equals(dim)) {
                return b;
            }
        }
        return null;
    }

    public static int countOwnedBy(String uuid) {
        int n = 0;
        for (Box b : store().boxes) {
            if (b.owner.equals(uuid)) {
                n++;
            }
        }
        return n;
    }

    public static List<Letter> queueOf(String uuid) {
        return store().queues.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    /** Letters already queued + still traveling toward this player (the maxQueued gate). */
    public static int pendingCountFor(String uuid) {
        int n = queueOf(uuid).size();
        for (Letter l : store().inTransit) {
            if (uuid.equals(l.toUuid)) {
                n++;
            }
        }
        return n;
    }

    public static int inboxCountFor(String uuid) {
        int n = 0;
        for (Box b : store().boxes) {
            if (b.owner.equals(uuid)) {
                n += b.inbox.size();
            }
        }
        return n;
    }

    // --- item stack (de)serialization ---

    public static JsonElement encodeStack(ServerLevel level, ItemStack stack) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        return ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
    }

    public static ItemStack decodeStack(ServerLevel level, JsonElement json) {
        if (json == null) {
            return ItemStack.EMPTY;
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        return ItemStack.CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY);
    }

    public static ServerLevel levelOf(MinecraftServer server, String dim) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
    }

    /** Run a command as the server with suppressed output (summon/kill/playsound/particle). */
    public static void run(ServerLevel level, String command) {
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
