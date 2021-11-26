package com.bgsoftware.superiorskyblock.nms.v1_18_R1;

import com.bgsoftware.common.reflection.ReflectField;
import com.bgsoftware.common.reflection.ReflectMethod;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.nms.v1_18_R1.world.BlockStatesMapper;
import com.bgsoftware.superiorskyblock.tag.ByteTag;
import com.bgsoftware.superiorskyblock.tag.CompoundTag;
import com.bgsoftware.superiorskyblock.tag.IntArrayTag;
import com.bgsoftware.superiorskyblock.tag.StringTag;
import com.bgsoftware.superiorskyblock.tag.Tag;
import com.bgsoftware.superiorskyblock.utils.threads.Executor;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.core.SectionPosition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.PlayerChunk;
import net.minecraft.server.level.PlayerChunkMap;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.block.BlockBed;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkConverter;
import net.minecraft.world.level.chunk.ChunkSection;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.HeightMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.bgsoftware.superiorskyblock.nms.v1_18_R1.NMSMappings.*;

public final class NMSUtils {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private static final ReflectField<Map<Long, PlayerChunk>> VISIBLE_CHUNKS = new ReflectField<>(
            PlayerChunkMap.class, Map.class, "visibleChunks", "l");
    private static final ReflectMethod<Void> SEND_PACKETS_TO_RELEVANT_PLAYERS = new ReflectMethod<>(
            PlayerChunk.class, "a", Packet.class, boolean.class);

    private NMSUtils() {

    }

    public static void runActionOnChunks(WorldServer worldServer, Collection<ChunkCoordIntPair> chunksCoords,
                                         boolean saveChunks, Runnable onFinish, Consumer<Chunk> chunkConsumer,
                                         BiConsumer<ChunkCoordIntPair, NBTTagCompound> unloadedChunkConsumer) {
        List<ChunkCoordIntPair> unloadedChunks = new ArrayList<>();
        List<Chunk> loadedChunks = new ArrayList<>();

        chunksCoords.forEach(chunkCoords -> {
            IChunkAccess chunkAccess;

//            try {
//                //chunkAccess = worldServer.getChunkIfLoadedImmediately(chunkCoords.b, chunkCoords.c);
//                // TODO: Paper
//            } catch (Throwable ex) {
//
//            }

            chunkAccess = worldServer.getChunkIfLoaded(chunkCoords.c, chunkCoords.d);

            if (chunkAccess instanceof Chunk) {
                loadedChunks.add((Chunk) chunkAccess);
            } else {
                unloadedChunks.add(chunkCoords);
            }
        });

        boolean hasUnloadedChunks = !unloadedChunks.isEmpty();

        if (!loadedChunks.isEmpty())
            runActionOnLoadedChunks(loadedChunks, chunkConsumer);

        if (hasUnloadedChunks) {
            runActionOnUnloadedChunks(worldServer, unloadedChunks, saveChunks, unloadedChunkConsumer, onFinish);
        } else if (onFinish != null) {
            onFinish.run();
        }
    }

    public static void runActionOnLoadedChunks(Collection<Chunk> chunks, Consumer<Chunk> chunkConsumer) {
        chunks.forEach(chunkConsumer);
    }

