/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_B_emu {
    public SWTPc_MP_B_emu(boolean DC_1_mod) {
        this.ss50Bus = new SS50Bus();
        this.ss30BusCommon = new SS30Bus(DC_1_mod);

        this.ss50Slots = new SS50Card[7];
        this.ss30Slots = new SS30Card[8];
    }

    private final SS50Bus ss50Bus;
    private final SS30Bus ss30BusCommon;

    private final SS50Card ss50Slots[];
    private final SS30Card ss30Slots[];

    public void installSS50Card(SS50Card card, int slot) {
        if (card == null) {
            throw new IllegalArgumentException("Installing null SS50 card");
        }
        if (slot < 0 || slot >= ss50Slots.length) {
            throw new IllegalArgumentException("SS50 slots are numbered 0 -> 6");
        }
        if (ss50Slots[slot] != null) {
            throw new IllegalArgumentException("SS50 slot " + slot + " already filled");
        }
        if (card instanceof SWTPc_MP_A_emu) {
            if (slot != 1) {
                throw new IllegalArgumentException("MP-A must be installed in slot 1");
            }
        } else if (slot == 1) {
            throw new IllegalArgumentException("Only MP-A cards may be installed in slot 1");
        }
        ss50Slots[slot] = card;
        card.connect(ss50Bus);
    }

    public void installSS30Card(SS30Card card, int slot) {
        if (card == null) {
            throw new IllegalArgumentException("Installing null SS50 card");
        }
        if (slot < 0 || slot >= ss30Slots.length) {
            throw new IllegalArgumentException("SS30 slots are numbered 0 -> 7");
        }
        if (ss30Slots[slot] != null) {
            throw new IllegalArgumentException("SS30 slot " + slot + " already filled");
        }
        ss30Slots[slot] = card;
        card.connect(ss30BusCommon.getSlotView(slot));
    }

    public void pushState(long newState) {
        ss50Bus.pushLines(newState);
        ss30BusCommon.pushLines(ss50Bus);
        // transfer data from ss30BusCommon if an SS30 card wrote to it
    }
}
