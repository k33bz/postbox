package com.k33bz.postbox;

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
                                            StringArgumentType.getString(ctx, "search"))))));
        });
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
