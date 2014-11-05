package org.flarbear.swtpc6800;

public interface RS232Device {
    public void sendTo(byte data);
    public void connectTo(RS232Device otherdevice);
    public void waitForCTS();
}
