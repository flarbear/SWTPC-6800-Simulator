package org.flarbear.swtpc6800;

/**
 * The general external interfaces of a standard MicroComputer which sports a
 * power switch and a reset button.
 */
public abstract class MicroComputer8x16 {
    public abstract void powerOn();
    public abstract void powerOff();
    public abstract void tripManualReset();
    public abstract boolean isPoweredOn();
}
