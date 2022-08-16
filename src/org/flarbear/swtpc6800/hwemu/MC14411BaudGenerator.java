/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.Divider;

/**
 * The MC14411 baud rate generator divides the output of what is assumed to
 * be a 1.8432 MHz crystal signal, supplied externally, down by the necessary
 * scaling factors to output a variety of signals at a rate depending on the
 * output clock multiplier selection and the which pin of the package is
 * monitored.
 *
 * In the application of the SWTPc MP-A board, the clock scalar is hard-wired
 * to 16x and the clock is actually 1.7971 MHz which skews the rates slightly.
 * Also, only the following lines are actually connected to the motherboard:
 * <dl>
 * <dt>F13 => SS50 line 46</dt>
 * <dd>110 baud rate line running at 109.9 * 16 = 1.758 KHz
 *     (actually 107.2 baud)</dd>
 * <dt>F11 => SS50 line 47</dt>
 * <dd>150 baud rate line running at 150.0 * 16 = 2.4 KHz
 *     (actually 146 baud)</dd>
 * <dt>F9 => SS50 line 48</dt>
 * <dd>300 baud rate line running at 300.0 * 16 = 4.8 KHz
 *     (actually 292.5 baud)</dd>
 * <dt>F8 => SS50 line 49</dt>
 * <dd>600 baud rate line running at 600.0 * 16 = 9.6 KHz
 *     (actually 585 baud)</dd>
 * <dt>F7 => SS50 line 50</dt>
 * <dd>1200 baud rate line running at 1200 * 16 = 19.2 KHz
 *     (actually 1170 baud)</dd>
 * </dl>
 *
 * @author Flar
 */
public class MC14411BaudGenerator {
    public MC14411BaudGenerator(boolean rateSelectA, boolean rateSelectB) {
        if (rateSelectB) {
            multiplier = rateSelectA ? 64 : 16;
        } else {
            multiplier = rateSelectA ? 8 : 1;
        }
        lines = new Divider[17];
        activeLines = new SignalState.TriggerImpl();
    }

    private final int multiplier;
    private final SignalState.Transfer lines[];
    private final SignalState.TriggerImpl activeLines;

    static int divisors[] = {
        0,    // F0 doesn't exist
        3,    // F1,  1,843,200 /   3 / 64 = 9600 baud * rateSelect
        4,    // F2,  1,843,200 /   4 / 64 = 7200 baud * rateSelect
        6,    // F3,  1,843,200 /   6 / 64 = 4800 baud * rateSelect
        8,    // F4,  1,843,200 /   8 / 64 = 3600 baud * rateSelect
        12,   // F5,  1,843,200 /  12 / 64 = 2400 baud * rateSelect
        16,   // F6,  1,843,200 /  16 / 64 = 1800 baud * rateSelect
        24,   // F7,  1,843,200 /  24 / 64 = 1200 baud * rateSelect
        48,   // F8,  1,843,200 /  48 / 64 =  600 baud * rateSelect
        96,   // F9,  1,843,200 /  96 / 64 =  300 baud * rateSelect
        144,  // F10, 1,843,200 / 144 / 64 =  200 baud * rateSelect
        192,  // F11, 1,843,200 / 192 / 64 =  150 baud * rateSelect
        214,  // F12, 1,843,200 / 214 / 64 = 134.5 baud * rateSelect
        262,  // F13, 1,843,200 / 262 / 64 = 109.9 baud * rateSelect
        384,  // F14, 1,843,200 / 384 / 64 =   75 baud * rateSelect
        2,    // F15, 1,843,200 /   2      = 921.6 KHz (ignores rateSelect)
        1,    // F16, 1,843,200 /   1      = 1.8432 MHz (ignores rateSelect)
    };

    public void pumpClock(SignalState.Transition transition) {
        activeLines.notifyListeners(transition);
    }

    public void addListener(SignalState.Listener listener, int Fline) {
        if (Fline < 1 || Fline > 16) {
            throw new IllegalArgumentException("Baud rate generator output lines are from 1-16");
        }
        SignalState.Transfer line = lines[Fline];
        if (line == null) {
            switch (Fline) {
                case 16 -> line = new SignalState.TransferImpl();
                case 15 -> line = new Divider(2);
                default -> {
                        int divisor = divisors[Fline];
                        divisor *= 64 / multiplier;
                        line = new Divider(divisor);
                }
            }
            activeLines.addListener(line);
            lines[Fline] = line;
        }
        line.addListener(listener);
    }
}
