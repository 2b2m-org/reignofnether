package com.solegendary.reignofnether.survival;

import net.minecraft.core.HolderLookup;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class SurvivalSaveData extends SavedData {

    public static final int CURRENT_VERSION = 1;

    public boolean isEnabled;
    public int waveNumber = 1;
    public int lastStartedWaveNumber;
    public WaveDifficulty difficulty = WaveDifficulty.BEGINNER;
    public long randomSeed = System.currentTimeMillis();
    public boolean legacyWavePointer;
    public final Map<BlockPos, WavePortal.SaveState> portalStates = new HashMap<>();

    private static SurvivalSaveData create() {
        return new SurvivalSaveData();
    }

    @Nonnull
    public static SurvivalSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return create();
        }
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(SurvivalSaveData::create, (tag, provider) -> SurvivalSaveData.load(tag)), "saved-survival-data");
    }

    public static SurvivalSaveData load(CompoundTag tag) {
        SurvivalSaveData data = create();
        data.isEnabled = tag.getBoolean("isEnabled");
        data.waveNumber = tag.getInt("waveNumber");
        boolean hasLastStartedWaveNumber = tag.contains("lastStartedWaveNumber");
        data.lastStartedWaveNumber = hasLastStartedWaveNumber
                ? tag.getInt("lastStartedWaveNumber")
                : data.isEnabled ? data.waveNumber : 0;
        data.legacyWavePointer = hasLastStartedWaveNumber && !tag.contains("waveStateVersion");
        data.difficulty = WaveDifficulty.valueOf(tag.getString("difficulty"));
        data.randomSeed = tag.getLong("randomSeed");
        if (tag.contains("wavePortals", Tag.TAG_LIST)) {
            ListTag portalTags = tag.getList("wavePortals", Tag.TAG_COMPOUND);
            for (Tag portalTag : portalTags) {
                CompoundTag portal = (CompoundTag) portalTag;
                BlockPos origin = new BlockPos(portal.getInt("x"), portal.getInt("y"), portal.getInt("z"));
                data.portalStates.put(origin, new WavePortal.SaveState(
                        portal.getInt("waveNumber"),
                        portal.getInt("spawnTicks"),
                        portal.getInt("initialSpawnPopulation"),
                        portal.getInt("targetPopulation")
                ));
            }
        }
        ReignOfNether.LOGGER.info("SurvivalSaveData.load: wave number: " + data.waveNumber);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        //ReignOfNether.LOGGER.info("SurvivalSaveData.save: " + waveNumber);
        tag.putBoolean("isEnabled", this.isEnabled);
        tag.putInt("waveStateVersion", CURRENT_VERSION);
        tag.putInt("waveNumber", this.waveNumber);
        tag.putInt("lastStartedWaveNumber", this.lastStartedWaveNumber);
        tag.putString("difficulty", this.difficulty.name());
        tag.putLong("randomSeed", this.randomSeed);
        ListTag portalTags = new ListTag();
        portalStates.forEach((origin, state) -> {
            CompoundTag portal = new CompoundTag();
            portal.putInt("x", origin.getX());
            portal.putInt("y", origin.getY());
            portal.putInt("z", origin.getZ());
            portal.putInt("waveNumber", state.waveNumber());
            portal.putInt("spawnTicks", state.spawnTicks());
            portal.putInt("initialSpawnPopulation", state.initialSpawnPopulation());
            portal.putInt("targetPopulation", state.targetPopulation());
            portalTags.add(portal);
        });
        tag.put("wavePortals", portalTags);
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
