package com.k33bz.postbox;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Commands. The surface is deliberately minimal — the crouch form IS sending:
 * <ul>
 *   <li>{@code /mail check} — inbox/queue counts + in-transit letters with ETA (permission 0).</li>
 *   <li>{@code /postbox send|filter} — permission-0 backends for the send dialog's buttons
 *       (templated input values land here via $(key) substitution).</li>
 *   <li>{@code /postbox testsend <recipient> [extraPostage]} — permission-0, session-free send
 *       twin: mails the held book via the same {@link Sender#send} economy path (for headless
 *       drivers that cannot open the dialog).</li>
 *   <li>{@code /postbox take} — permission-0, withdraws one letter (FIFO) from the caller's own
 *       inbox via the same {@link InboxGui#takeOne} path as the 3x3 GUI.</li>
 * </ul>
 */
public final class PostboxCommands {
    private PostboxCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("mail")
                    .executes(PostboxCommands::check)
                    .then(Commands.literal("check").executes(PostboxCommands::check)));

            // Dialog backends. Permission 0 — the dialog is the UI; these are its wiring.
            dispatcher.register(Commands.literal("postbox")
                    .then(Commands.literal("send")
                            .then(Commands.argument("recipient", StringArgumentType.word())
                                    .then(Commands.argument("extra", StringArgumentType.word())
                                            .then(Commands.argument("book", StringArgumentType.word())
                                                    .executes(ctx -> send(ctx, ""))
                                                    .then(Commands.argument("message", StringArgumentType.greedyString())
                                                            .executes(ctx -> send(ctx,
                                                                    StringArgumentType.getString(ctx, "message"))))))))
                    .then(Commands.literal("filter")
                            .executes(ctx -> filter(ctx, ""))
                            .then(Commands.argument("search", StringArgumentType.greedyString())
                                    .executes(ctx -> filter(ctx,
                                            StringArgumentType.getString(ctx, "search")))))
                    // Session-free SEND backend (permission 0). The command twin of the send
                    // dialog, but sourcing the recipient + extra postage from args and the letter
                    // from the WRITTEN/WRITABLE BOOK in the player's main hand — no dialog session.
                    // It runs the SAME Sender.send economy path (postage, emerald consume incl.
                    // blocks=9, insufficient-funds reject WITHOUT consuming, overpay-halves-delay,
                    // hand-delivery-free, poste-restante), sending FROM the player's own mailbox.
                    .then(Commands.literal("testsend")
                            .then(Commands.argument("recipient", StringArgumentType.word())
                                    .executes(ctx -> testSend(ctx, 0))
                                    .then(Commands.argument("extraPostage", IntegerArgumentType.integer(0, 64))
                                            .executes(ctx -> testSend(ctx,
                                                    IntegerArgumentType.getInteger(ctx, "extraPostage"))))))
                    // INBOX WITHDRAW backend (permission 0). Take one letter (FIFO) from the
                    // player's OWN inbox into their inventory, backfilling from the queue — the
                    // command twin of clicking a slot in the 3x3 GUI. Owner-only, same as the GUI.
                    .then(Commands.literal("take")
                            .executes(PostboxCommands::take)));
        });
    }

    /**
     * Session-free send backend. Resolves the sender's OWN mailbox as the origin (the dialog uses
     * the box you stood at; a bot has no session, so its own box is the natural origin — this also
     * makes mailing yourself a hand-delivery, exactly as the dialog would). Then defers entirely to
     * {@link Sender#send} with {@code useHeldBook = true}, so every economy rule is enforced
     * unchanged: postage calc, emerald/emerald-block payment, insufficient-funds reject WITHOUT
     * consuming, overpay express (halved delay), free instant hand delivery, and poste restante.
     */
    private static int testSend(CommandContext<CommandSourceStack> ctx, int extraPostage) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
        Mail.Box fromBox = Mail.firstBoxOf(player.getUUID().toString());
        if (fromBox == null) {
            player.sendSystemMessage(Component.literal(
                            "You have no mailbox to send from. Place one first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String recipient = StringArgumentType.getString(ctx, "recipient");
        // useHeldBook = true, message = "" — the letter is the written/writable book in main hand.
        return Sender.send(player, fromBox, recipient, Math.max(0, extraPostage), true,
                "", Postbox.CONFIG) ? 1 : 0;
    }

    /**
     * Inbox withdrawal backend. Owner-only (you can only drain your own box, exactly as the plain
     * right-click GUI). Backfills the inbox from the queue first, then takes the FIFO-front letter
     * (slot 0) via the SAME {@link InboxGui#takeOne} path the 3x3 click uses.
     */
    private static int take(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
        Mail.Box box = Mail.firstBoxOf(player.getUUID().toString());
        if (box == null) {
            player.sendSystemMessage(Component.literal("You have no mailbox.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level =
                (net.minecraft.server.level.ServerLevel) player.level();
        InboxGui.fill(level, box); // pull the queue forward, same as opening the GUI
        if (box.inbox.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your mailbox is empty.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (!InboxGui.takeOne(player, box, 0)) {
            return 0;
        }
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Took a letter from your mailbox (%d left, %d queued).",
                        box.inbox.size(), Mail.queueOf(box.owner).size()))
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
        String uuid = player.getUUID().toString();
        int inbox = Mail.inboxCountFor(uuid);
        int queued = Mail.queueOf(uuid).size();
        long now = System.currentTimeMillis();
        StringBuilder transit = new StringBuilder();
        int inTransit = 0;
        for (Mail.Letter l : Mail.store().inTransit) {
            if (!uuid.equals(l.toUuid)) {
                continue;
            }
            inTransit++;
            if (inTransit <= 5) {
                transit.append(String.format(Locale.ROOT, "\n  from %s — arrives in %s%s",
                        l.from, Sender.eta(l.arriveAtMs - now), l.express ? " (express)" : ""));
            }
        }
        if (inTransit > 5) {
            transit.append("\n  … and ").append(inTransit - 5).append(" more");
        }
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Mail: %d in your box, %d queued, %d in transit.%s",
                        inbox, queued, inTransit, transit))
                .withStyle(ChatFormatting.GOLD));
        return inbox + queued + inTransit;
    }

    private static int send(CommandContext<CommandSourceStack> ctx, String message) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Mail.Box usingBox = SendDialog.sessionBox(player);
        if (usingBox == null) {
            player.sendSystemMessage(Component.literal(
                            "Stand at a mailbox and sneak + right-click it to send mail.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String recipient = StringArgumentType.getString(ctx, "recipient");
        int extra = (int) Math.round(parseDouble(StringArgumentType.getString(ctx, "extra")));
        boolean book = Boolean.parseBoolean(StringArgumentType.getString(ctx, "book"));
        return Sender.send(player, usingBox, recipient, Math.max(0, extra), book,
                message, Postbox.CONFIG) ? 1 : 0;
    }

    private static int filter(CommandContext<CommandSourceStack> ctx, String search) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Mail.Box usingBox = SendDialog.sessionBox(player);
        if (usingBox == null) {
            player.sendSystemMessage(Component.literal(
                            "Stand at a mailbox and sneak + right-click it to send mail.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        SendDialog.open(player, usingBox, search);
        return 1;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
