package org.flarbear.swtpc6800;

/**
 * This interface provides services to SS30Card objects to interface
 * back to the system they are plugged into.
 */
public interface SS30Bus {
    public abstract void raiseIRQ();
    public abstract void lowerIRQ();	
}
