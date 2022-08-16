/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public class BusState {
    @FunctionalInterface
    public static interface Source {
        public void addListener(Listener listener);
    }

    @FunctionalInterface
    public static interface Listener {
        public void busStateChanged();
    }

    public interface Transfer extends Source, Listener {}

    public static class SourceImpl implements Source {
        protected Listener listenerHead;

        protected void notifyListeners() {
            if (listenerHead != null) {
                listenerHead.busStateChanged();
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

    public static class TransferImpl extends SourceImpl implements Transfer {
        @Override
        public void busStateChanged() {
            notifyListeners();
        }
    }

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
        public void busStateChanged() {
            first.busStateChanged();
            second.busStateChanged();
        }
    }
}
