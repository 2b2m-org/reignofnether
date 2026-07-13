package com.solegendary.reignofnether.gamerules;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.gamemode.ClientGameModeHelper;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class GameruleClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameruleClientboundPacket> TYPE =
        payloadType("gamerule_clientbound");
    public static final StreamCodec<FriendlyByteBuf, GameruleClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(GameruleClientboundPacket::encode, GameruleClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<GameruleClientboundPacket> type() {
        return TYPE;
    }

    GameruleAction action;
    String playerName;
    Long value;

    public static void setLogFalling(boolean logFalling) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_LOG_FALLING, "", logFalling ? 1L : 0L));
    }
    public static void setNeutralAggro(boolean neutralAggro) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_NEUTRAL_AGGRO, "", neutralAggro ? 1L : 0L));
    }
    public static void setMaxPopulation(long maxPopulation) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_MAX_POPULATION, "", maxPopulation));
    }
    public static void setUnitGriefing(boolean unitGriefing) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_UNIT_GRIEFING, "", unitGriefing ? 1L : 0L));
    }
    public static void setPlayerGriefing(boolean playerGriefing) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_PLAYER_GRIEFING, "", playerGriefing ? 1L : 0L));
    }
    public static void setGroundYLevel(long groundYLevel) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_GROUND_Y_LEVEL, "", groundYLevel));
    }
    public static void setFlyingMaxYLevel(long flyingMaxYLevel) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_FLYING_MAX_Y_LEVEL, "", flyingMaxYLevel));
    }
    public static void setAllowBeacons(boolean allowBeacons) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_ALLOW_BEACONS, "", allowBeacons ? 1L : 0L));
    }
    public static void setPvpModesOnly(boolean pvpModesOnly) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_PVP_MODES_ONLY, "", pvpModesOnly ? 1L : 0L));
    }
    public static void setBeaconWinMinutes(long beaconWinMinutes) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_BEACON_WIN_MINUTES, "", beaconWinMinutes));
    }
    public static void setSlantedBuilding(boolean slantedBuilding) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_SLANTED_BUILDING, "", slantedBuilding ? 1L : 0L));
    }
    public static void setAllowedHeroes(long allowedHeroes) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_ALLOWED_HEROES, "", allowedHeroes));
    }
    public static void setLockAlliances(boolean lockAlliances) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_LOCK_ALLIANCES, "", lockAlliances ? 1L : 0L));
    }
    public static void setScenarioMode(boolean scenarioMode) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_SCENARIO_MODE, "", scenarioMode ? 1L : 0L));
    }
    public static void setCoopMode(boolean coopMode) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_COOP_MODE, "", coopMode ? 1L : 0L));
    }
    public static void setBuildingsOutsideBorder(boolean buildingsOutsideBorder) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_BUILDINGS_OUTSIDE_BORDER, "", buildingsOutsideBorder ? 1L : 0L));
    }
    public static void setRtsPathfinding(boolean rtsPathfinding) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_RTS_PATHFINDING, "", rtsPathfinding ? 1L : 0L));
    }
    public static void setAnimalSpawnYDiff(long yDiff) {
        PacketDistributor.sendToAllPlayers(new GameruleClientboundPacket(GameruleAction.SET_ANIMAL_SPAWN_Y_DIFF, "", yDiff));
    }

    public GameruleClientboundPacket(GameruleAction action, String playerName, Long value) {
        this.action = action;
        this.playerName = playerName;
        this.value = value;
    }

    public GameruleClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(GameruleAction.class);
        this.playerName = buffer.readUtf();
        this.value = buffer.readLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.playerName);
        buffer.writeLong(this.value);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {
            {
                        switch (action) {
                            case SET_LOG_FALLING -> GameruleClient.doLogFalling = value == 1L;
                            case SET_NEUTRAL_AGGRO -> GameruleClient.neutralAggro = value == 1L;
                            case SET_MAX_POPULATION -> GameruleClient.maxPopulation = Math.toIntExact(value);
                            case SET_UNIT_GRIEFING -> GameruleClient.doUnitGriefing = value == 1L;
                            case SET_PLAYER_GRIEFING -> GameruleClient.doPlayerGriefing = value == 1L;
                            case SET_GROUND_Y_LEVEL -> {
                                GameruleClient.groundYLevel = value;
                                OrthoviewClientEvents.setMinOrthoviewY(value + 30);
                            }
                            case SET_FLYING_MAX_Y_LEVEL -> GameruleClient.flyingMaxYLevel = value;
                            case SET_ALLOW_BEACONS -> GameruleClient.allowBeacons = value == 1L;
                            case SET_PVP_MODES_ONLY -> {
                                GameruleClient.pvpModesOnly = value == 1L;
                                if (GameruleClient.pvpModesOnly) {
                                    ClientGameModeHelper.gameMode = GameMode.CLASSIC;
                                }
                            }
                            case SET_BEACON_WIN_MINUTES -> GameruleClient.beaconWinMinutes = value;
                            case SET_SLANTED_BUILDING -> GameruleClient.slantedBuilding = value == 1L;
                            case SET_ALLOWED_HEROES -> {
                                GameruleClient.allowedHeroes = Math.toIntExact(value);
                                for (BuildingPlacement buildingPlacement : BuildingClientEvents.getBuildings()) {
                                    if (buildingPlacement instanceof ProductionPlacement pp) {
                                        pp.updateButtons();
                                    }
                                }
                            }
                            case SET_LOCK_ALLIANCES -> GameruleClient.lockAlliances = value == 1L;
                            case SET_SCENARIO_MODE -> GameruleClient.scenarioMode = value == 1L;
                            case SET_COOP_MODE -> GameruleClient.coopMode = value == 1L;
                            case SET_BUILDINGS_OUTSIDE_BORDER -> GameruleClient.buildingsOutsideBorder = value == 1L;
                            case SET_RTS_PATHFINDING -> GameruleClient.rtsPathfinding = value == 1L;
                            case SET_ANIMAL_SPAWN_Y_DIFF -> GameruleClient.animalSpawnYDiff = Math.toIntExact(value);
                        }
                    }
        });
    }
}
