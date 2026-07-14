package com.k33bz.postbox;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The send pipeline: recipient resolution (online players, then offline names known from
 * mailboxes and the server's usercache), postage collection from the INVENTORY only (emeralds;
 * emerald blocks count as 9, no change, overpay = express), and dispatch into the in-transit
 * list — or straight into the inbox for hand delivery.
 */
public final class Sender {
    private Sender() {
    }

    public record Recipient(String uuid, String name, boolean online) {
    }

    /** Resolve a recipient name: online first, then usercache, then mailbox owners. */
    public static Recipient resolve(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return new Recipient(online.getUUID().toString(), online.getName().getString(), true);
        }
        for (Map.Entry<String, String> e : usercacheNames(server).entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return new Recipient(e.getValue(), e.getKey(), false);
            }
        }
        for (Mail.Box b : Mail.store().boxes) {
            if (b.ownerName.equalsIgnoreCase(name)) {
                return new Recipient(b.owner, b.ownerName, false);
            }
        }
        return null;
    }

    /**
     * All known recipients for the send dialog: ONLINE players first (alphabetical), then
     * OFFLINE known players (mailbox owners + usercache), alphabetical.
     */
    public static List<Recipient> knownRecipients(MinecraftServer server) {
        List<Recipient> out = new ArrayList<>();
        TreeMap<String, ServerPlayer> online = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.put(p.getName().getString(), p);
        }
        for (Map.Entry<String, ServerPlayer> e : online.entrySet()) {
            out.add(new Recipient(e.getValue().getUUID().toString(), e.getKey(), true));
        }
        TreeMap<String, String> offline = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Mail.Box b : Mail.store().boxes) {
            offline.put(b.ownerName, b.owner);
        }
        offline.putAll(usercacheNames(server));
        for (Map.Entry<String, String> e : offline.entrySet()) {
            if (!online.containsKey(e.getKey())) {
                out.add(new Recipient(e.getValue(), e.getKey(), false));
            }
        }
        return out;
    }

    /** name → uuid from the server's usercache.json (best effort; empty on any hiccup). */
    private static Map<String, String> usercacheNames(MinecraftServer server) {
        Map<String, String> names = new LinkedHashMap<>();
        try {
            Path file = server.getServerDirectory().resolve("usercache.json");
            if (Files.exists(file)) {
                JsonArray arr = new Gson().fromJson(Files.readString(file), JsonArray.class);
                for (var el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("name") && o.has("uuid")) {
                        names.put(o.get("name").getAsString(), o.get("uuid").getAsString());
                    }
                }
            }
        } catch (Exception e) {
            Postbox.LOGGER.debug("[postbox] could not read usercache", e);
        }
        return names;
    }

    // ------------------------------------------------------------------

    /**
     * Send a letter from {@code usingBox}. Either a typed {@code message} (postcard) or, when
     * {@code useHeldBook}, the WRITTEN/WRITABLE book in the sender's main hand. Returns true
     * on success (feedback messages are sent either way).
     */
    public static boolean send(ServerPlayer sender, Mail.Box usingBox, String recipientName,
                               int extraPostage, boolean useHeldBook, String message, PostboxConfig cfg) {
        MinecraftServer server = sender.level().getServer();
        ServerLevel level = (ServerLevel) sender.level();

        Recipient to = resolve(server, recipientName);
        if (to == null) {
            fail(sender, "No player named '" + recipientName + "' is known here.");
            return false;
        }

        // The letter itself.
        ItemStack letterStack;
        int chars;
        if (useHeldBook) {
            ItemStack held = sender.getMainHandItem();
            if (!Letters.isLetter(held)) {
                fail(sender, "Hold the book you want to mail in your main hand.");
                return false;
            }
            letterStack = held.copy();
            letterStack.setCount(1); // mail exactly one book — the hand only gives up one (shrink(1))
            chars = Letters.chars(letterStack);
        } else {
            if (message == null || message.isBlank()) {
                fail(sender, "Write a message (or tick 'send held book').");
                return false;
            }
            letterStack = Letters.postcard(sender.getName().getString(), message);
            chars = message.length();
        }

        Mail.Box toBox = Mail.firstBoxOf(to.uuid());
        boolean handDelivery = usingBox.owner.equals(to.uuid());
        boolean hasBox = toBox != null;

        // Queue cap: reject up front — mail is never silently dropped. Applies to hand delivery too
        // (it lands in the recipient's inbox, which pendingCountFor counts), so a free self-addressed
        // or hijacked-session hand delivery can no longer flood a player's box without bound.
        if (Mail.pendingCountFor(to.uuid()) >= cfg.maxQueued) {
            fail(sender, to.name() + "'s mail queue is full (" + cfg.maxQueued + "). Try later.");
            return false;
        }

        double distance = hasBox && !handDelivery ? boxDistance(usingBox, toBox, cfg) : 0.0;
        int cost = Postage.letterCost(handDelivery, hasBox, chars, distance, cfg.postageBase,
                cfg.postageCharsPerEmerald, cfg.postageBlocksPerEmerald, cfg.posteRestanteSurcharge);
        int needed = cost + Math.max(0, extraPostage);

        // Payment — inventory only, no change, no refunds.
        int paid = 0;
        if (needed > 0) {
            Inventory inv = sender.getInventory();
            int[] plan = Postage.paymentPlan(needed, countItem(inv, Items.EMERALD),
                    countItem(inv, Items.EMERALD_BLOCK));
            if (plan == null) {
                fail(sender, String.format(Locale.ROOT,
                        "Postage is %d emerald%s (you asked +%d extra) — you can't cover it. Nothing was taken.",
                        cost, cost == 1 ? "" : "s", extraPostage));
                return false;
            }
            consumeItem(inv, Items.EMERALD, plan[0]);
            consumeItem(inv, Items.EMERALD_BLOCK, plan[1]);
            paid = plan[2];
        }
        int overpaid = Math.max(0, paid - cost);
        boolean express = overpaid > 0;

        // The held book leaves the hand only once payment cleared.
        if (useHeldBook) {
            sender.getMainHandItem().shrink(1);
        }

        Mail.Letter letter = new Mail.Letter();
        letter.from = sender.getName().getString();
        letter.fromUuid = sender.getUUID().toString();
        letter.toUuid = to.uuid();
        letter.toName = to.name();
        letter.sentAtMs = System.currentTimeMillis();
        letter.postagePaid = paid;
        letter.express = express;
        letter.boxId = toBox == null ? null : toBox.id;
        letter.stack = Mail.encodeStack(level, letterStack);

        long delayMs = Postage.deliveryDelayMs(handDelivery, hasBox, distance,
                cfg.secondsPer100Blocks, cfg.minDeliverySeconds,
                cfg.posteRestanteDeliverySeconds, overpaid);
        letter.arriveAtMs = letter.sentAtMs + delayMs;

        if (handDelivery) {
            Delivery.deliver(server, letter, cfg); // instant, free, straight into the box
            Mail.save();
            sender.sendSystemMessage(Component.literal("Delivered by hand. No postage owed.")
                    .withStyle(ChatFormatting.GOLD));
            return true;
        }

        Mail.store().inTransit.add(letter);
        Mail.save();
        sender.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Letter to %s sent — %d emerald%s postage%s. Arrives in %s.",
                        to.name(), paid, paid == 1 ? "" : "s",
                        express ? " (EXPRESS: overpaid by " + overpaid + ")" : "",
                        eta(delayMs)))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Distance between two boxes; cross-dimension mail uses a fixed configured distance. */
    public static double boxDistance(Mail.Box a, Mail.Box b, PostboxConfig cfg) {
        if (!a.dim.equals(b.dim)) {
            return cfg.crossDimensionBlocks;
        }
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static String eta(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Locale.ROOT, "%dh %02dm", s / 3600, (s % 3600) / 60)
                : s >= 60 ? String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60)
                : s + "s";
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static int countItem(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void consumeItem(Inventory inv, Item item, int count) {
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int take = Math.min(count, s.getCount());
                s.shrink(take);
                count -= take;
            }
        }
    }
}
