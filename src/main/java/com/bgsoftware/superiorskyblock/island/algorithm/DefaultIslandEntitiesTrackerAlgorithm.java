package com.bgsoftware.superiorskyblock.island.algorithm;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.algorithms.IslandEntitiesTrackerAlgorithm;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.key.KeyMap;
import com.bgsoftware.superiorskyblock.core.key.KeyMapImpl;
import com.bgsoftware.superiorskyblock.core.logging.Debug;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.bgsoftware.superiorskyblock.world.BukkitEntities;
import com.google.common.base.Preconditions;
import org.bukkit.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DefaultIslandEntitiesTrackerAlgorithm implements IslandEntitiesTrackerAlgorithm {

    private static final long CALCULATE_DELAY = TimeUnit.MINUTES.toMillis(5);

    private final KeyMap<Integer> entityCounts = KeyMapImpl.createConcurrentHashMap();

    private final Island island;

    private volatile boolean beingRecalculated = false;
    private volatile long lastCalculateTime = 0L;

    public DefaultIslandEntitiesTrackerAlgorithm(Island island) {
        this.island = island;
    }

    @Override
    public boolean trackEntity(Key key, int amount) {
        Preconditions.checkNotNull(key, "key parameter cannot be null.");

        Log.debug(Debug.ENTITY_SPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "trackEntity",
                island.getOwner().getName(), key, amount);

        if (amount <= 0) {
            Log.debugResult(Debug.ENTITY_SPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "trackEntity",
                    "Return", "Negative Amount");
            return false;
        }

        if (!canTrackEntity(key)) {
            Log.debugResult(Debug.ENTITY_SPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "trackEntity",
                    "Return", "Cannot Track Entity");
            return false;
        }

        int currentAmount = entityCounts.getOrDefault(key, 0);
        entityCounts.put(key, currentAmount + amount);

        Log.debugResult(Debug.ENTITY_SPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "trackEntity",
                "Return", "Success");

        return true;
    }

    @Override
    public boolean untrackEntity(Key key, int amount) {
        Preconditions.checkNotNull(key, "key parameter cannot be null.");

        Log.debug(Debug.ENTITY_DESPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "untrackEntity",
                island.getOwner().getName(), key, amount);

        if (amount <= 0) {
            Log.debugResult(Debug.ENTITY_DESPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "untrackEntity",
                    "Return", "Negative Amount");
            return false;
        }

        if (!canTrackEntity(key)) {
            Log.debugResult(Debug.ENTITY_DESPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "untrackEntity",
                    "Return", "Cannot Untrack Entity");
            return false;
        }

        int currentAmount = entityCounts.getOrDefault(key, -1);

        if (currentAmount != -1) {
            if (currentAmount > amount) {
                entityCounts.put(key, currentAmount - amount);
            } else {
                entityCounts.remove(key);
            }
        }

        Log.debugResult(Debug.ENTITY_DESPAWN, "DefaultIslandEntitiesTrackerAlgorithm", "untrackEntity",
                "Return", "Success");

        return true;
    }

    @Override
    public int getEntityCount(Key key) {
        return entityCounts.getOrDefault(key, 0);
    }

    @Override
    public Map<Key, Integer> getEntitiesCounts() {
        return Collections.unmodifiableMap(entityCounts);
    }

    @Override
    public void clearEntityCounts() {
        this.entityCounts.clear();
    }

    @Override
    public void recalculateEntityCounts() {
        if (beingRecalculated)
            return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastCalculateTime <= CALCULATE_DELAY)
            return;

        this.beingRecalculated = true;

        try {
            this.lastCalculateTime = currentTime;

            clearEntityCounts();

            KeyMap<Integer> recalculatedEntityCounts = KeyMapImpl.createConcurrentHashMap();

            island.getLoadedChunks(true, true).forEach(chunk -> {
                for (Entity entity : chunk.getEntities()) {
                    if (BukkitEntities.canBypassEntityLimit(entity))
                        continue;

                    Key key = BukkitEntities.getLimitEntityType(entity);

                    if (!canTrackEntity(key))
                        continue;

                    int currentEntityAmount = recalculatedEntityCounts.getOrDefault(key, 0);
                    recalculatedEntityCounts.put(key, currentEntityAmount + 1);
                }
            });

            if (!this.entityCounts.isEmpty()) {
                for (Map.Entry<Key, Integer> entry : this.entityCounts.entrySet()) {
                    Integer currentAmount = recalculatedEntityCounts.remove(entry.getKey());
                    if (currentAmount != null)
                        entry.setValue(entry.getValue() + currentAmount);
                }
            }

            if (!recalculatedEntityCounts.isEmpty()) {
                this.entityCounts.putAll(recalculatedEntityCounts);
            }
        } finally {
            beingRecalculated = false;
        }
    }

    private boolean canTrackEntity(Key key) {
        return island.getEntityLimit(key) != -1;
    }

}
