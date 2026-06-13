package com.hooked.manager;

import com.hooked.model.Debris;
import org.bukkit.Location;

import java.util.Collection;
import java.util.UUID;

public interface IDebrisManager {

    void addDebris(Debris debris);

    void removeDebris(UUID entityId);

    Debris getDebris(UUID entityId);

    Debris findNearestHookable(Location hookLocation, double radius);

    Collection<Debris> getAllDebris();

    int countNearby(Location playerLocation, int radius);

    Debris findDebrisByInteractionUUID(UUID interactionUUID);

    void clearAll();
}
