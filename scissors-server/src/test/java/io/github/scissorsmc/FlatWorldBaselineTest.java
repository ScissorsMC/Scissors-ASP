package io.github.scissorsmc;

import com.infernalsuite.asp.api.world.SlimeChunk;
import com.infernalsuite.asp.api.world.SlimeFlatWorldProfile;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.level.FlatWorldGenerator;
import com.infernalsuite.asp.serialization.slime.ChunkPruner;
import java.util.List;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@VanillaFeature
class FlatWorldBaselineTest {
    private static final SlimeFlatWorldProfile PROFILE = new SlimeFlatWorldProfile(1, -64, -48, -64,
            -8, -8, 8, 8, "minecraft:plains", List.of(
            new SlimeFlatWorldProfile.Layer("minecraft:bedrock", 1),
            new SlimeFlatWorldProfile.Layer("minecraft:dirt", 2),
            new SlimeFlatWorldProfile.Layer("minecraft:grass_block", 1)));

    @Test
    void pristineBoundedTerrainAndOutsideVoidMatchBaseline() {
        FlatWorldGenerator generator = generator();
        assertTrue(generator.matchesBaseline(chunk(0, 0, false, false)));
        assertTrue(generator.matchesBaseline(chunk(3, 3, false, false)));
    }

    @Test
    void excavatedChunksAndChangedBiomesDoNotMatchBaseline() {
        FlatWorldGenerator generator = generator();
        assertFalse(generator.matchesBaseline(chunk(0, 0, true, false)));
        assertFalse(generator.matchesBaseline(chunk(0, 0, false, true)));
    }

    @Test
    void aSingleBlockEditDoesNotMatchBaseline() {
        FlatWorldGenerator generator = generator();
        LevelChunk chunk = chunk(0, 0, false, false);
        chunk.getSections()[0].setBlockState(1, 3, 1, Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
        assertFalse(generator.matchesBaseline(chunk));
    }

    @Test
    void serializationPreservesSnapshotsRetainedByTheLiveOwner() {
        SlimeWorld world = world();
        // The serializer must not inspect or override a retained snapshot, even if it is all air.
        assertFalse(ChunkPruner.canBePruned(world, mock(SlimeChunk.class)));
    }

    @Test
    void generationFillsLayersAndHeightmapsInsideBounds() {
        FlatWorldGenerator generator = generator();
        LevelChunk chunk = chunk(0, 0, true, false);
        generator.generate(chunk);
        assertTrue(generator.matchesBaseline(chunk));
        assertEquals(Blocks.BEDROCK.defaultBlockState(), chunk.getSections()[0].getBlockState(0, 0, 0));
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), chunk.getSections()[0].getBlockState(7, 3, 7));
        assertEquals(Blocks.AIR.defaultBlockState(), chunk.getSections()[0].getBlockState(8, 3, 7));
        assertEquals(-60, chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(7, 7));
        assertEquals(-64, chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(8, 7));
        verify(chunk).setLightCorrect(false);
    }

    @Test
    void generationPreservesExactAirStates() {
        SlimeFlatWorldProfile airProfile = new SlimeFlatWorldProfile(1, -64, -48, -64,
                -8, -8, 8, 8, "minecraft:plains", List.of(new SlimeFlatWorldProfile.Layer("minecraft:cave_air", 1)));
        FlatWorldGenerator generator = generator(airProfile);
        LevelChunk chunk = chunk(0, 0, true, false);
        generator.generate(chunk);
        assertEquals(Blocks.CAVE_AIR.defaultBlockState(), chunk.getSections()[0].getBlockState(0, 0, 0));
        assertTrue(generator.matchesBaseline(chunk));
    }

    @Test
    void profileRoundTripsAndRejectsInvalidOrDuplicateInstallation() {
        assertEquals(PROFILE, SlimeFlatWorldProfile.fromCompound(PROFILE.toCompound()));
        assertThrows(IllegalArgumentException.class, () -> SlimeFlatWorldProfile.fromCompound(
                PROFILE.toCompound().putInt("version", 2)));
        assertThrows(IllegalArgumentException.class, () -> SlimeFlatWorldProfile.fromCompound(
                PROFILE.toCompound().putInt("maxX", PROFILE.minX())));
        assertThrows(IllegalArgumentException.class, () -> SlimeFlatWorldProfile.fromCompound(
                PROFILE.toCompound().putString("unknown", "value")));
        assertThrows(IllegalStateException.class, () -> PROFILE.install(world()));
    }

    private static SlimeWorld world() {
        SlimeWorld world = mock(SlimeWorld.class);
        when(world.getExtraData()).thenReturn(new ConcurrentHashMap<>());
        PROFILE.install(world);
        return world;
    }

    private static FlatWorldGenerator generator() {
        return generator(PROFILE);
    }

    private static FlatWorldGenerator generator(SlimeFlatWorldProfile profile) {
        SlimeWorld world = mock(SlimeWorld.class);
        when(world.getExtraData()).thenReturn(new ConcurrentHashMap<>());
        profile.install(world);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.registryAccess()).thenReturn(RegistryHelper.registryAccess().freeze());
        try (var servers = mockStatic(MinecraftServer.class)) {
            servers.when(MinecraftServer::getServer).thenReturn(server);
            return FlatWorldGenerator.fromWorld(world);
        }
    }

    private static LevelChunk chunk(int chunkX, int chunkZ, boolean excavated, boolean changedBiome) {
        PalettedContainerFactory factory = PalettedContainerFactory.create(RegistryHelper.registryAccess());
        var biome = RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(changedBiome ? Biomes.DESERT : Biomes.PLAINS);
        LevelChunkSection section = new LevelChunkSection(
                new PalettedContainer<>(Blocks.AIR.defaultBlockState(), factory.blockStatesStrategy(), null),
                new PalettedContainer<>(biome, factory.biomeStrategy(), null));
        if (!excavated) {
            BlockState[] layers = {Blocks.BEDROCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()};
            for (int y = 0; y < layers.length; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int worldX = chunkX * 16 + x;
                        int worldZ = chunkZ * 16 + z;
                        if (worldX >= -8 && worldX < 8 && worldZ >= -8 && worldZ < 8) {
                            section.setBlockState(x, y, z, layers[y], false);
                        }
                    }
                }
            }
        }
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(chunkX, chunkZ));
        when(chunk.getSections()).thenReturn(new LevelChunkSection[]{section});
        when(chunk.getMinY()).thenReturn(-64);
        when(chunk.getHeight()).thenReturn(16);
        when(chunk.getHighestSectionPosition()).thenReturn(-64);
        EnumMap<Heightmap.Types, Heightmap> heightmaps = new EnumMap<>(Heightmap.Types.class);
        when(chunk.getOrCreateHeightmapUnprimed(any())).thenAnswer(invocation ->
                heightmaps.computeIfAbsent(invocation.getArgument(0), type -> new Heightmap(chunk, type)));
        when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos position = invocation.getArgument(0);
            return section.getBlockState(position.getX() & 15, position.getY() + 64, position.getZ() & 15);
        });
        return chunk;
    }
}

