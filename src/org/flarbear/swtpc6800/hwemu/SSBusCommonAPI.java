/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public interface SSBusCommonAPI {
    public static final int SS_CTRL_READ_LINE      = 0b000000000001;
    public static final int SS_CTRL_RESET_LINE     = 0b000000000010;
    public static final int SS_CTRL_IRQ_LINE       = 0b000000000100;
    public static final int SS_CTRL_NMI_LINE       = 0b000000001000;
    public static final int SS_CTRL_MASK           = 0b000000001111;

    public static final int SS50_CTRL_M_RESET_LINE = 0b000000010000;
    public static final int SS50_CTRL_VMA_LINE     = 0b000000100000;
    public static final int SS50_CTRL_HALT_LINE    = 0b000001000000;
    public static final int SS50_CTRL_MASK         = 0b000001110000 | SS_CTRL_MASK;

    public static final int SS30_SELECT_LINE       = 0b000010000000;
    public static final int SS30_RS0_LINE          = 0b000100000000;
    public static final int SS30_RS1_LINE          = 0b001000000000;
    public static final int SS30_UD3_LINE          = 0b010000000000;
    public static final int SS30_UD4_LINE          = 0b100000000000;
    public static final int SS30_CTRL_MASK         = 0b111110000000 | SS_CTRL_MASK;

    public Byte getDataLines();

    public void setDataLines(Byte new_data);

    public int getControlLines();
    public void setControlLines(int lines);

    public ClockSignal.Source phi2();

    public ClockSignal.Source baud110();
    public ClockSignal.Source baud150();
    public ClockSignal.Source baud300();
    public ClockSignal.Source baud600();
    public ClockSignal.Source baud1200();
}
