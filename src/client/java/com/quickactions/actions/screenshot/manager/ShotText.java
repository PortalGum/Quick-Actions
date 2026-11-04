package com.quickactions.actions.screenshot.manager;

import com.quickactions.actions.screenshot.ScreenshotInfo;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** date/time strings shared by gallery, detail panel and viewer */
final class ShotText {
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ShotText() {
    }

    static Component dayLabel(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) return Component.translatable("quickactions.shots.today");
        if (day.equals(today.minusDays(1))) return Component.translatable("quickactions.shots.yesterday");
        return fullDate(day);
    }

    static Component fullDate(LocalDate day) {
        return Component.translatable("quickactions.shots.date",
                day.getDayOfMonth(),
                Component.translatable("quickactions.shots.month." + day.getMonthValue()),
                day.getYear());
    }

    /** "12 May 2026, 14:03:22" line under the name */
    static Component dateLine(ScreenshotInfo info) {
        return Component.translatable("quickactions.shots.detail.date",
                fullDate(info.time().toLocalDate()), info.time().format(TIME_FORMAT));
    }
}
