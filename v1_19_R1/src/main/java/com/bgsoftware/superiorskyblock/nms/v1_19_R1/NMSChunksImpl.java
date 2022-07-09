package com.bgsoftware.superiorskyblock.nms.v1_19_R1;

import com.bgsoftware.common.reflection.ReflectMethod;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.key.KeyMap;
import com.bgsoftware.superiorskyblock.core.CalculatedChunk;
import com.bgsoftware.superiorskyblock.core.ChunkPosition;
import com.bgsoftware.superiorskyblock.core.SchematicBlock;
import com.bgsoftware.superiorskyblock.core.SequentialListBuilder;
import com.bgsoftware.superiorskyblock.core.key.KeyImpl;
import com.bgsoftware.superiorskyblock.core.key.KeyMapImpl;
import com.bgsoftware.superiorskyblock.core.threads.BukkitExecutor;
import com.bgsoftware.superiorskyblock.nms.NMSChunks;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.chunks.CropsTickingTileEntity;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.core.BlockPosition;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.core.SectionPosition;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.nbt.NBTTagCompound;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.nbt.NBTTagList;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.server.level.WorldServer;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.server.network.PlayerConnection;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.tags.TagsBlock;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.entity.Entity;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.ChunkCoordIntPair;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.block.Block;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.block.state.BlockData;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.chunk.ChunkAccess;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.chunk.ChunkSection;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.lighting.LightEngine;
import com.bgsoftware.superiorskyblock.nms.v1_19_R1.mapping.net.minecraft.world.level.lighting.LightEngineLayerEventListener;
import com.bgsoftware.superiorskyblock.world.chunk.ChunksTracker;
import com.bgsoftware.superiorskyblock.world.generator.IslandsGenerator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.IRegistry;
import net.minecraft.core.Registry;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.PacketPlayOutUnloadChunk;
import net.minecraft.server.level.RegionLimitedWorldAccess;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BlockStepAbstract;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockPropertySlabType;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataPaletteBlock;
import net.minecraft.world.level.chunk.NibbleArray;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_19_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.v1_19_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({"ConstantConditions", "deprecation"})
public final class NMSChunksImpl implements NMSChunks {

    private static final ReflectMethod<Codec<DataPaletteBlock<?>>> CODE_RW_METHOD = new ReflectMethod<>(
            DataPaletteBlock.class, "a", Registry.class, Codec.class, DataPaletteBlock.d.class, Object.class);

    private final SuperiorSkyblockPlugin plugin;

