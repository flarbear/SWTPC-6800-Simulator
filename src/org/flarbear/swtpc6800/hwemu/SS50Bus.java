/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.Transition;

/**
 * This class holds the state of an SS-50 bus.
 * 
 * The following bus lines are emulated. Note that any part of the system can
 * interact with these lines at any time, but they would be considered out of
 * spec if they do not adhere to the various signals like the clock phases,
 * R/W indicator, ready and interrupt lines, etc.
 * 
 * <dl>
 * <dt>Line 1</dt><dd>D0      data lines</dd>
 * <dt>Line 2</dt><dd>D1</dd>
 * <dt>Line 3</dt><dd>D2</dd>
 * <dt>Line 4</dt><dd>D3</dd>
 * <dt>Line 5</dt><dd>D4</dd>
 * <dt>Line 6</dt><dd>D5</dd>
 * <dt>Line 7</dt><dd>D6</dd>
 * <dt>Line 8</dt><dd>D7</dd>
 * <dt>Line 9</dt><dd>A15     address lines</dd>
 * <dt>Line 10</dt><dd>A14</dd>
 * <dt>Line 11</dt><dd>A13</dd>
 * <dt>Line 12</dt><dd>A12</dd>
 * <dt>Line 13</dt><dd>A11</dd>
 * <dt>Line 14</dt><dd>A10</dd>
 * <dt>Line 15</dt><dd>A9</dd>
 * <dt>Line 16</dt><dd>A8</dd>
 * <dt>Line 17</dt><dd>A7</dd>
 * <dt>Line 18</dt><dd>A6</dd>
 * <dt>Line 19</dt><dd>A5</dd>
 * <dt>Line 20</dt><dd>A4</dd>
 * <dt>Line 21</dt><dd>A3</dd>
 * <dt>Line 22</dt><dd>A2</dd>
 * <dt>Line 23</dt><dd>A1</dd>
 * <dt>Line 24</dt><dd>A0</dd>
 * <dt>Line 25</dt><dd>GND</dd>
 * <dt>Line 26</dt><dd>GND</dd>
 * <dt>Line 27</dt><dd>GND</dd>
 * <dt>Line 28</dt><dd>+8V</dd>
 * <dt>Line 29</dt><dd>+8V</dd>
 * <dt>Line 30</dt><dd>+8V</dd>
 * <dt>Line 31</dt><dd>-12V</dd>
 * <dt>Line 32</dt><dd>+12V</dd>
 * <dt>Line 33</dt><dd>INDEX     not used, prevents backwards card insertion</dd>
 * <dt>Line 34</dt><dd>M_RESET   user switch draws line low</dd>
 * <dt>Line 35</dt><dd>NMI       Non-maskable Interrupt from CPU, active low</dd>
 * <dt>Line 36</dt><dd>IRQ       Interrupt Request from CPU, active low</dd>
 * <dt>Line 37</dt><dd>UD2       User defined</dd>
 * <dt>Line 38</dt><dd>UD1</dd>
 * <dt>Line 39</dt><dd>Phi2      Inverted Phase 2 output, goes high to load/store data</dd>
 * <dt>Line 40</dt><dd>VMA       Valid Memory Address, low indicating address is present</dd>
 * <dt>Line 41</dt><dd>R/W       Read/Write, low indicates CPU is writing, high indicates a read</dd>
 * <dt>Line 42</dt><dd>Reset     One-shot triggered either from M_RESET or on power up</dd>
 * <dt>Line 43</dt><dd>BA        Bus available, high indicates the buses are floating for DMA</dd>
 * <dt>Line 44</dt><dd>Phi1      Phase 1 output, goes high when processor is modifying address lines</dd>
 * <dt>Line 45</dt><dd>HALT      Processor halted for DMA</dd>
 * <dt>Line 46</dt><dd>110b      Baud rate signals for I/O cards</dd>
 * <dt>Line 47</dt><dd>150b</dd>
 * <dt>Line 48</dt><dd>300b</dd>
 * <dt>Line 49</dt><dd>600b</dd>
 * <dt>Line 50</dt><dd>1200b</dd>
 * </dl>
 *
 * The address lines are in transition when Phi1 is high, Phi2 is low.
 * When Phi2 goes high and Phi1 is low, the address lines are valid and
 * the data lines will transition to the associated data for both read
 * and write. The CPU and peripherals adhere to specific timings to wait
 * for the data lines to contain data in a hardware CPU.
 *
 * Since the emulator will use discrete Phi1<->Phi2 switches and will not
 * simulate more granular timings, the CPU will be given first crack at
 * adjusting the data lines so that they are valid for a read. It will also
 * be given last crack at them so that it can read a value. All peripherals
 * should read or write the data lines as they are visited during propagation
 * of the signals through the bus.
 *
 * @author Flar
 */
