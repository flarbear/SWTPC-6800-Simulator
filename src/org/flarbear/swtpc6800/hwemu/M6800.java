/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import static org.flarbear.swtpc6800.hwemu.M6800Microcode.*;

/**
 *
 * @author Flar
 */
public class M6800 {
    public static final int CC_BIT_C = 0x1;
    public static final int CC_BIT_V = 0x2;
    public static final int CC_BIT_Z = 0x4;
    public static final int CC_BIT_N = 0x8;
    public static final int CC_BIT_I = 0x10;
    public static final int CC_BIT_H = 0x20;

    Character busAddress;
    Byte busData;

    char PC;
    char SP;
    char X;
    byte A;
    byte B;
    byte opCodes;

    byte tempAddrCarry;
    byte tempAddrHi;
    byte tempAddrLo;
    byte tempData0;
    byte tempData1;

    boolean isRead;

    M6800Microcode.CycleTask nextCode;
    M6800Microcode.CycleTask curCode;

    public boolean isRead() { return isRead; }

    public void phi1StateChanged(boolean isRising) {
        if (isRising) {
            M6800Microcode.CycleTask code = nextCode;
            if (code == null) {
                if (!checkRunning()) {
                    curCode = nextCode = null;
                    return;
                }
                code = LOAD_INSTRUCTION;
            }
            nextCode = code.next();
            curCode = code;
            busAddress = null;
            code.processPhi1Tasks(this);
        }
    }

    public void phi2StateChanged(boolean isRising) {
        if (isRising) {
            busData = null;
            if (curCode != null) {
                curCode.processPhi2LeadingTasks(this);
            }
        } else {
            if (curCode != null) {
                curCode.processPhi2TrailingTasks(this);
            }
        }
    }

    boolean checkRunning() {
        return true;
    }

    public Character getAddressLines() {
        return busAddress;
    }

    public Byte getDataLines() {
        return busData;
    }

    public void setDataLines(Byte data) {
        busData = data;
    }

