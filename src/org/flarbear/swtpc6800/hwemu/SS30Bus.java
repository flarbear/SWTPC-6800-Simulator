/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.Transition;

/**
 * This class holds the state of an SS-30 bus.
 * 
 * The following bus lines are emulated. Note that any part of the system can
 * interact with these lines at any time, but they would be considered out of
 * spec if they do not adhere to the various signals like the clock phases,
 * R/W indicator, ready and interrupt lines, etc.
 * 
 * <dl>
 * <dt>Line 1</dt><dd>UD0/A2       user defined or used for addressing on advanced motherboards</dd>
 * <dt>Line 2</dt><dd>UD1/A3       user defined or used for addressing on advanced motherboards</dd>
 * <dt>Line 3</dt><dd>-12V</dd>
 * <dt>Line 4</dt><dd>+12V</dd>
 * <dt>Line 5</dt><dd>GND</dd>
 * <dt>Line 6</dt><dd>GND</dd>
 * <dt>Line 7</dt><dd>Index        not used, blocked off to prevent backwards card insertion</dd>
 * <dt>Line 8</dt><dd>NMI</dd>
 * <dt>Line 9</dt><dd>IRQ</dd>
 * <dt>Line 10</dt><dd>RS0         register select, or A0</dd>
 * <dt>Line 11</dt><dd>RS1         register select, or A1</dd>
 * <dt>Line 12</dt><dd>D0          data lines</dd>
 * <dt>Line 13</dt><dd>D1</dd>
 * <dt>Line 14</dt><dd>D2</dd>
 * <dt>Line 15</dt><dd>D3</dd>
 * <dt>Line 16</dt><dd>D4</dd>
 * <dt>Line 17</dt><dd>D5</dd>
 * <dt>Line 18</dt><dd>D6</dd>
 * <dt>Line 19</dt><dd>D7</dd>
 * <dt>Line 20</dt><dd>Phase2     complement processor clock, aka SS-50 Phi2</dd>
 * <dt>Line 21</dt><dd>R/W        Read/Write, high for read</dd>
 * <dt>Line 22</dt><dd>+8V</dd>
 * <dt>Line 23</dt><dd>+8V</dd>
 * <dt>Line 24</dt><dd>1200 baud</dd>
 * <dt>Line 25</dt><dd>600 baud</dd>
 * <dt>Line 26</dt><dd>300 baud</dd>
 * <dt>Line 27</dt><dd>150 baud</dd>
 * <dt>Line 28</dt><dd>110 baud</dd>
 * <dt>Line 29</dt><dd>RESET</dd>
 * <dt>Line 30</dt><dd>BoardSelect   motherboard address decoding will set this for only 1 card</dd>
 * </dl>
 *
 * @author Flar
 */
public class SS30Bus {
    public enum Line {
        UD0(1),
        UD1(),
        N12v(),
        P12v(),
        GND1(),
        GND2(),
        Index(true),
        NMI(),
        IRQ(),
        RS0(),
        RS1(),
        D0(),
        D1(),
        D2(),
        D3(),
        D4(),
        D5(),
        D6(),
        D7(),
        Phi2(),
        R_W(),
        P8v1(),
        P8v2(),
        Baud1200(),
        Baud600(),
        Baud300(),
        Baud150(),
        Baud110(),
        RESET(),
        BoardSelect();

        Line(int index) {
            assert(index >= 1 && index <= 30);
            this.index = index;
            this.bit = (1 << index);
        }

        Line() {
            this.index = ordinal();
            assert(index >= 1 && index <= 30);
            this.bit = (1 << index);
        }

        Line(boolean disconnected) {
            this.index = ordinal();
            assert(index >= 1 && index <= 30);
            this.bit = disconnected ? 0 : (1 << index);
        }

        public final int index;
        public final int bit;
    }

