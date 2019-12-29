/*
 * Copyright 2014, 2016, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

@SuppressWarnings("PointlessBitwiseExpression")
public class SWTPc_MP_L extends SS30Card {
    public static final int CONTROL_C1_BIT0 = (1 << 0);
    public static final int CONTROL_C1_BIT1 = (1 << 1);
    public static final int CONTROL_DDR     = (1 << 2);
    public static final int CONTROL_C2_BIT3 = (1 << 3);
    public static final int CONTROL_C2_BIT4 = (1 << 4);
    public static final int CONTROL_C2_BIT5 = (1 << 5);
    public static final int CONTROL_IRQ2    = (1 << 6);
    public static final int CONTROL_IRQ1    = (1 << 7);
    public static final int CONTROL_C1_MASK = (CONTROL_C1_BIT0 |
                                               CONTROL_C1_BIT1);
    public static final int CONTROL_C2_MASK = (CONTROL_C2_BIT3 |
                                               CONTROL_C2_BIT4 |
                                               CONTROL_C2_BIT5);
    public static final int CONTROL_C2_HSHK = CONTROL_C2_BIT5;
    public static final int CONTROL_C2_FLIP = (CONTROL_C2_BIT5 | CONTROL_C2_BIT3);
    public static final int CONTROL_C2_HOLD_MASK = (CONTROL_C2_BIT5 | CONTROL_C2_BIT4);

    private static final int MPU_WRITABLE_CONTROL_REGISTERS =
        CONTROL_C1_MASK | CONTROL_DDR | CONTROL_C2_MASK;

    class PIASide implements PIADevice {
        PIADevice peripheral;
        byte DDR;
        byte PDR;
        byte CR;
        boolean prevC1;
        boolean prevC2;
        boolean outC2;

        private void sendTransition() {
            if (peripheral != null) {
                peripheral.transition(PDR, false, outC2);
            }
        }

        @Override
        public void transition(byte data, boolean c1, boolean c2) {
            PDR = (byte) ((PDR & DDR) | (data & ~DDR));
            if (prevC1 != c1) {
                if ((CR & CONTROL_C1_BIT0) != 0) {
                    boolean triggerC1 = ((CR & CONTROL_C1_BIT1) != 0);
                    if (c1 == triggerC1) {
                        raiseIRQ();
                    }
                }
                CR |= CONTROL_IRQ1;
            }
            if ((CR & CONTROL_C2_BIT5) == 0) {
                if (prevC2 != c2) {
                    if ((CR & CONTROL_C2_BIT3) != 0) {
                        boolean triggerC2 = ((CR & CONTROL_C2_BIT4) != 0);
                        if (c2 == triggerC2) {
                            raiseIRQ();
                        }
                    }
                    CR |= CONTROL_IRQ2;
                }
            } else {
                if (prevC1 != c1) {
                    outC2 = true;
                    sendTransition();
                }
            }
            prevC1 = c1;
            prevC2 = c2;
        }

        public void setCR(byte data) {
            CR = (byte) (data & MPU_WRITABLE_CONTROL_REGISTERS);
            if ((CR & CONTROL_C2_HOLD_MASK) == CONTROL_C2_HOLD_MASK) {
                boolean holdC2 = ((CR & CONTROL_C2_BIT3) != 0);
                if (outC2 != holdC2) {
                    outC2 = holdC2;
                    sendTransition();
                }
            }
        }

        public byte getPDR() {
            CR &= ~CONTROL_IRQ1;
            byte ret = PDR;
            if ((CR & CONTROL_C2_MASK) == CONTROL_C2_HSHK) {
                if (outC2) {
                    outC2 = false;
                    sendTransition();
                }
            } else if ((CR & CONTROL_C2_MASK) == CONTROL_C2_FLIP) {
                outC2 = true;
                sendTransition();
                outC2 = false;
                sendTransition();
            }
            return ret;
        }

        public void setPDR(byte data) {
            PDR = (byte) ((PDR & ~DDR) | (data & DDR));
            sendTransition();
        }

        void reset() {
            DDR = PDR = CR = 0;
        }
    }

    private final PIASide Aside;
    private final PIASide Bside;

    public SWTPc_MP_L() {
        Aside = new PIASide();
        Bside = new PIASide();
    }

    @Override
    public void tripRESET() {
        Aside.reset();
        Bside.reset();
    }

    public void raiseIRQ() {
        // REMIND...
    }

    public void connectSideA(PIADevice peripheral) {
        if (Aside.peripheral != null) {
            throw new IllegalStateException("Peripheral already installed on side A!");
        }
        Aside.peripheral = peripheral;
    }

    public void connectSideB(PIADevice peripheral) {
        if (Bside.peripheral != null) {
            throw new IllegalStateException("Peripheral already installed on side B!");
        }
        Bside.peripheral = peripheral;
    }

    @Override
    public byte load(boolean RS0high, boolean RS1high) {
        PIASide pia = RS1high ? Bside : Aside;
        if (RS0high) {
            return pia.CR;
        } else {
            if ((pia.CR & CONTROL_DDR) == 0) {
                return pia.DDR;
            } else {
                return pia.getPDR();
            }
        }
    }

    @Override
    public void store(boolean RS0high, boolean RS1high, byte data) {
        PIASide pia = RS1high ? Bside : Aside;
        if (RS0high) {
            pia.setCR(data);
        } else {
            if ((pia.CR & CONTROL_DDR) == 0) {
                pia.DDR = data;
            } else {
                pia.setPDR(data);
            }
        }
    }
}