    public static void runActionOnUnloadedChunks(WorldServer worldServer,
                                                 Collection<ChunkCoordIntPair> chunks,
                                                 boolean saveChunks,
                                                 BiConsumer<ChunkCoordIntPair, NBTTagCompound> chunkConsumer,
                                                 Runnable onFinish) {
        PlayerChunkMap playerChunkMap = getChunkProvider(worldServer).a;

        Executor.createTask().runAsync(v -> {
            chunks.forEach(chunkCoords -> {
                try {
                    NBTTagCompound chunkCompound = read(playerChunkMap, chunkCoords);

                    if (chunkCompound == null) {
                        ProtoChunk protoChunk = createProtoChunk(chunkCoords, worldServer);
                        chunkCompound = saveChunk(worldServer, protoChunk);
                    } else {
                        chunkCompound = getChunkData(playerChunkMap, worldServer.getTypeKey(),
                                Suppliers.ofInstance(getWorldPersistentData(worldServer)), chunkCompound, chunkCoords, worldServer);
                    }

                    if (hasKeyOfType(chunkCompound, "Level", 10)) {
                        chunkConsumer.accept(chunkCoords, getCompound(chunkCompound, "Level"));
                        if (saveChunks)
                            playerChunkMap.a(chunkCoords, chunkCompound);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SuperiorSkyblockPlugin.debug(ex);
                }
            });
        }).runSync(v -> {
            if (onFinish != null)
                onFinish.run();
        });
    }

    public static ProtoChunk createProtoChunk(ChunkCoordIntPair chunkCoords, WorldServer worldServer) {
        return new ProtoChunk(chunkCoords,
                ChunkConverter.a,
                worldServer,
                getCustomRegistry(worldServer).d(IRegistry.aR),
                null);
    }

    public static void sendPacketToRelevantPlayers(WorldServer worldServer, int chunkX, int chunkZ, Packet<?> packet) {
        PlayerChunkMap playerChunkMap = getChunkProvider(worldServer).a;
        ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(chunkX, chunkZ);
        PlayerChunk playerChunk;

        try {
            playerChunk = getVisibleChunk(playerChunkMap, pair(chunkCoordIntPair));
        } catch (Throwable ex) {
            playerChunk = VISIBLE_CHUNKS.get(playerChunkMap).get(pair(chunkCoordIntPair));
        }

        if (playerChunk != null) {
            SEND_PACKETS_TO_RELEVANT_PLAYERS.invoke(playerChunk, packet, false);
        }
    }

    public static void setBlock(net.minecraft.world.level.chunk.Chunk chunk, BlockPosition blockPosition,
                                int combinedId, CompoundTag statesTag, CompoundTag tileEntity) {
        IBlockData blockData = getByCombinedId(combinedId);

        if (statesTag != null) {
            for (Map.Entry<String, Tag<?>> entry : statesTag.getValue().entrySet()) {
                try {
                    // noinspection rawtypes
                    IBlockState blockState = BlockStatesMapper.getBlockState(entry.getKey());
                    if (blockState != null) {
                        if (entry.getValue() instanceof ByteTag) {
                            // noinspection unchecked
                            blockData = set(blockData, blockState, ((ByteTag) entry.getValue()).getValue() == 1);
                        } else if (entry.getValue() instanceof IntArrayTag) {
                            int[] data = ((IntArrayTag) entry.getValue()).getValue();
                            // noinspection unchecked
                            blockData = set(blockData, blockState, data[0]);
                        } else if (entry.getValue() instanceof StringTag) {
                            String data = ((StringTag) entry.getValue()).getValue();
                            // noinspection unchecked
                            blockData = set(blockData, blockState, Enum.valueOf(getType(blockState), data));
                        }
                    }
                } catch (Exception error) {
                    SuperiorSkyblockPlugin.debug(error);
                }
            }
        }

        if ((isLiquid(getMaterial(blockData)) && plugin.getSettings().isLiquidUpdate()) ||
                getBlock(blockData) instanceof BlockBed) {
            setTypeAndData(getWorld(chunk), blockPosition, blockData, 3);
            return;
        }

        if (plugin.getSettings().isLightsUpdate()) {
            setType(chunk, blockPosition, blockData, true, true);
        } else {
            int indexY = getSectionIndex(chunk, getY(blockPosition));

            ChunkSection chunkSection = getSections(chunk)[indexY];

            if (chunkSection == null) {
                int yOffset = SectionPosition.a(getY(blockPosition));
                chunkSection = new ChunkSection(yOffset, chunk.biomeRegistry);
            }

            int blockX = getX(blockPosition) & 15;
            int blockY = getY(blockPosition);
            int blockZ = getZ(blockPosition) & 15;

            setType(chunkSection, blockX, blockY & 15, blockZ, blockData, false);

            chunk.g.get(HeightMap.Type.e).a(blockX, blockY, blockZ, blockData);
            chunk.g.get(HeightMap.Type.f).a(blockX, blockY, blockZ, blockData);
            chunk.g.get(HeightMap.Type.d).a(blockX, blockY, blockZ, blockData);
            chunk.g.get(HeightMap.Type.b).a(blockX, blockY, blockZ, blockData);

            markDirty(chunk);
            setNeedsSaving(chunk, true);
        }

        if (tileEntity != null) {
            NBTTagCompound tileEntityCompound = (NBTTagCompound) tileEntity.toNBT();
            if (tileEntityCompound != null) {
                setInt(tileEntityCompound, "x", getX(blockPosition));
                setInt(tileEntityCompound, "y", getY(blockPosition));
                setInt(tileEntityCompound, "z", getZ(blockPosition));
                TileEntity worldTileEntity = getTileEntity(getWorld(chunk), blockPosition);
                if (worldTileEntity != null)
                    load(worldTileEntity, tileEntityCompound);
            }
        }
    }

}
