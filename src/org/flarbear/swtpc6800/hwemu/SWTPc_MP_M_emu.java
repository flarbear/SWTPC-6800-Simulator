/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import org.flarbear.swtpc6800.hwemu.SignalState.ThreeToEightDecoder;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_M_emu extends SS50Card {
    public SWTPc_MP_M_emu(char addressSelect, int numBanks) {
        if (numBanks != 1 && numBanks != 2 && numBanks != 4) {
            throw new IllegalArgumentException("only 1, 2, or 4 1k banks are supported on MP-M");
        }
        if (addressSelect < 0 || addressSelect > 7) {
            throw new IllegalArgumentException("MP-M address select must be 0-7");
        }
        this.addressSelect = addressSelect;
        baseAddress = (char) (addressSelect << 12);
        storage = new byte[numBanks * 0x0400];
    }

    private final int addressSelect;
    private final char baseAddress;
    private final byte storage[];

    private SS50Bus busState;

    @Override
    public void connect(SS50Bus busState) {
        if (this.busState != null) {
            throw new IllegalStateException("MP-M already installed on MP-B");
        }
        this.busState = busState;
        ThreeToEightDecoder decoder = new ThreeToEightDecoder(busState.A14(),
                                                              busState.A13(),
                                                              busState.A12(),
                                                              busState.VMA().and(busState.A15().not()));
        final SignalState.LineState selected = decoder.selectorLine(addressSelect);
        busState.Phi2Rising().addListener((t) -> {
            if (selected.isHigh()) {
                access();
            }
        });
    }

    private void access() {
        int index = busState.getAddress() - baseAddress;
        if (busState.isHigh(SS50Bus.Line.R_W)) {
            busState.setData(storage[index]);
        } else {
            storage[index] = busState.getData();
        }
    }
}
