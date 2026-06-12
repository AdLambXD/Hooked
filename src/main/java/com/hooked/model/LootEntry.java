package com.hooked.model;

import java.util.List;

public record LootEntry(String itemMaterial, String itemsAdderId, int min, int max, double chance) {

    public LootEntry {
        if (itemMaterial == null && itemsAdderId == null) {
            throw new IllegalArgumentException("LootEntry must have either itemMaterial or itemsAdderId");
        }
    }
}