    static {
        int bits = 0;
        int prevIndex = 1;
        for (Line line : Line.values()) {
            assert(line.index == prevIndex);
            prevIndex++;
            if (line == Line.Index) {
                assert(line.bit == 0);
                bits |= (1 << line.index);
            } else {
                assert(line.bit != 0);
                assert((line.bit ^ (line.bit - 1)) == 0);
                assert((bits & line.bit) == 0);
                bits |= line.bit;
            }
        }
        assert(bits == ((1 << 30) << 1));

        // These assertions ensure that we can get/set the data lines with a single shift
        assert(Line.D1.index == Line.D0.index + 1);
        assert(Line.D2.index == Line.D0.index + 2);
        assert(Line.D3.index == Line.D0.index + 3);
        assert(Line.D4.index == Line.D0.index + 4);
        assert(Line.D5.index == Line.D0.index + 5);
        assert(Line.D6.index == Line.D0.index + 6);
        assert(Line.D7.index == Line.D0.index + 7);
    }

    public static int Mask(Line... lines) {
        int mask = 0;
        for (Line line : lines) {
            mask |= line.bit;
        }
        return mask;
    }

    private static final int ALL_LINES_MASK = Mask(Line.values());

    public static boolean ValidMask(int mask) {
        return (mask == (mask & ALL_LINES_MASK));
    }

    private static class SharedLines extends BusState.SourceImpl {
        SharedLines(boolean DC_1_mod) {
            this.DC_1_mod = DC_1_mod;
        }

        private final boolean DC_1_mod;

        private int _prevLines;
        private int _curLines;
        private int _prevSelectedSlot;
        private int _curSelectedSlot;

        // A poor man's tri-state transciever
        private boolean dataWritten;

        void pushLines(int newLines, int selected) {
            assert((newLines & Line.BoardSelect.bit) == 0);
            _prevLines = _curLines;
            _curLines = newLines & ~Line.BoardSelect.bit;
            _prevSelectedSlot = _curSelectedSlot;
            _curSelectedSlot = selected;

            if (DC_1_mod && selected == 5) {
                _curLines |= Line.UD0.bit;
            }

            if (listenerHead != null) {
                notifyListeners();
            }
        }

        int curLines(int slot) {
            int lines = _curLines;
            if (slot == _curSelectedSlot) {
                lines |= Line.BoardSelect.bit;
            }
            return lines;
        }

        int prevLines(int slot) {
            int lines = _prevLines;
            if (slot == _prevSelectedSlot) {
                lines |= Line.BoardSelect.bit;
            }
            return lines;
        }

        private static final int DATA_MASK = Mask(Line.D0, Line.D1, Line.D2, Line.D3,
                                                  Line.D4, Line.D5, Line.D6, Line.D7);

        byte getData() {
            return (byte) (_curLines >> Line.D0.index);
        }

        void setData(byte data) {
            dataWritten = true;
            _curLines = (_curLines & ~DATA_MASK)
                        | ((data & 0xFF) << Line.D0.index);
        }
    }

    public SS30Bus(boolean DC_1_mod) {
        this(new SharedLines(DC_1_mod), -1);
    }

    public SS30Bus getSlotView(int slot) {
        return new SS30Bus(shared, slot);
    }

    private SS30Bus(SharedLines shared, int slot) {
        if (slot < 0 || slot > 7) {
            throw new IllegalArgumentException("SS-30 bus slots number from 0 -> 7");
        }
        this.shared = shared;
        this.slot = slot;
    }

    SharedLines shared;
    int slot;
    private SignalState.LineState lineSources[] = new SignalState.LineState[Line.values().length + 1];

