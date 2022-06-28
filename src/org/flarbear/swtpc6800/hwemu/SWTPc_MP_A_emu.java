/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.ClockSignal.Divider;
import org.flarbear.swtpc6800.hwemu.ClockSignal.DualPhase;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_A_emu extends SS50Card {
    SWTPc_MP_A_emu() {
        this.cpu = new M6800();

        this.xtal = new ClockSignal.Crystal();
        this.phi1 = new ClockSignal.TransferImpl();
        this.phi2 = new ClockSignal.TransferImpl();

        this.baud110 = new ClockSignal.TransferImpl();
        this.baud150 = new ClockSignal.TransferImpl();
        this.baud300 = new ClockSignal.TransferImpl();
        this.baud600 = new ClockSignal.TransferImpl();
        this.baud1200 = new ClockSignal.TransferImpl();

        // The xtal signal will be produced by the MP-A at approximately
        // 1.7971 MHz which drives everything else in the system.
        this.baudGenerator = new MC14411BaudGenerator(xtal, false, true);
        // The MP-A feeds the full clock line of the baud generator
        // into a divide-by-2 circuit and then feeds that into a pair
        // of non-overlapping dual phase generators to create the
        // inverted non-overlapping clocks phi1 and phi2.
        Divider IC20 = new Divider(2);
        IC20.addListener(new DualPhase(phi1, phi2));
        baudGenerator.addListener(IC20, 16);
        baudGenerator.addListener(baud110, 13);
        baudGenerator.addListener(baud150, 11);
        baudGenerator.addListener(baud300,  9);
        baudGenerator.addListener(baud600,  8);
        baudGenerator.addListener(baud1200, 7);

        addListeners();
    }

    // xtal is the clock signal that should be driven to run the emulation.
    private final ClockSignal.Crystal xtal;

    // phi1 and phi2 are generated from the xtal signal.
    private final ClockSignal.Transfer phi1;
    private final ClockSignal.Transfer phi2;

    private final ClockSignal.Transfer baud110;
    private final ClockSignal.Transfer baud150;
    private final ClockSignal.Transfer baud300;
    private final ClockSignal.Transfer baud600;
    private final ClockSignal.Transfer baud1200;

    private final M6800 cpu;
    private final MC14411BaudGenerator baudGenerator;

    private SS50BusState busState;

    @Override
    public void connect(SWTPc_MP_B_emu MP_B) {
        phi1.addListener(MP_B::phi1Listener);
        phi2.addListener(MP_B::phi2Listener);
        baud110.addListener(MP_B.baud110Line());
        baud150.addListener(MP_B.baud150Line());
        baud300.addListener(MP_B.baud300Line());
        baud600.addListener(MP_B.baud600Line());
        baud1200.addListener(MP_B.baud1200Line());
        busState = MP_B.getSS50State();
    }

    private void addListeners() {
        phi1.addListener(this::phi1Driver);
        phi2.addListener(this::phi2Driver);
    }

    private void phi1Driver(boolean isRising) {
        if (isRising) {
            busState.setAddressLines(null);
            cpu.phi1StateChanged(true);
            busState.setControlLines(cpu.isRead ? SS50BusState.SS_CTRL_READ_LINE : 0);
        } else {
            busState.setAddressLines(cpu.getAddressLines());
            cpu.phi1StateChanged(false);
        }
    }

    private void phi2Driver(boolean isRising) {
        if (isRising) {
            busState.setDataLines(cpu.getDataLines());
            cpu.phi2StateChanged(true);
        } else {
            cpu.setDataLines(busState.getDataLines());
            cpu.phi2StateChanged(false);
        }
    }

    public ClockSignal.Source phi1() { return phi1; }
    public ClockSignal.Source phi2() { return phi2; }
    public ClockSignal.Source baud110() { return baud110; }
    public ClockSignal.Source baud150() { return baud150; }
    public ClockSignal.Source baud300() { return baud300; }
    public ClockSignal.Source baud600() { return baud600; }
    public ClockSignal.Source baud1200() { return baud1200; }
}
