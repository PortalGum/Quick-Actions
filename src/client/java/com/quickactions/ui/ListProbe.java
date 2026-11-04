package com.quickactions.ui;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

public final class ListProbe {
    private static final int VERTICAL_RANGE = 160;
    private static final int STRIDE_SCAN = 24;

    @FunctionalInterface
    public interface EntryLookup {
        @Nullable Object entryAt(double x, double y);
    }

    /** segment [start, end) on one axis */
    public record Span(int start, int end) {
        public int length() {
            return end - start;
        }
    }

    private ListProbe() {
    }

    public static Span verticalSpan(EntryLookup lookup, Object entry, int x, int y, int screenHeight) {
        IntPredicate inside = p -> lookup.entryAt(x, p) == entry;
        int top = edge(inside, y, Math.max(0, y - VERTICAL_RANGE));
        int bottom = edge(inside, y, Math.min(screenHeight, y + VERTICAL_RANGE));
        return new Span(top, bottom + 1);
    }

    /** horizontal bounds of entry at height y */
    public static Span horizontalSpan(EntryLookup lookup, Object entry, int x, int y, int screenWidth) {
        IntPredicate inside = p -> lookup.entryAt(p, y) == entry;
        int left = edge(inside, x, 0);
        int right = edge(inside, x, screenWidth);
        return new Span(left, right + 1);
    }

    public static boolean isValid(EntryLookup lookup, Object entry, int x, Span span) {
        return lookup.entryAt(x, span.start()) == entry
                && lookup.entryAt(x, span.end() - 1) == entry
                && lookup.entryAt(x, span.start() - 1) != entry
                && lookup.entryAt(x, span.end()) != entry;
    }

    public static int measureStride(EntryLookup lookup, Object entry, int x, Span vertical) {
        for (int y = vertical.end(); y < vertical.end() + STRIDE_SCAN; y++) {
            Object next = lookup.entryAt(x, y);
            if (next != null && next != entry) {
                return y - vertical.start();
            }
        }
        return vertical.length();
    }

    private static int edge(IntPredicate inside, int inPos, int limitPos) {
        if (inside.test(limitPos)) return limitPos;
        int in = inPos;
        int out = limitPos;
        while (Math.abs(out - in) > 1) {
            int mid = (in + out) / 2;
            if (inside.test(mid)) {
                in = mid;
            } else {
                out = mid;
            }
        }
        return in;
    }
}