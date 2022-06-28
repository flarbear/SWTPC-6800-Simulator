/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

/**
 *
 * @author Flar
 */
public abstract class RS232DE9 {
    public class DE9 {
        public static final int DCD_BIT = 0b000000001;
        public static final int RxD_BIT = 0b000000010;
        public static final int TxD_BIT = 0b000000100;
        public static final int DTR_BIT = 0b000001000;
        //                                0b000010000;   // Signal Ground
        public static final int DSR_BIT = 0b000100000;
        public static final int RTS_BIT = 0b001000000;
        public static final int CTS_BIT = 0b010000000;
        public static final int RI_BIT  = 0b100000000;

        public interface DTE {
            public void sendDE9(int lines);
            public int getDE9(int lines);
        }

        public interface DCE {
            public void sendDE9(int lines);
            public int getDE9(int lines);
        }
    }

    public class DB25 {
        //                                0b0000000000000000000000001;   // Protective Ground
        public static final int TxD_BIT = 0b0000000000000000000000010;
        public static final int RxD_BIT = 0b0000000000000000000000100;
        public static final int RTS_BIT = 0b0000000000000000000001000;
        public static final int CTS_BIT = 0b0000000000000000000010000;
        public static final int DSR_BIT = 0b0000000000000000000100000;
        //                                0b0000000000000000001000000;   // Signal Ground
        public static final int DCD_BIT = 0b0000000000000000010000000;
        public static final int DTR_BIT = 0b0000010000000000000000000;
        public static final int RI_BIT  = 0b0001000000000000000000000;

        public interface DTE {
            public void sendDB25(int lines);
            public int getDB25(int lines);
        }

        public interface DCE {
            public void sendDE9(int lines);
            public int getDE9(int lines);
        }
    }

    static {
        assert((DE9.DCD_BIT | DE9.RxD_BIT | DE9.TxD_BIT | DE9.DTR_BIT |
                DE9.DSR_BIT | DE9.RTS_BIT | DE9.CTS_BIT | DE9.RI_BIT) == 0b111101111);
        assert((DB25.DCD_BIT | DB25.RxD_BIT | DB25.TxD_BIT | DB25.DTR_BIT |
                DB25.DSR_BIT | DB25.RTS_BIT | DB25.CTS_BIT | DB25.RI_BIT) == 0b0001010000000000010111110);
    }
}
