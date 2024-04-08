/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import java.util.function.BiFunction;

/**
 * Provides interfaces and utilities to model various logic signal paradigms.
 * 
 * A LineState is a passive state for a single line. You can filter and combine these
 * objects using logic, but they will not proactively fire when the associated line
 * changes. Instead you query their current state as needed.
 * 
 * A Trigger is an active line state object that will notify listeners when its
 * associated signal transitions from low to high (Rising) or high to low (Falling).
 * Clock signals are implemented as triggers and they drive other logic to perform
 * circuit logic.
 * 
 * @author Flar
 */
public interface SignalState {
    public enum Level {
        FLOATING,
        LOW,
        HIGH;

        public boolean isLow() {
            return this == LOW;
        }

        public boolean isHigh() {
            return this == HIGH;
        }

        public Level not() {
            return switch (this) {
                case LOW ->      HIGH;
                case HIGH ->     LOW;
                case FLOATING -> FLOATING;
            };
        }

        public Level and(Level other) {
            return Level(isHigh() && other.isHigh());
        }

        public Level nand(Level other) {
            return Level(!(isHigh() && other.isHigh()));
        }

        public Level or(Level other) {
            return Level(isHigh() || other.isHigh());
        }

        public Level nor(Level other) {
            return Level(!(isHigh() || other.isHigh()));
        }
    };

    public static Level Level(boolean isHigh) {
        return isHigh ? Level.LOW : Level.HIGH;
    }

    public interface LineState {
        public Level getState();

        public static final LineState FLOATING = () -> Level.FLOATING;
        public static final LineState HIGH = () -> Level.HIGH;
        public static final LineState LOW = () -> Level.LOW;

        default public boolean isLow() {
            return getState().isLow();
        }

        default public boolean isHigh() {
            return getState().isHigh();
        }

        default public LineState not() {
            return Not(this);
        }

        default public LineState and(LineState other) {
            return And(this, other);
        }

        default public LineState nand(LineState other) {
            return Nand(this, other);
        }

        default public LineState or(LineState other) {
            return Or(this, other);
        }

        default public LineState nor(LineState other) {
            return Nor(this, other);
        }

        public static class Boolean implements LineState {
            private boolean state;
            public void setState(boolean state) { this.state = state; }
            @Override public Level getState() { return Level(state); }
            public LineState readOnly() {
                return () -> getState();
            }
        }
    }

    public enum Transition {
        RISING((p,c) -> p.isLow() && c.isHigh()),
        FALLING((p,c) -> p.isHigh() && c.isLow());

        private Transition(BiFunction<Level, Level, Boolean> triggerFunction) {
            this.triggerFunction = triggerFunction;
        }

        private final BiFunction<Level, Level, Boolean> triggerFunction;

        public boolean triggers(Level previous, Level current) {
            return triggerFunction.apply(previous, current);
        }

        public Transition not() {
            return switch(this) {
                case RISING -> FALLING;
                case FALLING -> RISING;
            };
        }
    }

    @FunctionalInterface
    public interface Listener {
        public void signalFired(Transition transition);
    }

    @FunctionalInterface
    public interface Trigger {
        public void addListener(Listener listener);
    }

    public interface Transfer extends Trigger, Listener {}

    public static Listener chain(Listener first, Listener second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new ListenerChainer(first, second);
    }

    static class ListenerChainer implements Listener {
        ListenerChainer(Listener first, Listener second) {
            if (first == null || second == null) {
                throw new IllegalArgumentException("Both listeners must be specified");
            }
            this.first = first;
            this.second = second;
        }

        private Listener first;
        private Listener second;

        @Override
        public void signalFired(Transition transition) {
            first.signalFired(transition);
            second.signalFired(transition);
        }
    }

    public static class TriggerImpl implements Trigger {
        protected Listener listenerHead;

        protected void notifyListeners(Transition transition) {
            if (listenerHead != null) {
                listenerHead.signalFired(transition);
            }
        }

        @Override
        public void addListener(Listener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("Listener cannot be null");
            }
            listenerHead = chain(listenerHead, listener);
        }
    }

    public class TransferImpl extends TriggerImpl implements Transfer {
        @Override
        public void signalFired(Transition transition) {
            notifyListeners(transition);
        }
    }

    public static class Divider extends TransferImpl {
        public Divider(int divisor) {
            if (divisor <= 0) {
                throw new IllegalArgumentException("Divider divisor must be positive");
            }
            this.divisor = divisor;
        }

        private final int divisor;
        private int count;
        private Transition outputPhase;

        @Override
        public void signalFired(Transition transition) {
            if (outputPhase == null) {
                // Align our output - a RISING output on the first RISING input
                if (transition != Transition.RISING) {
                    return;
                }
                // Our first transition is from FALLING to RISING
                outputPhase = Transition.FALLING;
            } else if (++count < divisor) {
                return;
            }
            count = 0;
            outputPhase = outputPhase.not();
            notifyListeners(outputPhase);
        }
    }

    public static class Rising extends TransferImpl {
        @Override
        public void signalFired(Transition transition) {
            if (transition == Transition.RISING) {
                notifyListeners(transition);
            }
        }
    }

    public static class Falling extends TransferImpl {
        @Override
        public void signalFired(Transition transition) {
            if (transition == Transition.FALLING) {
                notifyListeners(transition);
            }
        }
    }

    // Notifies a pair of non-overlapping out-of-phase clock listeners
    // from a single phase input clock.
    public static class DualPhase implements Listener {
        public DualPhase(Listener inPhase, Listener outPhase) {
            this.inPhase = inPhase;
            this.outPhase = outPhase;
        }

        private final Listener inPhase;
        private final Listener outPhase;

        @Override
        public void signalFired(Transition transition) {
            if (transition == Transition.RISING) {
                outPhase.signalFired(Transition.FALLING);
                inPhase.signalFired(Transition.RISING);
            } else {
                inPhase.signalFired(Transition.FALLING);
                outPhase.signalFired(Transition.RISING);
            }
        }
    }

    public static LineState Not(LineState inputA) {
        return () -> Level(inputA.isLow());
    }

    public static LineState And(LineState inputA, LineState inputB) {
        return () -> inputA.getState().and(inputB.getState());
    }

    public static LineState Nand(LineState inputA, LineState inputB) {
        return () -> inputA.getState().nand(inputB.getState());
    }

    public static LineState Or(LineState inputA, LineState inputB) {
        return () -> inputA.getState().or(inputB.getState());
    }

    public static LineState Nor(LineState inputA, LineState inputB) {
        return () -> inputA.getState().nor(inputB.getState());
    }

    public static class ThreeToEightDecoder {
        public ThreeToEightDecoder(LineState lineA, LineState lineB, LineState lineC, LineState enable) {
            this.enable = enable;
            this.selectLineA = lineA;
            this.selectLineB = lineB;
            this.selectLineC = lineC;
        }

        private final LineState enable;
        private final LineState selectLineA;
        private final LineState selectLineB;
        private final LineState selectLineC;
        private final LineState outputs[] = new LineState[8];

        public int selector() {
            if (enable.getState().isLow()) {
                return -1;
            }
            int line = 0;
            if (selectLineA.isHigh()) line |= 4;
            if (selectLineB.isHigh()) line |= 2;
            if (selectLineC.isHigh()) line |= 1;
            return line;
        }

        public LineState selectorLine(int line) {
            if (line < 0 || line > 7) {
                throw new IllegalArgumentException("Output line must be from 0 to 7");
            }
            LineState state = outputs[line];
            if (state == null) {
                outputs[line] = state = () -> Level(selector() == line);
            }
            return state;
        }
    }
}
