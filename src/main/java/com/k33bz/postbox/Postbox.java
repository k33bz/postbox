package com.k33bz.postbox;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Postbox — an in-game mail system, entirely server-side (vanilla clients connect with no
 * mods): Rainbow Mailboxes raised on end rods, letters that are real book items, emerald
 * postage, delivery that takes time proportional to distance, express wandering-trader
 * couriers, and Lost Mail loot from slain traders.
 */
public class Postbox implements ModInitializer {
    public static final String MOD_ID = "postbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Loaded once at init. */
    public static PostboxConfig CONFIG;

    private int sweepCounter = 0;

    @Override
    public void onInitialize() {
        CONFIG = PostboxConfig.load();
        Mail.store(); // load the mail store early so a corrupt file complains at boot
        Mail.save();  // ...and materialize it, so external tools (web inbox) can rely on it
        PostboxCommands.register();
        registerInteraction();
        registerTick();
        registerBreak();
        registerJoinNotice();
        registerTraderLoot();

        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        String version = loader.getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String mc = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        LOGGER.info("[postbox] v{} initialized (server-authoritative) for Minecraft {}", version, mc);
    }

    /**
     * Right-clicks on mailbox hitboxes and couriers. Plain click = owner's inbox; sneak-click
     * = the send form (any box, including your own); couriers are interaction-blocked.
     */
    private void registerInteraction() {
        UseEntityCallback.EVENT.register((p, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(p instanceof ServerPlayer player)
                    || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            // Express couriers neither trade nor talk — consume the click outright.
            if (entity.entityTags().contains(Mail.COURIER_TAG)) {
                return InteractionResult.SUCCESS;
            }
            String prefix = Mail.BOX_TAG + "_";
            for (String tag : entity.entityTags()) {
                if (!tag.startsWith(prefix)) {
                    continue;
                }
                Mail.Box box = Mail.boxById(tag.substring(prefix.length()));
                if (box == null) {
                    return InteractionResult.PASS;
                }
                if (player.isShiftKeyDown()) {
                    SendDialog.open(player, box, "");
                } else if (box.owner.equals(player.getUUID().toString())) {
                    InboxGui.open(player, box);
                } else {
                    player.sendOverlayMessage(Component.literal("Not your mailbox — sneak to send."));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    /** Delivery sweep on an interval + the per-tick courier animation. */
    private void registerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Couriers.tick(server);
            if (++sweepCounter < Math.max(1, CONFIG.sweepIntervalTicks)) {
                return;
            }
            sweepCounter = 0;
            Delivery.sweep(server, CONFIG);
        });
    }

    /** Breaking the end rod under a mailbox dismantles it (head drops back, mail to queue). */
    private void registerBreak() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!state.is(Blocks.END_ROD) || !(world instanceof ServerLevel level)) {
                return;
            }
            Mail.Box box = Mail.boxAt(level.dimension().identifier().toString(),
                    pos.getX(), pos.getY(), pos.getZ());
            if (box != null) {
                Mailboxes.dismantle(level, box);
            }
        });
    }

    private void registerJoinNotice() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                Delivery.notifyOnJoin(handler.player));
    }

    /** Any slain wandering trader (vanilla spawns included) may drop a Lost Mail bundle. */
    private void registerTraderLoot() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof WanderingTrader)
                    || !(entity.level() instanceof ServerLevel level)
                    || entity.entityTags().contains(Mail.COURIER_TAG)) {
                return;
            }
            if (level.getRandom().nextDouble() >= CONFIG.traderMailChance) {
                return;
            }
            Block.popResource(level, entity.blockPosition(), LostMail.bundle(level.getRandom()));
            LOGGER.info("[postbox] a slain wandering trader dropped Lost Mail at {}",
                    entity.blockPosition().toShortString());
        });
    }
}
