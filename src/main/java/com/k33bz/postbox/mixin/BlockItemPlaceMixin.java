package com.k33bz.postbox.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.k33bz.postbox.Mail;
import com.k33bz.postbox.MailboxHead;
import com.k33bz.postbox.Mailboxes;
import com.k33bz.postbox.Postbox;

/**
 * Placement hooks: a Rainbow Mailbox head placed atop an END ROD forms a mailbox (identified by
 * the placed skull's profile — sanctuary's crystal pattern). The cap is enforced up front; on a
 * successful match the head block is consumed into the display cluster.
 */
@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {

    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void postbox$gateMailboxPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !MailboxHead.isMailbox(context.getItemInHand())
                || !level.getBlockState(context.getClickedPos().below()).is(Blocks.END_ROD)) {
            return; // not a mailbox-forming placement — vanilla proceeds
        }
        if (!player.isCreative()
                && Mail.countOwnedBy(player.getUUID().toString()) >= Postbox.CONFIG.maxBoxesPerPlayer) {
            player.sendOverlayMessage(Component.literal(String.format(java.util.Locale.ROOT,
                    "Mailbox limit reached (%d).", Postbox.CONFIG.maxBoxesPerPlayer)));
            // Resync the client: it already predicted the place (ghost block, shrunk stack).
            player.containerMenu.sendAllDataToRemote();
            BlockPos pos = context.getClickedPos();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, pos));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level,
                    pos.relative(context.getClickedFace().getOpposite())));
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN")
    )
    private void postbox$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()
                || !(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = context.getClickedPos();
        // Identify by the placed skull's profile — the hand stack may already be consumed.
        if (level.getBlockEntity(pos) instanceof SkullBlockEntity skull
                && MailboxHead.isMailbox(skull.getOwnerProfile())
                && level.getBlockState(pos.below()).is(Blocks.END_ROD)) {
            Mailboxes.form(level, player, pos);
        }
    }
}
