package com.hooked.model;

import org.bukkit.Location;

public record ChunkKey(int x, int z) {

    public static ChunkKey fromLocation(final Location loc) {
        return new ChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }
}
