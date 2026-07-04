package com.k33bz.postbox;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.UUID;

/**
 * The Rainbow Mailbox — a player head wearing the mailbox skin (minecraft-heads.com id 39194).
 * Placing it atop an end rod raises a mailbox. Identification is by the head's profile name,
 * which survives the item → placed-skull → drop round-trip natively (no custom block, no
 * client mod) — the same trick as sanctuary's SanctuaryCrystal.
 */
public final class MailboxHead {
    private MailboxHead() {
    }

    /** Profile name marking a mailbox head. Display names can be anvil-forged; this can't. */
    public static final String PROFILE_NAME = "RainbowMailbox";

    /** Fixed profile UUID so stacked mailbox heads stay stackable. */
    private static final UUID PROFILE_UUID = UUID.fromString("7b3e5f42-1c8d-4a6b-8f2e-c0ffee039194");

    /** minecraft-heads.com "Rainbow Mailbox" (head id 39194). */
    public static final String TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDIzZjY1MmNkNTNhNjIxZjY4ODAxZmFhN2FkNzIyNjFlNjBlY2I0N2IyNTUxN2ZjMTdlODg0YzI0YjhlNzlmOSJ9fX0=";

    /** Build one Rainbow Mailbox head item. */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        com.mojang.authlib.properties.PropertyMap properties = new com.mojang.authlib.properties.PropertyMap(
                com.google.common.collect.ImmutableMultimap.of("textures",
                        new Property("textures", TEXTURE)));
        GameProfile profile = new GameProfile(PROFILE_UUID, PROFILE_NAME, properties);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Rainbow Mailbox")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withItalic(false).withBold(true)));
        stack.set(DataComponents.RARITY, Rarity.RARE);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Place it atop an end rod to raise a mailbox.")
                        .withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    /** Whether this profile is a mailbox head (item components and placed skull entities). */
    public static boolean isMailbox(ResolvableProfile profile) {
        return profile != null && profile.name().map(PROFILE_NAME::equals).orElse(false);
    }

    /** Whether this item stack is a mailbox head. */
    public static boolean isMailbox(ItemStack stack) {
        return stack.is(Items.PLAYER_HEAD) && isMailbox(stack.get(DataComponents.PROFILE));
    }
}
