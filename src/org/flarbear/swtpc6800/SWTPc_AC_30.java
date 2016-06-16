/*
 * Copyright 2014, 2016, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Container;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SWTPc_AC_30 extends Panel {
    private RS232Device theComputer;
    private RS232Device theTerminal;
    private RS232Device computerPort;
    private RS232Device terminalPort;

    private final ByteArrayOutputStream tape = new ByteArrayOutputStream();
    private boolean recording;
    private Button saveButton;

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
                if (recording) {
                    tape.write(data & 0xff);
                    saveButton.setEnabled(true);
                }
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
                if (recording) return;
                if (theTerminal != null) {
                    theTerminal.waitForCTS();
                }
            }
        };

        terminalPort = new RS232Device() {
            @Override
            public void sendTo(byte data) {
                if (recording) {
                    tape.write(data & 0xff);
                    saveButton.setEnabled(true);
                }
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

    public void saveTape(File f) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        os.write(tape.toByteArray());
        os.flush();
        os.close();
        tape.reset();
        saveButton.setEnabled(false);
    }

    public void readOn() {
        send(TAPE_FILES[theTapeList.getSelectedIndex()]);
    }

    public void readOff() {
        senderThread = null;
    }

    public void punchOn() {
        recording = true;
    }

    public void punchOff() {
        recording = false;
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

    private static final String TAPE_FILES[] = {
        "resources/TicTacToePatch.S19",
        "resources/TSCMBasicPlusPatch.S19",
        "resources/MITSBasicPatch.S19",
        "resources/TSCSPACEPatch.S19",
        "resources/SWTSPRAC.S19",
        "resources/SwtBarTst-1.S19",
        "resources/SwtStarTrekProg.S19",
    };

    private static void findLabels() {
        if (labels == null) {
            labels = new String[TAPE_FILES.length];
            for (int i = 0; i < TAPE_FILES.length; i++) {
                String label;
                InputStream is = null;
                try {
                    is = SWTPc_AC_30.class.getResourceAsStream(TAPE_FILES[i]);
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

    FileDialog saveDialog;
    public void powerOn() {
        findLabels();
        Font f = new Font(Font.DIALOG, Font.PLAIN, Math.round(10 * SWTPc_CT_64.DPI_SCALE));
        setFont(f);
        setLayout(new BorderLayout());
        Panel p = new Panel();
        p.add(new Label("Load Tape:"));
        theTapeList = new Choice();
        for (String label : labels) {
            theTapeList.add(label);
        }
        p.add(theTapeList);
        saveButton = new Button("Save tape");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (saveDialog == null) {
                    Container c = getParent();
                    while (!(c instanceof Frame)) {
                        c = c.getParent();
                    }
                    saveDialog = new FileDialog((Frame) c, "Save Tape to...", FileDialog.SAVE);
                    saveDialog.setModal(true);
                }
                saveDialog.setVisible(true);
                File f[] = saveDialog.getFiles();
                if (f.length > 0) {
                    try {
                        saveTape(f[0]);
                    } catch (IOException ioe) {
                        ioe.printStackTrace(System.out);
                    }
                }
                
            }
        });
        saveButton.setEnabled(false);
        p.add(saveButton);
        add(p, "North");
        theInfoPane = new TextArea(20, 40);
        theInfoPane.setEditable(false);
        add(theInfoPane, "Center");
    }
}
