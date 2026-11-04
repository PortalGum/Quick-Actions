package com.quickactions.actions.screenshot.manager;

record Rect(int x, int y, int w, int h) {
    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
