package com.k33bz.postbox;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sender form — a native 26.x server dialog with INPUT controls (crouch + right-click any
 * mailbox): a recipient dropdown (online players first, then offline known players), a search
 * field (dialogs are static forms, so the Filter button re-opens the form narrowed to matches),
 * a multiline postcard message OR a "send held book" toggle, and an extra-postage slider for
 * intentional express. Send fires a permission-0 backend command with $(key) substitution.
 */
public final class SendDialog {
    private SendDialog() {
    }

    /** Which mailbox each player is currently sending from (set when the form opens). */
    public static final Map<UUID, String> SESSION = new ConcurrentHashMap<>();

    private static final int MAX_ENTRIES = 64;

    public static void open(ServerPlayer player, Mail.Box usingBox, String filter) {
        SESSION.put(player.getUUID(), usingBox.id);
        PostboxConfig cfg = Postbox.CONFIG;

        List<Sender.Recipient> all = Sender.knownRecipients(player.level().getServer());
        List<Sender.Recipient> shown = new ArrayList<>();
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        for (Sender.Recipient r : all) {
            if (needle.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(r);
                if (shown.size() >= MAX_ENTRIES) {
                    break;
                }
            }
        }
        if (shown.isEmpty() && !needle.isEmpty()) {
            player.sendOverlayMessage(Component.literal("No players match '" + filter + "' — showing all."));
            shown = all.subList(0, Math.min(all.size(), MAX_ENTRIES));
            needle = "";
        }
        if (shown.isEmpty()) {
            player.sendSystemMessage(Component.literal("Nobody is known to this post office yet.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        List<SingleOptionInput.Entry> entries = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            Sender.Recipient r = shown.get(i);
            entries.add(new SingleOptionInput.Entry(r.name(),
                    Optional.of(Component.literal(r.name() + (r.online() ? " (online)" : " (offline)"))),
                    i == 0));
        }

        List<Input> inputs = new ArrayList<>();
        inputs.add(new Input("recipient",
                new SingleOptionInput(210, entries, Component.literal("To"), true)));
        inputs.add(new Input("search",
                new TextInput(210, Component.literal("Search (then press Filter)"), true,
                        needle, 32, Optional.empty())));
        inputs.add(new Input("message",
                new TextInput(250, Component.literal("Message"), true, "", 1200,
                        Optional.of(new TextInput.MultilineOptions(Optional.of(8), Optional.of(70))))));
        inputs.add(new Input("book",
                new BooleanInput(Component.literal("Send the book in my main hand instead"),
                        false, "true", "false")));
        inputs.add(new Input("extra",
                new NumberRangeInput(210, Component.literal("Extra postage (express)"),
                        "options.generic_value",
                        new NumberRangeInput.RangeInfo(0.0f, 16.0f, Optional.of(0.0f), Optional.of(1.0f)))));

        boolean own = usingBox.owner.equals(player.getUUID().toString());
        var info = Component.literal("Sending from " + usingBox.ownerName + "'s mailbox.")
                .withStyle(ChatFormatting.GRAY);
        info.append(Component.literal(String.format(Locale.ROOT,
                        "\nPostage: %d + 1 per %d chars + 1 per %d blocks (emeralds; blocks = 9).",
                        cfg.postageBase, cfg.postageCharsPerEmerald, cfg.postageBlocksPerEmerald))
                .withStyle(ChatFormatting.DARK_GRAY));
        info.append(Component.literal(
                        "\nDropping mail into the recipient's own box is free and instant."
                                + "\nEvery overpaid emerald halves the travel time.")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (own) {
            info.append(Component.literal("\n(Mailing yourself a memo is free — it's your box.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        List<DialogBody> body = List.of(new PlainMessage(info, 250));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(new ActionButton(new CommonButtonData(Component.literal("Send"), 140),
                Optional.of(template("postbox send $(recipient) $(extra) $(book) $(message)"))));
        buttons.add(new ActionButton(new CommonButtonData(Component.literal("Filter"), 140),
                Optional.of(template("postbox filter $(search)"))));

        CommonDialogData common = new CommonDialogData(
                Component.literal("Send Mail"),
                Optional.empty(),
                true,   // closable with escape
                false,  // never pause the server from a dialog
                DialogAction.CLOSE,
                body,
                inputs);
        Dialog dialog = new MultiActionDialog(common, buttons,
                Optional.of(new ActionButton(new CommonButtonData(Component.literal("Cancel"), 100),
                        Optional.empty())),
                2);
        player.openDialog(Holder.direct(dialog));
    }

    /** Build a run_command template action with $(key) input substitution. */
    private static Action template(String command) {
        ParsedTemplate parsed = ParsedTemplate.CODEC
                .parse(JsonOps.INSTANCE, new JsonPrimitive(command))
                .result().orElseThrow(() -> new IllegalStateException("bad dialog template: " + command));
        return new CommandTemplate(parsed);
    }

    /** The mailbox this player most recently opened the form at (must still exist + be near). */
    public static Mail.Box sessionBox(ServerPlayer player) {
        String id = SESSION.get(player.getUUID());
        if (id == null) {
            return null;
        }
        Mail.Box box = Mail.boxById(id);
        if (box == null || !box.dim.equals(player.level().dimension().identifier().toString())
                || player.distanceToSqr(box.x + 0.5, box.y + 0.5, box.z + 0.5) > 8 * 8) {
            return null;
        }
        return box;
    }
}
