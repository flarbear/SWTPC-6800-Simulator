/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class SWTPc_MP_M extends SS50Card implements ClockSignal.Listener {
    public SWTPc_MP_M(char addressSelect, int numBanks) {
        if (numBanks != 1 && numBanks != 2 && numBanks != 4) {
            throw new IllegalArgumentException("only 1, 2, or 4 banks are supported on MP-M");
        }
        if (addressSelect < 0 || addressSelect > 7) {
            throw new IllegalArgumentException("MP-M address bank must be 0-7");
        }
        selectMask = 0xF000;
        selectValue = addressSelect << 12;
        storage = new byte[numBanks * 0x0400];
    }

    private int selectMask;
    private int selectValue;

    private byte storage[];

    private SS50BusState busState;

    @Override
    public void connect(SWTPc_MP_B_emu MP_B) {
        this.busState = MP_B.getSS50State();
        busState.phi2().addListener(this);
    }

    @Override
    public void clockStateChanged(boolean isRising) {
        if (isRising) {
            Character addressLines = busState.getAddressLines();
            if (addressLines != null && (addressLines & selectMask) == selectValue) {
                int index = addressLines - selectValue;
                if (index < storage.length) {
                    if (busState.isRead()) {
                        busState.setDataLines(storage[index]);
                    } else {
                        storage[index] = busState.getDataLines();
                    }
                }
            }
        }
    }
}
