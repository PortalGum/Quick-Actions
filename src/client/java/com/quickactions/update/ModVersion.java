package com.quickactions.update;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ModVersion implements Comparable<ModVersion> {
    private final long[] numbers;
    private final String preRelease; // "" for normal release

    private ModVersion(long[] numbers, String preRelease) {
        this.numbers = numbers;
        this.preRelease = preRelease;
    }

    public static @Nullable ModVersion parse(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("v")) s = s.substring(1);
        int plus = s.indexOf('+');
        if (plus >= 0) s = s.substring(0, plus);

        String pre = "";
        int dash = s.indexOf('-');
        if (dash >= 0) {
            pre = s.substring(dash + 1);
            s = s.substring(0, dash);
        }
        if (s.isEmpty()) return null;

        String[] parts = s.split("\\.", -1);
        long[] numbers = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 9) return null;
            for (int c = 0; c < parts[i].length(); c++) {
                if (!Character.isDigit(parts[i].charAt(c))) return null;
            }
            numbers[i] = Long.parseLong(parts[i]);
        }
        return new ModVersion(numbers, pre);
    }

    public boolean isNewerThan(ModVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(ModVersion other) {
        int length = Math.max(numbers.length, other.numbers.length);
        for (int i = 0; i < length; i++) { // missing parts are zeros: 1.0 == 1.0.0
            long a = i < numbers.length ? numbers[i] : 0;
            long b = i < other.numbers.length ? other.numbers[i] : 0;
            if (a != b) return Long.compare(a, b);
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0;
        if (preRelease.isEmpty()) return 1;       // release is newer than pre-release
        if (other.preRelease.isEmpty()) return -1;
        return comparePre(preRelease, other.preRelease);
    }

    private static int comparePre(String a, String b) {
        String[] x = a.split("[.\\-]");
        String[] y = b.split("[.\\-]");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            boolean xn = x[i].chars().allMatch(Character::isDigit) && !x[i].isEmpty();
            boolean yn = y[i].chars().allMatch(Character::isDigit) && !y[i].isEmpty();
            int cmp;
            if (xn && yn) cmp = Long.compare(Long.parseLong(x[i]), Long.parseLong(y[i]));
            else if (xn) cmp = -1;
            else if (yn) cmp = 1;
            else cmp = x[i].compareTo(y[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(x.length, y.length);
    }
}
