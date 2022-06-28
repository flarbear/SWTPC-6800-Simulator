/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class ClockSignal {
    @FunctionalInterface
    public interface Source {
        public void addListener(Listener listener);
    }

    @FunctionalInterface
    public interface Listener {
        public void clockStateChanged(boolean isRising);
    }

    public interface Transfer extends Source, Listener {}

    private static class ListenerChain implements Listener {
        ListenerChain(Listener listenerA, Listener listenerB) {
            this.listenerA = listenerA;
            this.listenerB = listenerB;
        }

        private final Listener listenerA;
        private final Listener listenerB;

        @Override
        public void clockStateChanged(boolean isRising) {
            listenerA.clockStateChanged(isRising);
            listenerB.clockStateChanged(isRising);
        }
    }

    public static class SourceImpl implements Source {
        protected Listener listenerHead;

        @Override
        public void addListener(Listener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("Listener cannot be null");
            }
            if (listenerHead == null) {
                listenerHead = listener;
            } else {
                listenerHead = new ListenerChain(listenerHead, listener);
            }
        }
    }

    public static class TransferImpl extends SourceImpl implements Transfer {
        @Override
        public void clockStateChanged(boolean isRising) {
            if (listenerHead != null) {
                listenerHead.clockStateChanged(isRising);
            }
        }
    }

    public static class Crystal extends SourceImpl {
        private boolean phase;

        public boolean state() {
            return phase;
        }

        public void pump() {
            phase = !phase;
            if (listenerHead != null) {
                listenerHead.clockStateChanged(phase);
            }
        }
    }

    public static class DualPhase implements Listener {
        public DualPhase(Transfer phi1, Transfer phi2) {
            this.phi1 = phi1;
            this.phi2 = phi2;
        }

        private final Transfer phi1;
        private final Transfer phi2;

        public Source getPhi1() {
            return phi1;
        }

        public Source getPhi2() {
            return phi2;
        }

        @Override
        public void clockStateChanged(boolean isRising) {
            // The two clocks are non-overlapping so we first
            // lower the phase that is falling before raising
            // the phase that is rising.
            if (isRising) {
                phi2.clockStateChanged(false);
                phi1.clockStateChanged(true);
            } else {
                phi1.clockStateChanged(false);
                phi2.clockStateChanged(true);
            }
        }
    }

    public static class Divider extends TransferImpl {
        public Divider(int divisor) {
            this.divisor = divisor;
        }

        private final int divisor;
        private int count;
        private boolean phase;

        @Override
        public void clockStateChanged(boolean isRising) {
            if (isRising) {
                if (++count >= divisor) {
                    count = 0;
                    phase = !phase;
                    super.clockStateChanged(phase);
                }
            }
        }
    }

    private ClockSignal() {}
}
