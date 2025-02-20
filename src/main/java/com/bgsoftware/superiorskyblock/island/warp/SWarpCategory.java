package com.bgsoftware.superiorskyblock.island.warp;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.island.warps.WarpCategory;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.database.bridge.IslandsDatabaseBridge;
import com.bgsoftware.superiorskyblock.core.itemstack.ItemBuilder;
import com.bgsoftware.superiorskyblock.core.logging.Debug;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.google.common.base.Preconditions;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class SWarpCategory implements WarpCategory {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    public static final ItemStack DEFAULT_WARP_ICON = new ItemBuilder(Material.BOOK)
            .withName("&6{0}").build();

    private final List<IslandWarp> islandWarps = new LinkedList<>();
    private final UUID islandUUID;
    private Island cachedIsland;

    private String name;
    private int slot;
    private ItemStack icon;

    public SWarpCategory(UUID islandUUID, String name, int slot, @Nullable ItemStack icon) {
        this.islandUUID = islandUUID;
        this.name = name;
        this.slot = slot;
        this.icon = icon == null ? DEFAULT_WARP_ICON.clone() : icon;
    }

    @Override
    public Island getIsland() {
        return cachedIsland == null ? (cachedIsland = plugin.getGrid().getIslandByUUID(islandUUID)) : cachedIsland;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        Preconditions.checkNotNull(name, "name parameter cannot be null.");

        Log.debug(Debug.SET_WARP_CATEGORY_NAME, "SWarpCategory", "setName", getOwnerName(), this.name, name);

        String oldName = this.name;
        this.name = name;

        for (IslandWarp islandWarp : islandWarps)
            IslandsDatabaseBridge.updateWarpCategory(getIsland(), islandWarp, oldName);

        IslandsDatabaseBridge.updateWarpCategoryName(getIsland(), this, oldName);
    }

    @Override
    public List<IslandWarp> getWarps() {
        return islandWarps;
    }

    @Override
    public int getSlot() {
        return slot;
    }

    @Override
    public void setSlot(int slot) {
        Log.debug(Debug.SET_WARP_CATEGORY_SLOT, "SWarpCategory", "setSlot", getOwnerName(), this.name, slot);

        this.slot = slot;

        IslandsDatabaseBridge.updateWarpCategorySlot(getIsland(), this);
    }

    @Override
    public ItemStack getRawIcon() {
        return icon.clone();
    }

    @Override
    public ItemStack getIcon(@Nullable SuperiorPlayer superiorPlayer) {
        ItemBuilder itemBuilder = new ItemBuilder(icon)
                .replaceAll("{0}", name);
        return superiorPlayer == null ? itemBuilder.build() : itemBuilder.build(superiorPlayer);
    }

    @Override
    public void setIcon(@Nullable ItemStack icon) {
        Log.debug(Debug.SET_WARP_CATEGORY_ICON, "SWarpCategory", "setSlot", getOwnerName(), this.name, icon);

        this.icon = icon == null ? DEFAULT_WARP_ICON.clone() : icon.clone();

        IslandsDatabaseBridge.updateWarpCategoryIcon(getIsland(), this);
    }

    private String getOwnerName() {
        SuperiorPlayer superiorPlayer = getIsland().getOwner();
        return superiorPlayer == null ? "None" : superiorPlayer.getName();
    }

}
