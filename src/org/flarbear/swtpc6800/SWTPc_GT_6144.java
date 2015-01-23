/*
 * Copyright 2014, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SWTPc_GT_6144 extends Canvas implements PIADevice {
    public static final int FF_LOAD_BIT = (1 << 7);
    public static final int RW_DATA_BIT = (1 << 6);

    public static final int HORIZONTAL_BITS = 0x3f;
    public static final int VERTICAL_BITS   = 0x7f;
    
    public static final int INVERTED_SCREEN  = 0;
    public static final int NORMAL_SCREEN    = 1;
    public static final int DISABLE_CT1024   = 2;
    public static final int ENABLE_CT1024    = 3;
    public static final int ENABLE_GRAPHICS  = 4;
    public static final int BLANKED_GRAPHICS = 5;

    public static final int BORDERW = 10;
    public static final int BORDERH = 10;

    public static final int ROWS = 96;
    public static final int COLS = 64;
    public static final int PIXW = 512/64;
    public static final int PIXH = 192/96;

    Frame theFrame;

    final boolean pixels[][] = new boolean[COLS][ROWS];
    int cellw;
    int cellh;

    int col;
    boolean write;
    boolean inverted;
    boolean blanked;
    boolean mixed;

    public void showAt(int x, int y, boolean zoom) {
        cellw = PIXW;
        cellh = PIXH;
        if (zoom) {
            cellw *= 2;
            cellh *= 3;
        }
        int w = BORDERW + COLS * cellw + BORDERW;
        int h = BORDERH + ROWS * cellh + BORDERH;
        w = (int) (Math.ceil(w * SWTPc_CT_64.DpiScale));
        h = (int) (Math.ceil(h * SWTPc_CT_64.DpiScale));
        setPreferredSize(new Dimension(w, h));
        if (theFrame == null) {
            theFrame = new Frame("SWTPc GT-6144 Emulator");
            theFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
            theFrame.add(this, "Center");
        }
        theFrame.pack();
        theFrame.setLocation(x, y);
        theFrame.setVisible(true);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update(g);
    }

    @Override
    public void update(Graphics g) {
        ((Graphics2D) g).scale(SWTPc_CT_64.DpiScale, SWTPc_CT_64.DpiScale);
        if (blanked) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        Rectangle clip = g.getClipBounds();
        int c0 = clip.x - BORDERW;
        int c1 = c0 + clip.width;
        int r0 = clip.y - BORDERH;
        int r1 = r0 + clip.height;
        if (c0 < 0 || c1 > COLS * cellw ||
            r0 < 0 || r1 > ROWS * cellh)
        {
            // BORDER was affected, clear entire clip
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        c0 = Math.max(0,    c0               / cellw);
        c1 = Math.min(COLS, (c1 + cellw - 1) / cellw);
        r0 = Math.max(0,    r0               / cellh);
        r1 = Math.min(ROWS, (r1 + cellh - 1) / cellh);
        for (int r = r0; r < r1; r++) {
            for (int c = c0; c < c1; c++) {
                g.setColor(pixels[c][r] == inverted ? Color.BLACK : Color.GREEN);
                g.fillRect(BORDERW + c * cellw,
                           BORDERH + r * cellh,
                           cellw, cellh);
            }
        }
    }

    void repaintCell(int row, int col) {
        int x0 = BORDERW + col * cellw;
        int y0 = BORDERH + row * cellh;
        int x1 = x0 + cellw;
        int y1 = y0 + cellh;
        int x = (int) Math.floor(x0 * SWTPc_CT_64.DpiScale);
        int y = (int) Math.floor(y0 * SWTPc_CT_64.DpiScale);
        int w = ((int) Math.ceil(x1 * SWTPc_CT_64.DpiScale)) - x;
        int h = ((int) Math.ceil(y1 * SWTPc_CT_64.DpiScale)) - y;
        repaint(x, y, w, h);
    }

    @Override
    public void transition(byte data, boolean c1, boolean c2) {
        if (c2) return;  // Only react to LOW transitions
        if ((data & FF_LOAD_BIT) == 0) {
            col = (byte) (data & HORIZONTAL_BITS);
            write = ((data & RW_DATA_BIT) != 0);
        } else {
            int row = data & VERTICAL_BITS;
            if (row < 96) {
                if (pixels[col][row] == write) return;
                pixels[col][row] = write;
                if (!blanked) {
                    repaintCell(row, col);
                }
            } else {
                switch (row & 7) {
                    case INVERTED_SCREEN:
                        if (inverted) return;
                        inverted = true;
                        break;
                    case NORMAL_SCREEN:
                        if (!inverted) return;
                        inverted = false;
                        break;
                    case BLANKED_GRAPHICS:
                        if (blanked) return;
                        blanked = true;
                        break;
                    case ENABLE_GRAPHICS:
                        if (!blanked) return;
                        blanked = false;
                        break;
                    case ENABLE_CT1024:
                        if (mixed) return;
                        mixed = true;
                        break;
                    case DISABLE_CT1024:
                        if (!mixed) return;
                        mixed = false;
                        break;
                    default:
                        return;
                }
                repaint();
            }
        }
    }
}
