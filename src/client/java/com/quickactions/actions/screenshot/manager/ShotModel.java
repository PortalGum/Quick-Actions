package com.quickactions.actions.screenshot.manager;

import com.quickactions.QuickActionsClient;
import com.quickactions.actions.screenshot.ScreenshotInfo;
import com.quickactions.actions.screenshot.ScreenshotLibrary;
import com.quickactions.actions.screenshot.ScreenshotTextures;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * what the manager shows and what's picked: shot list, the single selection,
 * fullscreen index and the multi-select (check marks)
 * no drawing, no layout in here
 * state is package-private on purpose, all the manager parts read it directly
 */
final class ShotModel {
    List<ScreenshotInfo> shots = List.of();
    long totalBytes;
    boolean loading = true;
    boolean loadFailed;
    /** png sizes read from disk, for the detail panel */
    final Map<Path, int[]> sizeCache = new HashMap<>();

    int selected = -1;
    /** index of the shot open in the fullscreen viewer, -1 = closed */
    int viewer = -1;

    boolean selectMode;
    final Set<Path> checked = new LinkedHashSet<>();
    long checkedBytes;
    int anchor = -1;

    private final Runnable onSelectionChanged;

    /** @param onSelectionChanged called when the selection changed in a way the buttons have to follow */
    ShotModel(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    void selectionChanged() {
        onSelectionChanged.run();
    }

    int size() {
        return shots.size();
    }

    boolean viewerOpen() {
        return viewer >= 0;
    }

    @Nullable ScreenshotInfo selectedInfo() {
        return selected >= 0 && selected < shots.size() ? shots.get(selected) : null;
    }

    // ---------- loading ----------

    void beginLoading() {
        loading = true;
    }

    /** @param error null if the scan went fine */
    void applyLoaded(List<ScreenshotInfo> list, @Nullable Throwable error) {
        loading = false;
        if (error != null) {
            QuickActionsClient.LOGGER.error("Failed to read screenshots folder", error);
            loadFailed = true;
            shots = List.of();
        } else {
            shots = list;
        }
        totalBytes = 0;
        for (ScreenshotInfo info : shots) {
            totalBytes += info.sizeBytes();
        }
    }

    // ---------- multi-select ----------

    void setChecked(int shot, boolean on) {
        ScreenshotInfo info = shots.get(shot);
        if (on ? checked.add(info.file()) : checked.remove(info.file())) {
            checkedBytes += on ? info.sizeBytes() : -info.sizeBytes();
        }
    }

    void toggleCheck(int shot) {
        if (shot < 0 || shot >= shots.size()) return;
        boolean on = !checked.contains(shots.get(shot).file());
        setChecked(shot, on);
        anchor = shot;
        if (on) {
            selectMode = true;
        } else if (checked.isEmpty()) {
            selectMode = false;
        }
        selectionChanged();
    }

    void checkRange(int shot) {
        int from = anchor >= 0 && anchor < shots.size() ? anchor : shot;
        for (int i = Math.min(from, shot); i <= Math.max(from, shot); i++) {
            setChecked(i, true);
        }
        selectMode = true;
        anchor = shot;
        selectionChanged();
    }

    void toggleSelectAll() {
        if (shots.isEmpty()) return;
        boolean all = checked.size() == shots.size();
        for (int i = 0; i < shots.size(); i++) {
            setChecked(i, !all);
        }
        selectMode = true;
        selectionChanged();
    }

    void toggleSelectMode() {
        if (selectMode) {
            exitSelectMode();
        } else {
            selectMode = true;
            selectionChanged();
        }
    }

    void exitSelectMode() {
        checked.clear();
        checkedBytes = 0;
        anchor = -1;
        selectMode = false;
        selectionChanged();
    }

    void recomputeChecked() {
        checkedBytes = 0;
        for (ScreenshotInfo info : shots) {
            if (checked.contains(info.file())) checkedBytes += info.sizeBytes();
        }
    }

    List<ScreenshotInfo> checkedInfos() {
        List<ScreenshotInfo> list = new ArrayList<>(checked.size());
        for (ScreenshotInfo info : shots) {
            if (checked.contains(info.file())) list.add(info);
        }
        return list;
    }

    /** 0 = none of the day picked, 1 = some, 2 = all */
    int dayState(Cell header, int firstShot) {
        int picked = 0;
        for (int i = firstShot; i < firstShot + header.count() && i < shots.size(); i++) {
            if (checked.contains(shots.get(i).file())) picked++;
        }
        return picked == 0 ? 0 : picked >= header.count() ? 2 : 1;
    }

    void toggleDay(Cell header, int firstShot) {
        boolean select = dayState(header, firstShot) != 2;
        for (int i = firstShot; i < firstShot + header.count() && i < shots.size(); i++) {
            setChecked(i, select);
        }
        anchor = firstShot;
        if (select) {
            selectMode = true;
        } else if (checked.isEmpty()) {
            selectMode = false;
        }
        selectionChanged();
    }

    // ---------- list changes ----------

    int indexOfFile(@Nullable Path file) {
        if (file == null) return -1;
        for (int i = 0; i < shots.size(); i++) {
            if (shots.get(i).file().equals(file)) return i;
        }
        return -1;
    }

    /** data side of a delete, caller rebuilds layout and buttons after */
    void remove(Collection<ScreenshotInfo> gone) {
        Set<Path> goneFiles = new HashSet<>();
        for (ScreenshotInfo info : gone) {
            goneFiles.add(info.file());
        }

        int oldSelected = selected;
        int remainingBefore = 0;
        List<ScreenshotInfo> remaining = new ArrayList<>(shots.size());
        for (int i = 0; i < shots.size(); i++) {
            ScreenshotInfo info = shots.get(i);
            if (goneFiles.contains(info.file())) {
                ScreenshotTextures.forget(info);
                sizeCache.remove(info.file());
                totalBytes -= info.sizeBytes();
                checked.remove(info.file());
            } else {
                if (i < oldSelected) remainingBefore++;
                remaining.add(info);
            }
        }
        shots = remaining;

        if (shots.isEmpty()) {
            selected = -1;
            viewer = -1;
        } else {
            selected = oldSelected < 0 ? -1 : Math.min(remainingBefore, shots.size() - 1);
            if (viewer >= 0) viewer = selected;
        }
        anchor = -1;
        if (checked.isEmpty()) selectMode = false;
        recomputeChecked();
    }

    /**
     * data side of a rename, caller rebuilds layout and buttons after
     * @return false if the renamed file couldn't be read (caller reloads everything)
     */
    boolean replace(ScreenshotInfo old, Path newFile) {
        ScreenshotInfo fresh;
        try {
            ScreenshotInfo read = ScreenshotLibrary.read(newFile);
            fresh = read.vanillaName() ? read
                    : new ScreenshotInfo(newFile, read.name(), read.sizeBytes(), old.time(), false);
        } catch (IOException e) {
            QuickActionsClient.LOGGER.error("Failed to read renamed screenshot {}", newFile, e);
            return false;
        }

        Path selectedFile = selected >= 0 && selected < shots.size() ? shots.get(selected).file() : null;
        Path viewerFile = viewer >= 0 && viewer < shots.size() ? shots.get(viewer).file() : null;
        if (old.file().equals(selectedFile)) selectedFile = fresh.file();
        if (old.file().equals(viewerFile)) viewerFile = fresh.file();

        ScreenshotTextures.forget(old);
        sizeCache.remove(old.file());
        if (checked.remove(old.file())) checked.add(fresh.file());

        List<ScreenshotInfo> copy = new ArrayList<>(shots.size());
        for (ScreenshotInfo info : shots) {
            copy.add(info.file().equals(old.file()) ? fresh : info);
        }
        copy.sort(ScreenshotLibrary.NEWEST_FIRST);
        shots = copy;

        selected = indexOfFile(selectedFile);
        viewer = viewerFile == null ? -1 : indexOfFile(viewerFile);
        anchor = -1;
        return true;
    }
}
