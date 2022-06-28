/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.simulator;

public interface SS50Bus extends Bus8x16 {
    public abstract void raiseRESET();
    public abstract void lowerRESET();
    public abstract void raiseIRQ();
    public abstract void lowerIRQ();	
    public abstract void tripNMI();
}
