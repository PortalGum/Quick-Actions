package com.quickactions.actions.pack.preset;

import com.quickactions.actions.pack.preset.PackPreset.PresetPack;
import com.quickactions.integration.ResourcePackIntegration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PackSetup {

    public record ApplyResult(int enabled, List<String> missing) {
    }

    private PackSetup() {
    }

    // ---------- read state ----------

    /** right list entries (enabled packs) */
    private static List<TransferableSelectionList.PackEntry> selectedEntries(Screen screen) {
        for (TransferableSelectionList list : ResourcePackIntegration.lists(screen)) {
            List<TransferableSelectionList.PackEntry> entries = ResourcePackIntegration.packEntries(list);
            if (!entries.isEmpty() && ResourcePackIntegration.modelOf(entries.get(0)).isSelected()) {
                return entries;
            }
        }
        return List.of();
    }

    private static boolean toggleable(PackSelectionModel.Entry model) {
        return model.canUnselect() && !model.isFixedPosition();
    }

    public static List<PresetPack> captureCurrent(Screen screen) {
        List<PresetPack> result = new ArrayList<>();
        for (TransferableSelectionList.PackEntry entry : selectedEntries(screen)) {
            PackSelectionModel.Entry model = ResourcePackIntegration.modelOf(entry);
            if (toggleable(model)) {
                result.add(new PresetPack(entry.getPackId(), model.getTitle().getString()));
            }
        }
        return result;
    }

    /** icons of all packs the screen knows */
    public static Map<String, Identifier> icons(Screen screen) {
        Map<String, Identifier> icons = new HashMap<>();
        for (TransferableSelectionList.PackEntry entry : ResourcePackIntegration.allEntries(screen)) {
            icons.put(entry.getPackId(), ResourcePackIntegration.modelOf(entry).getIconTexture());
        }
        return icons;
    }

    // ---------- disable all ----------

    private static List<PackSelectionModel.Entry> disableable(Screen screen) {
        List<PackSelectionModel.Entry> result = new ArrayList<>();
        for (TransferableSelectionList.PackEntry entry : selectedEntries(screen)) {
            PackSelectionModel.Entry model = ResourcePackIntegration.modelOf(entry);
            if (toggleable(model)) result.add(model);
        }
        return result;
    }

    public static int countDisableable(Screen screen) {
        return disableable(screen).size();
    }

    public static int disableAll(Screen screen) {
        List<PackSelectionModel.Entry> targets = disableable(screen); // collect first, lists get rebuilt
        for (PackSelectionModel.Entry model : targets) {
            model.unselect();
        }
        return targets.size();
    }

    // ---------- moving ----------

    public static void moveToTop(PackSelectionModel.Entry model) {
        for (int i = 0; i < 1000 && model.canMoveUp(); i++) {
            model.moveUp();
        }
    }

    public static void moveToBottom(PackSelectionModel.Entry model) {
        for (int i = 0; i < 1000 && model.canMoveDown(); i++) {
            model.moveDown();
        }
    }

    // ---------- apply preset ----------

    public static ApplyResult apply(Screen screen, PackPreset preset) {
        disableAll(screen);

        Map<String, PackSelectionModel.Entry> available = new HashMap<>();
        Set<String> selectedNow = new HashSet<>();
        for (TransferableSelectionList.PackEntry entry : ResourcePackIntegration.allEntries(screen)) {
            PackSelectionModel.Entry model = ResourcePackIntegration.modelOf(entry);
            if (model.isSelected()) {
                selectedNow.add(entry.getPackId());
            } else {
                available.put(entry.getPackId(), model);
            }
        }

        List<String> desired = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (PresetPack pack : preset.packs()) {
            if (available.containsKey(pack.id()) || selectedNow.contains(pack.id())) {
                desired.add(pack.id());
            } else {
                missing.add(pack.title());
            }
        }

        for (int i = preset.packs().size() - 1; i >= 0; i--) {
            PackSelectionModel.Entry model = available.get(preset.packs().get(i).id());
            if (model != null && model.canSelect()) {
                model.select();
            }
        }

        Set<String> desiredSet = new HashSet<>(desired);
        if (!orderOf(screen, desiredSet).equals(desired)) {
            for (int i = desired.size() - 1; i >= 0; i--) {
                PackSelectionModel.Entry model = selectedModel(screen, desired.get(i));
                if (model != null) moveToTop(model);
            }
        }
        return new ApplyResult(desired.size(), missing);
    }

    private static List<String> orderOf(Screen screen, Set<String> ids) {
        List<String> order = new ArrayList<>();
        for (TransferableSelectionList.PackEntry entry : selectedEntries(screen)) {
            if (ids.contains(entry.getPackId())) order.add(entry.getPackId());
        }
        return order;
    }

    private static PackSelectionModel.Entry selectedModel(Screen screen, String id) {
        for (TransferableSelectionList.PackEntry entry : selectedEntries(screen)) {
            if (entry.getPackId().equals(id)) return ResourcePackIntegration.modelOf(entry);
        }
        return null;
    }
}