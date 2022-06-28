/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

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
public class SS50BusState extends SSBusCommonDelegate {
    public SS50BusState() {
        super(SS50_CTRL_MASK);
    }

    public SS50BusState(SSBusCommonState state) {
        super(state, SS50_CTRL_MASK);
    }

    private Character addressLines;
    private ClockSignal.Source phi1;

    public Character getAddressLines() {
        return addressLines;
    }

    public void setAddressLines(Character new_address) {
        addressLines = new_address;
    }

    public ClockSignal.Source phi1() { return phi1; }
}
