package com.quickactions.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Icons {
    public static final int CAMERA_WIDTH = 14;
    public static final int CAMERA_HEIGHT = 11;
    public static final int CHEVRON_WIDTH = 7;
    public static final int CHEVRON_HEIGHT = 11;

    public static final String[] GLOBE = {
            "...#####...",
            ".##..#..##.",
            ".#...#...#.",
            "#....#....#",
            "#....#....#",
            "###########",
            "#....#....#",
            "#....#....#",
            ".#...#...#.",
            ".##..#..##.",
            "...#####..."
    };

    private Icons() {
    }

    public static void camera(GuiGraphicsExtractor g, int x, int y, int body, int cut, int lens) {
        g.fill(x + 4, y, x + 10, y + 2, body);        // viewfinder
        g.fill(x, y + 2, x + 14, y + 11, body);       // body
        g.fill(x + 5, y + 3, x + 9, y + 10, cut);     // lens: cutout with clipped corners
        g.fill(x + 4, y + 4, x + 10, y + 9, cut);
        g.fill(x + 6, y + 5, x + 8, y + 8, lens);     // glass
        g.fill(x + 11, y + 3, x + 13, y + 4, cut);    // flash
    }

    /** cross size x size, line 2px thick */
    public static void cross(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            g.fill(x + i, y + i, x + i + 2, y + i + 1, color);
            g.fill(x + size - 2 - i, y + i, x + size - i, y + i + 1, color);
        }
    }

    /** chevron < or > 7x11 */
    public static void chevron(GuiGraphicsExtractor g, int x, int y, boolean left, int color) {
        for (int i = 0; i < 6; i++) {
            int dx = left ? 5 - i : i;
            g.fill(x + dx, y + i, x + dx + 2, y + i + 1, color);
            g.fill(x + dx, y + 10 - i, x + dx + 2, y + 11 - i, color);
        }
    }

    public static void fillCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        double radius = r + 0.5;
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    public static void ringCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int thickness, int color) {
        double outer = r + 0.5;
        double inner = r - thickness + 0.5;
        for (int dy = -r; dy <= r; dy++) {
            int outerHalf = (int) Math.floor(Math.sqrt(outer * outer - dy * dy));
            double innerSquare = inner * inner - dy * dy;
            if (innerSquare <= 0) {
                g.fill(cx - outerHalf, cy + dy, cx + outerHalf + 1, cy + dy + 1, color);
            } else {
                int innerHalf = (int) Math.floor(Math.sqrt(innerSquare));
                g.fill(cx - outerHalf, cy + dy, cx - innerHalf, cy + dy + 1, color);
                g.fill(cx + innerHalf + 1, cy + dy, cx + outerHalf + 1, cy + dy + 1, color);
            }
        }
    }

    public static void checkMark(GuiGraphicsExtractor g, int cx, int cy, int color) {
        for (int i = 0; i < 3; i++) {
            g.fill(cx - 4 + i, cy + i, cx - 2 + i, cy + i + 1, color);
        }
        for (int i = 0; i < 6; i++) {
            g.fill(cx - 2 + i, cy + 2 - i, cx + i, cy + 3 - i, color);
        }
    }

    public static void expand(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int arm = Math.max(3, size / 3);
        g.fill(x, y, x + arm, y + 1, color);
        g.fill(x, y, x + 1, y + arm, color);
        g.fill(x + size - arm, y, x + size, y + 1, color);
        g.fill(x + size - 1, y, x + size, y + arm, color);
        g.fill(x, y + size - 1, x + arm, y + size, color);
        g.fill(x, y + size - arm, x + 1, y + size, color);
        g.fill(x + size - arm, y + size - 1, x + size, y + size, color);
        g.fill(x + size - 1, y + size - arm, x + size, y + size, color);
    }

    public static void bitmap(GuiGraphicsExtractor g, int x, int y, String[] rows, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int start = -1;
            for (int i = 0; i <= row.length(); i++) {
                boolean on = i < row.length() && row.charAt(i) == '#';
                if (on && start < 0) {
                    start = i;
                } else if (!on && start >= 0) {
                    g.fill(x + start, y + r, x + i, y + r + 1, color);
                    start = -1;
                }
            }
        }
    }

    public static int bitmapWidth(String[] rows) {
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        return width;
    }

    public static void gear(GuiGraphicsExtractor g, int cx, int cy, int r, float angle, int color) {
        final int teeth = 8;
        double outer = r + 0.5;
        double body = r * 0.72;
        double hole = r * 0.30;
        for (int dy = -r; dy <= r; dy++) {
            int runStart = Integer.MIN_VALUE;
            for (int dx = -r; dx <= r + 1; dx++) {
                boolean on = false;
                if (dx <= r) {
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d >= hole && d <= outer) {
                        if (d <= body) {
                            on = true;
                        } else {
                            // tooth narrows toward the edge
                            double reach = (d - body) / (outer - body);
                            double wave = Math.cos((Math.atan2(dy, dx) - angle) * teeth);
                            on = wave > -0.15 + 0.55 * reach;
                        }
                    }
                }
                if (on && runStart == Integer.MIN_VALUE) {
                    runStart = dx;
                } else if (!on && runStart != Integer.MIN_VALUE) {
                    g.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
    }
}
