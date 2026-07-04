package com.k33bz.postbox;

/**
 * Pure postage math — no game types, so it unit-tests without a runtime.
 *
 * <p>Cost (emeralds) = base + ceil(chars / charsPerEmerald) + ceil(distance / blocksPerEmerald),
 * or base + chars-part + posteRestanteSurcharge when the recipient has no mailbox. Hand delivery
 * (dropping mail into the recipient's own box) is free and instant.</p>
 *
 * <p>No change is given: payment uses loose emeralds first, then emerald blocks (worth 9). Every
 * overpaid emerald HALVES the remaining delivery delay — a block split that overpays counts as
 * express, and so does the intentional "extra postage" slider.</p>
 */
public final class Postage {
    private Postage() {
    }

    public static final int EMERALDS_PER_BLOCK = 9;

    public static int ceilDiv(int a, int b) {
        return b <= 0 ? 0 : (a + b - 1) / b;
    }

    /** Emerald cost of a letter, before any intentional extra postage. */
    public static int letterCost(boolean handDelivery, boolean recipientHasBox, int chars,
                                 double distance, int base, int charsPerEmerald,
                                 int blocksPerEmerald, int posteRestanteSurcharge) {
        if (handDelivery) {
            return 0;
        }
        int cost = base + ceilDiv(Math.max(0, chars), charsPerEmerald);
        if (recipientHasBox) {
            cost += (int) Math.ceil(Math.max(0.0, distance) / Math.max(1, blocksPerEmerald));
        } else {
            cost += posteRestanteSurcharge;
        }
        return cost;
    }

    /**
     * Greedy payment plan: loose emeralds first, then blocks (9 each). Returns
     * {@code {useSingles, useBlocks, paid}} or {@code null} if the player can't cover it.
     * paid may exceed needed (block split) — no change, the overpay buys speed.
     */
    public static int[] paymentPlan(int needed, int singles, int blocks) {
        if (needed <= 0) {
            return new int[]{0, 0, 0};
        }
        int useSingles = Math.min(singles, needed);
        int remaining = needed - useSingles;
        int useBlocks = ceilDiv(remaining, EMERALDS_PER_BLOCK);
        if (useBlocks > blocks) {
            return null; // insufficient funds
        }
        return new int[]{useSingles, useBlocks, useSingles + useBlocks * EMERALDS_PER_BLOCK};
    }

    /**
     * Delivery delay in milliseconds. Base = distance-scaled (min floor), or the fixed slow
     * poste-restante delay when the recipient has no box; hand delivery is instant. Every
     * overpaid emerald halves what remains.
     */
    public static long deliveryDelayMs(boolean handDelivery, boolean recipientHasBox, double distance,
                                       double secondsPer100Blocks, int minDeliverySeconds,
                                       int posteRestanteDeliverySeconds, int overpaidEmeralds) {
        if (handDelivery) {
            return 0L;
        }
        double seconds = recipientHasBox
                ? Math.max(minDeliverySeconds, distance / 100.0 * secondsPer100Blocks)
                : posteRestanteDeliverySeconds;
        double halved = seconds / Math.pow(2.0, Math.max(0, Math.min(62, overpaidEmeralds)));
        return (long) (halved * 1000.0);
    }
}
