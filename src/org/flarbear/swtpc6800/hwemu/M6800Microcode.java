/*
 * Copyright 2022, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800.hwemu;

import static org.flarbear.swtpc6800.hwemu.M6800.CC_BIT_C;
import static org.flarbear.swtpc6800.hwemu.M6800.CC_BIT_I;
import static org.flarbear.swtpc6800.hwemu.M6800.CC_BIT_N;
import static org.flarbear.swtpc6800.hwemu.M6800.CC_BIT_V;
import static org.flarbear.swtpc6800.hwemu.M6800.CC_BIT_Z;

/**
 *
 * @author Flar
 */
public class M6800Microcode {
    static abstract class CycleTask {
        // Phi1 Leading state is where the processor does all of its
        // work except for allowing the system to interact with the
        // data lines if it needs to read or write data. These tasks
        // will also compute the address and set the isRead flag
        // which will be loaded onto the bus by the processor board
        // during the phi1 trailing cycle.
        final void processPhi1Tasks(M6800 cpu) {
            for (CycleTask task = and; task != null; task = task.and) {
                task.handlePhi1(cpu);
            }
        }

        // The only use for Phi2 Leading tasks is to load the busData
        // lines when the processor needs to write something. The
        // address of the read or write is established during
        // phi1 processing.
        final void processPhi2LeadingTasks(M6800 cpu) {
            for (CycleTask task = and; task != null; task = task.and) {
                task.handlePhi2Leading(cpu);
            }
        }

        // The only use for Phi2 Trailing tasks is to store the busData
        // lines into a destination when the processor has asked to read
        // something.
        final void processPhi2TrailingTasks(M6800 cpu) {
            for (CycleTask task = and; task != null; task = task.and) {
                task.handlePhi2Trailing(cpu);
            }
        }

        protected void handlePhi1(M6800 cpu) {}
        protected void handlePhi2Leading(M6800 cpu) {}
        protected void handlePhi2Trailing(M6800 cpu) {}

        CycleTask and;
        CycleTask then;

        CycleTask next() {
            return then;
        }

        CycleTask and(CycleTask code) {
            // Make sure to append the "and" task to the last CycleTask
            // in the "then" chain so that then() and and() calls
            // can be interspersed and will always build from the
            // tail end of all tasks.
            if (then != null) {
                then.and(code);
            } else if (and != null) {
                and.and(code);
            } else {
                and = code;
            }
            return this;
        }

        CycleTask then(CycleTask code) {
            if (then != null) {
                then.then(code);
            } else {
                then = code;
            }
            return this;
        }
    }

    static abstract class TerminalCycleTask extends CycleTask {
        @Override
        CycleTask and(CycleTask and) {
            throw new UnsupportedOperationException("Cannot chain from a terminal task");
        }

        @Override
        CycleTask then(CycleTask then) {
            throw new UnsupportedOperationException("Cannot chain from a terminal task");
        }
    }

    static final CycleTask NOP = new TerminalCycleTask() { };
    static final CycleTask TBD = new TerminalCycleTask() {
        @Override
        protected void handlePhi1(M6800 cpu) {
            throw new UnsupportedOperationException("Instruction not implemented yet.");
        }

        @Override
        protected void handlePhi2Leading(M6800 cpu) {
            throw new UnsupportedOperationException("Instruction not implemented yet.");
        }

        @Override
        protected void handlePhi2Trailing(M6800 cpu) {
            throw new UnsupportedOperationException("Instruction not implemented yet.");
        }
    };
    static final CycleTask UNDOCUMENTED = new TerminalCycleTask() {
        @Override
        protected void handlePhi1(M6800 cpu) {
            throw new UnsupportedOperationException("Instruction not implemented yet.");
        }

        @Override
        protected void handlePhi2Leading(M6800 cpu) {
            throw new UnsupportedOperationException("Instruction not implemented yet.");
        }

        @Override
        protected void handlePhi2Trailing(M6800 cpu) {
            throw new UnsupportedOperationException("Instruction not implemented yet.");
        }
    };

    static final CycleTask LOAD_INSTRUCTION = new TerminalCycleTask() {
        @Override
        protected void handlePhi1(M6800 cpu) {
            cpu.addressLines = cpu.PC++;
            cpu.isRead = true;
        }

        @Override
        protected void handlePhi2Trailing(M6800 cpu) {
            cpu.nextCode = instructionCodes[cpu.dataLines & 0xFF];
        }
    };

    private static enum Reg {
        A,
        B,
        XHi,
        XLo,
        X,
        SHi,
        SLo,
        S,
        PCHi,
        PCLo,
        PC,
        CC,
        THi,
        TLo,
        TAddr,
        T0,
        T1,
    };

    private static char setHi(char value, byte hival) {
        return (char) ((hival & 0xFF00) | (value & 0xFF));
    }

    private static char setLo(char value, byte loval) {
        return (char) ((value & 0xFF00) | (loval & 0xFF));
    }

