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
public class SS30SlotState extends SSBusCommonDelegate {
    public SS30SlotState(SSBusCommonState state) {
        super(state, SS30_CTRL_MASK);
    }

    private boolean isSelected;

    public void select(boolean selected) { this.isSelected = selected; }

    public int getSelectLines() {
        return isSelected
               ? super.getControlLines() | SS30_SELECT_LINE
               : super.getControlLines() & ~SS30_SELECT_LINE;
    }
}