    public NMSChunksImpl(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setBiome(List<ChunkPosition> chunkPositions, Biome biome, Collection<Player> playersToUpdate) {
        if (chunkPositions.isEmpty())
            return;

        List<ChunkCoordIntPair> chunksCoords = new SequentialListBuilder<ChunkCoordIntPair>()
                .build(chunkPositions, chunkPosition -> new ChunkCoordIntPair(chunkPosition.getX(), chunkPosition.getZ()));

        WorldServer worldServer = new WorldServer(((CraftWorld) chunkPositions.get(0).getWorld()).getHandle());
        IRegistry<BiomeBase> biomesRegistry = worldServer.getBiomeRegistry();
        Registry<Holder<BiomeBase>> biomesRegistryHolder = worldServer.getBiomeRegistryHolder();
        Holder<BiomeBase> biomeBase = CraftBlock.biomeToBiomeBase(biomesRegistry, biome);

        NMSUtils.runActionOnChunks(worldServer, chunksCoords, true, null, chunk -> {
            ChunkCoordIntPair chunkCoords = chunk.getPos();

            net.minecraft.world.level.chunk.ChunkSection[] chunkSections = chunk.getSections();
            for (int i = 0; i < chunkSections.length; ++i) {
                ChunkSection currentSection = new ChunkSection(chunkSections[i]);
                if (currentSection != null) {
                    DataPaletteBlock<IBlockData> dataPaletteBlock = currentSection.getBlocks();
                    chunkSections[i] = new net.minecraft.world.level.chunk.ChunkSection(
                            currentSection.getYPosition() >> 4, dataPaletteBlock,
                            new DataPaletteBlock<>(biomesRegistryHolder, biomeBase, DataPaletteBlock.d.e));
                }
            }

            chunk.setNeedsSaving(true);

            PacketPlayOutUnloadChunk unloadChunkPacket = new PacketPlayOutUnloadChunk(chunkCoords.getX(), chunkCoords.getZ());
            ClientboundLevelChunkWithLightPacket mapChunkPacket = new ClientboundLevelChunkWithLightPacket(
                    (Chunk) chunk.getHandle(), worldServer.getLightEngine().getHandle(), null, null, true);

            playersToUpdate.forEach(player -> {
                Entity playerEntity = new Entity(((CraftPlayer) player).getHandle());
                PlayerConnection playerConnection = playerEntity.getPlayerConnection();
                playerConnection.sendPacket(unloadChunkPacket);
                playerConnection.sendPacket(mapChunkPacket);
            });
        }, unloadedChunkCompound -> {
            Codec<PalettedContainerRO<Holder<BiomeBase>>> codec = DataPaletteBlock.b(biomesRegistryHolder,
                    biomesRegistry.q(), DataPaletteBlock.d.e, biomesRegistry.h(Biomes.b));
            DataResult<NBTBase> dataResult = codec.encodeStart(DynamicOpsNBT.a,
                    new DataPaletteBlock<>(biomesRegistryHolder, biomeBase, DataPaletteBlock.d.e));
            NBTBase biomesCompound = dataResult.getOrThrow(false, error -> {
            });

            NBTTagList sectionsList = unloadedChunkCompound.getSections();

            for (int i = 0; i < sectionsList.size(); ++i)
                sectionsList.getCompound(i).set("biomes", biomesCompound);
        });
    }

    @Override
    public void deleteChunks(Island island, List<ChunkPosition> chunkPositions, Runnable onFinish) {
        if (chunkPositions.isEmpty())
            return;

        List<ChunkCoordIntPair> chunksCoords = new SequentialListBuilder<ChunkCoordIntPair>()
                .build(chunkPositions, chunkPosition -> new ChunkCoordIntPair(chunkPosition.getX(), chunkPosition.getZ()));

        chunkPositions.forEach(chunkPosition -> ChunksTracker.markEmpty(island, chunkPosition, false));

        WorldServer worldServer = new WorldServer(((CraftWorld) chunkPositions.get(0).getWorld()).getHandle());

        NMSUtils.runActionOnChunks(worldServer, chunksCoords, true, onFinish, chunk -> {
            IRegistry<BiomeBase> biomesRegistry = worldServer.getBiomeRegistry();

            net.minecraft.world.level.chunk.ChunkSection[] chunkSections = chunk.getSections();
            for (int i = 0; i < chunkSections.length; ++i) {
                chunkSections[i] = new net.minecraft.world.level.chunk.ChunkSection(
                        worldServer.getSectionYFromSectionIndex(i), biomesRegistry);
            }

            removeEntities(chunk);

            chunk.getTileEntities().keySet().forEach(worldServer::removeTileEntity);
            chunk.getTilePositions().clear();

            removeBlocks(chunk);
        }, unloadedChunkCompound -> {
            Codec<PalettedContainerRO<IBlockData>> blocksCodec = DataPaletteBlock.b(Block.CODEC, IBlockData.b,
                    DataPaletteBlock.d.d, Block.AIR.getBlockData().getHandle());

            NBTTagList tileEntities = new NBTTagList();

            unloadedChunkCompound.setEntities(new NBTTagList());
            unloadedChunkCompound.setBlockEntities(tileEntities);

            if (worldServer.getBukkitGenerator() instanceof IslandsGenerator) {
                DataResult<NBTBase> dataResult = blocksCodec.encodeStart(DynamicOpsNBT.a,
                        new DataPaletteBlock<>(Block.CODEC, Block.AIR.getBlockData().getHandle(), DataPaletteBlock.d.d));
                NBTBase blockStatesCompound = dataResult.getOrThrow(false, error -> {
                });

                NBTTagList sectionsList = unloadedChunkCompound.getSections();
                for (int i = 0; i < sectionsList.size(); ++i) {
                    NBTTagCompound sectionCompound = sectionsList.getCompound(i);
                    sectionCompound.set("block_states", blockStatesCompound);
                }
            } else {
                ChunkAccess protoChunk = NMSUtils.createProtoChunk(unloadedChunkCompound.getChunkCoords(), worldServer);

                try {
                    CustomChunkGenerator customChunkGenerator = new CustomChunkGenerator(worldServer.getHandle(),
                            worldServer.getChunkProvider().getGenerator(), worldServer.getBukkitGenerator());
                    customChunkGenerator.a(null, null, protoChunk.getHandle());
                } catch (Exception ignored) {
                }

                IRegistry<BiomeBase> biomesRegistry = worldServer.getBiomeRegistry();
                Registry<Holder<BiomeBase>> biomesRegistryHolder = worldServer.getBiomeRegistryHolder();
                Codec<PalettedContainerRO<Holder<BiomeBase>>> biomesCodec = DataPaletteBlock.b(biomesRegistryHolder,
                        biomesRegistry.q(), DataPaletteBlock.d.e, biomesRegistry.h(Biomes.b));

                LightEngine lightEngine = worldServer.getLightEngine();
                net.minecraft.world.level.chunk.ChunkSection[] chunkSections = protoChunk.getSections();

                NBTTagList sectionsList = new NBTTagList();

                // Save blocks
                for (int i = lightEngine.getMinSection(); i < lightEngine.getMaxSection(); ++i) {
                    int chunkSectionIndex = worldServer.getSectionIndex(i);

                    NBTTagCompound sectionCompound = new NBTTagCompound();

                    if (chunkSectionIndex >= 0 && chunkSectionIndex < chunkSections.length) {
                        ChunkSection chunkSection = new ChunkSection(chunkSections[chunkSectionIndex]);

                        {
                            DataResult<NBTBase> dataResult = blocksCodec.encodeStart(DynamicOpsNBT.a, chunkSection.getBlocks());
                            sectionCompound.set("block_states", dataResult.getOrThrow(false, error -> {
                            }));
                        }

                        {
                            DataResult<NBTBase> dataResult = biomesCodec.encodeStart(DynamicOpsNBT.a, chunkSection.getBiomes());
                            sectionCompound.set("biomes", dataResult.getOrThrow(false, error -> {
                            }));
                        }
                    }

                    if (!sectionCompound.isEmpty()) {
                        sectionCompound.setByte("Y", (byte) i);
                        sectionsList.add(sectionCompound.getHandle());
                    }
                }

                for (BlockPosition tilePosition : protoChunk.getTileEntities().keySet()) {
                    NBTTagCompound tileCompound = protoChunk.getTileEntityNBT(tilePosition);
                    if (tileCompound != null)
                        tileEntities.add(tileCompound.getHandle());
                }

                unloadedChunkCompound.setSections(sectionsList);
            }
        });
    }

    @Override
    public CompletableFuture<List<CalculatedChunk>> calculateChunks(List<ChunkPosition> chunkPositions) {
        CompletableFuture<List<CalculatedChunk>> completableFuture = new CompletableFuture<>();
        List<CalculatedChunk> allCalculatedChunks = new LinkedList<>();

        List<ChunkCoordIntPair> chunksCoords = new SequentialListBuilder<ChunkCoordIntPair>()
                .build(chunkPositions, chunkPosition -> new ChunkCoordIntPair(chunkPosition.getX(), chunkPosition.getZ()));
        WorldServer worldServer = new WorldServer(((CraftWorld) chunkPositions.get(0).getWorld()).getHandle());

        NMSUtils.runActionOnChunks(worldServer, chunksCoords, false, () -> {
            completableFuture.complete(allCalculatedChunks);
        }, chunk -> {
            ChunkCoordIntPair chunkCoords = chunk.getPos();
            ChunkPosition chunkPosition = ChunkPosition.of(chunk.getWorld().getWorld(), chunkCoords.getX(), chunkCoords.getZ());
            allCalculatedChunks.add(calculateChunk(chunkPosition, chunk.getSections()));
        }, unloadedChunkCompound -> {
            IRegistry<BiomeBase> biomesRegistry = worldServer.getBiomeRegistry();
            Registry<Holder<BiomeBase>> biomesRegistryHolder = worldServer.getBiomeRegistryHolder();

            Codec<DataPaletteBlock<IBlockData>> blocksCodec = makeCodecRW(Block.CODEC, IBlockData.b,
                    DataPaletteBlock.d.d, Blocks.a.m());
            Codec<DataPaletteBlock<Holder<BiomeBase>>> biomesCodec = makeCodecRW(biomesRegistryHolder,
                    biomesRegistry.q(), DataPaletteBlock.d.e, biomesRegistry.h(Biomes.b));

            net.minecraft.world.level.chunk.ChunkSection[] chunkSections =
                    new net.minecraft.world.level.chunk.ChunkSection[worldServer.getSectionsAmount()];

            NBTTagList sectionsList = unloadedChunkCompound.getSections();
            for (int i = 0; i < sectionsList.size(); ++i) {
                NBTTagCompound sectionCompound = sectionsList.getCompound(i);
                byte yPosition = sectionCompound.getByte("Y");
                int sectionIndex = worldServer.getSectionIndexFromSectionY(yPosition);

                if (sectionIndex >= 0 && sectionIndex < chunkSections.length) {
                    DataPaletteBlock<IBlockData> blocksDataPalette;
                    if (sectionCompound.hasKeyOfType("block_states", 10)) {
                        DataResult<DataPaletteBlock<IBlockData>> dataResult = blocksCodec.parse(DynamicOpsNBT.a,
                                sectionCompound.getCompound("block_states").getHandle()).promotePartial((sx) -> {
                        });
                        blocksDataPalette = dataResult.getOrThrow(false, error -> {
                        });
                    } else {
                        blocksDataPalette = new DataPaletteBlock<>(Block.CODEC, Blocks.a.m(), DataPaletteBlock.d.d);
                    }

                    DataPaletteBlock<Holder<BiomeBase>> biomesDataPalette;
                    if (sectionCompound.hasKeyOfType("biomes", 10)) {
                        DataResult<DataPaletteBlock<Holder<BiomeBase>>> dataResult = biomesCodec.parse(DynamicOpsNBT.a,
                                sectionCompound.getCompound("biomes").getHandle()).promotePartial((sx) -> {
                        });
                        biomesDataPalette = dataResult.getOrThrow(false, error -> {
                        });
                    } else {
                        biomesDataPalette = new DataPaletteBlock<>(biomesRegistryHolder, biomesRegistry.h(Biomes.b),
                                DataPaletteBlock.d.e);
                    }

                    chunkSections[sectionIndex] = new net.minecraft.world.level.chunk.ChunkSection(
                            yPosition, blocksDataPalette, biomesDataPalette);
                }

            }

            ChunkCoordIntPair chunkCoords = unloadedChunkCompound.getChunkCoords();
            ChunkPosition chunkPosition = ChunkPosition.of(worldServer.getWorld(), chunkCoords.getX(), chunkCoords.getZ());
            allCalculatedChunks.add(calculateChunk(chunkPosition, chunkSections));
        });

        return completableFuture;
    }

    @Override
    public void injectChunkSections(org.bukkit.Chunk chunk) {
        // No implementation
    }

    @Override
    public boolean isChunkEmpty(org.bukkit.Chunk bukkitChunk) {
        ChunkAccess chunk = new ChunkAccess(((CraftChunk) bukkitChunk).getHandle());
        return Arrays.stream(chunk.getSections()).allMatch(chunkSection ->
                chunkSection == null || new ChunkSection(chunkSection).isEmpty());
    }

    @Override
    public void refreshLights(org.bukkit.Chunk bukkitChunk, List<SchematicBlock> blockDataList) {
        ChunkAccess chunk = new ChunkAccess(((CraftChunk) bukkitChunk).getHandle());
        WorldServer world = chunk.getWorld();

        // Update lights for the blocks.
        // We use a delayed task to avoid null nibbles
        BukkitExecutor.sync(() -> {
            boolean canSkyLight = bukkitChunk.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL;
            LightEngine lightEngine = world.getLightEngine();
            LightEngineLayerEventListener blocksLightLayer = lightEngine.getLayer(EnumSkyBlock.b);
            LightEngineLayerEventListener skyLightLayer = lightEngine.getLayer(EnumSkyBlock.a);

            if (plugin.getSettings().isLightsUpdate()) {
                for (SchematicBlock blockData : blockDataList) {
                    BlockPosition blockPosition = new BlockPosition(blockData.getX(), blockData.getY(), blockData.getZ());
                    if (blockData.getBlockLightLevel() > 0) {
                        blocksLightLayer.flagDirty(blockPosition, blockData.getBlockLightLevel());
                    }
                    if (canSkyLight && blockData.getSkyLightLevel() > 0) {
                        skyLightLayer.flagDirty(blockPosition, blockData.getSkyLightLevel());
                    }
                }
            } else if (canSkyLight) {
                int sectionsAmount = chunk.getSections().length;
                ChunkCoordIntPair chunkCoords = chunk.getPos();

                for (int i = 0; i < sectionsAmount; ++i) {
                    byte[] skyLightArray = new byte[2048];
                    for (int j = 0; j < skyLightArray.length; j += 2)
                        skyLightArray[j] = 15;
                    lightEngine.queueData(EnumSkyBlock.a, SectionPosition.getByIndex(chunkCoords, i),
                            new NibbleArray(skyLightArray), true);
                }
            }
        }, 10L);
    }

    @Override
    public org.bukkit.Chunk getChunkIfLoaded(ChunkPosition chunkPosition) {
        WorldServer worldServer = new WorldServer(((CraftWorld) chunkPosition.getWorld()).getHandle());
        ChunkAccess chunk = worldServer.getChunkProvider().getChunkAt(chunkPosition.getX(), chunkPosition.getZ(), false);
        return chunk == null ? null : chunk.getBukkitChunk();
    }

    @Override
    public void startTickingChunk(Island island, org.bukkit.Chunk chunk, boolean stop) {
        if (plugin.getSettings().getCropsInterval() <= 0)
            return;

        ChunkAccess chunkAccess = new ChunkAccess(((CraftChunk) chunk).getHandle());

        if (stop) {
            CropsTickingTileEntity cropsTickingTileEntity = CropsTickingTileEntity.remove(chunkAccess.getPos());
            if (cropsTickingTileEntity != null)
                cropsTickingTileEntity.remove();
        } else {
            CropsTickingTileEntity.create(island, chunkAccess);
        }
    }

    private static CalculatedChunk calculateChunk(ChunkPosition chunkPosition,
                                                  net.minecraft.world.level.chunk.ChunkSection[] chunkSections) {
        KeyMap<Integer> blockCounts = KeyMapImpl.createHashMap();
        Set<Location> spawnersLocations = new HashSet<>();

        for (net.minecraft.world.level.chunk.ChunkSection nmsSection : chunkSections) {
            ChunkSection chunkSection = ChunkSection.ofNullable(nmsSection);
            if (chunkSection != null) {
                for (BlockPosition blockPosition : BlockPosition.allBlocksBetween(0, 0, 0, 15, 15, 15)) {
                    BlockData blockData = chunkSection.getType(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
                    Block block = blockData.getBlock();

                    if (block.getHandle() != Blocks.a) {
                        Location location = new Location(chunkPosition.getWorld(),
                                (chunkPosition.getX() << 4) + blockPosition.getX(),
                                chunkSection.getYPosition() + blockPosition.getY(),
                                (chunkPosition.getZ() << 4) + blockPosition.getZ());

                        int blockAmount = 1;

                        if ((TagsBlock.isTagged(TagsBlock.SLABS, block) || TagsBlock.isTagged(TagsBlock.WOODEN_SLABS, block)) &&
                                blockData.get(BlockStepAbstract.a) == BlockPropertySlabType.c) {
                            blockAmount = 2;
                            blockData = blockData.set(BlockStepAbstract.a, BlockPropertySlabType.b);
                        }

                        Material type = CraftMagicNumbers.getMaterial(blockData.getBlock().getHandle());
                        Key blockKey = KeyImpl.of(type.name() + "", "0", location);
                        blockCounts.put(blockKey, blockCounts.getOrDefault(blockKey, 0) + blockAmount);
                        if (type == Material.SPAWNER) {
                            spawnersLocations.add(location);
                        }
                    }
                }
            }
        }

        return new CalculatedChunk(chunkPosition, blockCounts, spawnersLocations);
    }

    private static void removeEntities(ChunkAccess chunk) {
        ChunkCoordIntPair chunkCoords = chunk.getPos();
        WorldServer worldServer = chunk.getWorld();

        int minBuildHeight = worldServer.getWorld().getMinHeight();
        int maxBuildHeight = worldServer.getWorld().getMaxHeight();

        int chunkWorldCoordX = chunkCoords.getX() << 4;
        int chunkWorldCoordZ = chunkCoords.getZ() << 4;

        net.minecraft.world.phys.AxisAlignedBB chunkBounds = new net.minecraft.world.phys.AxisAlignedBB(
                chunkWorldCoordX, minBuildHeight, chunkWorldCoordZ,
                chunkWorldCoordX + 15, maxBuildHeight, chunkWorldCoordZ + 15);

        List<net.minecraft.world.entity.Entity> worldEntities = new LinkedList<>();
        worldServer.getEntities().get(chunkBounds, worldEntities::add);

        worldEntities.forEach(nmsEntity -> {
            Entity entity = new Entity(nmsEntity);
            if (!(entity.getHandle() instanceof EntityHuman))
                entity.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.b);
        });
    }

    private static void removeBlocks(ChunkAccess chunk) {
        WorldServer worldServer = chunk.getWorld();

        ChunkGenerator bukkitGenerator = worldServer.getWorld().getGenerator();

        if (bukkitGenerator != null && !(bukkitGenerator instanceof IslandsGenerator)) {
            CustomChunkGenerator chunkGenerator = new CustomChunkGenerator(worldServer.getHandle(),
                    worldServer.getChunkProvider().getGenerator(),
                    bukkitGenerator);

            RegionLimitedWorldAccess region = new RegionLimitedWorldAccess(worldServer.getHandle(),
                    Collections.singletonList(chunk.getHandle()), ChunkStatus.h, 0);

            chunkGenerator.a(region,
                    worldServer.getStructureManager().getStructureManager(region).getHandle(),
                    chunk.getHandle());
        }
    }

    private static <E> Codec makeCodecRW(Registry<E> idMap, Codec<E> codec, DataPaletteBlock.d strategy, E object) {
        if (CODE_RW_METHOD.isValid()) {
            return CODE_RW_METHOD.invoke(null, idMap, codec, strategy, object);
        } else {
            return DataPaletteBlock.codecRW(idMap, codec, strategy, object, null);
        }
    }

}
