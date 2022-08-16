/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.LineState;
import static org.flarbear.swtpc6800.hwemu.SignalState.LineState.FLOATING;
import org.flarbear.swtpc6800.hwemu.SignalState.Transition;

/**
 * Note that RS232 is supposed to use positive voltages from +5v to +25v to
 * indicate logical zeros and negative voltages from -5v to -25v to indicate
 * logical ones.
 *
 * @author Flar
 */
public class MC6850_emu {
    enum Parity {
        None,
        Even,
        Odd
    }

    public static final int RDRF_BIT = 0b00000001;
    public static final int TDRE_BIT = 0b00000010;
    public static final int DCD_BIT  = 0b00000100;
    public static final int CTS_BIT  = 0b00001000;
    public static final int FE_BIT   = 0b00010000;
    public static final int OVRN_BIT = 0b00100000;
    public static final int PE_BIT   = 0b01000000;
    public static final int IRQ_BIT  = 0b10000000;

    private void checkLines(String name, int count, LineState... lines) {
        String names = name + ((count > 1) ? " lines" : " line");
        if (lines.length != count) {
            throw new IllegalArgumentException("There must be " + count + " " + names);
        }
        for (LineState line : lines) {
            if (line == null) {
                throw new IllegalArgumentException(names + " cannot be null");
            }
        }
    }

    MC6850_emu() {
        TxData = new LineState.Boolean();
        RTS = new LineState.Boolean();
        IRQ = new LineState.Boolean();

        this.baudDivisor = 1;
        this.eightDataBits = false;
        this.parity = Parity.Even;
        this.twoStopBits = true;
    }

    /****** Lines connecting the MC6850 to the MPU ******/

    // The 3 chip select lines are combined with an AND operation into
    // a single state to query on the bus clock cycle.
    private LineState CS = FLOATING;

    private LineState RS = FLOATING;
    private LineState R_W = FLOATING;

    private LineState D0 = FLOATING;
    private LineState D1 = FLOATING;
    private LineState D2 = FLOATING;
    private LineState D3 = FLOATING;
    private LineState D4 = FLOATING;
    private LineState D5 = FLOATING;
    private LineState D6 = FLOATING;
    private LineState D7 = FLOATING;

    private final LineState.Boolean IRQ;

    public void wireBusDataLines(LineState... dataLines) {
        checkLines("Data", 8, dataLines);

        this.D0 = dataLines[0];
        this.D1 = dataLines[1];
        this.D2 = dataLines[2];
        this.D3 = dataLines[3];
        this.D4 = dataLines[4];
        this.D5 = dataLines[5];
        this.D6 = dataLines[6];
        this.D7 = dataLines[7];
    }

    public void wireMPUSelectLines(LineState CS0, LineState CS1, LineState CS2,
                                   LineState RS, LineState R_W) {
        checkLines("Chip Select", 3, CS0, CS1, CS2);
        checkLines("Register Select", 1, RS);
        checkLines("Read/Write", 1, R_W);
        this.CS = CS0
                .and(CS1)
                .and(CS2.not());
        this.RS = RS;
        this.R_W = R_W;
    }

    public LineState getIRQLine() {
        return IRQ.readOnly();
    }

    /****** Lines connecting the MC6850 to the other serial terminal ******/

    private final LineState.Boolean TxData;
    private final LineState.Boolean RTS;

    private LineState RxData;
    private LineState CTS;
    private LineState DCD;

    public LineState getTransmitDataLine() {
        return TxData.readOnly();
    }

    public LineState getRTSLine() {
        return RTS.readOnly();
    }

    public void wireInterconnect(LineState RxData, LineState CTS, LineState DCD) {
        checkLines("RxData", 1, RxData);
        checkLines("Clear To Send", 1, CTS);
        checkLines("Data Carrier Detect", 1, DCD);

        this.RxData = RxData;
        this.CTS = CTS;
        this.DCD = DCD;
    }

    private int baudDivisor;
    private boolean eightDataBits;
    private Parity parity;
    private boolean twoStopBits;

    private boolean running;
    private int status;

    private boolean receiveInterruptEnabled;
    private boolean transmitInterruptEnabled;
    private boolean sendBREAK;

    private byte transmitData;
    private int transmitShiftRegister;
    private int transmitHoldCount;

    private byte receiveData;
    private int receiveShiftRegister;
    private int receiveHoldCount;

    private static int setStatus(int status, int bit, boolean isHigh) {
        return isHigh ? (status | bit) : (status & ~bit);
    }

