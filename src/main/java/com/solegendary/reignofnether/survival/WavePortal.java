package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.survival.spawners.PiglinWaveSpawner;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;
import com.solegendary.reignofnether.unit.units.piglins.HoglinUnit;
import com.solegendary.reignofnether.unit.units.piglins.MagmaCubeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static com.solegendary.reignofnether.survival.SurvivalServerEvents.ENEMY_OWNER_NAME;
import static com.solegendary.reignofnether.survival.spawners.PiglinWaveSpawner.checkAndApplyArmour;
import static com.solegendary.reignofnether.survival.spawners.WaveSpawner.getModifiedPopCost;

public class WavePortal {

    record SaveState(int waveNumber, int spawnTicks, int initialSpawnPopulation, int targetPopulation) {}

    private static final int SPAWN_TICKS_MAX = 600;
    private int spawnTicks = 0;

    public final PortalPlacement portal;
    public final Wave wave;
    private final int targetPopulation;
    private int initialSpawnPop;

    private BlockPos lastOnPos;

    public WavePortal(PortalPlacement portal, Wave wave) {
        this(portal, wave, new SaveState(
                wave.number,
                0,
                (wave.population * PlayerServerEvents.rtsPlayers.size() / wave.getNumPortals()) / 2,
                wave.population * PlayerServerEvents.rtsPlayers.size()
        ));
    }

    WavePortal(PortalPlacement portal, Wave wave, SaveState state) {
        this.portal = portal;
        this.portal.selfBuilding = true;
        if (!this.portal.isBuilt && !this.portal.hasPendingBlockPlacements())
            this.portal.refreshBlocks();
        this.wave = wave;
        this.targetPopulation = state.targetPopulation();
        this.spawnTicks = state.spawnTicks();
        this.initialSpawnPop = state.initialSpawnPopulation();
    }

    public PortalPlacement getPortal() {
        return portal;
    }

    SaveState saveState() {
        return new SaveState(wave.number, spawnTicks, initialSpawnPop, targetPopulation);
    }

    public void tick(long ticksToAdd) {
        if (!portal.isBuilt || targetPopulation == 0)
            return;

        if (initialSpawnPop > 0) {
            doSpawn();
        } else {
            int pop = SurvivalServerEvents.getTotalEnemyPopulation();
            if (pop > targetPopulation * 2) {
                return;
            }
            else if (spawnTicks >= SPAWN_TICKS_MAX) {
                spawnTicks = 0;
                doSpawn();
            } else {
                spawnTicks += ticksToAdd;
            }
        }
    }

    public void doSpawn() {
        Random random = new Random();

        EntityType<? extends Unit> mobType = (EntityType<? extends Unit>) PiglinWaveSpawner.getRandomUnitOfTier(wave.highestUnitTier);

        ServerLevel level = (ServerLevel) portal.getLevel();

        // produceUnit spawns them before applying the ownerName, meaning they aren't registered as WaveEnemies automatically
        Entity entity = portal.produceUnit(level, mobType, ENEMY_OWNER_NAME, true);

        if (entity instanceof GhastUnit ghastUnit)
            ghastUnit.move(MoverType.SELF, new Vec3(0,10,0));

        if (entity instanceof Unit unit) {
            checkAndApplyArmour((LivingEntity) unit, wave.highestUnitTier);

            if (wave.highestUnitTier >= 3 && random.nextBoolean() && entity instanceof HoglinUnit hoglinUnit) {
                Entity entityPassenger = UnitServerEvents.spawnMob(EntityRegistrar.HEADHUNTER_UNIT.get(),
                        level, hoglinUnit.getOnPos(), ENEMY_OWNER_NAME);
                if (entityPassenger instanceof Unit pUnit) {
                    entityPassenger.startRiding(hoglinUnit);
                    if (initialSpawnPop > 0)
                        initialSpawnPop -= getModifiedPopCost(pUnit);
                }
            }

            if (unit instanceof MagmaCubeUnit magmaCubeUnit)
                magmaCubeUnit.setSize(wave.highestUnitTier, true);

            SurvivalServerEvents.registerEnemy(unit);
            if (initialSpawnPop > 0)
                initialSpawnPop -= getModifiedPopCost(unit);
        }
    }
}
