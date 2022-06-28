/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

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

    private static class SharedLines {
        private int _prevLines;
        private int _curLines;
        private int _prevSelectedSlot;
        private int _curSelectedSlot;

        void pushLines(int newLines, int selected) {
            assert((newLines & Line.BoardSelect.bit) == 0);
            _prevLines = _curLines;
            _curLines = newLines & ~Line.BoardSelect.bit;
            _prevSelectedSlot = _curSelectedSlot;
            _curSelectedSlot = selected;
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
            _curLines = (_curLines & ~DATA_MASK)
                        | ((data & 0xFF) << Line.D0.index);
        }
    }

    public SS30Bus() {
        this.shared = new SharedLines();
        this.slot = -2;
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
    boolean DC_1_mod;

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
        if (DC_1_mod && selected == 5) {
            lines |= Line.UD0.bit;
        }
        shared.pushLines(lines, selected);
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
