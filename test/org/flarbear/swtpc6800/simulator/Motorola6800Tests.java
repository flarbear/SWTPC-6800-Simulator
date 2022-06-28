/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.simulator;

import static org.flarbear.swtpc6800.simulator.Motorola6800.COND_C;
import static org.flarbear.swtpc6800.simulator.Motorola6800.COND_H;
import org.flarbear.swtpc6800.simulator.Motorola6800.State;
import org.junit.Test;

/**
 * @author Flar
 */
public class Motorola6800Tests {
    @Test
    public void testDAAInstruction() {
        Motorola6800 cpu = new Motorola6800();
        State state = new Motorola6800.State();

        byte mem[] = { 0x19 };

        for (int a = 0; a < 100; a++) {
            for (int b = 0; b < 100; b++) {
                final int ad = ((a / 10) << 4) + (a % 10);
                final int bd = ((b / 10) << 4) + (b % 10);
                int sum = ad + bd;
                state.ccode = 0;
                if (sum > 255) state.ccode |= COND_C;
                int adlo = a % 10;
                int bdlo = b % 10;
                if (adlo + bdlo > 15) state.ccode |= COND_H;
                state.accA = (byte) sum;
                state.PCreg = 0;
                cpu.test(state, mem);
                String res = "";
                if ((state.ccode & COND_C) != 0) res += "1";
                res += "0123456789ABCDEF".charAt((state.accA >> 4) & 0xF);
                res += "0123456789ABCDEF".charAt(state.accA & 0xF);
                String expect = Integer.toString(a + b);
                if (expect.length() < 2) expect = "0" + expect;
                if (!res.equals(expect)) {
                    System.out.println("OOOOPS");
                    System.out.println(a + " + " + b + " == 0x" + Integer.toHexString(ad) + " + 0x" + Integer.toHexString(bd) + " == 0x" + Integer.toHexString(sum) + " == " + res);
                }
            }
        }
    }
}
