package com.k33bz.postbox;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The inbox — a 3x3 dispenser-style sgui (owner only, plain right-click on the box). Up to 9
 * letters; withdrawing one backfills FIFO from the owner's queue, so the box drains the backlog
 * nine at a time. Server-driven: vanilla clients render it natively.
 */
public class InboxGui extends SimpleGui {
    private final Mail.Box box;

    public static void open(ServerPlayer player, Mail.Box box) {
        // Fill any free inbox slots from the queue before showing (FIFO).
        backfill((ServerLevel) player.level(), box);
        new InboxGui(player, box).open();
    }

    private InboxGui(ServerPlayer player, Mail.Box box) {
        super(MenuType.GENERIC_3x3, player, false);
        this.box = box;
        this.setTitle(Component.literal(box.ownerName + "'s Mailbox"));
        refresh();
    }

    private void refresh() {
        ServerLevel level = (ServerLevel) player.level();
        int queued = Mail.queueOf(box.owner).size();
        for (int slot = 0; slot < Mail.INBOX_SIZE; slot++) {
            if (slot < box.inbox.size()) {
                Mail.Letter letter = box.inbox.get(slot);
                ItemStack stack = Mail.decodeStack(level, letter.stack);
                GuiElementBuilder el;
                if (stack.isEmpty()) {
                    el = new GuiElementBuilder(Items.PAPER)
                            .setName(Component.literal("Unreadable letter").withStyle(ChatFormatting.GRAY));
                } else {
                    el = GuiElementBuilder.from(stack);
                }
                String when = new SimpleDateFormat("MMM d, HH:mm").format(new Date(letter.sentAtMs));
                el.setLore(List.of(
                        Component.literal("From " + letter.from + " — " + when)
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal(letter.express ? "Express delivery" : "Standard post")
                                .withStyle(letter.express ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY),
                        Component.literal("Click to take").withStyle(ChatFormatting.DARK_GRAY)));
                final int idx = slot;
                el.setCallback((index, type, action, gui) -> withdraw(idx));
                this.setSlot(slot, el);
            } else {
                this.setSlot(slot, new GuiElementBuilder(Items.GLASS_PANE)
                        .setName(Component.literal(queued > 0
                                        ? queued + " more waiting in the queue"
                                        : "Empty")
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
    }

    /** Take one letter out; the queue backfills FIFO behind it. */
    private void withdraw(int index) {
        if (takeOne(player, box, index)) {
            refresh();
        }
    }

    /**
     * Take one letter (at {@code index}) out of {@code box} into {@code player}'s inventory
     * (overflow drops at their feet), backfilling the queue FIFO behind it — the exact effect of
     * clicking a slot in the 3x3 GUI. Shared by the GUI callback and the {@code /postbox take}
     * command backend so both drive the identical withdrawal path. Returns true if a letter left
     * the box (so the GUI knows to refresh). Callers enforce owner-only, just as the GUI does.
     */
    public static boolean takeOne(ServerPlayer player, Mail.Box box, int index) {
        if (index < 0 || index >= box.inbox.size()) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Mail.Letter letter = box.inbox.get(index);
        ItemStack stack = Mail.decodeStack(level, letter.stack);
        if (stack.isEmpty()) {
            return false; // never destroy a letter we can't materialize
        }
        box.inbox.remove(index);
        if (!player.getInventory().add(stack)) {
            level.addFreshEntity(new ItemEntity(level,
                    player.getX(), player.getY() + 0.4, player.getZ(), stack));
        }
        backfill(level, box);
        Mailboxes.updateFlag(level, box);
        Mail.save();
        return true;
    }

    /** Fill any free inbox slots of {@code box} from its owner's queue (FIFO). */
    public static void fill(ServerLevel level, Mail.Box box) {
        backfill(level, box);
    }

    private static void backfill(ServerLevel level, Mail.Box box) {
        var queue = Mail.queueOf(box.owner);
        boolean moved = false;
        while (box.inbox.size() < Mail.INBOX_SIZE && !queue.isEmpty()) {
            box.inbox.add(queue.remove(0));
            moved = true;
        }
        if (moved) {
            Mailboxes.updateFlag(level, box);
            Mail.save();
        }
    }
}