    private SignalState.LineState lineState(Line line) {
        SignalState.LineState source = lineSources[line.index];
        if (source == null) {
            lineSources[line.index] = source =
                    () -> SignalState.Level((shared.curLines(slot) & line.bit) != 0);
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

    BusTrigger phi2Trigger;
    BusTrigger baud110Trigger;
    BusTrigger baud150Trigger;
    BusTrigger baud300Trigger;
    BusTrigger baud600Trigger;
    BusTrigger baud1200Trigger;

    private BusTrigger makeTrigger(BusTrigger cached, Line line) {
        if (cached == null) {
            cached = new BusTrigger(lineState(line));
            shared.addListener(cached);
        }
        return cached;
    }

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

    public SignalState.LineState RS0() { return lineState(Line.RS0); }
    public SignalState.LineState RS1() { return lineState(Line.RS1); }
    public SignalState.LineState UD0() { return lineState(Line.UD0); }
    public SignalState.LineState UD1() { return lineState(Line.UD1); }

    public SignalState.LineState NMI() { return lineState(Line.NMI); }
    public SignalState.LineState IRQ() { return lineState(Line.IRQ); }
    public SignalState.LineState R_W() { return lineState(Line.R_W); }
    public SignalState.LineState BS() { return lineState(Line.BoardSelect); }
    public SignalState.LineState RESET() { return lineState(Line.RESET); }

    public SignalState.LineState Phi2() { return lineState(Line.Phi2); }

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

    private static class LineMapping {
        LineMapping(SS50Bus.Line line, int bit) {
            this.line = line;
            this.bit = bit;
        }

        SS50Bus.Line line;
        int bit;
    }

    private static LineMapping lineMappings[] = {
        new LineMapping(SS50Bus.Line.NMI, SS30Bus.Line.NMI.bit),
        new LineMapping(SS50Bus.Line.IRQ, SS30Bus.Line.IRQ.bit),
        new LineMapping(SS50Bus.Line.A0, SS30Bus.Line.RS0.bit),
        new LineMapping(SS50Bus.Line.A1, SS30Bus.Line.RS1.bit),
        new LineMapping(SS50Bus.Line.Phi2, SS30Bus.Line.Phi2.bit),
        new LineMapping(SS50Bus.Line.R_W, SS30Bus.Line.R_W.bit),
        new LineMapping(SS50Bus.Line.Baud110, SS30Bus.Line.Baud110.bit),
        new LineMapping(SS50Bus.Line.Baud150, SS30Bus.Line.Baud150.bit),
        new LineMapping(SS50Bus.Line.Baud300, SS30Bus.Line.Baud300.bit),
        new LineMapping(SS50Bus.Line.Baud600, SS30Bus.Line.Baud600.bit),
        new LineMapping(SS50Bus.Line.Baud1200, SS30Bus.Line.Baud1200.bit),
        new LineMapping(SS50Bus.Line.Reset, SS30Bus.Line.RESET.bit),
    };

    public void pushLines(SS50Bus master) {
        int lines = (((int) (master.currentLines() >> SS50Bus.Line.D0.index)) & 0xFF) << SS30Bus.Line.D0.index;
        for (LineMapping mapping : lineMappings) {
            if (master.isHigh(mapping.line)) {
                lines |= mapping.bit;
            }
        }
        int selected = 0;
        if (master.isHigh(SS50Bus.Line.A2)) {
            selected |= 1;
        }
        if (master.isHigh(SS50Bus.Line.A3)) {
            selected |= 2;
        }
        if (master.isHigh(SS50Bus.Line.A4)) {
            selected |= 4;
        }
        shared.dataWritten = false;
        shared.pushLines(lines, selected);
        if (shared.dataWritten) {
            master.setData(shared.getData());
        }
    }

    public byte getData() {
        return (byte) (shared.curLines(slot) >> Line.D0.index);
    }

    public void setData(byte data) {
        shared.setData(data);
    }

    public int currentLines() {
        return shared.curLines(slot);
    }

    public boolean linesMatch(int mask, int value) {
        assert((value & mask) == value);
        return ((shared.curLines(slot) & mask) == value);
    }

    public boolean allHigh(int mask) {
        return linesMatch(mask, mask);
    }

    public boolean allLow(int mask) {
        return linesMatch(mask, 0);
    }

    public boolean isHigh(Line line) {
        return (shared.curLines(slot) & line.bit) != 0;
    }

    public boolean isLow(Line line) {
        return (shared.curLines(slot) & line.bit) == 0;
    }

    public boolean isRising(Line line) {
        return ((shared.prevLines(slot) & line.bit) == 0) &&
               ((shared.curLines(slot) & line.bit) != 0);
    }

    public boolean isFalling(Line line) {
        return ((shared.prevLines(slot) & line.bit) != 0) &&
               ((shared.curLines(slot) & line.bit) == 0);
    }
}
