package org.flarbear.swtpc6800;

public class MemoryDevice8x16 {
    char base;
    char addrmask;
    char end;
    byte mem[];

    public MemoryDevice8x16(char base, int len) {
        this(base, (char) 0xFFFF, len);
    }

    public MemoryDevice8x16(char base, char addrmask, int len) {
        if (len < 0 || (0x10000 - base) < len) {
            throw new IllegalArgumentException("bad length");
        }
        if (base != (base & addrmask)) {
            throw new InternalError("mask and base address conflict");
        }
        this.base = base;
        this.addrmask = addrmask;
        this.end = (char) (base + len - 1);
        this.mem = new byte[len];
    }

    public boolean maps(char addr) {
        addr &= addrmask;
        return (addr >= base && addr <= end);
    }

    public byte load(char addr) {
        addr &= addrmask;
        if (addr >= base && addr <= end) {
            return mem[addr - base];
        }
        return 0;
    }

    public void store(char addr, byte data) {
        addr &= addrmask;
        if (addr >= base && addr <= end) {
            mem[addr - base] = data;
        }
    }
}
