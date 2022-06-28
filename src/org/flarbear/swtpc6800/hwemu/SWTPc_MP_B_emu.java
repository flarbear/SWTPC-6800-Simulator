/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_B_emu {
    public SWTPc_MP_B_emu() {
        this.ss50State = new SS50BusState();
        this.ss30BaseState = new SSBusCommonState();
        this.ss50Slots = new SS50Card[7];
        this.ss30Slots = new SS30Card[8];
        this.ss30SlotStates = new SS30SlotState[8];
        this.phi1 = new ClockSignal.TransferImpl();
        this.phi2 = new ClockSignal.TransferImpl();
        this.baud110 = new ClockSignal.TransferImpl();
        this.baud150 = new ClockSignal.TransferImpl();
        this.baud300 = new ClockSignal.TransferImpl();
        this.baud600 = new ClockSignal.TransferImpl();
        this.baud1200 = new ClockSignal.TransferImpl();
    }

    private final SS50BusState ss50State;
    private final SSBusCommonState ss30BaseState;

    private final SS50Card ss50Slots[];
    private final SS30Card ss30Slots[];
    private final SS30SlotState ss30SlotStates[];

    private final ClockSignal.Transfer phi1;
    private final ClockSignal.Transfer phi2;

    private final ClockSignal.Transfer baud110;
    private final ClockSignal.Transfer baud150;
    private final ClockSignal.Transfer baud300;
    private final ClockSignal.Transfer baud600;
    private final ClockSignal.Transfer baud1200;

    private boolean DC_1_mod;

    public SS50BusState getSS50State() { return ss50State; }

    public ClockSignal.Listener baud110Line() { return baud110; }
    public ClockSignal.Listener baud150Line() { return baud150; }
    public ClockSignal.Listener baud300Line() { return baud300; }
    public ClockSignal.Listener baud600Line() { return baud600; }
    public ClockSignal.Listener baud1200Line() { return baud1200; }

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
        card.connect(this);
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
        ss30SlotStates[slot] = new SS30SlotState(ss30BaseState);
    }

    public void phi1Listener(boolean isRising) {
        phi1.clockStateChanged(isRising);
    }

    public void phi2Listener(boolean isRising) {
        int controlLines = ss50State.getControlLines() & SS30SlotState.SS30_CTRL_MASK;
        Character addressLines = ss50State.getAddressLines();
        boolean validAddress = (addressLines != null && (addressLines >> 12) == 8);
        int selected = -1;
        if (validAddress) {
            if ((addressLines & 1) != 0) {
                controlLines |= SS30SlotState.SS30_RS0_LINE;
            }
            if ((addressLines & 2) != 0) {
                controlLines |= SS30SlotState.SS30_RS1_LINE;
            }
            selected = (addressLines >> 2) & 0x7;
        }
        for (int i = 0; i < 8; i++) {
            if (ss30SlotStates[i] != null) {
                ss30SlotStates[i].select(i == selected);
            }
        }
        if (DC_1_mod && selected == 5) {
            controlLines |= SS30SlotState.SS30_UD3_LINE;
        }
        ss30BaseState.setControlLines(controlLines);
        if (validAddress) {
            if (isRising) {
                if (ss30BaseState.isWrite()) {
                    ss30BaseState.setDataLines(ss50State.getDataLines());
                }
            } else if (ss30BaseState.isRead()) {
                Byte lines = ss30BaseState.getDataLines();
                if (lines != null) {
                    ss50State.setDataLines(lines);
                }
            }
        }
        phi2.clockStateChanged(isRising);
    }
}
