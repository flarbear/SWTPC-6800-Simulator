/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.simulator;

public interface RS232Device {
    public void sendTo(byte data);
    public void connectTo(RS232Device otherdevice);
    public void waitForCTS();
}
