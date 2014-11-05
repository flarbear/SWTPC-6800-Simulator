/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

public interface Bus8x16 {
    public byte load(char address);
    public void store(char address, byte data);
}