    private void setStatus(int bit, boolean isHigh) {
        status = setStatus(status, bit, isHigh);
    }

    private boolean getStatus(int bit) {
        return (status & bit) != 0;
    }

    public void setData(byte data) {
        
    }

    public Byte getData() {
        return 0;
    }

    private int index(boolean B2, boolean B1, boolean B0) {
        return (B2 ? 4 : 0) | (B1 ? 2 : 0) | (B0 ? 1 : 0);
    }

    private void setDivisor() {
        int newDivisor;
        switch(index(false, D1.isHigh(), D0.isHigh())) {
            case 0b00 -> newDivisor = 1;
            case 0b01 -> newDivisor = 16;
            case 0b10 -> newDivisor = 64;
            default -> {
                // Master reset
                status = 0;
                running = true;
                return;
            }
        }
        if (baudDivisor != newDivisor) {
            baudDivisor = newDivisor;
            // Reset the in transit data?
        }
    }

    private void setWordSelect() {
        switch (index(D4.isHigh(), D3.isHigh(), D2.isHigh())) {
            case 0b000 -> { eightDataBits = false; parity = Parity.Even; twoStopBits = true;  }
            case 0b001 -> { eightDataBits = false; parity = Parity.Odd;  twoStopBits = true;  }
            case 0b010 -> { eightDataBits = false; parity = Parity.Even; twoStopBits = false; }
            case 0b011 -> { eightDataBits = false; parity = Parity.Odd;  twoStopBits = false; }
            case 0b100 -> { eightDataBits = true;  parity = Parity.None; twoStopBits = true;  }
            case 0b101 -> { eightDataBits = true;  parity = Parity.None; twoStopBits = false; }
            case 0b110 -> { eightDataBits = true;  parity = Parity.Even; twoStopBits = false; }
            case 0b111 -> { eightDataBits = true;  parity = Parity.Odd;  twoStopBits = false; }
        }
    }

    private void setTransmitControlBits() {
        switch (index(false, D6.isHigh(), D5.isHigh())) {
            case 0b00 -> { RTS.setState(false); transmitInterruptEnabled = false; sendBREAK = false; }
            case 0b01 -> { RTS.setState(false); transmitInterruptEnabled = true;  sendBREAK = false; }
            case 0b10 -> { RTS.setState(true);  transmitInterruptEnabled = false; sendBREAK = false; }
            case 0b11 -> { RTS.setState(false); transmitInterruptEnabled = false; sendBREAK = true;  }
        }
    }

    public Byte enableFired(Transition transition) {
        if (CS.isLow()) {
            return null;
        }
        if (RS.isLow()) {
            if (R_W.isLow()) {
                // Write to Control Register
                setDivisor();
                setWordSelect();
                setTransmitControlBits();
                receiveInterruptEnabled = D7.isHigh();
            } else {
                // Read from Status Register
                int retStatus = status;
                if (CTS.isHigh()) {
                    retStatus = setStatus(retStatus, TDRE_BIT, false);
                }
                setStatus(retStatus, CTS_BIT, CTS.isHigh());
                setStatus(retStatus, DCD_BIT, DCD.isHigh());
                return (byte) retStatus;
            }
        } else {
            if (R_W.isLow()) {
                // Accessing Transmit Data Register
                if (getStatus(TDRE_BIT) && CTS.isLow()) {
                    int bits = D7.isHigh() ? 1 : 0;
                    bits = (bits << 1) | (D6.isHigh() ? 1 : 0);
                    bits = (bits << 1) | (D5.isHigh() ? 1 : 0);
                    bits = (bits << 1) | (D4.isHigh() ? 1 : 0);
                    bits = (bits << 1) | (D3.isHigh() ? 1 : 0);
                    bits = (bits << 1) | (D2.isHigh() ? 1 : 0);
                    bits = (bits << 1) | (D1.isHigh() ? 1 : 0);
                    bits = (bits << 1) | (D0.isHigh() ? 1 : 0);

                    transmitData = (byte) bits;
                    setStatus(TDRE_BIT, false);
                }
            } else {
                // Accessing Receive Data Register
                // Send data to ACIA pins
                setStatus(RDRF_BIT, false);
                setStatus(PE_BIT, false);
                setStatus(FE_BIT, false);
                setStatus(OVRN_BIT, false);
                return receiveData;
            }
        }
        return null;
    }

    public int computeParity(int bits) {
        bits ^= (bits >> 1);
        bits ^= (bits >> 2);
        bits ^= (bits >> 4);
        return bits & 1;
    }

