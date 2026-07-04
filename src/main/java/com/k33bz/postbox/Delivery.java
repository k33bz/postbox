package com.k33bz.postbox;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.UUID;

/**
 * Mail travels. A sweep on the server tick moves arrived letters into the recipient's inbox
 * (or their queue when the box is full or missing), chimes at the box, raises the flag, and —
 * for express letters landing in a loaded chunk — stages the wandering-trader courier scene.
 * The scene never gates delivery: unloaded chunk or failed pathing just means quiet mail.
 */
public final class Delivery {
    private Delivery() {
    }

    /** Move every due letter out of the in-transit list. */
    public static void sweep(MinecraftServer server, PostboxConfig cfg) {
        var inTransit = Mail.store().inTransit;
        if (inTransit.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean dirty = false;
        Iterator<Mail.Letter> it = inTransit.iterator();
        while (it.hasNext()) {
            Mail.Letter letter = it.next();
            if (letter.arriveAtMs > now) {
                continue;
            }
            it.remove();
            deliver(server, letter, cfg);
            dirty = true;
        }
        if (dirty) {
            Mail.save();
        }
    }

    /**
     * Land one letter: inbox if the recipient's box exists and has room, else the FIFO queue.
     * Visuals (flag, chime, courier) only when the box's chunk is loaded. Callers save.
     */
    public static void deliver(MinecraftServer server, Mail.Letter letter, PostboxConfig cfg) {
        Mail.Box box = letter.boxId == null ? null : Mail.boxById(letter.boxId);
        if (box == null || !box.owner.equals(letter.toUuid)) {
            box = Mail.firstBoxOf(letter.toUuid); // original box dismantled; any current one
        }
        boolean boxed = false;
        if (box != null && box.inbox.size() < Mail.INBOX_SIZE) {
            box.inbox.add(letter);
            boxed = true;
        } else {
            Mail.queueOf(letter.toUuid).add(letter);
        }

        if (boxed) {
            ServerLevel level = Mail.levelOf(server, box.dim);
            if (level != null && level.isLoaded(new BlockPos(box.x, box.y, box.z))) {
                Mailboxes.updateFlag(level, box);
                Mailboxes.chime(level, box);
                if (letter.express && cfg.courierSceneEnabled) {
                    Couriers.start(level, box);
                }
            }
        }

        ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(letter.toUuid));
        if (online != null) {
            online.sendOverlayMessage(Component.literal("You have mail.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Login notice: "You have mail (N)." when the inbox and/or queue holds anything. */
    public static void notifyOnJoin(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        int n = Mail.inboxCountFor(uuid) + Mail.queueOf(uuid).size();
        if (n > 0) {
            player.sendSystemMessage(Component.literal("You have mail (" + n + ").")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
