/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 * This class holds the state shared between the SS50 and SS30 buses.
 *
 * See the documentation for the SS50State for the definition of these
 * properties.
 *
 * @author Flar
 */
public class SSBusCommonState implements SSBusCommonAPI {
    private Byte dataLines;
    private int controlLines;

    private ClockSignal.Source phi2;
    private ClockSignal.Source baud110;
    private ClockSignal.Source baud150;
    private ClockSignal.Source baud300;
    private ClockSignal.Source baud600;
    private ClockSignal.Source baud1200;

    @Override
    public Byte getDataLines() {
        return dataLines;
    }

    @Override
    public void setDataLines(Byte new_data) {
        dataLines = new_data;
    }

    @Override
    public int getControlLines() {
        return controlLines;
    }

    @Override
    public void setControlLines(int lines) {
        controlLines = lines;
    }

    public boolean isRead() {
        return ((controlLines & SS_CTRL_READ_LINE) != 0);
    }

    public boolean isWrite() {
        return ((controlLines & SS_CTRL_READ_LINE) == 0);
    }

    @Override public ClockSignal.Source phi2() { return phi2; }

    @Override public ClockSignal.Source baud110() { return baud110; }
    @Override public ClockSignal.Source baud150() { return baud150; }
    @Override public ClockSignal.Source baud300() { return baud300; }
    @Override public ClockSignal.Source baud600() { return baud600; }
    @Override public ClockSignal.Source baud1200() { return baud1200; }
}
