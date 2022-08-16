/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class SWTPc_RS232_cable_emu {
    SignalState.LineState dataToTerminal;
    SignalState.LineState dataToComputer;

    SignalState.LineState RTS;

    SignalState.Trigger clockToTerminal;
    SignalState.Trigger clockToComputer;
}
