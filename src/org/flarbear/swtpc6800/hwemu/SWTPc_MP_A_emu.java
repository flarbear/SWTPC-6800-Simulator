/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.Divider;
import org.flarbear.swtpc6800.hwemu.SignalState.DualPhase;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_A_emu extends SS50Card {
    public SWTPc_MP_A_emu() {
        this.cpu = new M6800();

        // The xtal signal will be produced by the MP-A at approximately
        // 1.7971 MHz which drives everything else in the system.
        this.baudGenerator = new MC14411BaudGenerator(false, true);
        baudGenerator.addListener((t) -> baudLines ^= SS50Bus.Line.Baud110.bit, 13);
        baudGenerator.addListener((t) -> baudLines ^= SS50Bus.Line.Baud150.bit, 11);
        baudGenerator.addListener((t) -> baudLines ^= SS50Bus.Line.Baud300.bit,  9);
        baudGenerator.addListener((t) -> baudLines ^= SS50Bus.Line.Baud600.bit,  8);
        baudGenerator.addListener((t) -> baudLines ^= SS50Bus.Line.Baud1200.bit, 7);

        // The MP-A feeds the full clock line of the baud generator
        // into a divide-by-2 circuit and then feeds that into a pair
        // of non-overlapping dual phase generators to create the
        // inverted non-overlapping clocks phi1 and phi2.
        Divider IC20 = new Divider(2);
        IC20.addListener(new DualPhase(this::phi1Phase, this::phi2Phase));
        baudGenerator.addListener(IC20, 16);
    }

    private static final long BAUD_MASK = SS50Bus.Mask(SS50Bus.Line.Baud110,
                                                       SS50Bus.Line.Baud150,
                                                       SS50Bus.Line.Baud300,
                                                       SS50Bus.Line.Baud600,
                                                       SS50Bus.Line.Baud1200);
    private static final long CLEAN_MASK = ~(SS50Bus.Mask(SS50Bus.Line.Phi1,
                                                          SS50Bus.Line.Phi2) |
                                             BAUD_MASK);

    private final M6800 cpu;
    private final MC14411BaudGenerator baudGenerator;
    private long baudLines;

    private SS50Bus busState;

    private final boolean verbose = true;

    public void powerOn() {
        Thread t = new Thread(this::runClock);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @Override
    public void connect(SS50Bus busState) {
        this.busState = busState;
        // For now the cpu.phi1,2 methods are called directly from the MP-A dualPhase method
        // It would be nice to rework the logic so that things happen more naturally on the
        // dual phase clock pulses.
//        busState.onPhi1Rising(() -> cpu.phi1Leading());
//        busState.onPhi2Rising(() -> cpu.phi2Leading());
//        busState.onPhi2Falling(() -> cpu.phi2Trailing());
    }

    @SuppressWarnings("CallToThreadYield")
    private void runClock() {
        long start = System.nanoTime();
        long cycles = 0;
        SignalState.Transition phase = SignalState.Transition.FALLING;
        do {
            phase = phase.not();
            baudGenerator.pumpClock(phase);
            if (phase == SignalState.Transition.RISING && (++cycles & 0xFFFFF) == 0) {
                if (verbose) {
                    double elapsed = (System.nanoTime() - start) / 1000.0 / 1000.0 / 1000.0;
                    double MHz = cycles / elapsed;
                    System.out.printf("%d cycles in %f seconds == %f MHz\n", cycles, elapsed, MHz);
                }
                Thread.yield();
            }
        } while(true);
    }

    private long cleanedBusLines() {
        return (busState.currentLines() & CLEAN_MASK) | baudLines;
    }

    private void phi1Phase(SignalState.Transition transition) {
        if (transition == SignalState.Transition.RISING) {
            // Dual-phase Phi1 leading edge, the CPU has not produced any signals for the bus yet
            // and typically has open transceiver ports.
            // But the baud rate generator has computed new baud lines.
            cpu.phi1Leading();
            busState.pushLines(baudLines | SS50Bus.Line.Phi1.bit);
        } else {
            busState.pushLines(baudLines);
        }
    }

    private void phi2Phase(SignalState.Transition transition) {
        long busLines = cleanedBusLines();
        if (transition == SignalState.Transition.RISING) {
            // Dual-phase Phi2 leading edge, we transfer the CPU data to the bus and raise the Phi2 line.
            cpu.phi2Leading();
            if (cpu.getAddressLines() != null) {
                busLines |= SS50Bus.AddressMask(cpu.getAddressLines());
                busLines |= SS50Bus.Line.VMA.bit;
            }
            if (cpu.isRead()) {
                busLines |= SS50Bus.Line.R_W.bit;
            } else if (cpu.getDataLines() != null) {
                busLines |= SS50Bus.DataMask(cpu.getDataLines());
            }
            busState.pushLines(busLines | SS50Bus.Line.Phi2.bit);
        } else {
            // Dual-phase Phi2 trailing edge, we grab the state (data lines) from the bus and transfer it to the CPU.
            busLines = busState.currentLines() & ~SS50Bus.Mask(SS50Bus.Line.Phi1, SS50Bus.Line.Phi2);
            cpu.setDataLines(SS50Bus.getDataLines(busLines));
            cpu.phi2Trailing();
            busState.pushLines(busLines);
        }
    }
}