    @FunctionalInterface
    private static interface CCOp {
        public boolean predicate(boolean A, boolean B, boolean R);
    }
    private static void fillTable(byte table[][][], int bit, CCOp op) {
        table[0][0][0] |= op.predicate(false, false, false) ? bit : 0;
        table[0][0][1] |= op.predicate(false, false,  true) ? bit : 0;
        table[0][1][0] |= op.predicate(false,  true, false) ? bit : 0;
        table[0][1][1] |= op.predicate(false,  true,  true) ? bit : 0;
        table[1][0][0] |= op.predicate( true, false, false) ? bit : 0;
        table[1][0][1] |= op.predicate( true, false,  true) ? bit : 0;
        table[1][1][0] |= op.predicate( true,  true, false) ? bit : 0;
        table[1][1][1] |= op.predicate( true,  true,  true) ? bit : 0;
    }
    /* NVC condition code truth table for addition (with or without carry) */
    private static final byte CC_ADD_NVC_TABLE[][][] = new byte[2][2][2];
    private static final byte CC_ADD_H_TABLE[][][] = new byte[2][2][2];
    static {
        fillTable(CC_ADD_NVC_TABLE, CC_BIT_N, (A7,B7,R7) -> R7);
        fillTable(CC_ADD_NVC_TABLE, CC_BIT_V, (A7,B7,R7) -> (A7 && B7 && !R7) | (!A7 && !B7 && R7));
        fillTable(CC_ADD_NVC_TABLE, CC_BIT_C, (A7,B7,R7) -> (A7 && B7) | (B7 && !R7) | (!R7 && A7));

        fillTable(CC_ADD_H_TABLE,   CC_BIT_H, (A3,B3,R3) -> (A3 && B3) | (B3 && !R3) | (!R3 && A3));
    }
    void addAndSetCC(boolean withCarry) {
        int res = tempData0 + tempData1;
        if (withCarry && (opCodes & CC_BIT_C) != 0) {
            res++;
        }
        int cc = opCodes & CC_BIT_I;
        cc |= CC_ADD_NVC_TABLE[(tempData0 >> 7) & 1]
                              [(tempData1 >> 7) & 1]
                              [(res >> 7) & 1];
        cc |= CC_ADD_NVC_TABLE[(tempData0 >> 3) & 1]
                              [(tempData1 >> 3) & 1]
                              [(res >> 3) & 1];
        if ((res & 0xFF) == 0) cc |= CC_BIT_Z;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    /* NVC condition code truth table for subtraction (with or without carry) */
    private static final byte CC_SUB_NVC_TABLE[][][] = new byte[2][2][2];
    static {
        fillTable(CC_SUB_NVC_TABLE, CC_BIT_N, (A7,B7,R7) -> R7);
        fillTable(CC_SUB_NVC_TABLE, CC_BIT_V, (A7,B7,R7) -> (A7 && !B7 && !R7) | (!A7 && B7 && R7));
        fillTable(CC_SUB_NVC_TABLE, CC_BIT_C, (A7,B7,R7) -> (!A7 && B7) | (B7 && R7) | (R7 && !A7));
    }
    void subAndSetCC(boolean withCarry) {
        int res = tempData0 - tempData1;
        if (withCarry && (opCodes & CC_BIT_C) != 0) {
            res--;
        }
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        cc |= CC_ADD_NVC_TABLE[(tempData0 >> 7) & 1]
                              [(tempData1 >> 7) & 1]
                              [(res >> 7) & 1];
        if ((res & 0xFF) == 0) cc |= CC_BIT_Z;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    
    void andAndSetCC() {
        int res = tempData0 & tempData1;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H | CC_BIT_C);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        if ((res & 0xFF) == 0) cc |= CC_BIT_Z;
        // CC_BIT_V always cleared
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void orAndSetCC() {
        int res = tempData0 | tempData1;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H | CC_BIT_C);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        if ((res & 0xFF) == 0) cc |= CC_BIT_Z;
        // CC_BIT_V always cleared
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void xorAndSetCC() {
        int res = tempData0 ^ tempData1;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H | CC_BIT_C);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        if ((res & 0xFF) == 0) cc |= CC_BIT_Z;
        // CC_BIT_V always cleared
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void negAndSetCC() {
        int res = -tempData0;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        cc |= (res == 0) ? CC_BIT_Z : CC_BIT_C;
        if ((res & 0xFF) == 0x80) cc |= CC_BIT_V;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void comAndSetCC() {
        int res = ~tempData0;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        if (res == 0) cc |= CC_BIT_Z;
        cc |= CC_BIT_C;
        // CC_BIT_V always cleared
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void lsrAndSetCC() {
        int res = (tempData0 >> 1) & 0x7F;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if (res == 0) cc |= CC_BIT_Z;
        // CC_BIT_N always cleared
        // NOTE: V = N^C == C since N is always cleared
        if ((tempData0 & 0x1) != 0) cc |= (CC_BIT_C | CC_BIT_V);
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void rorAndSetCC() {
        int res = (tempData0 >> 1) & 0x7f;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if ((opCodes & CC_BIT_C) != 0) {
            res |= 0x80;
            cc |= CC_BIT_N;
        }
        if (res == 0) cc |= CC_BIT_Z;
        if ((tempData0 & 1) != 0) cc |= CC_BIT_C;
        if (((cc & CC_BIT_C) == 0) != ((cc & CC_BIT_N) == 0)) cc |= CC_BIT_V;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void asrAndSetCC() {
        int res = tempData0 >> 1;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if (res == 0) cc |= CC_BIT_Z;
        else if (res < 0) cc |= CC_BIT_N;
        if ((tempData0 & 1) != 0) cc |= CC_BIT_C;
        if (((cc & CC_BIT_C) == 0) != ((cc & CC_BIT_N) == 0)) cc |= CC_BIT_V;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void aslAndSetCC() {
        int res = tempData0 << 1;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if (res == 0) cc |= CC_BIT_Z;
        else if (res < 0) cc |= CC_BIT_N;
        if (tempData0 > 127) cc |= CC_BIT_C;
        if (((cc & CC_BIT_C) == 0) != ((cc & CC_BIT_N) == 0)) cc |= CC_BIT_V;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void rolAndSetCC() {
        int res = (tempData0 << 1) & 0xFF;
        if ((opCodes & CC_BIT_C) != 0) res |= 1;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        if ((tempData0 & 0x80) != 0) cc |= CC_BIT_C;
        if ((res == 0)) cc |= CC_BIT_Z;
        if (((cc & CC_BIT_C) == 0) != ((cc & CC_BIT_N) == 0)) cc |= CC_BIT_V;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void decAndSetCC() {
        int res = --tempData0;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H | CC_BIT_C);
        if (res == 0) cc |= CC_BIT_Z;
        if (res < -128) cc |= CC_BIT_V;
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void incAndSetCC() {
        int res = ++tempData0;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H | CC_BIT_C);
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        if (res == 0) cc |= CC_BIT_Z;
        if (res == 0x80) cc |= CC_BIT_V;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    void tstAndSetCC() {
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if (tempData0 == 0) cc |= CC_BIT_Z;
        else if (tempData0 == -128) cc |= CC_BIT_N;
        // CC_BIT_C cleared
        // CC_BIT_V cleared
        opCodes = (byte) cc;
    }

    void clrAndSetCC() {
        tempData0 = 0;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        cc |= CC_BIT_Z;
        opCodes = (byte) cc;
    }

    void daaAndSetCC() {
        int res = tempData0 & 0xFF;
        int cc = opCodes & (CC_BIT_I | CC_BIT_H);
        if ((res & 0x0F) > 9 || (opCodes & CC_BIT_H) != 0) {
            res += 0x06;
        }
        if (res > 0x9A || (opCodes & CC_BIT_C) != 0) {
            res += 0x60;
            cc |= CC_BIT_C; // and V?
        }
        if ((res & 0xFF) == 0) cc |= CC_BIT_Z;
        if ((res & 0x80) != 0) cc |= CC_BIT_N;
        tempData0 = (byte) res;
        opCodes = (byte) cc;
    }

    public static void main(String argv[]) {
        M6800 cpu = new M6800();
        for (int a = 0; a < 100; a++) {
            for (int b = 0; b < 100; b++) {
                final int ad = ((a / 10) << 4) + (a % 10);
                final int bd = ((b / 10) << 4) + (b % 10);
                int sum = ad + bd;
                cpu.opCodes = 0;
                if (sum > 255) cpu.opCodes |= CC_BIT_C;
                int adlo = a % 10;
                int bdlo = b % 10;
                if (adlo + bdlo > 15) cpu.opCodes |= CC_BIT_H;
                cpu.tempData0 = (byte) sum;
                cpu.daaAndSetCC();
                String res = "";
                if ((cpu.opCodes & CC_BIT_C) != 0) res += "1";
                res += "0123456789ABCDEF".charAt((cpu.tempData0 >> 4) & 0xF);
                res += "0123456789ABCDEF".charAt(cpu.tempData0 & 0xF);
                String expect = Integer.toString(a + b);
                if (expect.length() < 2) expect = "0" + expect;
                if (!res.equals(expect)) {
                    System.out.println("OOOOPS");
                    System.out.println(a + " + " + b + " == 0x" + Integer.toHexString(ad) + " + 0x" + Integer.toHexString(bd) + " == 0x" + Integer.toHexString(sum) + " == " + res);
                }
            }
        }
    }

    void cmpXAndSetCC() {
        char addr = (char) (((tempAddrHi) << 8) | (tempAddrLo & 0xFF));
        int res = X - addr;
        int cc = opCodes & (CC_BIT_H | CC_BIT_I | CC_BIT_C);
        if (res == 0) cc |= CC_BIT_Z;
        cc |= (CC_BIT_N | CC_BIT_V) & CC_SUB_NVC_TABLE[(X >> 15) & 1]
                                                      [(addr >> 15) & 1]
                                                      [(res >> 15) & 1];
        opCodes = (byte) cc;
    }
}