public class SS50Bus implements BusState.Source {
    public static enum Line {
        D0(1),
        D1(),
        D2(),
        D3(),
        D4(),
        D5(),
        D6(),
        D7(),
        A15(),
        A14(),
        A13(),
        A12(),
        A11(),
        A10(),
        A9(),
        A8(),
        A7(),
        A6(),
        A5(),
        A4(),
        A3(),
        A2(),
        A1(),
        A0(),
        Gnd1(),
        Gnd2(),
        Gnd3(),
        P8v1(),
        P8v2(),
        P8v3(),
        N12v(),
        P12v(),
        Index(true),
        M_Reset(),
        NMI(),
        IRQ(),
        UD2(),
        UD1(),
        Phi2(),
        VMA(),
        R_W(),
        Reset(),
        BA(),
        Phi1(),
        HALT(),
        Baud110(),
        Baud150(),
        Baud300(),
        Baud600(),
        Baud1200();

        Line(int index) {
            assert(index >= 1 && index <= 50);
            this.index = index;
            this.bit = 1L << index;
        }

        Line() {
            this.index = ordinal();
            assert(index >= 1 && index <= 50);
            this.bit = 1L << index;
        }

        Line(boolean disconnected) {
            this.index = ordinal();
            assert(index >= 1 && index <= 50);
            this.bit = disconnected ? 0 : (1L << index);
        }

        public final int index;
        public final long bit;
    }

    private static Line[] DATA_LINES = {
        Line.D0,  Line.D1,  Line.D2,  Line.D3,
        Line.D4,  Line.D5,  Line.D6,  Line.D7,
    };

    private static Line[] ADDRESS_LINES = {
        Line.A0,  Line.A1,  Line.A2,  Line.A3,
        Line.A4,  Line.A5,  Line.A6,  Line.A7,
        Line.A8,  Line.A9,  Line.A10, Line.A11,
        Line.A12, Line.A13, Line.A14, Line.A15,
    };

