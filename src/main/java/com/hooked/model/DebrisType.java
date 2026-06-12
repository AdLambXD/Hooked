package com.hooked.model;

import org.bukkit.Material;
import java.util.List;

public final class DebrisType {

    private final String id;
    private final Material blockMaterial;
    private final String itemsAdderId;
    private final int weight;
    private final List<LootEntry> lootTable;

    public DebrisType(final String id, final Material blockMaterial, final String itemsAdderId,
                      final int weight, final List<LootEntry> lootTable) {
        this.id = id;
        this.blockMaterial = blockMaterial;
        this.itemsAdderId = itemsAdderId;
        this.weight = weight;
        this.lootTable = List.copyOf(lootTable);
    }

    public String getId() { return id; }
    public Material getBlockMaterial() { return blockMaterial; }
    public String getItemsAdderId() { return itemsAdderId; }
    public int getWeight() { return weight; }
    public List<LootEntry> getLootTable() { return lootTable; }
}
