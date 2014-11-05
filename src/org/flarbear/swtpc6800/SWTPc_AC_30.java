/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SWTPc_AC_30 extends Panel {
    private RS232Device theComputer;
    private RS232Device theTerminal;
    private RS232Device computerPort;
    private RS232Device terminalPort;

    public SWTPc_AC_30() {
        setupPorts();
    }

    public void connectToComputer(RS232Device comp) {
        computerPort.connectTo(comp);
        comp.connectTo(computerPort);
    }

    public void connectToTerminal(RS232Device term) {
        terminalPort.connectTo(term);
        term.connectTo(terminalPort);
    }

    private void setupPorts() {
        computerPort = new RS232Device() {
            @Override
            public void sendTo(byte data) {
                if (theTerminal != null) {
                    theTerminal.sendTo(data);
                }
            }

            @Override
            public void connectTo(RS232Device otherdevice) {
                theComputer = otherdevice;
            }

            @Override
            public void waitForCTS() {
                if (theTerminal != null) {
                    theTerminal.waitForCTS();
                }
            }
        };

        terminalPort = new RS232Device() {
            @Override
            public void sendTo(byte data) {
                if (theComputer != null) {
                    theComputer.sendTo(data);
                }
            }

            @Override
            public void connectTo(RS232Device otherdevice) {
                theTerminal = otherdevice;
            }

            @Override
            public void waitForCTS() {
                if (theComputer != null) {
                    theComputer.waitForCTS();
                }
            }
        };
    }

    public void readOn() {
        send(tapefiles[theTapeList.getSelectedIndex()]);
    }

    public void readOff() {
        senderThread = null;
    }

    public void punchOn() {
        // Prompt for filename, pipe computer output to file?
    }

    public void punchOff() {
        // Finalize and close file set up in punchOn() method?
    }

    private Thread senderThread;

    private void send(final String filename) {
        theInfoPane.setText(null);
        theInfoPane.setCaretPosition(0);
        senderThread = new Thread() {
            @Override
            public void run() {
                Thread me = Thread.currentThread();
                InputStream is = null;
                try {
                    is = getClass().getResourceAsStream(filename);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    boolean firstline = true;
                    String line;
                    while (senderThread == me && (line = br.readLine()) != null) {
                        if (line.startsWith("S1") || line.startsWith("S9")) {
                            sendLine(me, line);
                        } else {
                            if (!(firstline && line.startsWith("LABEL="))) {
                                theInfoPane.append(line);
                                theInfoPane.append("\n");
                                theInfoPane.setCaretPosition(0);
                            }
                        }
                        firstline = false;
                    }
                } catch (IOException e) {
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException e2) {
                    }
                }
            }

            public void sendLine(Thread me, String line) {
                for (int i = 0; i < line.length(); i++) {
                    if (senderThread != me) {
                        return;
                    }
                    theComputer.waitForCTS();
                    if (senderThread == me) {
                        theComputer.sendTo((byte) line.charAt(i));
                    }
                }
            }
        };
        senderThread.start();
    }

    private Choice theTapeList;
    private TextArea theInfoPane;

    private static String labels[];

    private static final String tapefiles[] = {
        "resources/TicTacToePatch.S19",
        "resources/TSCMBasicPlusPatch.S19",
        "resources/MITSBasicPatch.S19",
        "resources/TSCSPACEPatch.S19",
    };

    private static void findLabels() {
        if (labels == null) {
            labels = new String[tapefiles.length];
            for (int i = 0; i < tapefiles.length; i++) {
                String label;
                InputStream is = null;
                try {
                    is = SWTPc_AC_30.class.getResourceAsStream(tapefiles[i]);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    label = br.readLine();
                    if (label != null && label.startsWith("LABEL=")) {
                        label = label.substring(6);
                    } else {
                        label = "<Unlabeled>";
                    }
                } catch (IOException e) {
                    label = "<Bad tape>";
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException e2) {
                    }
                }
                labels[i] = label;
            }
        }
    }

    public void powerOn() {
        findLabels();
        setLayout(new BorderLayout());
        Panel p = new Panel();
        p.add(new Label("Load Tape:"));
        theTapeList = new Choice();
        for (String label : labels) {
            theTapeList.add(label);
        }
        p.add(theTapeList);
        add(p, "North");
        theInfoPane = new TextArea(20, 40);
        theInfoPane.setEditable(false);
        add(theInfoPane, "Center");
    }
}
