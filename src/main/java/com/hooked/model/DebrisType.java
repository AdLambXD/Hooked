package com.hooked.model;

import org.bukkit.Material;
import java.util.List;
import java.util.Map;

public final class DebrisType {

    private final String id;
    private final Material blockMaterial;
    private final String itemsAdderId;
    private final int weight;
    private final List<LootEntry> lootTable;
    private final Map<String, Double> envWeightMods;

    public DebrisType(final String id, final Material blockMaterial, final String itemsAdderId,
                      final int weight, final List<LootEntry> lootTable) {
        this(id, blockMaterial, itemsAdderId, weight, lootTable, Map.of());
    }

    public DebrisType(final String id, final Material blockMaterial, final String itemsAdderId,
                      final int weight, final List<LootEntry> lootTable,
                      final Map<String, Double> envWeightMods) {
        this.id = id;
        this.blockMaterial = blockMaterial;
        this.itemsAdderId = itemsAdderId;
        this.weight = weight;
        this.lootTable = List.copyOf(lootTable);
        this.envWeightMods = Map.copyOf(envWeightMods);
    }

    public String getId() { return id; }
    public Material getBlockMaterial() { return blockMaterial; }
    public String getItemsAdderId() { return itemsAdderId; }
    public int getWeight() { return weight; }
    public List<LootEntry> getLootTable() { return lootTable; }
    public Map<String, Double> getEnvWeightMods() { return envWeightMods; }
}
