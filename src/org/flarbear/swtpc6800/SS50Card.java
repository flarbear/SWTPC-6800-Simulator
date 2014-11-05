package org.flarbear.swtpc6800;

/**
 * A data/processing card for a SWTPc 6800 sytem.
 * These boards had an 8-bit data bus and a 16-bit address bus so
 * they could support reads and writes to 65K total byte addresses,
 * 8 bits at a time.
 */
public abstract class SS50Card {
    protected SS50Bus theBus;

    public synchronized void connectTo(SS50Bus bus) {
        if (this.theBus != null) {
            throw new InternalError("Card already connected to a bus");
        }
        this.theBus = bus;
    }

    public void powerOn() {
    }

    public void powerOff() {
    }

    public void raiseIRQ() {
    }

    public void lowerIRQ() {
    }

    public void tripNMI() {
    }

    public void raiseRESET() {
    }

    public void lowerRESET() {
    }

    public void raiseManualReset() {
    }

    public void lowerManualReset() {
    }

    public abstract boolean maps(char addr);
    public abstract byte load(char addr);
    public abstract void store(char addr, byte data);
}
