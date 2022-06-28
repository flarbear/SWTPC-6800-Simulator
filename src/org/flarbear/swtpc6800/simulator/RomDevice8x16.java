/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.simulator;

public class RomDevice8x16 extends MemoryDevice8x16 {
    public RomDevice8x16(char base, int len) {
        super(base, len);
    }

    public RomDevice8x16(char base, char addrmask, int len) {
        super(base, addrmask, len);
    }

    @Override
    public void store(char addr, byte data) {
    }

    public void burn(char reladdr, byte data) {
        mem[reladdr] = data;
    }
}
