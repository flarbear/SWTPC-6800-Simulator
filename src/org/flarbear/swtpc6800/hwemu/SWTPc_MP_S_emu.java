/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.Transition;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_S_emu extends SS30Card {
    public SWTPc_MP_S_emu(SS30Bus.Line baudLine) {
        switch (baudLine) {
            case Baud110:
            case Baud150:
            case Baud300:
            case Baud600:
            case Baud1200:
                this.baudLine = baudLine;
                break;
            default:
                throw new IllegalArgumentException("baudLine must be one of the SS30 baud lines");
        }
        this.Acia = new MC6850_emu();
    }

    private final MC6850_emu Acia;
    private final SS30Bus.Line baudLine;

    private SS30Bus busState;
    private SignalState.Listener CO_Listener;

    @Override
    public void connect(SS30Bus busState) {
        if (this.busState != null) {
            throw new IllegalStateException("MP-S already installed on MP-B");
        }
        this.busState = busState;
        busState.Phi2Rising().addListener(this::busClock);
        SignalState.Trigger baudTrigger = switch (baudLine) {
            case Baud110 -> busState.Baud110Trigger();
            case Baud150 -> busState.Baud150Trigger();
            case Baud300 -> busState.Baud300Trigger();
            case Baud600 -> busState.Baud600Trigger();
            case Baud1200 -> busState.Baud1200Trigger();
            default -> throw new IllegalStateException("bad baud line");
        };
        baudTrigger.addListener(this::clockOut);
    }

    private void busClock(Transition transition) {
        Byte data = Acia.enableFired(transition);
        if (data != null) {
            busState.setData(data);
        }
    }

    private void clockOut(Transition transition) {
        if (CO_Listener != null) {
            CO_Listener.signalFired(transition);
        }
    }

    private void clockIn(SignalState.Transition transition) {
        // The MP-S ties both clocks together to the RS232 clock in line.
        Acia.bothClocksFired(transition);
    }
}