    static {
        long bits = 0;
        int prevIndex = 1;
        for (Line line : Line.values()) {
            assert(line.index == prevIndex);
            prevIndex++;
            long bit;
            if (line == Line.Index) {
                assert(line.bit == 0);
                bit = (1L << line.index);
            } else {
                assert(line.bit != 0);
                bit = line.bit;
            }
            assert((bit ^ (bit - 1)) == 0);
            assert((bits & bit) == 0);
            bits |= bit;
        }
        assert(bits == ((1L << 50) << 1));

        // These assertions ensure that we can get/set the data lines with a single shift
        assert(Line.D1.index == Line.D0.index + 1);
        assert(Line.D2.index == Line.D0.index + 2);
        assert(Line.D3.index == Line.D0.index + 3);
        assert(Line.D4.index == Line.D0.index + 4);
        assert(Line.D5.index == Line.D0.index + 5);
        assert(Line.D6.index == Line.D0.index + 6);
        assert(Line.D7.index == Line.D0.index + 7);
        // And this assert allows us to cast the 50 lines to an int before we shirt
        assert(Line.D0.index < 32 && Line.D7.index < 32);

        // These assertions ensure that we can get/set the address lines with a single shift
        // (modulo reversing the bits)
        assert(Line.A1.index == Line.A0.index - 1);
        assert(Line.A2.index == Line.A0.index - 2);
        assert(Line.A3.index == Line.A0.index - 3);
        assert(Line.A4.index == Line.A0.index - 4);
        assert(Line.A5.index == Line.A0.index - 5);
        assert(Line.A6.index == Line.A0.index - 6);
        assert(Line.A7.index == Line.A0.index - 7);
        assert(Line.A8.index == Line.A0.index - 8);
        assert(Line.A9.index == Line.A0.index - 9);
        assert(Line.A10.index == Line.A0.index - 10);
        assert(Line.A11.index == Line.A0.index - 11);
        assert(Line.A12.index == Line.A0.index - 12);
        assert(Line.A13.index == Line.A0.index - 13);
        assert(Line.A14.index == Line.A0.index - 14);
        assert(Line.A15.index == Line.A0.index - 15);
        // And this assert allows us to cast the 50 lines to an int before we shirt
        assert(Line.A0.index < 32 && Line.A15.index < 32);
    }

    public static long Mask(Line... lines) {
        long mask = 0;
        for (Line line : lines) {
            mask |= line.bit;
        }
        return mask;
    }

    public static long DataMask(byte data) {
        return ArrayMask(DATA_LINES, data);
    }

    public static long AddressMask(char address) {
        return ArrayMask(ADDRESS_LINES, address);
    }

    private static long ArrayMask(Line[] lines, int value) {
        int bit = 1;
        long mask = 0;
        for (int i = 0; i < lines.length; i++, bit <<= 1) {
            if ((value & bit) != 0) {
                mask |= lines[i].bit;
            }
        }
        return mask;
    }

    private static final long ALL_LINES_MASK = Mask(Line.values());
    private static final long DATA_MASK = DataMask((byte) 0xff);
    private static final long ADDR_MASK = AddressMask((char) 0xFFFF);

    public static boolean ValidMask(long mask) {
        return (mask == (mask & ALL_LINES_MASK));
    }

    private long prevLines;
    private long curLines;
    private BusState.Listener listenerHead;
    private SignalState.LineState lineSources[] = new SignalState.LineState[Line.values().length + 1];

    private SignalState.LineState lineState(Line line) {
        SignalState.LineState source = lineSources[line.index];
        if (source == null) {
            lineSources[line.index] = source =
                    () -> SignalState.Level((curLines & line.bit) != 0);
        }
        return source;
    }

    private static class BusTrigger extends SignalState.TriggerImpl implements BusState.Listener {
        BusTrigger(SignalState.LineState triggerLine) {
            this.triggerLine = triggerLine;
        }

        private final SignalState.LineState triggerLine;
        private SignalState.Level prevState;

        private SignalState.Rising rising;
        private SignalState.Falling falling;

        SignalState.Trigger getRising() {
            if (rising == null) {
                rising = new SignalState.Rising();
                addListener(rising);
            }
            return rising;
        }

        SignalState.Trigger getFalling() {
            if (falling == null) {
                falling = new SignalState.Falling();
                addListener(falling);
            }
            return falling;
        }

        @Override
        public void busStateChanged() {
            SignalState.Level curState = triggerLine.getState();
            if (curState != prevState) {
                notifyListeners(curState.isHigh() ? Transition.RISING : Transition.FALLING);
                prevState = curState;
            }
        }
    }

    BusTrigger phi1Trigger;
    BusTrigger phi2Trigger;

    BusTrigger baud110Trigger;
    BusTrigger baud150Trigger;
    BusTrigger baud300Trigger;
    BusTrigger baud600Trigger;
    BusTrigger baud1200Trigger;

    private BusTrigger makeTrigger(BusTrigger cached, Line line) {
        if (cached == null) {
            cached = new BusTrigger(lineState(line));
            addListener(cached);
        }
        return cached;
    }

