package org.flarbear.swtpc6800;

public class SWTPc_MP_M extends SS50Card {
    MemoryDevice8x16 mem;

    public SWTPc_MP_M(char base, int numbanks) {
        if (numbanks != 1 && numbanks != 2 && numbanks != 4) {
            throw new IllegalArgumentException("only 1, 2, or 4 banks are supported on MP_M");
        }
        if ((base & 0xF000) != base) {
            throw new IllegalArgumentException("card must be based on a 4K boundary");
        }
        mem = new MemoryDevice8x16(base, numbanks * 1024);
    }

    @Override
    public boolean maps(char addr) {
        return mem.maps(addr);
    }

    @Override
    public byte load(char addr) {
        return mem.load(addr);
    }

    @Override
    public void store(char addr, byte data) {
        mem.store(addr, data);
    }
}
