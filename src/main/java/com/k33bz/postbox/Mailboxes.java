package com.k33bz.postbox;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

/**
 * Mailbox lifecycle: forming (head placed atop an end rod), the display cluster (a stretched
 * item_display of the mailbox head + label + interaction hitbox — sanctuary's grave-headstone
 * idiom), the flag marker, and dismantling (break the end rod). The placed head block itself is
 * consumed into the display, which is scaled to real-mailbox proportions: wider and much deeper
 * than tall, perched on the rod.
 */
public final class Mailboxes {
    private Mailboxes() {
    }

    /** Form a mailbox: head block at {@code headPos} was just placed on an end rod. */
    public static Mail.Box form(ServerLevel level, ServerPlayer placer, BlockPos headPos) {
        BlockPos rod = headPos.below();
        // The head block is consumed into the display cluster.
        level.removeBlock(headPos, false);

        Mail.Box box = new Mail.Box();
        box.id = Long.toHexString(level.getGameTime()) + "b" + Mail.store().boxes.size();
        box.owner = placer.getUUID().toString();
        box.ownerName = placer.getName().getString();
        box.dim = level.dimension().identifier().toString();
        box.x = rod.getX();
        box.y = rod.getY();
        box.z = rod.getZ();
        Mail.store().boxes.add(box);
        Mail.save();
        spawnDisplays(level, box);
        Mail.run(level, String.format(Locale.ROOT,
                "playsound minecraft:block.amethyst_block.chime block @a %d %d %d 1 1.2",
                box.x, box.y, box.z));
        placer.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Your mailbox stands at %d %d %d. Others sneak-click it to send you mail.",
                        box.x, box.y, box.z))
                .withStyle(ChatFormatting.GOLD));
        Postbox.LOGGER.info("[postbox] {} raised a mailbox at {} {} {} ({})",
                box.ownerName, box.x, box.y, box.z, box.id);
        return box;
    }

    /** Spawn (or respawn) the display cluster for a box. Idempotent: kills the old cluster. */
    public static void spawnDisplays(ServerLevel level, Mail.Box box) {
        killDisplays(level, box);
        String tag = Mail.BOX_TAG + "_" + box.id;
        double cx = box.x + 0.5, cz = box.z + 0.5;
        double top = box.y + 1.0; // the end rod's top face
        // The mailbox body: the head item stretched to real-mailbox proportions — wider and
        // much deeper than tall (item scale 1 renders a head as a ~0.5 cube, so 1.6/1.0/2.2
        // gives roughly 0.8 wide x 0.5 tall x 1.1 deep), perched on the rod.
        Mail.run(level, String.format(Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\"],"
                        + "item:{id:\"minecraft:player_head\",count:1,"
                        + "components:{\"minecraft:profile\":{properties:[{name:\"textures\",value:\"%s\"}]}}},"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,0.25f,0f],scale:[1.6f,1.0f,2.2f]},"
                        + "brightness:{sky:15,block:15}}",
                cx, top, cz, Mail.BOX_TAG, tag, MailboxHead.TEXTURE));
        // Label
        Mail.run(level, String.format(Locale.ROOT,
                "summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\"],billboard:\"center\","
                        + "see_through:1b,text:{text:\"%s's Mailbox\",color:\"gold\"}}",
                cx, top + 0.95, cz, Mail.BOX_TAG, tag, box.ownerName));
        // Interaction hitbox around the body
        Mail.run(level, String.format(Locale.ROOT,
                "summon minecraft:interaction %.2f %.2f %.2f {Tags:[\"%s\",\"%s\"],width:1.2f,height:0.8f}",
                cx, top - 0.05, cz, Mail.BOX_TAG, tag));
        updateFlag(level, box);
    }

    /**
     * The flag: a small second item_display raised while the inbox is nonempty — kill/summon
     * by tag on deposit, withdraw, and delivery.
     */
    public static void updateFlag(ServerLevel level, Mail.Box box) {
        String flagTag = Mail.FLAG_TAG + "_" + box.id;
        Mail.run(level, "kill @e[tag=" + flagTag + "]");
        if (box.inbox.isEmpty()) {
            return;
        }
        Mail.run(level, String.format(Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\",\"%s\"],"
                        + "item:{id:\"minecraft:red_banner\",count:1},"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,0.3f,0f],scale:[0.45f,0.45f,0.45f]},"
                        + "brightness:{sky:15,block:15}}",
                box.x + 0.85, box.y + 1.35, box.z + 0.5,
                Mail.BOX_TAG, Mail.BOX_TAG + "_" + box.id, flagTag));
    }

    public static void killDisplays(ServerLevel level, Mail.Box box) {
        Mail.run(level, "kill @e[tag=" + Mail.BOX_TAG + "_" + box.id + "]");
    }

    /**
     * Dismantle: the end rod under a mailbox was broken. Displays die, the Rainbow Mailbox head
     * drops back, and any letters still inside slide into the owner's queue — mail is never lost.
     */
    public static void dismantle(ServerLevel level, Mail.Box box) {
        killDisplays(level, box);
        if (!box.inbox.isEmpty()) {
            Mail.queueOf(box.owner).addAll(box.inbox);
            box.inbox.clear();
        }
        Mail.store().boxes.remove(box);
        Mail.save();
        Block.popResource(level, new BlockPos(box.x, box.y + 1, box.z), MailboxHead.create());
        Postbox.LOGGER.info("[postbox] mailbox {} of {} dismantled", box.id, box.ownerName);
    }

    /** The delivery chime at a box (skipped silently if the chunk is unloaded). */
    public static void chime(ServerLevel level, Mail.Box box) {
        Mail.run(level, String.format(Locale.ROOT,
                "playsound minecraft:block.note_block.chime block @a %d %d %d 1 1.6",
                box.x, box.y + 1, box.z));
    }
}