    private static CycleTask BusTo(Reg reg) {
        return switch (reg) {
            case A -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.A = cpu.dataLines;
                }
            };
            case B -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.B = cpu.dataLines;
                }
            };
            case XHi -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.X = setHi(cpu.X, cpu.dataLines);
                }
            };
            case XLo -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.X = setLo(cpu.X, cpu.dataLines);
                }
            };
            case SHi -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.SP = setHi(cpu.SP, cpu.dataLines);
                }
            };
            case SLo -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.SP = setLo(cpu.SP, cpu.dataLines);
                }
            };
            case PCHi -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.PC = setHi(cpu.PC, cpu.dataLines);
                }
            };
            case PCLo -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.PC = setLo(cpu.PC, cpu.dataLines);
                }
            };
            case CC -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.opCodes = cpu.dataLines;
                }
            };
            case THi -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.tempAddrHi = cpu.dataLines;
                }
            };
            case TLo -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.tempAddrLo = cpu.dataLines;
                }
            };
            case TAddr -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.tempAddrHi = 0;
                    cpu.tempAddrLo = cpu.dataLines;
                }
            };
            case T0 -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.tempData0 = cpu.dataLines;
                }
            };
            case T1 -> new CycleTask() {
                @Override
                protected void handlePhi2Trailing(M6800 cpu) {
                    cpu.tempData1 = cpu.dataLines;
                }
            };
            default -> throw new RuntimeException("BusTo not supported for "+reg);
        };
    }

    private static CycleTask BusFrom(Reg reg) {
        return switch (reg) {
            case A -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = cpu.A;
                }
            };
            case B -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = cpu.B;
                }
            };
            case XHi -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.X >> 16);
                }
            };
            case XLo -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.X);
                }
            };
            case SHi -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.SP >> 16);
                }
            };
            case SLo -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.SP);
                }
            };
            case PCHi -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.PC >> 16);
                }
            };
            case PCLo -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.PC);
                }
            };
            case CC -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = (byte) (cpu.opCodes | 0xC0);
                }
            };
            case T0 -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = cpu.tempData0;
                }
            };
            case T1 -> new CycleTask() {
                @Override
                protected void handlePhi2Leading(M6800 cpu) {
                    cpu.dataLines = cpu.tempData1;
                }
            };
            default -> throw new RuntimeException("BusFrom not supported for "+reg);
        };
    }

    private static CycleTask TempFrom(Reg reg) {
        return switch (reg) {
            case A -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempData0 = cpu.A;
                }
            };
            case B -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempData0 = cpu.B;
                }
            };
            case XHi -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrHi = (byte) (cpu.X >> 16);
                }
            };
            case XLo -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrLo = (byte) (cpu.X);
                }
            };
            case X -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrLo = (byte) (cpu.X);
                    cpu.tempAddrHi = (byte) (cpu.X >> 16);
                }
            };
            case SHi -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrHi = (byte) (cpu.SP >> 16);
                }
            };
            case SLo -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrLo = (byte) (cpu.SP);
                }
            };
            case S -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrLo = (byte) (cpu.SP);
                    cpu.tempAddrHi = (byte) (cpu.SP >> 16);
                }
            };
            case PCHi -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrHi = (byte) (cpu.PC >> 16);
                }
            };
            case PCLo -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrLo = (byte) (cpu.PC);
                }
            };
            case PC -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempAddrLo = (byte) (cpu.PC);
                    cpu.tempAddrHi = (byte) (cpu.PC >> 16);
                }
            };
            case CC -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.tempData0 = (byte) (cpu.opCodes | 0xC0);
                }
            };
            default -> throw new RuntimeException("TempFrom not supported for "+reg);
        };
    }

    private static CycleTask TempAddr(char address) {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.tempAddrHi = (byte) (address >> 8);
                cpu.tempAddrLo = (byte) (address & 0xFF);
            }
        };
    }

    private static CycleTask LoadIRQ() {
        return TempAddr((char) 0xFFFA)
                .then(LoadFromTempAddrInc(Reg.PCHi))
                .then(LoadFromTempAddr(Reg.PCLo));
    }

    private static CycleTask TempTo(Reg reg) {
        return switch (reg) {
            case A -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.A = cpu.tempData0;
                }
            };
            case B -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.B = cpu.tempData0;
                }
            };
            case XHi -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.X = setHi(cpu.X, cpu.tempAddrHi);
                }
            };
            case XLo -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.X = setLo(cpu.X, cpu.tempAddrLo);
                }
            };
            case X -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.X = setHi(cpu.X, cpu.tempAddrHi);
                    cpu.X = setLo(cpu.X, cpu.tempAddrLo);
                }
            };
            case SHi -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.SP = setHi(cpu.SP, cpu.tempAddrHi);
                }
            };
            case SLo -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.SP = setLo(cpu.SP, cpu.tempAddrLo);
                }
            };
            case S -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.X = setHi(cpu.SP, cpu.tempAddrHi);
                    cpu.X = setLo(cpu.SP, cpu.tempAddrLo);
                }
            };
            case PCHi -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.PC = setHi(cpu.PC, cpu.tempAddrHi);
                }
            };
            case PCLo -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.PC = setLo(cpu.PC, cpu.tempAddrLo);
                }
            };
            case PC -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.PC = setHi(cpu.PC, cpu.tempAddrHi);
                    cpu.PC = setLo(cpu.PC, cpu.tempAddrLo);
                }
            };
            case CC -> new CycleTask() {
                @Override
                protected void handlePhi1(M6800 cpu) {
                    cpu.opCodes = cpu.tempData0;
                }
            };
            default -> throw new RuntimeException("TempTo not supported for "+reg);
        };
    }

    private static CycleTask AddTempAddrLo() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                int addr = (cpu.tempAddrLo & 0xFF) + (cpu.tempData0 & 0xFF);
                cpu.tempAddrLo = (byte) addr;
                cpu.tempAddrCarry = (byte) (addr >> 8);
            }
        };
    }

    private static CycleTask AddTempAddrHi() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                int addr = (cpu.tempAddrHi & 0xFF) + (cpu.tempData0 & 0xFF) + cpu.tempAddrCarry;
                cpu.tempAddrHi = (byte) addr;
            }
        };
    }

    private static CycleTask BregToTempData1() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.tempData1 = cpu.B; }
        };
    }

    private static CycleTask LoadIMM(Reg dest) {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.addressLines = cpu.PC++;
                cpu.isRead = true;
            }
        }.and(BusTo(dest));
    }

    private static CycleTask LoadIMM16(Reg destHi, Reg destLo) {
        return LoadIMM(destHi)
                .then(LoadIMM(destLo));
    }

    private static CycleTask IncTempAddr() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.tempAddrLo++;
                if (cpu.tempAddrLo == 0) {
                    cpu.tempAddrHi++;
                }
            }
        };
    }

    private static CycleTask DecTempAddr() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                if (cpu.tempAddrLo == 0) {
                    cpu.tempAddrHi--;
                }
                cpu.tempAddrLo--;
            }
        };
    }

    private static CycleTask LoadFromTempAddr(Reg dest) {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.addressLines = (char) ((cpu.tempAddrHi << 8) + cpu.tempAddrLo);
                cpu.isRead = true;
            }
        }.and(BusTo(dest));
    }

    private static CycleTask LoadFromTempAddrInc(Reg dest) {
        return LoadFromTempAddr(dest).and(IncTempAddr());
    }

    private static CycleTask StoreAtTempAddr(Reg src) {
        return BusFrom(src).and(new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.addressLines = (char) ((cpu.tempAddrHi << 8) + cpu.tempAddrLo);
                cpu.isRead = false;
            }
        });
    }

    private static CycleTask StoreAtTempAddrInc(Reg src) {
        return StoreAtTempAddr(src).and(IncTempAddr());
    }

    private static CycleTask LoadDIR(Reg dest) {
        return LoadIMM(Reg.TAddr)
                .then(LoadFromTempAddr(dest));
    }

    private static CycleTask LoadDIR16(Reg destHi, Reg destLo) {
        return LoadIMM(Reg.TAddr)
                .then(LoadFromTempAddrInc(destHi))
                .then(LoadFromTempAddr(destLo));
    }

    private static CycleTask StoreDIR(Reg src) {
        return LoadIMM(Reg.TAddr)
                .then((StoreAtTempAddr(src)));
    }

    private static CycleTask StoreDIR16(Reg srcHi, Reg srcLo) {
        return LoadIMM(Reg.TAddr)
                .then(StoreAtTempAddrInc(srcHi))
                .then(StoreAtTempAddr(srcLo));
    }

    private static CycleTask LoadEXT(Reg dest) {
        return LoadIMM(Reg.THi)
                .then(LoadIMM(Reg.TLo))
                .then(LoadFromTempAddr(dest));
    }

    private static CycleTask LoadEXT16(Reg destHi, Reg destLo) {
        return LoadIMM(Reg.THi)
                .then(LoadIMM(Reg.TLo))
                .then(LoadFromTempAddrInc(destHi))
                .then(LoadFromTempAddr(destLo));
    }

    private static CycleTask StoreEXT(Reg src) {
        return LoadIMM(Reg.THi)
                .then(LoadIMM(Reg.TLo))
                .then(StoreAtTempAddr(src));
    }

    private static CycleTask StoreEXT16(Reg srcHi, Reg srcLo) {
        return LoadIMM(Reg.THi)
                .then(LoadIMM(Reg.TLo))
                .then(StoreAtTempAddrInc(srcHi))
                .then(StoreAtTempAddr(srcLo));
    }

    private static CycleTask LoadIND(Reg dest) {
        return LoadIMM(Reg.T0).and(TempFrom(Reg.X))
                .then(AddTempAddrLo())
                .then(AddTempAddrHi())
                .then(LoadFromTempAddr(dest));
    }

    private static CycleTask LoadIND16(Reg destHi, Reg destLo) {
        return LoadIMM(Reg.T0).and(TempFrom(Reg.X))
                .then(AddTempAddrLo())
                .then(AddTempAddrHi())
                .then(LoadFromTempAddrInc(destHi))
                .then(LoadFromTempAddr(destLo));
    }

    private static CycleTask StoreIND(Reg src) {
        return LoadIMM(Reg.T0).and(TempFrom(Reg.X))
                .then(AddTempAddrLo())
                .then(AddTempAddrHi())
                .then(StoreAtTempAddr(src));
    }

    private static CycleTask StoreIND16(Reg srcHi, Reg srcLo) {
        return LoadIMM(Reg.T0).and(TempFrom(Reg.X))
                .then(AddTempAddrLo())
                .then(AddTempAddrHi())
                .then(StoreAtTempAddrInc(srcHi))
                .then(StoreAtTempAddr(srcLo));
    }

    private static CycleTask RegOpImpl(Reg reg, CycleTask opTask, boolean storeResult) {
        assert(reg == Reg.A || reg == Reg.B);
        CycleTask op = TempFrom(reg);
        op.and(opTask);
        if (storeResult) {
            op.and(TempTo(reg));
        }
        return op;
    }
    private static CycleTask RegOp(Reg reg, CycleTask opTask) { return RegOpImpl(reg, opTask, true); }
    private static CycleTask TestOp(Reg reg, CycleTask opTask) { return RegOpImpl(reg, opTask, false); }

    private static CycleTask UnaryOp(Reg reg, CycleTask opTask) {
        return TempFrom(reg).and(opTask).and(TempTo(reg));
    }
    private static CycleTask UnaryOp(CycleTask opTask) {
        return opTask.then(StoreAtTempAddr(Reg.T0));
    }

    private static CycleTask Add() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.addAndSetCC(false); }
        };
    }

    private static CycleTask Adc() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.addAndSetCC(true); }
        };
    }

    private static CycleTask Sub() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.subAndSetCC(false); }
        };
    }

    private static CycleTask SubX() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.cmpXAndSetCC(); }
        };
    }

    private static CycleTask Sbc() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.subAndSetCC(true); }
        };
    }

    private static CycleTask And() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.andAndSetCC(); }
        };
    }

    private static CycleTask Or() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.orAndSetCC(); }
        };
    }

    private static CycleTask Xor() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.xorAndSetCC(); }
        };
    }

    private static CycleTask Neg() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.negAndSetCC(); }
        };
    }

    private static CycleTask Com() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.comAndSetCC(); }
        };
    }

    private static CycleTask Lsr() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.lsrAndSetCC(); }
        };
    }

    private static CycleTask Ror() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.rorAndSetCC(); }
        };
    }

    private static CycleTask Asr() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.asrAndSetCC(); }
        };
    }

    private static CycleTask Asl() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.aslAndSetCC(); }
        };
    }

    private static CycleTask Rol() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.rolAndSetCC(); }
        };
    }

    private static CycleTask Dec() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.decAndSetCC(); }
        };
    }

    private static CycleTask Inc() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.incAndSetCC(); }
        };
    }

    private static CycleTask Tst() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.tstAndSetCC(); }
        };
    }

    private static CycleTask Clr() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.clrAndSetCC(); }
        };
    }

    private static CycleTask Daa() {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) { cpu.daaAndSetCC(); }
        };
    }

    @FunctionalInterface
    private static interface BranchPredicate {
        boolean shouldBranch(int codes);
    }

    private static CycleTask Branch(BranchPredicate predicate) {
        return LoadIMM(Reg.T0).and(TempFrom(Reg.PC))
                .then(AddTempAddrLo())
                .then(AddTempAddrHi()).and(new CycleTask() {
                    @Override
                    protected void handlePhi1(M6800 cpu) {
                        if (predicate.shouldBranch(cpu.opCodes)) {
                            cpu.PC = (char) ((cpu.tempAddrHi << 8) | (cpu.tempAddrLo & 0xFF));
                        }
                    }
                });
    }

    private static CycleTask Bsr() {
        return LoadIMM(Reg.T0)
                .then(TempFrom(Reg.PC))
                .then(AddTempAddrLo())
                .then(AddTempAddrHi())
                .then(PushOnStack(Reg.PCLo))
                .then(PushOnStack(Reg.PCHi))
                .then(TempTo(Reg.PC));
    }

    private static CycleTask JsrEXT() {
        return LoadEXT16(Reg.THi, Reg.TLo)
                .then(PushOnStack(Reg.PCLo))
                .then(PushOnStack(Reg.PCHi))
                .then(TempTo(Reg.PC));
    }

    private static CycleTask JsrIND() {
        return LoadIND16(Reg.THi, Reg.TLo)
                .then(PushOnStack(Reg.PCLo))
                .then(PushOnStack(Reg.PCHi))
                .then(TempTo(Reg.PC));
    }

    private static CycleTask PushOnStack(Reg reg) {
        return BusFrom(reg).and(new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.addressLines = cpu.SP;
                cpu.SP--;
                cpu.isRead = false;
            }
        });
    }

    private static CycleTask Push(Reg reg) {
        return (TempFrom(Reg.S).and(StoreAtTempAddr(reg))
                .then(DecTempAddr()))
                .then(TempTo(Reg.S));
    }

    private static CycleTask Pull(Reg reg) {
        return (TempFrom(Reg.S).and(IncTempAddr()))
                .then(LoadFromTempAddr(reg))
                .then(TempTo(Reg.S));
    }

    private static CycleTask Push(Reg... regs) {
        CycleTask op = TempFrom(Reg.S);
        for (Reg reg : regs) {
            op.then(StoreAtTempAddr(reg).and(DecTempAddr()));
        }
        op.then(TempTo(Reg.S));
        return op;
    }

    private static CycleTask Pull(Reg... regs) {
        CycleTask op = TempFrom(Reg.S).and(IncTempAddr());
        for (Reg reg : regs) {
            op.then(LoadFromTempAddrInc(reg));
        }
        op.then(TempTo(Reg.S));
        return op;
    }

    private static CycleTask SetCC(int bit) {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.opCodes |= bit;
            }
        };
    }

    private static CycleTask ClearCC(int bit) {
        return new CycleTask() {
            @Override
            protected void handlePhi1(M6800 cpu) {
                cpu.opCodes = (byte) (cpu.opCodes & ~bit);
            }
        };
    }

    static final CycleTask[] instructionCodes = {
        /* 0x00           */ UNDOCUMENTED,
        /* 0x01 NOP       */ NOP,
        /* 0x02           */ UNDOCUMENTED,
        /* 0x03           */ UNDOCUMENTED,
        /* 0x04           */ UNDOCUMENTED,
        /* 0x05           */ UNDOCUMENTED,
        /* 0x06 TAP       */ TempFrom(Reg.A).and(TempTo(Reg.CC)),
        /* 0x07 TPA       */ TempFrom(Reg.CC).and(TempTo(Reg.A)),
        /* 0x08 INX       */ TempFrom(Reg.X).then(IncTempAddr()).then(TempTo(Reg.X)),
        /* 0x09 DEX       */ TempFrom(Reg.X).then(DecTempAddr()).then(TempTo(Reg.X)),
        /* 0x0A CLV       */ ClearCC(CC_BIT_V),
        /* 0x0B SEV       */ SetCC(CC_BIT_V),
        /* 0x0C CLC       */ ClearCC(CC_BIT_C),
        /* 0x0D SEC       */ SetCC(CC_BIT_C),
        /* 0x0E CLI       */ ClearCC(CC_BIT_I),
        /* 0x0F SEI       */ SetCC(CC_BIT_I),

        /* 0x10 SBA       */ TempFrom(Reg.A).and(BregToTempData1()).and(Sub()).and(TempTo(Reg.A)),
        /* 0x11 CBA       */ TempFrom(Reg.A).and(BregToTempData1()).and(Sub()),
        /* 0x12           */ UNDOCUMENTED,
        /* 0x13           */ UNDOCUMENTED,
        /* 0x14           */ UNDOCUMENTED,
        /* 0x15           */ UNDOCUMENTED,
        /* 0x16 TAB       */ TempFrom(Reg.A).and(TempTo(Reg.B)),
        /* 0x17 TBA       */ TempFrom(Reg.B).and(TempTo(Reg.A)),
        /* 0x18           */ UNDOCUMENTED,
        /* 0x19 DAA       */ UnaryOp(Reg.A, Daa()),
        /* 0x1A           */ UNDOCUMENTED,
        /* 0x1B ABA       */ BregToTempData1().and(RegOp(Reg.A, Add())),
        /* 0x1C           */ UNDOCUMENTED,
        /* 0x1D           */ UNDOCUMENTED,
        /* 0x1E           */ UNDOCUMENTED,
        /* 0x1F           */ UNDOCUMENTED,

        /* 0x20 BRA  ,REL */ Branch((cc) -> true),
        /* 0x21           */ UNDOCUMENTED,
        /* 0x22 BHI  ,REL */ Branch((cc) -> ((cc & (CC_BIT_C | CC_BIT_Z)) == 0)),
        /* 0x23 BLS  ,REL */ Branch((cc) -> ((cc & (CC_BIT_C | CC_BIT_Z)) != 0)),
        /* 0x24 BCC  ,REL */ Branch((cc) -> (cc & CC_BIT_C) == 0),
        /* 0x25 BCS  ,REL */ Branch((cc) -> (cc & CC_BIT_C) != 0),
        /* 0x26 BNE  ,REL */ Branch((cc) -> (cc & CC_BIT_Z) == 0),
        /* 0x27 BEQ  ,REL */ Branch((cc) -> (cc & CC_BIT_Z) != 0),
        /* 0x28 BVC  ,REL */ Branch((cc) -> (cc & CC_BIT_V) == 0),
        /* 0x29 BVS  ,REL */ Branch((cc) -> (cc & CC_BIT_V) != 0),
        /* 0x2A BPL  ,REL */ Branch((cc) -> (cc & CC_BIT_N) == 0),
        /* 0x2B BMI  ,REL */ Branch((cc) -> (cc & CC_BIT_N) != 0),
        /* 0x2C BGE  ,REL */ Branch((cc) -> ((cc & CC_BIT_N) == 0) == ((cc & CC_BIT_V) == 0)),
        /* 0x2D BLT  ,REL */ Branch((cc) -> ((cc & CC_BIT_N) == 0) != ((cc & CC_BIT_V) == 0)),
        /* 0x2E BGT  ,REL */ Branch((cc) -> ((cc & CC_BIT_Z) == 0) && (((cc & CC_BIT_N) == 0) == ((cc & CC_BIT_V) == 0))),
        /* 0x2F BLE  ,REL */ Branch((cc) -> ((cc & CC_BIT_Z) != 0) || (((cc & CC_BIT_N) == 0) != ((cc & CC_BIT_V) == 0))),

        /* 0x30 TSX       */ TempFrom(Reg.S).then(IncTempAddr()).then(TempTo(Reg.X)),
        /* 0x31 INS       */ TempFrom(Reg.S).then(IncTempAddr()).then(TempTo(Reg.S)),
        /* 0x32 PUL A     */ Pull(Reg.A),
        /* 0x33 PUL B     */ Pull(Reg.B),
        /* 0x34 DES       */ TempFrom(Reg.S).then(DecTempAddr()).then(TempTo(Reg.S)),
        /* 0x35 TXS       */ TempFrom(Reg.X).then(DecTempAddr()).then(TempTo(Reg.S)),
        /* 0x36 PSH A     */ Push(Reg.A),
        /* 0x37 PSH B     */ Push(Reg.B),
        /* 0x38           */ UNDOCUMENTED,
        /* 0x39 RTS       */ Pull(Reg.PCLo, Reg.PCHi),
        /* 0x3A           */ UNDOCUMENTED,
        /* 0x3B RTI       */ Pull(Reg.CC, Reg.B, Reg.A, Reg.XHi, Reg.XLo, Reg.PCHi, Reg.PCLo),
        /* 0x3C           */ UNDOCUMENTED,
        /* 0x3D           */ UNDOCUMENTED,
        /* 0x3E WAI       */ TBD,
        /* 0x3F SWI       */ Push(Reg.PCLo, Reg.PCHi, Reg.XLo, Reg.XHi, Reg.A, Reg.B, Reg.CC).then(LoadIRQ()),

        /* 0x40 NEG A     */ UnaryOp(Reg.A, Neg()),
        /* 0x41           */ UNDOCUMENTED,
        /* 0x42           */ UNDOCUMENTED,
        /* 0x43 COM A     */ UnaryOp(Reg.A, Com()),
        /* 0x44 LSR A     */ UnaryOp(Reg.A, Lsr()),
        /* 0x45           */ UNDOCUMENTED,
        /* 0x46 ROR A     */ UnaryOp(Reg.A, Ror()),
        /* 0x47 ASR A     */ UnaryOp(Reg.A, Asr()),
        /* 0x48 ASL A     */ UnaryOp(Reg.A, Asl()),
        /* 0x49 ROL A     */ UnaryOp(Reg.A, Rol()),
        /* 0x4A DEC A     */ UnaryOp(Reg.A, Dec()),
        /* 0x4B           */ UNDOCUMENTED,
        /* 0x4C INC A     */ UnaryOp(Reg.A, Inc()),
        /* 0x4D TST A     */ UnaryOp(Reg.A, Tst()),
        /* 0x4E           */ UNDOCUMENTED,
        /* 0x4F CLR A     */ UnaryOp(Reg.A, Clr()),

        /* 0x50 NEG B     */ UnaryOp(Reg.B, Neg()),
        /* 0x51           */ UNDOCUMENTED,
        /* 0x52           */ UNDOCUMENTED,
        /* 0x53 COM B     */ UnaryOp(Reg.B, Com()),
        /* 0x54 LSR B     */ UnaryOp(Reg.B, Lsr()),
        /* 0x55           */ UNDOCUMENTED,
        /* 0x56 ROR B     */ UnaryOp(Reg.B, Ror()),
        /* 0x57 ASR B     */ UnaryOp(Reg.B, Asr()),
        /* 0x58 ASL B     */ UnaryOp(Reg.B, Asl()),
        /* 0x59 ROL B     */ UnaryOp(Reg.B, Rol()),
        /* 0x5A DEC B     */ UnaryOp(Reg.B, Dec()),
        /* 0x5B           */ UNDOCUMENTED,
        /* 0x5C INC B     */ UnaryOp(Reg.B, Inc()),
        /* 0x5D TST B     */ UnaryOp(Reg.B, Tst()),
        /* 0x5E           */ UNDOCUMENTED,
        /* 0x5F CLR B     */ UnaryOp(Reg.B, Clr()),

        /* 0x60 NEG  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Neg())),
        /* 0x61           */ UNDOCUMENTED,
        /* 0x62           */ UNDOCUMENTED,
        /* 0x63 COM  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Com())),
        /* 0x64 LSR  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Lsr())),
        /* 0x65           */ UNDOCUMENTED,
        /* 0x66 ROR  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Ror())),
        /* 0x67 ASR  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Asr())),
        /* 0x68 ASL  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Asl())),
        /* 0x69 ROL  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Rol())),
        /* 0x6A DEC  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Dec())),
        /* 0x6B           */ UNDOCUMENTED,
        /* 0x6C INC  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Inc())),
        /* 0x6D TST  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Tst())),
        /* 0x6E JMP  ,IND */ LoadIND16(Reg.PCHi, Reg.PCLo),
        /* 0x6F CLR  ,IND */ LoadIND(Reg.T0).then(UnaryOp(Clr())),

        /* 0x70 NEG  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Neg())),
        /* 0x71           */ UNDOCUMENTED,
        /* 0x72           */ UNDOCUMENTED,
        /* 0x73 COM  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Com())),
        /* 0x74 LSR  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Lsr())),
        /* 0x75           */ UNDOCUMENTED,
        /* 0x76 ROR  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Ror())),
        /* 0x77 ASR  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Asr())),
        /* 0x78 ASL  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Asl())),
        /* 0x79 ROL  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Rol())),
        /* 0x7A DEC  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Dec())),
        /* 0x7B           */ UNDOCUMENTED,
        /* 0x7C INC  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Inc())),
        /* 0x7D TST  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Tst())),
        /* 0x7E JMP  ,EXT */ LoadEXT16(Reg.PCHi, Reg.PCLo),
        /* 0x7F CLR  ,EXT */ LoadEXT(Reg.T0).then(UnaryOp(Clr())),

        /* 0x80 SUB A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, Sub())),
        /* 0x81 CMP A,IMM */ LoadIMM(Reg.T1).and(TestOp(Reg.A, Sub())),
        /* 0x82 SBC A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, Sbc())),
        /* 0x83           */ UNDOCUMENTED,
        /* 0x84 AND A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, And())),
        /* 0x85 BIT A,IMM */ LoadIMM(Reg.T1).and(TestOp(Reg.A, And())),
        /* 0x86 LDA A,IMM */ LoadIMM(Reg.A),
        /* 0x87           */ UNDOCUMENTED,
        /* 0x88 EOR A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, Xor())),
        /* 0x89 ADC A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, Adc())),
        /* 0x8A ORA A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, Or())),
        /* 0x8B ADD A,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.A, Add())),
        /* 0x8C CPX X,IMM */ LoadIMM16(Reg.THi, Reg.TLo).and(SubX()),
        /* 0x8D BSR  ,REL */ Bsr(),
        /* 0x8E LDS S,IMM */ LoadIMM16(Reg.SHi, Reg.SLo),
        /* 0x8F           */ UNDOCUMENTED,

        /* 0x90 SUB A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, Sub())),
        /* 0x91 CMP A,DIR */ LoadDIR(Reg.T1).and(TestOp(Reg.A, Sub())),
        /* 0x92 SBC A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, Sbc())),
        /* 0x93           */ UNDOCUMENTED,
        /* 0x94 AND A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, And())),
        /* 0x95 BIT A,DIR */ LoadDIR(Reg.T1).and(TestOp(Reg.A, And())),
        /* 0x96 LDA A,DIR */ LoadDIR(Reg.A),
        /* 0x97 STA A,DIR */ StoreDIR(Reg.A),
        /* 0x98 EOR A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, Xor())),
        /* 0x99 ADC A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, Adc())),
        /* 0x9A ORA A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, Or())),
        /* 0x9B ADD A,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.A, Add())),
        /* 0x9C CPX X,DIR */ LoadDIR(Reg.TAddr).and(SubX()),
        /* 0x9D           */ UNDOCUMENTED,
        /* 0x9E LDS S,DIR */ LoadDIR16(Reg.SHi, Reg.SLo),
        /* 0x9F STS S,DIR */ StoreDIR16(Reg.SHi, Reg.SLo),

        /* 0xA0 SUB A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, Sub())),
        /* 0xA1 CMP A,IND */ LoadIND(Reg.T1).and(TestOp(Reg.A, Sub())),
        /* 0xA2 SBC A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, Sbc())),
        /* 0xA3           */ UNDOCUMENTED,
        /* 0xA4 AND A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, And())),
        /* 0xA5 BIT A,IND */ LoadIND(Reg.T1).and(TestOp(Reg.A, And())),
        /* 0xA6 LDA A,IND */ LoadIND(Reg.A),
        /* 0xA7 STA A,IND */ StoreIND(Reg.A),
        /* 0xA8 EOR A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, Xor())),
        /* 0xA9 ADC A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, Adc())),
        /* 0xAA ORA A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, Or())),
        /* 0xAB ADD A,IND */ LoadIND(Reg.T1).and(RegOp(Reg.A, Add())),
        /* 0xAC CPX X,IND */ LoadIND16(Reg.THi, Reg.TLo).and(SubX()),
        /* 0xAD JSR  ,IND */ JsrIND(),
        /* 0xAE LDS S,IND */ LoadIND16(Reg.SHi, Reg.SLo),
        /* 0xAF STS S,IND */ StoreIND16(Reg.SHi, Reg.SLo),

        /* 0xB0 SUB A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, Sub())),
        /* 0xB1 CMP A,EXT */ LoadEXT(Reg.T1).and(TestOp(Reg.A, Sub())),
        /* 0xB2 SBC A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, Sbc())),
        /* 0xB3           */ UNDOCUMENTED,
        /* 0xB4 AND A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, And())),
        /* 0xB5 BIT A,EXT */ LoadEXT(Reg.T1).and(TestOp(Reg.A, And())),
        /* 0xB6 LDA A,EXT */ LoadEXT(Reg.A),
        /* 0xB7 STA A,EXT */ StoreEXT(Reg.A),
        /* 0xB8 EOR A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, Xor())),
        /* 0xB9 ADC A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, Adc())),
        /* 0xBA ORA A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, Or())),
        /* 0xBB ADD A,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.A, Add())),
        /* 0xBC CPX X,EXT */ LoadEXT16(Reg.THi, Reg.TLo).and(SubX()),
        /* 0xBD JSR  ,EXT */ JsrEXT(),
        /* 0xBE LDS S,EXT */ LoadEXT16(Reg.SHi, Reg.SLo),
        /* 0xBF STS S,EXT */ StoreEXT16(Reg.SHi, Reg.SLo),

        /* 0xC0 SUB B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, Sub())),
        /* 0xC1 CMP B,IMM */ LoadIMM(Reg.T1).and(TestOp(Reg.B, Sub())),
        /* 0xC2 SBC B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, Sbc())),
        /* 0xC3           */ UNDOCUMENTED,
        /* 0xC4 AND B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, And())),
        /* 0xC5 BIT B,IMM */ LoadIMM(Reg.T1).and(TestOp(Reg.B, And())),
        /* 0xC6 LDA B,IMM */ LoadIMM(Reg.B),
        /* 0xC7           */ UNDOCUMENTED,
        /* 0xC8 EOR B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, Xor())),
        /* 0xC9 ADC B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, Adc())),
        /* 0xCA ORA B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, Or())),
        /* 0xCB ADD B,IMM */ LoadIMM(Reg.T1).and(RegOp(Reg.B, Add())),
        /* 0xCC           */ UNDOCUMENTED,
        /* 0xCD           */ UNDOCUMENTED,
        /* 0xCE LDX X,IMM */ LoadIMM16(Reg.XHi, Reg.XLo),
        /* 0xCF           */ UNDOCUMENTED,

        /* 0xD0 SUB B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, Sub())),
        /* 0xD1 CMP B,DIR */ LoadDIR(Reg.T1).and(TestOp(Reg.B, Sub())),
        /* 0xD2 SBC B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, Sbc())),
        /* 0xD3           */ UNDOCUMENTED,
        /* 0xD4 AND B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, And())),
        /* 0xD5 BIT B,DIR */ LoadDIR(Reg.T1).and(TestOp(Reg.B, And())),
        /* 0xD6 LDA B,DIR */ LoadDIR(Reg.B),
        /* 0xD7 STA B,DIR */ StoreDIR(Reg.B),
        /* 0xD8 EOR B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, Xor())),
        /* 0xD9 ADC B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, Adc())),
        /* 0xDA ORA B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, Or())),
        /* 0xDB ADD B,DIR */ LoadDIR(Reg.T1).and(RegOp(Reg.B, Add())),
        /* 0xDC           */ UNDOCUMENTED,
        /* 0xDD           */ UNDOCUMENTED,
        /* 0xDE LDX X,DIR */ LoadDIR16(Reg.XHi, Reg.XLo),
        /* 0xDF STX X,DIR */ StoreDIR16(Reg.XHi, Reg.XLo),

        /* 0xE0 SUB B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, Sub())),
        /* 0xE1 CMP B,IND */ LoadIND(Reg.T1).and(TestOp(Reg.B, Sub())),
        /* 0xE2 SBC B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, Sbc())),
        /* 0xE3           */ UNDOCUMENTED,
        /* 0xE4 AND B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, And())),
        /* 0xE5 BIT B,IND */ LoadIND(Reg.T1).and(TestOp(Reg.B, And())),
        /* 0xE6 LDA B,IND */ LoadIND(Reg.B),
        /* 0xE7 STA B,IND */ StoreIND(Reg.B),
        /* 0xE8 EOR B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, Xor())),
        /* 0xE9 ADC B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, Adc())),
        /* 0xEA ORA B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, Or())),
        /* 0xEB ADD B,IND */ LoadIND(Reg.T1).and(RegOp(Reg.B, Add())),
        /* 0xEC           */ UNDOCUMENTED,
        /* 0xED           */ UNDOCUMENTED,
        /* 0xEE LDX X,IND */ LoadIND16(Reg.XHi, Reg.XLo),
        /* 0xEF STX X,IND */ StoreIND16(Reg.XHi, Reg.XLo),

        /* 0xF0 SUB B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, Sub())),
        /* 0xF1 CMP B,EXT */ LoadEXT(Reg.T1).and(TestOp(Reg.B, Sub())),
        /* 0xF2 SBC B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, Sbc())),
        /* 0xF3           */ UNDOCUMENTED,
        /* 0xF4 AND B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, And())),
        /* 0xF5 BIT B,EXT */ LoadEXT(Reg.T1).and(TestOp(Reg.B, And())),
        /* 0xF6 LDA B,EXT */ LoadEXT(Reg.B),
        /* 0xF7 STA B,EXT */ StoreEXT(Reg.B),
        /* 0xF8 EOR B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, Xor())),
        /* 0xF9 ADC B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, Adc())),
        /* 0xFA ORA B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, Or())),
        /* 0xFB ADD B,EXT */ LoadEXT(Reg.T1).and(RegOp(Reg.B, Add())),
        /* 0xFC           */ UNDOCUMENTED,
        /* 0xFD           */ UNDOCUMENTED,
        /* 0xFE LDX X,EXT */ LoadEXT16(Reg.XHi, Reg.XLo),
        /* 0xFF STX X,EXT */ StoreEXT16(Reg.XHi, Reg.XLo),
    };
}