    private BusTrigger getPhi1Trigger() { return phi1Trigger = makeTrigger(phi1Trigger, Line.Phi1); }
    private BusTrigger getPhi2Trigger() { return phi2Trigger = makeTrigger(phi2Trigger, Line.Phi2); }

    private BusTrigger getBaud110Trigger() { return baud110Trigger = makeTrigger(baud110Trigger, Line.Baud110); }
    private BusTrigger getBaud150Trigger() { return baud110Trigger = makeTrigger(baud150Trigger, Line.Baud150); }
    private BusTrigger getBaud300Trigger() { return baud110Trigger = makeTrigger(baud300Trigger, Line.Baud300); }
    private BusTrigger getBaud600Trigger() { return baud110Trigger = makeTrigger(baud600Trigger, Line.Baud600); }
    private BusTrigger getBaud1200Trigger() { return baud110Trigger = makeTrigger(baud1200Trigger, Line.Baud1200); }

    public SignalState.LineState D0() { return lineState(Line.D0); }
    public SignalState.LineState D1() { return lineState(Line.D1); }
    public SignalState.LineState D2() { return lineState(Line.D2); }
    public SignalState.LineState D3() { return lineState(Line.D3); }
    public SignalState.LineState D4() { return lineState(Line.D4); }
    public SignalState.LineState D5() { return lineState(Line.D5); }
    public SignalState.LineState D6() { return lineState(Line.D6); }
    public SignalState.LineState D7() { return lineState(Line.D7); }

    public SignalState.LineState A0() { return lineState(Line.A0); }
    public SignalState.LineState A1() { return lineState(Line.A1); }
    public SignalState.LineState A2() { return lineState(Line.A2); }
    public SignalState.LineState A3() { return lineState(Line.A3); }
    public SignalState.LineState A4() { return lineState(Line.A4); }
    public SignalState.LineState A5() { return lineState(Line.A5); }
    public SignalState.LineState A6() { return lineState(Line.A6); }
    public SignalState.LineState A7() { return lineState(Line.A7); }
    public SignalState.LineState A8() { return lineState(Line.A8); }
    public SignalState.LineState A9() { return lineState(Line.A9); }
    public SignalState.LineState A10() { return lineState(Line.A10); }
    public SignalState.LineState A11() { return lineState(Line.A11); }
    public SignalState.LineState A12() { return lineState(Line.A12); }
    public SignalState.LineState A13() { return lineState(Line.A13); }
    public SignalState.LineState A14() { return lineState(Line.A14); }
    public SignalState.LineState A15() { return lineState(Line.A15); }

    public SignalState.LineState NMI() { return lineState(Line.NMI); }
    public SignalState.LineState IRQ() { return lineState(Line.IRQ); }
    public SignalState.LineState VMA() { return lineState(Line.VMA); }
    public SignalState.LineState R_W() { return lineState(Line.R_W); }
    public SignalState.LineState Reset() { return lineState(Line.Reset); }
    public SignalState.LineState Halt() { return lineState(Line.HALT); }

    public SignalState.LineState Phi1() { return lineState(Line.Phi1); }
    public SignalState.LineState Phi2() { return lineState(Line.Phi2); }

    public SignalState.Trigger Phi1Trigger() { return getPhi1Trigger(); }
    public SignalState.Trigger Phi1Rising() { return getPhi1Trigger().getRising(); }
    public SignalState.Trigger Phi1Falling() { return getPhi1Trigger().getFalling(); }

    public SignalState.Trigger Phi2Trigger() { return getPhi2Trigger(); }
    public SignalState.Trigger Phi2Rising() { return getPhi2Trigger().getRising(); }
    public SignalState.Trigger Phi2Falling() { return getPhi2Trigger().getFalling(); }

