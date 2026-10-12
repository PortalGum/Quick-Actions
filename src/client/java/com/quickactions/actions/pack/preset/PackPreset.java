package com.quickactions.actions.pack.preset;

import java.nio.file.Path;
import java.util.List;

public record PackPreset(Path file, String name, long savedMillis, List<PresetPack> packs) {

    public record PresetPack(String id, String title) {
    }

    public List<String> ids() {
        return packs.stream().map(PresetPack::id).toList();
    }
}