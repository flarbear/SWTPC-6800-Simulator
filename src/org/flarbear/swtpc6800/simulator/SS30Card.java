/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.simulator;

/**
 * An IO card for a SWTPc 6800 system.
 * These boards had an 8-bit data bus and 2 address lines (RS0 and RS1)
 * so they could only support reads and writes to 4 total byte addresses,
 * 8 bits at a time.
 */
public abstract class SS30Card {
    SS30Bus theBus;

    public synchronized void connectTo(SS30Bus bus) {
        if (this.theBus != null) {
            throw new InternalError("Card already attached to a bus");
        }
        this.theBus = bus;
    }

    public void powerOn() {
    }

    public void powerOff() {
    }

    public void tripRESET() {
    }

    public abstract byte load(boolean RS0high, boolean RS1high);

    public abstract void store(boolean RS0high, boolean RS1high, byte data);
}
