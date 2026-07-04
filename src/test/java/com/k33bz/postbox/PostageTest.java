package com.k33bz.postbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic postage math — no game runtime required. */
class PostageTest {

    @Test
    void handDeliveryIsFree() {
        assertEquals(0, Postage.letterCost(true, true, 5000, 99999, 1, 150, 512, 3));
        assertEquals(0L, Postage.deliveryDelayMs(true, true, 99999, 1.0, 10, 600, 0));
    }

    @Test
    void baseCostShortLetterSameSpot() {
        // 100 chars, 0 blocks: 1 base + 1 char-emerald + 0 distance
        assertEquals(2, Postage.letterCost(false, true, 100, 0, 1, 150, 512, 3));
    }

    @Test
    void distanceAndCharsScale() {
        // 300 chars => 2, 1024 blocks => 2, base 1 => 5
        assertEquals(5, Postage.letterCost(false, true, 300, 1024, 1, 150, 512, 3));
    }

    @Test
    void posteRestanteReplacesDistance() {
        // no box: base 1 + 1 char + 3 surcharge
        assertEquals(5, Postage.letterCost(false, false, 10, 0, 1, 150, 512, 3));
    }

    @Test
    void paymentPrefersSinglesThenBlocks() {
        assertArrayEquals(new int[]{4, 0, 4}, Postage.paymentPlan(4, 10, 2));
        // 4 singles + rest from one block: pays 4 + 9 = 13 for 10 (overpay 3)
        assertArrayEquals(new int[]{4, 1, 13}, Postage.paymentPlan(10, 4, 2));
        assertArrayEquals(new int[]{0, 2, 18}, Postage.paymentPlan(17, 0, 2));
    }

    @Test
    void insufficientFundsIsNull() {
        assertNull(Postage.paymentPlan(10, 4, 0));
        assertNull(Postage.paymentPlan(19, 0, 2));
    }

    @Test
    void overpayHalvesDelay() {
        long base = Postage.deliveryDelayMs(false, true, 2000, 1.0, 10, 600, 0);
        assertEquals(20_000L, base); // 2000 blocks at 1s/100
        assertEquals(10_000L, Postage.deliveryDelayMs(false, true, 2000, 1.0, 10, 600, 1));
        assertEquals(5_000L, Postage.deliveryDelayMs(false, true, 2000, 1.0, 10, 600, 2));
    }

    @Test
    void minimumDelayApplies() {
        assertEquals(10_000L, Postage.deliveryDelayMs(false, true, 50, 1.0, 10, 600, 0));
    }

    @Test
    void posteRestanteIsSlow() {
        assertEquals(600_000L, Postage.deliveryDelayMs(false, false, 0, 1.0, 10, 600, 0));
        // ... unless someone overpays hard
        assertTrue(Postage.deliveryDelayMs(false, false, 0, 1.0, 10, 600, 4) < 40_000L);
    }
}
