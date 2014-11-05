package org.flarbear.swtpc6800;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.IOException;

public class SWTPc_MP_A extends SS50Card {
    public static final char SCRATCHPAD_ADDR = 0xA000;
    public static final int  SCRATCHPAD_SIZE = 128;
    public static final char ROM_ADDR        = 0xE000;
    public static final char ROM_MASK        = 0xE7FF;
    public static final int  ROM_SIZE        = 2048;

    MemoryDevice8x16 scratchpad;
    RomDevice8x16 rom;
    Motorola6800 processor;

    public SWTPc_MP_A() {
        scratchpad = new MemoryDevice8x16(SCRATCHPAD_ADDR, SCRATCHPAD_SIZE);
        rom = new RomDevice8x16(ROM_ADDR, ROM_MASK, ROM_SIZE);
        loadRom();
        swapRom();
        processor = new Motorola6800();
    }

    @Override
    public void connectTo(SS50Bus bus) {
        super.connectTo(bus);
        processor.connectTo(bus);
    }

    @Override
    public void powerOn() {
        processor.powerOn();
    }

    @Override
    public void powerOff() {
        processor.powerOff();
    }

    @Override
    public void raiseIRQ() {
        processor.raiseIRQ();
    }

    @Override
    public void lowerIRQ() {
        processor.lowerIRQ();
    }

    @Override
    public void tripNMI() {
        processor.tripNMI();
    }

    @Override
    public void raiseRESET() {
        processor.raiseRESET();
    }

    @Override
    public void lowerRESET() {
        processor.lowerRESET();
    }

    // The MP-A is responsible for responding to a Manual Reset by turning
    // around and signalling the Reset line in the system busses
    @Override
    public void raiseManualReset() {
        theBus.raiseRESET();
    }

    @Override
    public void lowerManualReset() {
        theBus.lowerRESET();
    }

    @Override
    public boolean maps(char addr) {
        return (rom.maps(addr) || scratchpad.maps(addr));
    }

    @Override
    public byte load(char addr) {
        return (rom.maps(addr) ? rom.load(addr) : scratchpad.load(addr));
    }

    @Override
    public void store(char addr, byte data) {
        scratchpad.store(addr, data);
    }

    private void swapRom() {
        // Replicate the second 1024 bytes of ROM into the first 1024 bytes
        for (int i = 0; i < 1024; i++) {
            rom.burn((char) i, rom.load((char) (0xE000 + i + 1024)));
        }
    }

    private int readHex(BufferedInputStream bis, int numdigits)
        throws IOException
    {
        int ret = 0;
        while (--numdigits >= 0) {
            int ch = bis.read();
            if (ch < 0) {
                return ch;
            }
            if (ch >= '0' && ch <= '9') {
                ret = (ret << 4) | (ch - '0');
            } else if (ch >= 'A' && ch <= 'F') {
                ret = (ret << 4) | (ch - 'A' + 10);
            } else if (ch >= 'a' && ch <= 'f') {
                ret = (ret << 4) | (ch - 'a' + 10);
            } else {
                return -1;
            }
        }
        return ret;
    }

    private void loadRom() {
        InputStream is = null;
        int bytesloaded = 0;
        try {
            is = getClass().getResourceAsStream("resources/SwtMik.S19");
            BufferedInputStream bis = new BufferedInputStream(is);

            int ch;
            boolean foundS = false;
            outer_loop:
            while ((ch = bis.read()) >= 0) {
                if (ch == 'S') {
                    foundS = true;
                    continue;
                }
                if (foundS && ch == '9') {
                    break;
                }
                if (foundS && ch == '1') {
                    int numbytes = readHex(bis, 2);
                    if (numbytes < 0) {
                        break;
                    }
                    if (numbytes < 3) {
                        throw new InternalError("bogus packet length");
                    }
                    int addr = readHex(bis, 4);
                    if (addr < 0) {
                        break;
                    }
                    int sum = numbytes + (addr >> 8) + (addr & 0xFF);
                    numbytes -= 3;
                    for (int i = 0; i < numbytes; i++) {
                        int b = readHex(bis, 2);
                        if (b < 0) {
                            break outer_loop;
                        }
                        sum += b;
                        rom.burn((char) addr++, (byte) b);
                        bytesloaded++;
                    }
                    int chksum = readHex(bis, 2);
                    if (chksum < 0) {
                        break;
                    }
                    if (((sum + chksum) & 0xff) != 0xff) {
                        throw new InternalError("Bad checksum in SWTBUG ROM");
                    }
                }
                foundS = false;
            }
            if (bytesloaded != 2048) {
                throw new InternalError("Wrong number of SWTBUG bytes loaded");
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new InternalError("IO Exception reading SWTBUG ROM file");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e2) {
                }
            }
        }
    }
}
