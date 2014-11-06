/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

@SuppressWarnings("PointlessBitwiseExpression")
public class SWTPc_MP_S extends SS30Card implements RS232Device {
    public static final int STATUS_RDRF = (1 << 0);  // Set implies data ready to be read
    public static final int STATUS_TDRE = (1 << 1);  // Set implies data can be written
    public static final int STATUS_DCD  = (1 << 2);  // Set implies modem carrier was dropped
    public static final int STATUS_CTS  = (1 << 3);  // Set implies modem is busy, do not send
    public static final int STATUS_FE   = (1 << 4);  // Set implies bad data was received
    public static final int STATUS_ROV  = (1 << 5);  // Set implies excess received data was lost
    public static final int STATUS_PE   = (1 << 6);  // Set implies bad parity on received data
    public static final int STATUS_IRQ  = (1 << 7);  // Set implies IRQ line is signalling

    public static final int CONTROL_CDIV = (3 << 0);  // Counter divide (baud rate?)
    public static final int CONTROL_WSEL = (7 << 2);  // # start and stop and parity bits
    public static final int CONTROL_TXC  = (3 << 5);  // Transmission interrupts
    public static final int CONTROL_RIE  = (1 << 7);  // Receiver interupt enable

    private RS232Device terminal;

    private byte receiverdata;
    private boolean receiving;
    private boolean dataready;
    private boolean dataoverrun;

    public synchronized void raiseRESET() {
        dataready = dataoverrun = receiving = false;
        notifyAll();
    }

    int throttle;

    @SuppressWarnings("CallToThreadYield")
    public void pause() {
        try {
            if (++throttle > 10) {
                throttle = 0;
                Thread.sleep(1);
            } else {
                Thread.yield();
            }
        } catch (InterruptedException e) {
        }
    }

    @Override
    public synchronized byte load(boolean RS0high, boolean RS1high) {
        byte ret = 0;
        if (RS0high) {
            // reading received data
            ret = receiverdata;
            dataready = false;
            dataoverrun = false;
            receiving = false;
            notifyAll();
        } else {
            // reading status
            if (terminal == null) {
                ret |= STATUS_DCD;
            } else {
                if (dataready) {
                    ret |= STATUS_RDRF;
                    throttle = 0;
                } else {
                    pause();
                }
                if (dataoverrun) {
                    ret |= STATUS_ROV;
                }
                ret |= STATUS_TDRE | STATUS_IRQ;
            }
        }
        return ret;
    }

    @Override
    public synchronized void store(boolean RS0high, boolean RS1high, byte data) {
        if (RS0high) {
            // writing transmitted data
            terminal.waitForCTS();
            terminal.sendTo(data);
        } else {
            // writing control register
            if ((data & 3) == 3) {
                // Master reset
                receiving = true;
                dataready = dataoverrun = false;
                notifyAll();
            } else {
                // SWTBug does this right before checking for a character
                receiving = (data == 0x15);
                notifyAll();
            }
        }
    }

    @Override
    public void connectTo(RS232Device otherdevice) {
        this.terminal = otherdevice;
    }

    @Override
    public synchronized void sendTo(byte data) {
        dataoverrun = dataready;
        receiverdata = data;
        dataready = true;
    }

    @Override
    public synchronized void waitForCTS() {
        while (dataready || !receiving) {
            try {
                wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