    public int encodeTransmitData() {
        if (getStatus(TDRE_BIT)) {
            return 0;
        }
        int numbits = eightDataBits ? 8 : 7;
        int bits = transmitData & ~((1 << numbits) - 1);
        if (parity != Parity.None) {
            int pbits = computeParity(bits);
            if (parity == Parity.Even) {
                pbits ^= 1;
            }
            bits |= (pbits & 1) << numbits;
            numbits++;
        }
        bits |= 1 << numbits;
        numbits++;
        if (twoStopBits) {
            bits |= 1 << numbits;
            numbits++;
        }
        // Pad with a zero for the start bit
        bits <<= 1;
        return bits;
    }

    public void decodeReceiveData() {
        int bits = receiveShiftRegister;
        receiveShiftRegister = 0;
        if ((bits & 1) == 0) {
            setStatus(FE_BIT, true);
        }
        bits >>= 1;
        if (twoStopBits && (bits & 1) == 0) {
            setStatus(FE_BIT, true);
        }
        int rxParity;
        if (parity != Parity.None) {
            rxParity = bits & 1;
            bits >>= 1;
        } else {
            rxParity = 0;
        }
        // Bits have to be reversed...
        int data = 0;
        for (int i = eightDataBits ? 8 : 7; i > 0; --i) {
            data = (data << 1) | (bits & 1);
            bits >>= 1;
        }
        if (parity != Parity.None) {
            int expectedParity = computeParity(data);
            if (parity == Parity.Even) {
                expectedParity ^= 1;
            }
            setStatus(PE_BIT, expectedParity != rxParity);
        } else {
            setStatus(PE_BIT, false);
        }
        assert(bits == 1);
        if (getStatus(RDRF_BIT)) {
            setStatus(OVRN_BIT, true);
        } else {
            setStatus(RDRF_BIT, true);
        }
        receiveData = (byte) data;
    }

    public void receiveClockFired() {
        if (receiveHoldCount > 1) {
            --receiveHoldCount;
            return;
        }
        if (receiveShiftRegister != 0) {
            receiveShiftRegister = (receiveShiftRegister << 1) | (RxData.isHigh() ? 1 : 0);
            int numbits = (eightDataBits ? 8 : 7) + (parity != Parity.None ? 1 : 0) + (twoStopBits ? 2 : 1);
            if ((receiveShiftRegister >>> numbits) != 0) {
                if (getStatus(RDRF_BIT)) {
                    // Transfer of data from the receive buffer is inhibited in this state
                    setStatus(OVRN_BIT, true);
                    setStatus(PE_BIT, false);
                } else {
                    setStatus(RDRF_BIT, true);
                }
                if (receiveInterruptEnabled) {
                    setStatus(IRQ_BIT, true);
                }
                receiveShiftRegister = 0;
                // Wait out the rest of the last bit
                receiveHoldCount = baudDivisor / 2;
            } else {
                receiveHoldCount = baudDivisor;
            }
        } else if (RxData.isHigh()) {
            // The start bit is a logic "0" which is a high voltage state
            // for the line, but we need to load some non-zero data into
            // the shift register so we can detect when we are done.
            // We start with 2 1-bits in the shift register so that we
            // can tell if we are done when the first bit is high enough
            // to shift by the number of transmission bits and the result
            // will be non-zero.
            receiveShiftRegister = 0b11;
            // Skip a bit and a half to align with center of following bits
            receiveHoldCount = baudDivisor + (baudDivisor / 2);
        }
    }

    public void transmitClockFired() {
        if (transmitHoldCount > 1) {
            --transmitHoldCount;
            return;
        }
        if (transmitShiftRegister == 0) {
            if (sendBREAK) {
                // Two full characters with all start/stop/parity bits
                transmitHoldCount = baudDivisor * 20;
                sendBREAK = false;
            } else {
                transmitShiftRegister = encodeTransmitData();
                if (transmitShiftRegister != 0) {
                    setStatus(TDRE_BIT, true);
                }
            }
        }
        if (transmitShiftRegister != 0) {
            // Send data on the TxData line, a zero bit is a high voltage state
            TxData.setState((transmitShiftRegister & 1) == 0);
            transmitShiftRegister >>>= 1;
            transmitHoldCount = baudDivisor;
        } else {
            TxData.setState(false);
        }
    }

    public void bothClocksFired(Transition transition) {
        receiveClockFired();
        transmitClockFired();
    }
}
