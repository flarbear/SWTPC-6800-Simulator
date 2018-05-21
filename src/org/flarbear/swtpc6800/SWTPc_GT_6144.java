/*
 * Copyright 2014, 2016, Jim Graham, Flarbear Widgets
 */

package org.flarbear.swtpc6800;

import java.awt.AlphaComposite;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

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
    public static final int BORDERH = 18;

    public static final int ROWS = 96;
    public static final int COLS = 64;
    public static final int PIXW = 8;
    public static final int PIXH = 2;

    public static final int SCRDOTCOLS = BORDERW + COLS * PIXW + BORDERW;
    public static final int SCRDOTROWS = BORDERH + ROWS * PIXH + BORDERH;

    static final Color BLANK_COLOR = new Color(0, 0, 0, 0);
    static final Color PHOSPHOR_COLOR = Color.GREEN;

    BufferedImage theImage;
    Frame theFrame;
    SWTPc_CT_64 theCT64;

    final boolean pixels[][] = new boolean[COLS][ROWS];

    int col;
    boolean write;
    boolean inverted;
    boolean blanked;
    boolean mixed;

    public SWTPc_GT_6144() {
        theImage = new BufferedImage(COLS * PIXW, ROWS * PIXH, BufferedImage.TYPE_INT_ARGB);
    }

    public void showAt(int x, int y) {
        int w = (int) (Math.ceil(7.2 * 96 * SWTPc_CT_64.DPI_SCALE));
        int h = (int) (Math.ceil(5.4 * 96 * SWTPc_CT_64.DPI_SCALE));
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

    public void showOn(SWTPc_CT_64 theCT64) {
        if (theFrame != null) {
            theFrame.dispose();
            theFrame = null;
        }
        this.theCT64 = theCT64;
    }

    @Override
    public int getWidth() {
        return (theCT64 != null)
               ? theCT64.getWidth()
               : super.getWidth();
    }

    @Override
    public int getHeight() {
        return (theCT64 != null)
               ? theCT64.getHeight()
               : super.getHeight();
    }

    @Override
    public void repaint(int x, int y, int w, int h) {
        if (theCT64 != null) {
            theCT64.repaint(x, y, w, h);
        } else {
            super.repaint(x, y, w, h);
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update(g);
    }

    @Override
    public void update(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (theCT64 == null) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
        }
        if (!blanked) {
            ((Graphics2D) g).scale((float) w / (float) SCRDOTCOLS,
                                   (float) h / (float) SCRDOTROWS);
            g.drawImage(theImage, BORDERW, BORDERH, null);
        }
    }

    void updateImage() {
        Graphics2D g2d = theImage.createGraphics();
        g2d.setComposite(AlphaComposite.Src);
        g2d.setColor(BLANK_COLOR);
        g2d.fillRect(0, 0, theImage.getWidth(), theImage.getHeight());
        g2d.setComposite(AlphaComposite.SrcOver);
        g2d.setColor(PHOSPHOR_COLOR);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (pixels[c][r] != inverted) {
                    g2d.fillRect(c * PIXW, r * PIXH, PIXW, PIXH);
                }
            }
        }
    }

    void updateImage(int row, int col) {
        Graphics2D g2d = theImage.createGraphics();
        if (pixels[col][row] == inverted) {
            g2d.setComposite(AlphaComposite.Src);
            g2d.setColor(BLANK_COLOR);
        } else {
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setColor(PHOSPHOR_COLOR);
        }
        g2d.fillRect(col * PIXW, row * PIXH, PIXW, PIXH);
    }

    void repaintCell(int row, int col) {
        updateImage(row, col);
        int x0 = BORDERW + col * PIXW;
        int y0 = BORDERH + row * PIXH;
        int x1 = x0 + PIXW;
        int y1 = y0 + PIXH;
        float scalex = (float) getWidth() / (float) SCRDOTCOLS;
        float scaley = (float) getHeight() / (float) SCRDOTROWS;
        int x = (int) Math.floor(x0 * scalex);
        int y = (int) Math.floor(y0 * scaley);
        int w = ((int) Math.ceil(x1 * scalex)) - x;
        int h = ((int) Math.ceil(y1 * scaley)) - y;
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
                updateImage();
                repaint();
            }
        }
    }
}
