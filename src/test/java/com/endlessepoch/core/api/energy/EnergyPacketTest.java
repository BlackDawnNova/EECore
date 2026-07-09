package com.endlessepoch.core.api.energy;

import com.endlessepoch.core.api.tier.VoltageTier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EnergyPacket step-down, split, merge, and power calculations.
 * EnergyPacket 步降、分割、合并、功率计算单元测试。
 */
class EnergyPacketTest {

    @AfterEach
    void resetLoss() {
        EnergyPacket.setStepLoss(0.8);
    }

    // ── Step-down / 步降 ──

    @Test
    void stepDownTo_oneStep() {
        // MV @ 1A → LV. V ratio = MV_min / LV_min, amperage scales up.
        // Energy after 1 step with loss 0.8: 100 * 0.8 = 80 Ω
        EnergyPacket mv = new EnergyPacket(VoltageTier.MV, 1, 100);
        EnergyPacket lv = mv.stepDownTo(VoltageTier.LV);

        BigInteger expectedAmp = VoltageTier.MV.getMinVoltage().divide(VoltageTier.LV.getMinVoltage());
        assertSame(VoltageTier.LV, lv.getTier());
        assertEquals(expectedAmp, lv.getAmperage());
        // 100 * 80/100 = 80
        assertEquals(OmegaValue.of(80), lv.getEnergy());
    }

    @Test
    void stepDownTo_multiStep() {
        // HV @ 1A → LV (2-step drop). V ratio = HV_min / LV_min.
        // Energy after 2 steps: 200 * 0.8 * 0.8 = 128
        EnergyPacket hv = new EnergyPacket(VoltageTier.HV, 1, 200);
        EnergyPacket lv = hv.stepDownTo(VoltageTier.LV);

        BigInteger expectedAmp = VoltageTier.HV.getMinVoltage().divide(VoltageTier.LV.getMinVoltage());
        assertSame(VoltageTier.LV, lv.getTier());
        assertEquals(expectedAmp, lv.getAmperage());
        assertEquals(OmegaValue.of(128), lv.getEnergy());
    }

    @Test
    void stepDownTo_sameTier_noOp() {
        EnergyPacket lv = new EnergyPacket(VoltageTier.LV, 2, 50);
        EnergyPacket result = lv.stepDownTo(VoltageTier.LV);
        assertSame(lv, result);
    }

    @Test
    void stepDownTo_higherTier_noOp() {
        // Can't step "down" to a higher tier
        EnergyPacket lv = new EnergyPacket(VoltageTier.LV, 2, 50);
        EnergyPacket result = lv.stepDownTo(VoltageTier.MV);
        assertSame(lv, result);
    }

    @Test
    void stepDownTo_zeroLoss() {
        EnergyPacket.setStepLoss(0.0);
        EnergyPacket mv = new EnergyPacket(VoltageTier.MV, 1, 100);
        EnergyPacket lv = mv.stepDownTo(VoltageTier.LV);

        assertEquals(OmegaValue.zero(), lv.getEnergy());
    }

    @Test
    void stepDownTo_noLoss() {
        EnergyPacket.setStepLoss(1.0);
        EnergyPacket mv = new EnergyPacket(VoltageTier.MV, 1, 100);
        EnergyPacket lv = mv.stepDownTo(VoltageTier.LV);

        assertEquals(OmegaValue.of(100), lv.getEnergy());
    }

    // ── Split / 分割 ──

    @Test
    void split_evenCount() {
        EnergyPacket pkt = new EnergyPacket(VoltageTier.LV, 1, 100);
        List<EnergyPacket> parts = pkt.split(4);

        assertEquals(4, parts.size());
        for (int i = 0; i < 3; i++) {
            assertSame(VoltageTier.LV, parts.get(i).getTier());
            assertEquals(OmegaValue.of(25), parts.get(i).getEnergy());
        }
        // Last part gets 25 (no remainder since 100/4 is exact)
        assertEquals(OmegaValue.of(25), parts.get(3).getEnergy());
    }

    @Test
    void split_remainder() {
        EnergyPacket pkt = new EnergyPacket(VoltageTier.LV, 1, 100);
        List<EnergyPacket> parts = pkt.split(3);

        assertEquals(3, parts.size());
        // 100/3 = 33 each, remainder 1 goes to last
        assertEquals(OmegaValue.of(33), parts.get(0).getEnergy());
        assertEquals(OmegaValue.of(33), parts.get(1).getEnergy());
        assertEquals(OmegaValue.of(34), parts.get(2).getEnergy()); // 33 + 1 remainder
    }

    @Test
    void split_single() {
        EnergyPacket pkt = new EnergyPacket(VoltageTier.LV, 2, 50);
        List<EnergyPacket> parts = pkt.split(1);
        assertEquals(1, parts.size());
        assertSame(pkt, parts.get(0));
    }

    // ── Merge / 合并 ──

    @Test
    void merge_sameTier() {
        EnergyPacket a = new EnergyPacket(VoltageTier.LV, 1, 30);
        EnergyPacket b = new EnergyPacket(VoltageTier.LV, 1, 70);
        EnergyPacket merged = EnergyPacket.merge(List.of(a, b));

        assertSame(VoltageTier.LV, merged.getTier());
        assertEquals(OmegaValue.of(100), merged.getEnergy());
    }

    @Test
    void merge_emptyList() {
        EnergyPacket merged = EnergyPacket.merge(List.of());
        assertSame(VoltageTier.ELV, merged.getTier());
        assertTrue(merged.isEmpty());
    }

    @Test
    void merge_tierMismatch_throws() {
        EnergyPacket lv = new EnergyPacket(VoltageTier.LV, 1, 30);
        EnergyPacket mv = new EnergyPacket(VoltageTier.MV, 1, 30);
        assertThrows(IllegalArgumentException.class, () -> EnergyPacket.merge(List.of(lv, mv)));
    }

    // ── Power calculation / 功率计算 ──

    @Test
    void powerPerTick_perSecond() {
        // Use actual API values: P = V_min × I
        EnergyPacket pkt = new EnergyPacket(VoltageTier.LV, 2, 100);
        BigInteger expectedPower = VoltageTier.LV.getMinVoltage().multiply(BigInteger.valueOf(2));
        assertEquals(expectedPower, pkt.getPowerPerTick());
        assertEquals(expectedPower.multiply(BigInteger.valueOf(20)), pkt.getPowerPerSecond());
    }

    // ── FE conversion / FE 转换 ──

    @Test
    void feConversion_bigInteger() {
        // 1 Ω = 2 FE, 50 Ω → 100 FE
        EnergyPacket pkt = new EnergyPacket(VoltageTier.LV, 1, 50);
        assertEquals(BigInteger.valueOf(100), pkt.getFEBigInteger());
    }

    // ── canBeReceivedBy / 可接收判定 ──

    @Test
    void canBeReceivedBy() {
        EnergyPacket lv = new EnergyPacket(VoltageTier.LV, 1, 10);
        assertTrue(lv.canBeReceivedBy(VoltageTier.LV));
        assertTrue(lv.canBeReceivedBy(VoltageTier.MV));
        assertFalse(lv.canBeReceivedBy(VoltageTier.ELV));
    }
}
