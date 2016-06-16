/*
 * Copyright 2014, 2016, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

public class SWTPc6800 extends MicroComputer8x16
    implements SS50Bus, SS30Bus
{
    public static SWTPc6800 makeStandardSystem() {
        // REMIND: It would be nice to simulate an MP_A with MIKBUG and an MP_C...
        SWTPc6800 theComputer = new SWTPc6800();
        theComputer.installcard(new SWTPc_MP_A(), 1);
        theComputer.installcard(new SWTPc_MP_M((char) 0x0000, 4), 2);
        theComputer.installcard(new SWTPc_MP_M((char) 0x1000, 4), 3);
        theComputer.installcard(new SWTPc_MP_M((char) 0x2000, 4), 4);
        theComputer.installcard(new SWTPc_MP_S(), 1);
        return theComputer;
    }

    private final SS50Card mainslots[];
    private final SS30Card ioslots[];

    private boolean poweron;

    public SWTPc6800() {
        mainslots = new SS50Card[7];
        ioslots = new SS30Card[8];
    }

    public void installcard(SS50Card c, int slot) {
        if (poweron) {
            throw new IllegalStateException("Cannot install cards while powered on!");
        }
        if (mainslots[slot] != null) {
            throw new IllegalStateException("Slot already has a card installed!");
        }
        c.connectTo(this);
        mainslots[slot] = c;
    }

    public void installcard(SS30Card c, int slot) {
        if (poweron) {
            throw new IllegalStateException("Cannot install cards while powered on!");
        }
        if (ioslots[slot] != null) {
            throw new IllegalStateException("Slot already has a card installed!");
        }
        c.connectTo(this);
        ioslots[slot] = c;
    }

    private final static int POWER_OFF         = 0;
    private final static int POWER_ON          = 1;
    private final static int IRQ_RAISE         = 2;
    private final static int IRQ_LOWER         = 3;
    private final static int NMI_TRIP          = 4;
    private final static int RESET_RAISE       = 5;
    private final static int RESET_LOWER       = 6;
    private final static int RESETBUTTON_RAISE = 7;
    private final static int RESETBUTTON_LOWER = 8;

    private void signalOne(SS50Card card, int signal) {
        switch (signal) {
            case POWER_OFF:
                card.powerOff();
                break;
            case POWER_ON:
                card.powerOn();
                break;
            case IRQ_RAISE:
                card.raiseIRQ();
                break;
            case IRQ_LOWER:
                card.lowerIRQ();
                break;
            case NMI_TRIP:
                card.tripNMI();
                break;
            case RESET_RAISE:
                card.raiseRESET();
                break;
            case RESET_LOWER:
                card.lowerRESET();
                break;
            case RESETBUTTON_RAISE:
                card.raiseManualReset();
                break;
            case RESETBUTTON_LOWER:
                card.lowerManualReset();
                break;
            default:
                throw new InternalError("Unrecognized command for SS50 card: " + signal);
        }
    }

    private void signalOne(SS30Card card, int signal) {
        switch (signal) {
            case POWER_OFF:
                card.powerOff();
                break;
            case POWER_ON:
                card.powerOn();
                break;
            case RESET_RAISE:
                card.tripRESET();
                break;
            case IRQ_RAISE:
            case IRQ_LOWER:
            case NMI_TRIP:
            case RESET_LOWER:
            case RESETBUTTON_RAISE:
            case RESETBUTTON_LOWER:
                // These signals are specific to the SS50 bus
                break;
            default:
                throw new InternalError("Unrecognized command for SS30 card: " + signal);
        }
    }

    private void signalAll(int signal) {
        for (SS50Card c : mainslots) {
            if (c != null) {
                signalOne(c, signal);
            }
        }
        for (SS30Card c : ioslots) {
            if (c != null) {
                signalOne(c, signal);
            }
        }
    }

    @Override
    public void powerOn() {
        poweron = true;
        signalAll(POWER_ON);
    }

    @Override
    public void powerOff() {
        signalAll(POWER_OFF);
        poweron = false;
    }

    @Override
    public boolean isPoweredOn() {
        return poweron;
    }

    @Override
    public void raiseIRQ() {
        signalAll(IRQ_RAISE);
    }

    @Override
    public void lowerIRQ() {
        signalAll(IRQ_LOWER);
    }

    @Override
    public void tripNMI() {
        signalAll(NMI_TRIP);
    }

    @Override
    public void raiseRESET() {
        signalAll(RESET_RAISE);
    }

    @Override
    public void lowerRESET() {
        signalAll(RESET_LOWER);
    }

    @Override
    public void tripManualReset() {
        signalAll(RESETBUTTON_RAISE);
        signalAll(RESETBUTTON_LOWER);
    }

    @Override
    public byte load(char addr) {
        byte data = (byte) 0xFF;
        for (SS50Card c : mainslots) {
            if (c != null && c.maps(addr)) {
                data &= c.load(addr);
            }
        }
        if (addr >= 0x8000 && addr < 0x8020) {
            int ioslot = (addr - 0x8000) / 4;
            SS30Card c = ioslots[ioslot];
            if (c != null) {
                data &= c.load((addr & 1) != 0,
                               (addr & 2) != 0);
            }
        }
        return data;
    }

    @Override
    public void store(char addr, byte data) {
        for (SS50Card c : mainslots) {
            if (c != null && c.maps(addr)) {
                c.store(addr, data);
            }
        }
        if (addr >= 0x8000 && addr < 0x8020) {
            int ioslot = (addr - 0x8000) / 4;
            SS30Card c = ioslots[ioslot];
            if (c != null) {
                c.store((addr & 1) != 0,
                        (addr & 2) != 0,
                        data);
            }
        }
    }

    public static void main(String argv[]) {
        SWTPc6800 myMachine = makeStandardSystem();
        SWTPc_CT_64 myTerminal = new SWTPc_CT_64();
        SWTPc_AC_30 myCassette = new SWTPc_AC_30();
        SWTPc_GT_6144 myGraphics = new SWTPc_GT_6144();
        SWTPc_MP_S mySerialPort = ((SWTPc_MP_S) myMachine.ioslots[1]);
        SWTPc_MP_L myParallelPort = new SWTPc_MP_L();
        myMachine.installcard(myParallelPort, 3);
        myParallelPort.connectSideA(myGraphics);
        myTerminal.connectGraphics(myGraphics);
        myCassette.connectToComputer(mySerialPort);
        myCassette.connectToTerminal(myTerminal);
        myTerminal.connectCassetteControl(myCassette);
        myTerminal.addResetFor(myMachine);
        if (Motorola6800.STATS) {
            myTerminal.addStatsButtonFor(((SWTPc_MP_A) myMachine.mainslots[1]).processor);
        }
        myCassette.powerOn();
        myTerminal.powerOn();
        myMachine.powerOn();
        myMachine.tripManualReset();
    }
}
