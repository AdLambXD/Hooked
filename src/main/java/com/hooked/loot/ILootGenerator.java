package com.hooked.loot;

import com.hooked.model.DebrisType;
import org.bukkit.entity.Player;

public interface ILootGenerator {

    void giveLoot(Player player, DebrisType type);
}
