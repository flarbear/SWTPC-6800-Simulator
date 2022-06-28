/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class SSBusCommonDelegate implements SSBusCommonAPI {
    protected SSBusCommonState state;
    private final int controlMask;

    protected SSBusCommonDelegate(int controlMask) {
        this.state = new SSBusCommonState();
        this.controlMask = controlMask;
    }

    protected SSBusCommonDelegate(SSBusCommonState state, int controlMask) {
        this.state = state;
        this.controlMask = controlMask;
    }

    @Override public Byte getDataLines() { return state.getDataLines(); }
    @Override public void setDataLines(Byte new_data) { state.setDataLines(new_data); }

    @Override public int getControlLines() { return state.getControlLines() & controlMask; }
    @Override public void setControlLines(int lines) { state.setControlLines(lines); }

    public boolean isRead() { return state.isRead(); }
    public boolean isWrite() { return state.isWrite(); }

    @Override public ClockSignal.Source phi2() { return state.phi2(); }

    @Override public ClockSignal.Source baud110() { return state.baud110(); }
    @Override public ClockSignal.Source baud150() { return state.baud150(); }
    @Override public ClockSignal.Source baud300() { return state.baud300(); }
    @Override public ClockSignal.Source baud600() { return state.baud600(); }
    @Override public ClockSignal.Source baud1200() { return state.baud1200(); }
}
