/*
 * Copyright 2014, 2016, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

@SuppressWarnings("PointlessBitwiseExpression")
public class Motorola6800
    implements Runnable
{
    private static final int DEBUG = 0;
    public static final boolean STATS = false;

    Bus8x16 theBus;

    private Thread executor;
    private boolean HALTraised;
    private boolean RESETraised;
    private boolean NMIraised;
    private boolean IRQraised;
    private boolean isRunning;

    private char PCreg;
    private char SPreg;
    private byte ccode;

    private char IXreg;

    private byte accA;
    private byte accB;

    public static final int SHIFT_C = 0;    // Carry
    public static final int SHIFT_V = 1;    // Overflow
    public static final int SHIFT_Z = 2;    // Zero
    public static final int SHIFT_N = 3;    // Negative
    public static final int SHIFT_I = 4;    // Interrupt mask
    public static final int SHIFT_H = 5;    // Half carry

    public static final int COND_C = (1 << SHIFT_C);    // Carry
    public static final int COND_V = (1 << SHIFT_V);    // Overflow
    public static final int COND_Z = (1 << SHIFT_Z);    // Zero
    public static final int COND_N = (1 << SHIFT_N);    // Negative
    public static final int COND_I = (1 << SHIFT_I);    // Interrupt mask
    public static final int COND_H = (1 << SHIFT_H);    // Half carry
    public static final int COND_NBITS = 6;
    public static final int COND_MASK = ((1 << COND_NBITS) - 1);

    public static final char IRQ_JUMP_ADDR = 0xFFF8;
    public static final char SWI_JUMP_ADDR = 0xFFFA;
    public static final char NMI_JUMP_ADDR = 0xFFFC;
    public static final char RESET_JUMP_ADDR = 0xFFFE;

    public Motorola6800() {
        if (STATS) {
            this.opTotals = new int[256];
            this.opCounts = new int[256];
        }
    }

    public Motorola6800(Bus8x16 bus) {
        this();
        connectTo(bus);
    }

    public synchronized final void connectTo(Bus8x16 bus) {
        if (this.theBus != null) {
            throw new InternalError("Processor already installed in a system");
        }
        this.theBus = bus;
    }

    private byte load8(char addr) {
        return theBus.load(addr);
    }

    private char load16(char addr) {
        byte H = theBus.load(addr++);
        byte L = theBus.load(addr);
        return (char) ((H << 8) | (L & 0xff));
    }

    private void store8(char addr, byte val) {
        theBus.store(addr, val);
    }

    private void store16(char addr, char val) {
        byte H = (byte) (val >> 8);
        byte L = (byte) val;
        theBus.store(addr++, H);
        theBus.store(addr, L);
    }

    public synchronized void powerOn() {
        executor = new Thread(this);
        executor.setPriority(Thread.MIN_PRIORITY);
        IRQraised = NMIraised = RESETraised = false;
        ccode |= COND_I;
        PCreg = load16(RESET_JUMP_ADDR);
        notifyAll();
    }

    public synchronized void powerOff() {
        executor = null;
        notifyAll();
    }

    public synchronized void raiseIRQ() {
        IRQraised = true;
        notifyAll();
    }

    public synchronized void tripNMI() {
        NMIraised = true;
        notifyAll();
    }

    public synchronized void raiseRESET() {
        RESETraised = true;
        waitForHalt();
        if (RESETraised) {
            PCreg = load16(RESET_JUMP_ADDR);
        }
    }

    public synchronized void lowerIRQ() {
        IRQraised = false;
        notifyAll();
    }

    public synchronized void lowerRESET() {
        RESETraised = false;
        if (!executor.isAlive()) {
            executor.start();
        }
        notifyAll();
    }

    private synchronized void waitForHalt() {
        while ((RESETraised || HALTraised) && isRunning) {
            notifyAll();
            try {
                wait();
            } catch (InterruptedException e) {
                powerOff();
                return;
            }
        }
    }

    private synchronized boolean handleControlLines() {
        while (RESETraised || HALTraised) {
            isRunning = false;
            notifyAll();
            try {
                wait();
            } catch (InterruptedException e) {
                executor = null;
                return false;
            }
            isRunning = true;
        }
        if (NMIraised) {
            NMIraised = false;
            handleInterrupt(NMI_JUMP_ADDR);
        } else if (IRQraised && (ccode & COND_I) == 0) {
            handleInterrupt(IRQ_JUMP_ADDR);
        }
        return true;
    }

    private void push(byte b) {
        theBus.store(SPreg--, b);
    }

    private void push(char c16) {
        push((byte) c16);
        push((byte) (c16 >> 8));
    }

    private byte pop8() {
        return theBus.load(++SPreg);
    }

    private char pop16() {
        return (char) ((pop8() << 8) | (pop8() & 0xff));
    }

    private void handleInterrupt(char jumpAddr) {
        push(PCreg);
        push(IXreg);
        push(accA);
        push(accB);
        push(ccode);
        ccode |= COND_I;
        PCreg = load16(jumpAddr);
    }

    private static final int OP_SHIFT  = 0;
    private static final int OP_ILLEGAL  = 0;
    private static final int OP_NOP      = 1;
    private static final int OP_MOV8     = 2;
    private static final int OP_ADD      = 3;
    private static final int OP_SUB      = 4;
    private static final int OP_COM      = 5;
    private static final int OP_NEG      = 6;
    private static final int OP_AND      = 7;
    private static final int OP_EOR      = 8;
    private static final int OP_ORA      = 9;
    private static final int OP_ROL      = 10;
    private static final int OP_ROR      = 11;
    private static final int OP_ASL      = 12;
    private static final int OP_ASR      = 13;
    private static final int OP_LSR      = 14;
    private static final int OP_DAA      = 15;
    private static final int OP_MOV16    = 16;
    private static final int OP_ADD16    = 17;
    private static final int OP_SUB16    = 18;
    private static final int OP_BCOND    = 19;
    private static final int OP_BSR      = 20;
    private static final int OP_JMP      = 21;
    private static final int OP_JSR      = 22;
    private static final int OP_RTS      = 23;
    private static final int OP_SWI      = 24;
    private static final int OP_RTI      = 25;
    private static final int OP_WAI      = 26;
    private static final int OP_MODCC    = 27;
    private static final int OP_NBITS  = 5;
    private static final int OP_MASK   = ((1 << OP_NBITS) - 1);

    private static final int MODE_ACCA    = 0;
    private static final int MODE_ACCB    = 1;
    private static final int MODE_CCODE   = 2;
    private static final int MODE_IXREG   = 3;
    private static final int MODE_SPREG   = 4;
    private static final int MODE_MEMORY  = 5;
    private static final int MODE_ZERO    = 6;
    private static final int MODE_ONE     = 7;
    private static final int MODE_NBITS = 3;
    private static final int MODE_MASK  = (1 << MODE_NBITS) - 1;

    private static final int SRC1_SHIFT = OP_SHIFT + OP_NBITS;
    private static final int SRC1_ACCA     = (MODE_ACCA   << SRC1_SHIFT);
    private static final int SRC1_ACCB     = (MODE_ACCB   << SRC1_SHIFT);
    private static final int SRC1_CCODE    = (MODE_CCODE  << SRC1_SHIFT);
    private static final int SRC1_IXREG    = (MODE_IXREG  << SRC1_SHIFT);
    private static final int SRC1_SPREG    = (MODE_SPREG  << SRC1_SHIFT);
    private static final int SRC1_MEMORY   = (MODE_MEMORY << SRC1_SHIFT);
    private static final int SRC1_ZERO     = (MODE_ZERO   << SRC1_SHIFT);
    private static final int SRC1_ONE      = (MODE_ONE    << SRC1_SHIFT);
    private static final int SRC1_NBITS = MODE_NBITS;
    private static final int SRC1_MASK  = (MODE_MASK   << SRC1_SHIFT);

    private static final int SRC2_SHIFT = SRC1_SHIFT + SRC1_NBITS;
    private static final int SRC2_ACCA     = (MODE_ACCA   << SRC2_SHIFT);
    private static final int SRC2_ACCB     = (MODE_ACCB   << SRC2_SHIFT);
    private static final int SRC2_CCODE    = (MODE_CCODE  << SRC2_SHIFT);
    private static final int SRC2_IXREG    = (MODE_IXREG  << SRC2_SHIFT);
    private static final int SRC2_SPREG    = (MODE_SPREG  << SRC2_SHIFT);
    private static final int SRC2_MEMORY   = (MODE_MEMORY << SRC2_SHIFT);
    private static final int SRC2_ZERO     = (MODE_ZERO   << SRC2_SHIFT);
    private static final int SRC2_ONE      = (MODE_ONE    << SRC2_SHIFT);
    private static final int SRC2_NBITS = MODE_NBITS;
    private static final int SRC2_MASK  = (MODE_MASK   << SRC2_SHIFT);

    private static final int DST_SHIFT = SRC2_SHIFT + SRC2_NBITS;
    private static final int DST_ACCA     = (MODE_ACCA   << DST_SHIFT);
    private static final int DST_ACCB     = (MODE_ACCB   << DST_SHIFT);
    private static final int DST_CCODE    = (MODE_CCODE  << DST_SHIFT);
    private static final int DST_IXREG    = (MODE_IXREG  << DST_SHIFT);
    private static final int DST_SPREG    = (MODE_SPREG  << DST_SHIFT);
    private static final int DST_MEMORY   = (MODE_MEMORY << DST_SHIFT);
    private static final int DST_DISCARD  = (MODE_ZERO   << DST_SHIFT);
    private static final int DST_NBITS = MODE_NBITS;
    private static final int DST_MASK  = (MODE_MASK   << DST_SHIFT);

    private static final int IAM_NONE   = 0;
    private static final int IAM_IMMED  = 1;
    private static final int IAM_DIRECT = 2;
    private static final int IAM_INDEX  = 3;
    private static final int IAM_EXTEND = 4;
    private static final int IAM_PUSH   = 5;
    private static final int IAM_PULL   = 6;

    private static final int ADDR_SHIFT = DST_SHIFT + DST_NBITS;
    private static final int ADDR_NONE    = (IAM_NONE   << ADDR_SHIFT);
    private static final int ADDR_IMMED   = (IAM_IMMED  << ADDR_SHIFT);
    private static final int ADDR_DIRECT  = (IAM_DIRECT << ADDR_SHIFT);
    private static final int ADDR_INDEX   = (IAM_INDEX  << ADDR_SHIFT);
    private static final int ADDR_EXTEND  = (IAM_EXTEND << ADDR_SHIFT);
    private static final int ADDR_PUSH    = (IAM_PUSH   << ADDR_SHIFT);
    private static final int ADDR_PULL    = (IAM_PULL   << ADDR_SHIFT);
    private static final int ADDR_NBITS = 3;
    private static final int ADDR_MASK  = (((1 << ADDR_NBITS) - 1) << ADDR_SHIFT);

    private static final int CCMOD_SHIFT = ADDR_SHIFT + ADDR_NBITS;
    private static final int CCMOD_H       = (COND_H    << CCMOD_SHIFT);
    private static final int CCMOD_I       = (COND_I    << CCMOD_SHIFT);
    private static final int CCMOD_N       = (COND_N    << CCMOD_SHIFT);
    private static final int CCMOD_Z       = (COND_Z    << CCMOD_SHIFT);
    private static final int CCMOD_V       = (COND_V    << CCMOD_SHIFT);
    private static final int CCMOD_C       = (COND_C    << CCMOD_SHIFT);
    private static final int CCMOD_NBITS = COND_NBITS;
    private static final int CCMOD_MASK  = (COND_MASK << CCMOD_SHIFT);

    private static final int CCVAL_SHIFT = CCMOD_SHIFT + CCMOD_NBITS;
    private static final int CCVAL_H       = (COND_H    << CCVAL_SHIFT);
    private static final int CCVAL_I       = (COND_I    << CCVAL_SHIFT);
    private static final int CCVAL_N       = (COND_N    << CCVAL_SHIFT);
    private static final int CCVAL_Z       = (COND_Z    << CCVAL_SHIFT);
    private static final int CCVAL_V       = (COND_V    << CCVAL_SHIFT);
    private static final int CCVAL_C       = (COND_C    << CCVAL_SHIFT);
    private static final int CCVAL_NBITS = COND_NBITS;
    private static final int CCVAL_MASK  = (COND_MASK << CCVAL_SHIFT);

    private static final int CC_ADD    = (CCMOD_H | CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_INC    = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_DEC    = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_AND    = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_BIT    = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_CLR    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_SUB    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_COM    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_NEG    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_CMP    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_LD     = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_ST     = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_TX     = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_EOR    = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_OR     = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_ROT    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_SH     = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_TST    = (          CCMOD_N | CCMOD_Z | CCMOD_V | CCMOD_C);
    private static final int CC_PSH    = (                       0                       );
    private static final int CC_PLL    = (                       0                       );
    private static final int CC_CPX    = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_DEX    = (                    CCMOD_Z                    );
    private static final int CC_INX    = (                    CCMOD_Z                    );
    private static final int CC_DES    = (                       0                       );
    private static final int CC_INS    = (                       0                       );
    private static final int CC_LD16   = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_ST16   = (          CCMOD_N | CCMOD_Z | CCMOD_V          );
    private static final int CC_TX16   = (                       0                       );
    private static final int CC_TXCC   = (                       0                       );

    private static final int OP_ADC = (OP_ADD | CCVAL_C);
    private static final int OP_SBC = (OP_SUB | CCVAL_C);

    private static final int BCC_SHIFT = OP_SHIFT + OP_NBITS;
    private static final int BCC_N       = (COND_N << BCC_SHIFT);
    private static final int BCC_Z       = (COND_Z << BCC_SHIFT);
    private static final int BCC_V       = (COND_V << BCC_SHIFT);
    private static final int BCC_C       = (COND_C << BCC_SHIFT);
    private static final int BCC_NBITS = COND_NBITS;

    private static final int BCC_XOR_SHIFT = BCC_SHIFT + BCC_NBITS;
    private static final int BCC_XOR_N       = (COND_N << BCC_XOR_SHIFT);
    private static final int BCC_XOR_Z       = (COND_Z << BCC_XOR_SHIFT);
    private static final int BCC_XOR_V       = (COND_V << BCC_XOR_SHIFT);
    private static final int BCC_XOR_C       = (COND_C << BCC_XOR_SHIFT);
    private static final int BCC_XOR_NBITS = COND_NBITS;

    private static final int BCC_OR_SHIFT = BCC_XOR_SHIFT + BCC_XOR_NBITS;
    private static final int BCC_OR_N       = (COND_N << BCC_OR_SHIFT);
    private static final int BCC_OR_Z       = (COND_Z << BCC_OR_SHIFT);
    private static final int BCC_OR_V       = (COND_V << BCC_OR_SHIFT);
    private static final int BCC_OR_C       = (COND_C << BCC_OR_SHIFT);
    private static final int BCC_OR_NBITS = COND_NBITS;

    private static final int BCC_EQ_SHIFT = BCC_OR_SHIFT + BCC_OR_NBITS;
    private static final int BCC_EQ_ZERO    = (0 << BCC_EQ_SHIFT);
    private static final int BCC_EQ_ONE     = (1 << BCC_EQ_SHIFT);
    private static final int BCC_EQ_NBITS = 1;

    static {
        if (CCVAL_SHIFT + CCVAL_NBITS > 32) {
            throw new InternalError("Too many bits for instruction flags!");
        }
        if (BCC_EQ_SHIFT + BCC_EQ_NBITS > 32) {
            throw new InternalError("Too many bits for conditional branch flags!");
        }
    }

    private char modeAddress(int flags, int nbytes) {
        char addr;
        switch ((flags & ADDR_MASK) >> ADDR_SHIFT) {
            case IAM_NONE:
                addr = 0;
                break;
            case IAM_IMMED:
                addr = PCreg;
                PCreg += nbytes;
                break;
            case IAM_DIRECT:
                addr = (char) (load8(PCreg) & 0xff);
                PCreg += 1;
                break;
            case IAM_INDEX:
                addr = (char) (IXreg + (load8(PCreg) & 0xff));
                PCreg += 1;
                break;
            case IAM_EXTEND:
                addr = load16(PCreg);
                PCreg += 2;
                break;
            case IAM_PUSH:
                SPreg -= nbytes;
                addr = (char) (SPreg + 1);
                break;
            case IAM_PULL:
                addr = (char) (SPreg + 1);
                SPreg += nbytes;
                break;
            default:
                throw new InternalError("Unrecognized Addressing Mode");
        }
        if (DEBUG > 2) {
            System.out.println("   mode Address: " + hex(addr, 4));
        }
        return addr;
    }

    private byte load8(int mode, char addr) {
        switch (mode & MODE_MASK) {
            case MODE_ACCA:
                return accA;
            case MODE_ACCB:
                return accB;
            case MODE_CCODE:
                return ccode;
            case MODE_IXREG:
            case MODE_SPREG:
                throw new InternalError("Trying to access IX or SP in 8 bit instruction");
            case MODE_MEMORY:
                return load8(addr);
            case MODE_ZERO:
                return 0;
            case MODE_ONE:
                return 1;
            default:
                throw new InternalError("Unrecognized 8-bit source type");
        }
    }

    private char load16(int mode, char addr) {
        switch (mode & MODE_MASK) {
            case MODE_ACCA:
            case MODE_ACCB:
            case MODE_CCODE:
                throw new InternalError("Trying to access A, B or CRC in 16 bit instruction");
            case MODE_IXREG:
                return IXreg;
            case MODE_SPREG:
                return SPreg;
            case MODE_MEMORY:
                return load16(addr);
            case MODE_ZERO:
                return 0;
            case MODE_ONE:
                return 1;
            default:
                throw new InternalError("Unrecognized 16-bit source type");
        }
    }

    private void store8(int mode, char addr, byte val) {
        switch (mode & MODE_MASK) {
            case MODE_ACCA:
                accA = val;
                break;
            case MODE_ACCB:
                accB = val;
                break;
            case MODE_CCODE:
                ccode = (byte) (val & COND_MASK);
                break;
            case MODE_IXREG:
            case MODE_SPREG:
                throw new InternalError("Trying to access IX or SP in 8 bit instruction");
            case MODE_MEMORY:
                store8(addr, val);
                break;
            case MODE_ZERO:
                // Means discard for stores...
                break;
            case MODE_ONE:
                throw new InternalError("Trying to use constant for destination");
            default:
                throw new InternalError("Unrecognized 8-bit source type");
        }
    }

    private void store16(int mode, char addr, char val) {
        switch (mode & MODE_MASK) {
            case MODE_ACCA:
            case MODE_ACCB:
            case MODE_CCODE:
                throw new InternalError("Trying to access A, B or CRC in 16 bit instruction");
            case MODE_IXREG:
                IXreg = val;
                break;
            case MODE_SPREG:
                SPreg = val;
                break;
            case MODE_MEMORY:
                store16(addr, val);
                break;
            case MODE_ZERO:
                // Means discard for stores...
                break;
            case MODE_ONE:
                throw new InternalError("Trying to use constant for destination");
            default:
                throw new InternalError("Unrecognized 16-bit source type");
        }
    }

    private void applyCC(int flags, byte newcc) {
        byte ccmask = (byte) ((flags >> CCMOD_SHIFT) & COND_MASK);
        ccode = (byte) ((ccode & (~ccmask)) | (newcc & ccmask));
    }

    private void opMove8(int flags) {
        char addr = modeAddress(flags, 1);
        byte c = load8(flags >> SRC1_SHIFT, addr);
        byte cctmp = 0;
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
            // COND_C is always cleared (or unaffected) in Load/Store/Transfer operations
        // COND_V is always cleared in Load/Store/Transfer operations
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    /* NVC condition code truth table for addition (with or without carry) */
    private static final byte CC_ADD_TABLE[] = {
        /* a7=0  b7=0  c7=0 */   (     0 |      0 |      0),
        /* a7=0  b7=0  c7=1 */   (COND_N | COND_V |      0),
        /* a7=0  b7=1  c7=0 */   (     0 |      0 | COND_C),
        /* a7=0  b7=1  c7=1 */   (COND_N |      0 |      0),
        /* a7=1  b7=0  c7=0 */   (     0 |      0 | COND_C),
        /* a7=1  b7=0  c7=1 */   (COND_N |      0 |      0),
        /* a7=1  b7=1  c7=0 */   (     0 | COND_V | COND_C),
        /* a7=1  b7=1  c7=1 */   (COND_N |      0 | COND_C),
    };	
    private void opAdd(int flags) {
        char addr = modeAddress(flags, 1);
        int a = load8(flags >> SRC1_SHIFT, addr);
        int b = load8(flags >> SRC2_SHIFT, addr);
        int carry = (((flags >> CCVAL_SHIFT) & ccode) >> SHIFT_C);
        int c = a + b + carry;
        byte cctmp = CC_ADD_TABLE[((a >> 5) & 0x04) |
                                  ((b >> 6) & 0x02) |
                                  ((c >> 7) & 0x01)];
        if ((c & 0xFF) == 0) {
            cctmp |= COND_Z;
        }
        if ((a & 0x0F) + (b & 0x0F) + carry > 0x0F) {
            cctmp |= COND_H;
        }
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, (byte) c);
    }

    /* NVC condition code truth table for subtraction (with or without borrow) */
    private static final byte CC_SUB_TABLE[] = {
        /* a7=0  b7=0  c7=0 */   (     0 |      0 |      0),
        /* a7=0  b7=0  c7=1 */   (COND_N |      0 | COND_C),
        /* a7=0  b7=1  c7=0 */   (     0 |      0 | COND_C),
        /* a7=0  b7=1  c7=1 */   (COND_N | COND_V | COND_C),
        /* a7=1  b7=0  c7=0 */   (     0 | COND_V |      0),
        /* a7=1  b7=0  c7=1 */   (COND_N |      0 |      0),
        /* a7=1  b7=1  c7=0 */   (     0 |      0 |      0),
        /* a7=1  b7=1  c7=1 */   (COND_N |      0 | COND_C),
    };	
    private void opSubtract(int flags) {
        char addr = modeAddress(flags, 1);
        int a = load8(flags >> SRC1_SHIFT, addr);
        int b = load8(flags >> SRC2_SHIFT, addr);
        int carry = (((flags >> CCVAL_SHIFT) & ccode) >> SHIFT_C);
        int c = a - b - carry;
        byte cctmp = CC_SUB_TABLE[((a >> 5) & 0x04) |
                                  ((b >> 6) & 0x02) |
                                  ((c >> 7) & 0x01)];
        if ((c & 0xFF) == 0) {
            cctmp |= COND_Z;
        }
        // Half Carry is never affected by subtract operations
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, (byte) c);
    }

    private void opComplement(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) (~a);
        byte cctmp = COND_C;
        // COND_V is always cleared by Complement operation
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    // In 6800 parlance, negate is 1's complement == ((~a) + 1)
    private void opNegate(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) (0 - a);
        byte cctmp = 0;
        if (c == 0) {
            cctmp |= (COND_Z | COND_C);
        }
        cctmp |= (c >> 7) & COND_N;
        if (c == (byte) 0x80) {
            cctmp |= COND_V;
        }
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opAnd(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte b = load8(flags >> SRC2_SHIFT, addr);
        byte c = (byte) (a & b);
        byte cctmp = 0;
        // COND_V is always cleared by And operation
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opExclusiveOr(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte b = load8(flags >> SRC2_SHIFT, addr);
        byte c = (byte) (a ^ b);
        byte cctmp = 0;
        // COND_V is always cleared by ExclusiveOr operation
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opInclusiveOr(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte b = load8(flags >> SRC2_SHIFT, addr);
        byte c = (byte) (a | b);
        byte cctmp = 0;
        // COND_V is always cleared by InclusiveOr operation
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opRotateLeft(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) ((a << 1) | ((ccode & COND_C) >> SHIFT_C));
        byte cctmp = (byte) ((a >> 7) & COND_C);
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        cctmp |= (((cctmp >> SHIFT_N) ^ (cctmp >> SHIFT_C)) & 0x01) << SHIFT_V;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opRotateRight(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) (((a >> 1) & 0x7f) | ((ccode & COND_C) << (7-SHIFT_C)));
        byte cctmp = (byte) ((a & 0x01) << SHIFT_C);
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        cctmp |= (((cctmp >> SHIFT_N) ^ (cctmp >> SHIFT_C)) & 0x01) << SHIFT_V;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opArithmeticShiftLeft(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) (a << 1);
        byte cctmp = (byte) ((a >> 7) & COND_C);
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        cctmp |= (((cctmp >> SHIFT_N) ^ (cctmp >> SHIFT_C)) & 0x01) << SHIFT_V;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opArithmeticShiftRight(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) (a >> 1);
        byte cctmp = (byte) ((a & 0x01) << SHIFT_C);
        if (c == 0) {
            cctmp |= COND_Z;
        }
        cctmp |= (c >> 7) & COND_N;
        cctmp |= (((cctmp >> SHIFT_N) ^ (cctmp >> SHIFT_C)) & 0x01) << SHIFT_V;
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opLogicalShiftRight(int flags) {
        char addr = modeAddress(flags, 1);
        byte a = load8(flags >> SRC1_SHIFT, addr);
        byte c = (byte) ((a >> 1) & 0x7F);
        byte cctmp = (byte) ((a & 0x01) * (COND_C | COND_V));
        // COND_N is always cleared by LogicalShiftRight operation
        // NOTE: V = N^C == C since N is always cleared
        if (c == 0) {
            cctmp |= COND_Z;
        }
        applyCC(flags, cctmp);
        store8(flags >> DST_SHIFT, addr, c);
    }

    private void opDecimalAdjustA(int flags) {
        int a = accA & 0xFF;
        int cctmp = ccode & (COND_H | COND_I | COND_C);
        if ((a & 0x0F) > 0x09 || (cctmp & COND_H) != 0) {
            a += 6;
        }
        if (a > 0x9F || (cctmp & COND_C) != 0) {
            a += 0x60;
            cctmp |= COND_C;
            cctmp |= COND_V;  // ???
        }
        if ((a & 0xFF) == 0) {
            cctmp |= COND_Z;
        }
        if ((a & 0x80) != 0) {
            cctmp |= COND_N;
        }
        accA = (byte) a;
        ccode = (byte) cctmp;
    }

    private void opMove16(int flags) {
        char addr = modeAddress(flags, 2);
        char c = load16(flags >> SRC1_SHIFT, addr);
        byte cctmp = 0;
        if (c == 0) {
            cctmp |= COND_Z;
        }
        if ((c & 0x8000) != 0) {
            cctmp |= COND_N;
        }
        // COND_V always reset (or unaffected) on 16 bit Load/Store/Transfer operations
        applyCC(flags, cctmp);
        store16(flags >> DST_SHIFT, addr, c);
    }

    private void opAdd16(int flags) {
        char addr = modeAddress(flags, 2);
        char a = load16(flags >> SRC1_SHIFT, addr);
        char b = load16(flags >> SRC2_SHIFT, addr);
        char c = (char) (a + b);
        byte cctmp = 0;
        // Only Z bit is ever used for OP_ADD16 instructions
        if (c == 0) {
            cctmp |= COND_Z;
        }
        applyCC(flags, cctmp);
        store16(flags >> DST_SHIFT, addr, c);
    }

    private void opSubtract16(int flags) {
        char addr = modeAddress(flags, 2);
        int a = load16(flags >> SRC1_SHIFT, addr);
        int b = load16(flags >> SRC2_SHIFT, addr);
        int c = a - b;
        byte cctmp = 0;
        if (c == 0) {
            cctmp |= COND_Z;
        }
        if ((c & 0x8000) != 0) {
            cctmp |= COND_N;
        }
        // Strangely, V bit is overflow (? not carry?) from subtracting LSBs...
        if (((byte) a) - ((byte) b) != ((byte) c)) {
            cctmp |= COND_V;
        }
        applyCC(flags, cctmp);
        store16(flags >> DST_SHIFT, addr, (char) c);
    }

    private boolean testBCC(int flags) {
        int cctmp = (ccode & COND_MASK);
        boolean res = ((flags >> BCC_SHIFT) & cctmp) != 0;
        if (((flags >> BCC_XOR_SHIFT) & cctmp) != 0) {
            res = !res;
        }
        if (((flags >> BCC_OR_SHIFT) & cctmp) != 0) {
            res = true;
        }
        return (res == ((flags & BCC_EQ_ONE) != 0));
    }

    private void opBranchConditional(int flags) {
        byte offset = load8(PCreg++);
        if (testBCC(flags)) {
            PCreg = (char) (PCreg + offset);
        }
    }

    private void opBranchSubroutine(int flags) {
        byte offset = load8(PCreg++);
        push(PCreg);
        PCreg = (char) (PCreg + offset);
    }

    private void opJump(int flags) {
        char addr = modeAddress(flags, 2);
        PCreg = addr;
    }

    private void opJumpSubroutine(int flags) {
        char addr = modeAddress(flags, 2);
        push(PCreg);
        PCreg = addr;
    }

    private void opReturnFromSubroutine(int flags) {
        PCreg = pop16();
    }

    private void opSoftwareInterrupt(int flags) {
        handleInterrupt(SWI_JUMP_ADDR);
    }

    private void opReturnFromInterrupt(int flags) {
        ccode = (byte) (pop8() & COND_MASK);
        accB = pop8();
        accA = pop8();
        IXreg = pop16();
        PCreg = pop16();
    }

    private synchronized void opWaitForInterrupt(int flags) {
        while (!NMIraised && ((ccode & COND_I) != 0 || !IRQraised)) {
            try {
                wait();
            } catch (InterruptedException e) {
                powerOff();
            }
        }
    }

    private void opModifyCC(int flags) {
        applyCC(flags, (byte) ((flags >> CCVAL_SHIFT) & COND_MASK));
    }

    public static String hex(int v, int numdigits) {
        StringBuilder sb = new StringBuilder(numdigits);
        while (--numdigits >= 0) {
            int d = (v >> (numdigits*4)) & 0xf;
            sb.append("0123456789ABCDEF".charAt(d));
        }
        return sb.toString();
    }

    public static String padHead(String str, char c, int desiredsize) {
        while (str.length() < desiredsize) {
            str = c + str;
        }
        return str;
    }

    public static int fld(int v, int off, int len) {
        return (v >> off) & ((1 << len) - 1);
    }

    private String opToMnemonic(byte opcode) {
        String mnem = opMnemonics[opcode & 0xFF];
        switch (mnem.charAt(5)) {
        case ' ':
            break;
        case '$':
            mnem += hex(load16(PCreg), 4);
            break;
        case '*':
            int offset = load8(PCreg);
            if (offset >= 0) {
                mnem += "+";
            }
            mnem += offset;
            break;
        case 'X':
            mnem += "["+(load8(PCreg)&0xFF)+"]";
            break;
        case '%':
            mnem = mnem.substring(0, 5)+"$"+hex(load8(PCreg), 2);
            break;
        case '#':
            if (mnem.charAt(3) == ' ') {
                mnem += hex(load16(PCreg), 4);
            } else {
                mnem += hex(load8(PCreg), 2);
            }
            break;
        default:
            mnem += "???";
            break;
        }
        return mnem;
    }

    private String ccstr() {
        char codes[] = new char[6];
        codes[0] = ((ccode & COND_H) == 0) ? '-' : 'H';
        codes[1] = ((ccode & COND_I) == 0) ? '-' : 'I';
        codes[2] = ((ccode & COND_N) == 0) ? '-' : 'N';
        codes[3] = ((ccode & COND_Z) == 0) ? '-' : 'Z';
        codes[4] = ((ccode & COND_V) == 0) ? '-' : 'V';
        codes[5] = ((ccode & COND_C) == 0) ? '-' : 'C';
        return new String(codes);
    }

    private int opCounts[];
    private int opTotals[];
    public synchronized void printStats() {
        HALTraised = true;
        waitForHalt();
        System.out.println("       count          total   opcode");
        int ctotal = 0;
        int ttotal = 0;
        for (int i = 0; i < 256; i++) {
            opTotals[i] += opCounts[i];
            String c = padHead(Integer.toString(opCounts[i]), ' ', 12);
            String t = padHead(Integer.toString(opTotals[i]), ' ', 12);
            String mnem = opMnemonics[i];
            System.out.println(c+",  "+t+",  $"+hex(i, 2)+"  "+mnem);
            ctotal += opCounts[i];
            ttotal += opTotals[i];
        }
        System.out.println("------------   ------------   ------");
        String c = padHead(Integer.toString(ctotal), ' ', 12);
        String t = padHead(Integer.toString(ttotal), ' ', 12);
        System.out.println(c+",  "+t+",  total");
        System.out.println();
        HALTraised = false;
        notifyAll();
    }

    private void executeInstruction() {
        if (DEBUG > 0) {
            System.out.print("PC:"+hex(PCreg,4)+
                             "  SP:"+hex(SPreg,4)+
                             "  IX:"+hex(IXreg,4)+
                             "  A:"+hex(accA,2)+
                             "  B:"+hex(accB,2)+
                             "  CC:"+ccstr());
        }
        byte opcode = load8(PCreg++);
        if (STATS) {
            opCounts[opcode & 0xFF]++;
        }
        int flags = INSTRUCTION_FLAGS[opcode & 0xff];
        if (DEBUG > 0) {
            System.out.println("   op:"+hex(opcode, 2)+",  "+opToMnemonic(opcode));
            if (DEBUG > 1) {
                    System.out.println("   flags [op:"+fld(flags, OP_SHIFT, OP_NBITS)+
                                       ", s1:"+fld(flags, SRC1_SHIFT, SRC1_NBITS)+
                                       ", s2:"+fld(flags, SRC2_SHIFT, SRC2_NBITS)+
                                       ", dst:"+fld(flags, DST_SHIFT, DST_NBITS)+
                                       ", adr:"+fld(flags, ADDR_SHIFT, ADDR_NBITS)+
                                       ", ccm:"+fld(flags, CCMOD_SHIFT, CCMOD_NBITS)+
                                       ", ccv:"+fld(flags, CCVAL_SHIFT, CCVAL_NBITS)+"]");
            }
        }
        switch (flags & OP_MASK) {
            case OP_ILLEGAL:
            default:
                System.err.println("Illegal Instruction at " + hex(PCreg - 1, 4));
                break;
            case OP_NOP:
                break;
            case OP_MOV8:
                opMove8(flags);
                break;
            case OP_ADD:
                opAdd(flags);
                break;
            case OP_SUB:
                opSubtract(flags);
                break;
            case OP_COM:
                opComplement(flags);
                break;
            case OP_NEG:
                opNegate(flags);
                break;
            case OP_AND:
                opAnd(flags);
                break;
            case OP_EOR:
                opExclusiveOr(flags);
                break;
            case OP_ORA:
                opInclusiveOr(flags);
                break;
            case OP_ROL:
                opRotateLeft(flags);
                break;
            case OP_ROR:
                opRotateRight(flags);
                break;
            case OP_ASL:
                opArithmeticShiftLeft(flags);
                break;
            case OP_ASR:
                opArithmeticShiftRight(flags);
                break;
            case OP_LSR:
                opLogicalShiftRight(flags);
                break;
            case OP_DAA:
                opDecimalAdjustA(flags);
                break;
            case OP_MOV16:
                opMove16(flags);
                break;
            case OP_ADD16:
                opAdd16(flags);
                break;
            case OP_SUB16:
                opSubtract16(flags);
                break;
            case OP_BCOND:
                opBranchConditional(flags);
                break;
            case OP_BSR:
                opBranchSubroutine(flags);
                break;
            case OP_JMP:
                opJump(flags);
                break;
            case OP_JSR:
                opJumpSubroutine(flags);
                break;
            case OP_RTS:
                opReturnFromSubroutine(flags);
                break;
            case OP_SWI:
                opSoftwareInterrupt(flags);
                break;
            case OP_RTI:
                opReturnFromInterrupt(flags);
                break;
            case OP_WAI:
                opWaitForInterrupt(flags);
                break;
            case OP_MODCC:
                opModifyCC(flags);
                break;
        }
    }

    @Override
    public void run() {
        Thread me = Thread.currentThread();
        isRunning = true;
        while (executor == me) {
            if (handleControlLines()) {
                executeInstruction();
            }
        }
    }

    private static final int INSTRUCTION_FLAGS[] = {
        /* $00 */ OP_ILLEGAL,
        /* $01 */ (OP_NOP),
        /* $02 */ OP_ILLEGAL,
        /* $03 */ OP_ILLEGAL,
        /* $04 */ OP_ILLEGAL,
        /* $05 */ OP_ILLEGAL,
        /* $06 */ (OP_MOV8  | CC_TXCC | SRC1_ACCA             | DST_CCODE),
        /* $07 */ (OP_MOV8  | CC_TXCC | SRC1_CCODE            | DST_ACCA),
        /* $08 */ (OP_ADD16 | CC_INX  | SRC1_IXREG | SRC2_ONE | DST_IXREG),
        /* $09 */ (OP_SUB16 | CC_DEX  | SRC1_IXREG | SRC2_ONE | DST_IXREG),
        /* $0A */ (OP_MODCC | CCMOD_V |       0),
        /* $0B */ (OP_MODCC | CCMOD_V | CCVAL_V),
        /* $0C */ (OP_MODCC | CCMOD_C |       0),
        /* $0D */ (OP_MODCC | CCMOD_C | CCVAL_C),
        /* $0E */ (OP_MODCC | CCMOD_I |       0),
        /* $0F */ (OP_MODCC | CCMOD_I | CCVAL_I),

        /* $10 */ (OP_SUB  | CC_SUB | SRC1_ACCA | SRC2_ACCB | DST_ACCA),
        /* $11 */ (OP_SUB  | CC_CMP | SRC1_ACCA | SRC2_ACCB | DST_DISCARD),
        /* $12 */ OP_ILLEGAL,
        /* $13 */ OP_ILLEGAL,
        /* $14 */ OP_ILLEGAL,
        /* $15 */ OP_ILLEGAL,
        /* $16 */ (OP_MOV8 | CC_TX  | SRC1_ACCA             | DST_ACCB),
        /* $17 */ (OP_MOV8 | CC_TX  | SRC1_ACCB             | DST_ACCA),
        /* $18 */ OP_ILLEGAL,
        /* $19 */ (OP_DAA),
        /* $1A */ OP_ILLEGAL,
        /* $1B */ (OP_ADD  | CC_ADD | SRC1_ACCA | SRC2_ACCB | DST_ACCA),
        /* $1C */ OP_ILLEGAL,
        /* $1D */ OP_ILLEGAL,
        /* $1E */ OP_ILLEGAL,
        /* $1F */ OP_ILLEGAL,

        /* $20 */ (OP_BCOND                                | BCC_EQ_ZERO),
        /* $21 */ OP_ILLEGAL,
        /* $22 */ (OP_BCOND | BCC_C             | BCC_OR_Z | BCC_EQ_ZERO),
        /* $23 */ (OP_BCOND | BCC_C             | BCC_OR_Z | BCC_EQ_ONE),
        /* $24 */ (OP_BCOND | BCC_C                        | BCC_EQ_ZERO),
        /* $25 */ (OP_BCOND | BCC_C                        | BCC_EQ_ONE),
        /* $26 */ (OP_BCOND | BCC_Z                        | BCC_EQ_ZERO),
        /* $27 */ (OP_BCOND | BCC_Z                        | BCC_EQ_ONE),
        /* $28 */ (OP_BCOND | BCC_V                        | BCC_EQ_ZERO),
        /* $29 */ (OP_BCOND | BCC_V                        | BCC_EQ_ONE),
        /* $2A */ (OP_BCOND | BCC_N                        | BCC_EQ_ZERO),
        /* $2B */ (OP_BCOND | BCC_N                        | BCC_EQ_ONE),
        /* $2C */ (OP_BCOND | BCC_N | BCC_XOR_V            | BCC_EQ_ZERO),
        /* $2D */ (OP_BCOND | BCC_N | BCC_XOR_V            | BCC_EQ_ONE),
        /* $2E */ (OP_BCOND | BCC_N | BCC_XOR_V | BCC_OR_Z | BCC_EQ_ZERO),
        /* $2F */ (OP_BCOND | BCC_N | BCC_XOR_V | BCC_OR_Z | BCC_EQ_ONE),

        /* $30 */ (OP_ADD16 | CC_TX16             | SRC1_SPREG  | SRC2_ONE | DST_IXREG),
        /* $31 */ (OP_ADD16 | CC_INS              | SRC1_SPREG  | SRC2_ONE | DST_SPREG),
        /* $32 */ (OP_MOV8  | CC_PLL  | ADDR_PULL | SRC1_MEMORY            | DST_ACCA),
        /* $33 */ (OP_MOV8  | CC_PLL  | ADDR_PULL | SRC1_MEMORY            | DST_ACCB),
        /* $34 */ (OP_SUB16 | CC_DES              | SRC1_SPREG  | SRC2_ONE | DST_SPREG),
        /* $35 */ (OP_SUB16 | CC_TX16             | SRC1_IXREG  | SRC2_ONE | DST_SPREG),
        /* $36 */ (OP_MOV8  | CC_PSH  | ADDR_PUSH | SRC1_ACCA              | DST_MEMORY),
        /* $37 */ (OP_MOV8  | CC_PSH  | ADDR_PUSH | SRC1_ACCB              | DST_MEMORY),
        /* $38 */ OP_ILLEGAL,
        /* $39 */ (OP_RTS),
        /* $3A */ OP_ILLEGAL,
        /* $3B */ (OP_RTI),
        /* $3C */ OP_ILLEGAL,
        /* $3D */ OP_ILLEGAL,
        /* $3E */ (OP_WAI),
        /* $3F */ (OP_SWI),

        /* $40 */ (OP_NEG  | CC_NEG | SRC1_ACCA             | DST_ACCA),
        /* $41 */ OP_ILLEGAL,
        /* $42 */ OP_ILLEGAL,
        /* $43 */ (OP_COM  | CC_COM | SRC1_ACCA             | DST_ACCA),
        /* $44 */ (OP_LSR  | CC_SH  | SRC1_ACCA             | DST_ACCA),
        /* $45 */ OP_ILLEGAL,
        /* $46 */ (OP_ROR  | CC_ROT | SRC1_ACCA             | DST_ACCA),
        /* $47 */ (OP_ASR  | CC_SH  | SRC1_ACCA             | DST_ACCA),
        /* $48 */ (OP_ASL  | CC_SH  | SRC1_ACCA             | DST_ACCA),
        /* $49 */ (OP_ROL  | CC_ROT | SRC1_ACCA             | DST_ACCA),
        /* $4A */ (OP_SUB  | CC_DEC | SRC1_ACCA | SRC2_ONE  | DST_ACCA),
        /* $4B */ OP_ILLEGAL,
        /* $4C */ (OP_ADD  | CC_INC | SRC1_ACCA | SRC2_ONE  | DST_ACCA),
        /* $4D */ (OP_SUB  | CC_TST | SRC1_ACCA | SRC2_ZERO | DST_DISCARD),
        /* $4E */ OP_ILLEGAL,
        /* $4F */ (OP_MOV8 | CC_CLR | SRC1_ZERO             | DST_ACCA),

        /* $50 */ (OP_NEG  | CC_NEG | SRC1_ACCB             | DST_ACCB),
        /* $51 */ OP_ILLEGAL,
        /* $52 */ OP_ILLEGAL,
        /* $53 */ (OP_COM  | CC_COM | SRC1_ACCB             | DST_ACCB),
        /* $54 */ (OP_LSR  | CC_SH  | SRC1_ACCB             | DST_ACCB),
        /* $55 */ OP_ILLEGAL,
        /* $56 */ (OP_ROR  | CC_ROT | SRC1_ACCB             | DST_ACCB),
        /* $57 */ (OP_ASR  | CC_SH  | SRC1_ACCB             | DST_ACCB),
        /* $58 */ (OP_ASL  | CC_SH  | SRC1_ACCB             | DST_ACCB),
        /* $59 */ (OP_ROL  | CC_ROT | SRC1_ACCB             | DST_ACCB),
        /* $5A */ (OP_SUB  | CC_DEC | SRC1_ACCB | SRC2_ONE  | DST_ACCB),
        /* $5B */ OP_ILLEGAL,
        /* $5C */ (OP_ADD  | CC_INC | SRC1_ACCB | SRC2_ONE  | DST_ACCB),
        /* $5D */ (OP_SUB  | CC_TST | SRC1_ACCB | SRC2_ZERO | DST_DISCARD),
        /* $5E */ OP_ILLEGAL,
        /* $5F */ (OP_MOV8 | CC_CLR | SRC1_ZERO             | DST_ACCB),

        /* $60 */ (OP_NEG  | CC_NEG | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $61 */ OP_ILLEGAL,
        /* $62 */ OP_ILLEGAL,
        /* $63 */ (OP_COM  | CC_COM | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $64 */ (OP_LSR  | CC_SH  | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $65 */ OP_ILLEGAL,
        /* $66 */ (OP_ROR  | CC_ROT | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $67 */ (OP_ASR  | CC_SH  | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $68 */ (OP_ASL  | CC_SH  | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $69 */ (OP_ROL  | CC_ROT | ADDR_INDEX  | SRC1_MEMORY               | DST_MEMORY),
        /* $6A */ (OP_SUB  | CC_DEC | ADDR_INDEX  | SRC1_MEMORY | SRC2_ONE    | DST_MEMORY),
        /* $6B */ OP_ILLEGAL,
        /* $6C */ (OP_ADD  | CC_INC | ADDR_INDEX  | SRC1_MEMORY | SRC2_ONE    | DST_MEMORY),
        /* $6D */ (OP_SUB  | CC_TST | ADDR_INDEX  | SRC1_MEMORY | SRC2_ZERO   | DST_DISCARD),
        /* $6E */ (OP_JMP           | ADDR_INDEX),
        /* $6F */ (OP_MOV8 | CC_CLR | ADDR_INDEX  | SRC1_ZERO                 | DST_MEMORY),

        /* $70 */ (OP_NEG  | CC_NEG | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $71 */ OP_ILLEGAL,
        /* $72 */ OP_ILLEGAL,
        /* $73 */ (OP_COM  | CC_COM | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $74 */ (OP_LSR  | CC_SH  | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $75 */ OP_ILLEGAL,
        /* $76 */ (OP_ROR  | CC_ROT | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $77 */ (OP_ASR  | CC_SH  | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $78 */ (OP_ASL  | CC_SH  | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $79 */ (OP_ROL  | CC_ROT | ADDR_EXTEND | SRC1_MEMORY               | DST_MEMORY),
        /* $7A */ (OP_SUB  | CC_DEC | ADDR_EXTEND | SRC1_MEMORY | SRC2_ONE    | DST_MEMORY),
        /* $7B */ OP_ILLEGAL,
        /* $7C */ (OP_ADD  | CC_INC | ADDR_EXTEND | SRC1_MEMORY | SRC2_ONE    | DST_MEMORY),
        /* $7D */ (OP_SUB  | CC_TST | ADDR_EXTEND | SRC1_MEMORY | SRC2_ZERO   | DST_DISCARD),
        /* $7E */ (OP_JMP           | ADDR_EXTEND),
        /* $7F */ (OP_MOV8 | CC_CLR | ADDR_EXTEND | SRC1_ZERO                 | DST_MEMORY),

        /* $80 */ (OP_SUB   | CC_SUB  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $81 */ (OP_SUB   | CC_CMP  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $82 */ (OP_SBC   | CC_SUB  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $83 */ OP_ILLEGAL,
        /* $84 */ (OP_AND   | CC_AND  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $85 */ (OP_AND   | CC_BIT  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $86 */ (OP_MOV8  | CC_LD   | ADDR_IMMED  | SRC1_MEMORY               | DST_ACCA),
        /* $87 */ OP_ILLEGAL,
        /* $88 */ (OP_EOR   | CC_EOR  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $89 */ (OP_ADC   | CC_ADD  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $8A */ (OP_ORA   | CC_OR   | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $8B */ (OP_ADD   | CC_ADD  | ADDR_IMMED  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $8C */ (OP_SUB16 | CC_CPX  | ADDR_IMMED  | SRC1_IXREG  | SRC2_MEMORY | DST_DISCARD),
        /* $8D */ (OP_BSR),
        /* $8E */ (OP_MOV16 | CC_LD16 | ADDR_IMMED  | SRC1_MEMORY               | DST_SPREG),
        /* $8F */ OP_ILLEGAL,

        /* $90 */ (OP_SUB   | CC_SUB  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $91 */ (OP_SUB   | CC_CMP  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $92 */ (OP_SBC   | CC_SUB  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $93 */ OP_ILLEGAL,
        /* $94 */ (OP_AND   | CC_AND  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $95 */ (OP_AND   | CC_BIT  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $96 */ (OP_MOV8  | CC_LD   | ADDR_DIRECT | SRC1_MEMORY               | DST_ACCA),
        /* $97 */ (OP_MOV8  | CC_ST   | ADDR_DIRECT | SRC1_ACCA                 | DST_MEMORY),
        /* $98 */ (OP_EOR   | CC_EOR  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $99 */ (OP_ADC   | CC_ADD  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $9A */ (OP_ORA   | CC_OR   | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $9B */ (OP_ADD   | CC_ADD  | ADDR_DIRECT | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $9C */ (OP_SUB16 | CC_CPX  | ADDR_DIRECT | SRC1_IXREG  | SRC2_MEMORY | DST_DISCARD),
        /* $9D */ OP_ILLEGAL,
        /* $9E */ (OP_MOV16 | CC_LD16 | ADDR_DIRECT | SRC1_MEMORY               | DST_SPREG),
        /* $9F */ (OP_MOV16 | CC_ST16 | ADDR_DIRECT | SRC1_SPREG                | DST_MEMORY),

        /* $A0 */ (OP_SUB   | CC_SUB  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $A1 */ (OP_SUB   | CC_CMP  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $A2 */ (OP_SBC   | CC_SUB  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $A3 */ OP_ILLEGAL,
        /* $A4 */ (OP_AND   | CC_AND  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $A5 */ (OP_AND   | CC_BIT  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $A6 */ (OP_MOV8  | CC_LD   | ADDR_INDEX  | SRC1_MEMORY               | DST_ACCA),
        /* $A7 */ (OP_MOV8  | CC_ST   | ADDR_INDEX  | SRC1_ACCA                 | DST_MEMORY),
        /* $A8 */ (OP_EOR   | CC_EOR  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $A9 */ (OP_ADC   | CC_ADD  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $AA */ (OP_ORA   | CC_OR   | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $AB */ (OP_ADD   | CC_ADD  | ADDR_INDEX  | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $AC */ (OP_SUB16 | CC_CPX  | ADDR_INDEX  | SRC1_IXREG  | SRC2_MEMORY | DST_DISCARD),
        /* $AD */ (OP_JSR             | ADDR_INDEX),
        /* $AE */ (OP_MOV16 | CC_LD16 | ADDR_INDEX  | SRC1_MEMORY               | DST_SPREG),
        /* $AF */ (OP_MOV16 | CC_ST16 | ADDR_INDEX  | SRC1_SPREG                | DST_MEMORY),

        /* $B0 */ (OP_SUB   | CC_SUB  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $B1 */ (OP_SUB   | CC_CMP  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $B2 */ (OP_SBC   | CC_SUB  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $B3 */ OP_ILLEGAL,
        /* $B4 */ (OP_AND   | CC_AND  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $B5 */ (OP_AND   | CC_BIT  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_DISCARD),
        /* $B6 */ (OP_MOV8  | CC_LD   | ADDR_EXTEND | SRC1_MEMORY               | DST_ACCA),
        /* $B7 */ (OP_MOV8  | CC_ST   | ADDR_EXTEND | SRC1_ACCA                 | DST_MEMORY),
        /* $B8 */ (OP_EOR   | CC_EOR  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $B9 */ (OP_ADC   | CC_ADD  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $BA */ (OP_ORA   | CC_OR   | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $BB */ (OP_ADD   | CC_ADD  | ADDR_EXTEND | SRC1_ACCA   | SRC2_MEMORY | DST_ACCA),
        /* $BC */ (OP_SUB16 | CC_CPX  | ADDR_EXTEND | SRC1_IXREG  | SRC2_MEMORY | DST_DISCARD),
        /* $BD */ (OP_JSR             | ADDR_EXTEND),
        /* $BE */ (OP_MOV16 | CC_LD16 | ADDR_EXTEND | SRC1_MEMORY               | DST_SPREG),
        /* $BF */ (OP_MOV16 | CC_ST16 | ADDR_EXTEND | SRC1_SPREG                | DST_MEMORY),

        /* $C0 */ (OP_SUB   | CC_SUB  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $C1 */ (OP_SUB   | CC_CMP  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $C2 */ (OP_SBC   | CC_SUB  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $C3 */ OP_ILLEGAL,
        /* $C4 */ (OP_AND   | CC_AND  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $C5 */ (OP_AND   | CC_BIT  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $C6 */ (OP_MOV8  | CC_LD   | ADDR_IMMED  | SRC1_MEMORY               | DST_ACCB),
        /* $C7 */ OP_ILLEGAL,
        /* $C8 */ (OP_EOR   | CC_EOR  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $C9 */ (OP_ADC   | CC_ADD  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $CA */ (OP_ORA   | CC_OR   | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $CB */ (OP_ADD   | CC_ADD  | ADDR_IMMED  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $CC */ OP_ILLEGAL,
        /* $CD */ OP_ILLEGAL,
        /* $CE */ (OP_MOV16 | CC_LD16 | ADDR_IMMED  | SRC1_MEMORY               | DST_IXREG),
        /* $CF */ OP_ILLEGAL,

        /* $D0 */ (OP_SUB   | CC_SUB  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $D1 */ (OP_SUB   | CC_CMP  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $D2 */ (OP_SBC   | CC_SUB  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $D3 */ OP_ILLEGAL,
        /* $D4 */ (OP_AND   | CC_AND  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $D5 */ (OP_AND   | CC_BIT  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $D6 */ (OP_MOV8  | CC_LD   | ADDR_DIRECT | SRC1_MEMORY               | DST_ACCB),
        /* $D7 */ (OP_MOV8  | CC_ST   | ADDR_DIRECT | SRC1_ACCB                 | DST_MEMORY),
        /* $D8 */ (OP_EOR   | CC_EOR  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $D9 */ (OP_ADC   | CC_ADD  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $DA */ (OP_ORA   | CC_OR   | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $DB */ (OP_ADD   | CC_ADD  | ADDR_DIRECT | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $DC */ OP_ILLEGAL,
        /* $DD */ OP_ILLEGAL,
        /* $DE */ (OP_MOV16 | CC_LD16 | ADDR_DIRECT | SRC1_MEMORY               | DST_IXREG),
        /* $DF */ (OP_MOV16 | CC_ST16 | ADDR_DIRECT | SRC1_IXREG                | DST_MEMORY),

        /* $E0 */ (OP_SUB   | CC_SUB  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $E1 */ (OP_SUB   | CC_CMP  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $E2 */ (OP_SBC   | CC_SUB  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $E3 */ OP_ILLEGAL,
        /* $E4 */ (OP_AND   | CC_AND  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $E5 */ (OP_AND   | CC_BIT  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $E6 */ (OP_MOV8  | CC_LD   | ADDR_INDEX  | SRC1_MEMORY               | DST_ACCB),
        /* $E7 */ (OP_MOV8  | CC_ST   | ADDR_INDEX  | SRC1_ACCB                 | DST_MEMORY),
        /* $E8 */ (OP_EOR   | CC_EOR  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $E9 */ (OP_ADC   | CC_ADD  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $EA */ (OP_ORA   | CC_OR   | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $EB */ (OP_ADD   | CC_ADD  | ADDR_INDEX  | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $EC */ OP_ILLEGAL,
        /* $ED */ OP_ILLEGAL,
        /* $EE */ (OP_MOV16 | CC_LD16 | ADDR_INDEX  | SRC1_MEMORY               | DST_IXREG),
        /* $EF */ (OP_MOV16 | CC_ST16 | ADDR_INDEX  | SRC1_IXREG                | DST_MEMORY),

        /* $F0 */ (OP_SUB   | CC_SUB  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $F1 */ (OP_SUB   | CC_CMP  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $F2 */ (OP_SBC   | CC_SUB  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $F3 */ OP_ILLEGAL,
        /* $F4 */ (OP_AND   | CC_AND  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $F5 */ (OP_AND   | CC_BIT  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_DISCARD),
        /* $F6 */ (OP_MOV8  | CC_LD   | ADDR_EXTEND | SRC1_MEMORY               | DST_ACCB),
        /* $F7 */ (OP_MOV8  | CC_ST   | ADDR_EXTEND | SRC1_ACCB                 | DST_MEMORY),
        /* $F8 */ (OP_EOR   | CC_EOR  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $F9 */ (OP_ADC   | CC_ADD  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $FA */ (OP_ORA   | CC_OR   | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $FB */ (OP_ADD   | CC_ADD  | ADDR_EXTEND | SRC1_ACCB   | SRC2_MEMORY | DST_ACCB),
        /* $FC */ OP_ILLEGAL,
        /* $FD */ OP_ILLEGAL,
        /* $FE */ (OP_MOV16 | CC_LD16 | ADDR_EXTEND | SRC1_MEMORY               | DST_IXREG),
        /* $FF */ (OP_MOV16 | CC_ST16 | ADDR_EXTEND | SRC1_IXREG                | DST_MEMORY),
    };

    public static String opMnemonics[] = {
        /*         $_0       $_1       $_2       $_3       $_4       $_5       $_6       $_7  */
        /*         $_8       $_9       $_A       $_B       $_C       $_D       $_E       $_F  */

        /* $00 */ "ILL   ", "NOP   ", "ILL   ", "ILL   ", "ILL   ", "ILL   ", "TAP   ", "TPA   ",
        /* $08 */ "INX   ", "DEX   ", "CLV   ", "SEV   ", "CLC   ", "SEC   ", "CLI   ", "SEI   ",

        /* $10 */ "SBA   ", "CBA   ", "ILL   ", "ILL   ", "ILL   ", "ILL   ", "TAB   ", "TBA   ",
        /* $18 */ "ILL   ", "DAA   ", "ILL   ", "ABA   ", "ILL   ", "ILL   ", "ILL   ", "ILL   ",

        /* $20 */ "BRA  *", "ILL   ", "BHI  *", "BLS  *", "BCC  *", "BCS  *", "BNE  *", "BEQ  *",
        /* $28 */ "BVC  *", "BVS  *", "BPL  *", "BMI  *", "BGE  *", "BLT  *", "BGT  *", "BLE  *",

        /* $30 */ "TSX   ", "INS   ", "PULA  ", "PULB  ", "DES   ", "TXS   ", "PHSA  ", "PSHB  ",
        /* $38 */ "ILL   ", "RTS   ", "ILL   ", "RTI   ", "ILL   ", "ILL   ", "WAI   ", "SWI   ",

        /* $40 */ "NEGA  ", "ILL   ", "ILL   ", "COMA  ", "LSRA  ", "ILL   ", "RORA  ", "ASRA  ",
        /* $48 */ "ASLA  ", "ROLA  ", "DECA  ", "ILL   ", "INCA  ", "TSTA  ", "ILL   ", "CLRA  ",

        /* $50 */ "NEGB  ", "ILL   ", "ILL   ", "COMB  ", "LSRB  ", "ILL   ", "RORB  ", "ASRB  ",
        /* $58 */ "ASLB  ", "ROLB  ", "DECB  ", "ILL   ", "INCB  ", "TSTB  ", "ILL   ", "CLRB  ",

        /* $60 */ "NEG  X", "ILL   ", "ILL   ", "COM  X", "LSR  X", "ILL   ", "ROR  X", "ASR  X",
        /* $68 */ "ASL  X", "ROL  X", "DEC  X", "ILL   ", "INC  X", "TST  X", "JMP  X", "CLR  X",

        /* $70 */ "NEG  $", "ILL   ", "ILL   ", "COM  $", "LSR  $", "ILL   ", "ROR  $", "ASR  $",
        /* $78 */ "ASL  $", "ROL  $", "DEC  $", "ILL   ", "INC  $", "TST  $", "JMP  $", "CLR  $",

        /*         $_0       $_1       $_2       $_3       $_4       $_5       $_6       $_7  */
        /*         $_8       $_9       $_A       $_B       $_C       $_D       $_E       $_F  */

        /* $80 */ "SUBA #", "CMPA #", "SBCA #", "ILL   ", "ANDA #", "BITA #", "LDAA #", "ILL   ",
        /* $88 */ "EORA #", "ADCA #", "ORAA #", "ADDA #", "CPX  #", "BSR  *", "LDS  #", "ILL   ",

        /* $90 */ "SUBA %", "CMPA %", "SBCA %", "ILL   ", "ANDA %", "BITA %", "LDAA %", "STAA %",
        /* $98 */ "EORA %", "ADCA %", "ORAA %", "ADDA %", "CPX  %", "ILL   ", "LDS  %", "STS  %",

        /* $A0 */ "SUBA X", "CMPA X", "SBCA X", "ILL   ", "ANDA X", "BITA X", "LDAA X", "STAA X",
        /* $A8 */ "EORA X", "ADCA X", "ORAA X", "ADDA X", "CPX  X", "JSR  X", "LDS  X", "STS  X",

        /* $B0 */ "SUBA $", "CMPA $", "SBCA $", "ILL   ", "ANDA $", "BITA $", "LDAA $", "STAA $",
        /* $B8 */ "EORA $", "ADCA $", "ORAA $", "ADDA $", "CPX  $", "JSR  $", "LDS  $", "STS  $",

        /* $C0 */ "SUBB #", "CMPB #", "SBCB #", "ILL   ", "ANDB #", "BITB #", "LDAB #", "ILL   ",
        /* $C8 */ "EORB #", "ADCB #", "ORAB #", "ADDB #", "ILL   ", "ILL   ", "LDX  #", "ILL   ",

        /* $D0 */ "SUBB %", "CMPB %", "SBCB %", "ILL   ", "ANDB %", "BITB %", "LDAB %", "STAB %",
        /* $D8 */ "EORB %", "ADCB %", "ORAB %", "ADDB %", "ILL   ", "ILL   ", "LDX  %", "STX  %",

        /* $E0 */ "SUBB X", "CMPB X", "SBCB X", "ILL   ", "ANDB X", "BITB X", "LDAB X", "STAB X",
        /* $E8 */ "EORB X", "ADCB X", "ORAB X", "ADDB X", "ILL   ", "ILL   ", "LDX  X", "STX  X",

        /* $F0 */ "SUBB $", "CMPB $", "SBCB $", "ILL   ", "ANDB $", "BITB $", "LDAB $", "STAB $",
        /* $F8 */ "EORB $", "ADCB $", "ORAB $", "ADDB $", "ILL   ", "ILL   ", "LDX  $", "STX  $",
    };
}