    public SignalState.Trigger Baud110Trigger() { return getBaud110Trigger(); }
    public SignalState.Trigger Baud110Rising() { return getBaud110Trigger().getRising(); }
    public SignalState.Trigger Baud110Falling() { return getBaud110Trigger().getFalling(); }
    public SignalState.Trigger Baud150Trigger() { return getBaud150Trigger(); }
    public SignalState.Trigger Baud150Rising() { return getBaud150Trigger().getRising(); }
    public SignalState.Trigger Baud150Falling() { return getBaud150Trigger().getFalling(); }
    public SignalState.Trigger Baud300Trigger() { return getBaud300Trigger(); }
    public SignalState.Trigger Baud300Rising() { return getBaud300Trigger().getRising(); }
    public SignalState.Trigger Baud300Falling() { return getBaud300Trigger().getFalling(); }
    public SignalState.Trigger Baud600Trigger() { return getBaud600Trigger(); }
    public SignalState.Trigger Baud600Rising() { return getBaud600Trigger().getRising(); }
    public SignalState.Trigger Baud600Falling() { return getBaud600Trigger().getFalling(); }
    public SignalState.Trigger Baud1200Trigger() { return getBaud1200Trigger(); }
    public SignalState.Trigger Baud1200Rising() { return getBaud1200Trigger().getRising(); }
    public SignalState.Trigger Baud1200Falling() { return getBaud1200Trigger().getFalling(); }

    @Override
    public void addListener(BusState.Listener listener) {
        listenerHead = BusState.chain(listenerHead, listener);
    }

    public void pushLines(long newLines) {
        prevLines = curLines;
        curLines = newLines;
        if (listenerHead != null) {
            listenerHead.busStateChanged();
        }
    }

    public long currentLines() {
        return curLines;
    }

    public static byte getDataLines(long lines) {
        return (byte) (((int) lines) >> Line.D0.index);
    }

    public byte getData() {
        return getDataLines(curLines);
    }

    public static long setDataLines(long lines, byte data) {
        return (lines & ~DATA_MASK) | ((data & 0xFF) << Line.D0.index);
    }

    public void setData(byte data) {
        curLines = setDataLines(curLines, data);
    }

    public static char getAddress(long lines) {
        return addressReverse[(((int) lines) >> Line.A15.index) & 0xFFFF];
    }

    public char getAddress() {
        return getAddress(curLines);
    }

    public static long setAddressLines(long lines, char address) {
        address = addressReverse[address];
        return (lines & ~ADDR_MASK) | ((address & 0xFFFFL) << Line.A15.index);
    }

    public void setAddress(char address) {
        curLines = setAddressLines(curLines, address);
    }

    public boolean linesMatch(long mask, long value) {
        assert((value & mask) == value);
        return ((curLines & mask) == value);
    }

    public boolean allHigh(long mask) {
        return linesMatch(mask, mask);
    }

    public boolean allLow(long mask) {
        return linesMatch(mask, 0L);
    }

    public boolean isHigh(Line line) {
        return ((curLines & line.bit) != 0);
    }

    public boolean isLow(Line line) {
        return ((curLines & line.bit) == 0);
    }

    public boolean isRising(Line line) {
        return ((prevLines & line.bit) == 0) &&
               ((curLines & line.bit) != 0);
    }

    public boolean isFalling(Line line) {
        return ((prevLines & line.bit) != 0) &&
               ((curLines & line.bit) == 0);
    }

    public static char reverseAddress(char address) {
        return addressReverse[address];
    }

    private static final char addressReverse[];

    static {
        addressReverse = new char[0x10000];
        for (int i = 0; i <= 0xFFFF; i++) {
            addressReverse[i] = computeReverse((char) i);
        }
    }

    private static char computeReverse(char addr) {
        int fwd = addr;
        int rev = 0;
        for (int j = 0; j < 16; j++) {
            rev <<= 1;
            rev |= (fwd & 1);
            fwd >>= 1;
        }
        assert(rev >= 0 && rev <= 0xFFFF);
        return (char) rev;
    }
}
