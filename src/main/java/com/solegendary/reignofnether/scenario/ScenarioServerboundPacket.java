package com.solegendary.reignofnether.scenario;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

import static com.solegendary.reignofnether.scenario.ScenarioAction.*;

public class ScenarioServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ScenarioServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:scenario_serverbound");
    public static final StreamCodec<FriendlyByteBuf, ScenarioServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(ScenarioServerboundPacket::encode, ScenarioServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<ScenarioServerboundPacket> type() {
        return TYPE;
    }

    public ScenarioAction action;
    public int roleIndex;
    public int x;
    public int y;
    public int z;
    public boolean boolValue;
    public int intValue;
    public String strValue;

    public static void setUnitRole(int roleIndex, int unitId) {
        if (!MiscUtil.isConnected()) return;
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_UNIT_ROLE, roleIndex, 0,0,0, false, unitId, ""));
    }

    public static void setBuildingRole(int roleIndex, BlockPos bp) {
        if (!MiscUtil.isConnected()) return;
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_BUILDING_ROLE, roleIndex, bp.getX(), bp.getY(), bp.getZ(), false, 0, ""));
    }

    public static void setStartingResources(int roleIndex, ResourceName resName, int amount) {
        if (!MiscUtil.isConnected()) return;
        ScenarioAction scenarioAction = switch (resName) {
            case FOOD -> ScenarioAction.SET_ROLE_STARTING_FOOD;
            case WOOD -> ScenarioAction.SET_ROLE_STARTING_WOOD;
            case ORE -> ScenarioAction.SET_ROLE_STARTING_ORE;
            case NONE -> null;
        };
        if (scenarioAction != null)
            PacketDistributor.sendToServer(new ScenarioServerboundPacket(scenarioAction, roleIndex, 0,0,0, false, amount, ""));
    }

    public static void setTeamNumber(int roleIndex, int teamNumber) {
        if (!MiscUtil.isConnected()) return;
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_ROLE_TEAM_NUMBER, roleIndex, 0,0,0, false, teamNumber, ""));
    }

    public static void setRoleFaction(int roleIndex, Faction faction) {
        if (!MiscUtil.isConnected()) return;
        ScenarioAction scenarioAction = switch (faction) {
            case VILLAGERS -> ScenarioAction.SET_ROLE_FACTION_VILLAGER;
            case MONSTERS -> ScenarioAction.SET_ROLE_FACTION_MONSTER;
            case PIGLINS -> ScenarioAction.SET_ROLE_FACTION_PIGLIN;
            default -> ScenarioAction.SET_ROLE_FACTION_NEUTRAL;
        };
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(scenarioAction, roleIndex, 0,0,0, false, 0, ""));
    }

    public static void setRoleIsNpc(int roleIndex, boolean isNpc) {
        if (!MiscUtil.isConnected()) return;
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_ROLE_NPC, roleIndex, 0,0,0, isNpc, 0, ""));
    }

    public static void setRoleName(int roleIndex, String name) {
        if (!MiscUtil.isConnected()) return;
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_ROLE_NAME, roleIndex, 0,0,0, false, 0, name));
    }

    public static void saveScenario() {
        if (!MiscUtil.isConnected()) return;
        PacketDistributor.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SAVE_SCENARIO, 0, 0,0,0, false, 0, ""));
    }

    public ScenarioServerboundPacket(ScenarioAction action, int roleIndex, int x, int y, int z,
                                   boolean boolValue, int intValue, String strValue) {
        this.action = action;
        this.roleIndex = roleIndex;
        this.x = x;
        this.y = y;
        this.z = z;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.strValue = strValue;
    }

    public ScenarioServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(ScenarioAction.class);
        this.roleIndex = buffer.readInt();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.boolValue = buffer.readBoolean();
        this.intValue = buffer.readInt();
        this.strValue = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeInt(this.roleIndex);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeBoolean(this.boolValue);
        buffer.writeInt(this.intValue);
        buffer.writeUtf(this.strValue);
    }

    private static final List<ScenarioAction> NON_ROLE_EDIT_ACTIONS = List.of(
            SET_SCENARIO_NAME,
            SET_UNIT_ROLE,
            SET_BUILDING_ROLE,
            SAVE_SCENARIO
    );

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!SandboxServer.isAnyoneASandboxPlayer())
                return;

            ReignOfNether.LOGGER.info("[Scenario] action={}(roleIndex={}, intValue={}, strValue={})", this.action, this.roleIndex, this.intValue, this.strValue);

            ScenarioRole role = ScenarioUtils.getScenarioRole(false, roleIndex);
            if (role == null && !NON_ROLE_EDIT_ACTIONS.contains(action))
                return;

            switch (this.action) {
                case SET_ROLE_STARTING_FOOD -> role.startingResources.food = intValue;
                case SET_ROLE_STARTING_WOOD -> role.startingResources.wood = intValue;
                case SET_ROLE_STARTING_ORE -> role.startingResources.ore = intValue;
                case SET_ROLE_FACTION_VILLAGER -> role.faction = Faction.VILLAGERS;
                case SET_ROLE_FACTION_MONSTER -> role.faction = Faction.MONSTERS;
                case SET_ROLE_FACTION_PIGLIN -> role.faction = Faction.PIGLINS;
                case SET_ROLE_FACTION_NEUTRAL -> role.faction = Faction.NEUTRAL;
                case SET_ROLE_NAME -> {
                    role.name = strValue;
                    // since this is sent from a text input that is updated on defocus, save here in case the user pressed close & save while still focused
                    ScenarioServerEvents.saveScenarioRoles();
                }
                case SET_ROLE_TEAM_NUMBER -> {
                    role.teamNumber = intValue;
                    ServerPlayer serverPlayer = (ServerPlayer) context.player();
                    if (serverPlayer != null) {
                        MinecraftServer server = serverPlayer.level().getServer();
                        if (server != null) {
                            if (server.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get())
                                AlliancesServerEvents.applyScenarioAlliances();
                            if (server.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())
                                AlliancesServerEvents.applyCoopAlliances();
                        }
                    }
                }
                case SET_ROLE_NPC -> role.isNpc = boolValue;
                case SET_UNIT_ROLE -> {
                    for (LivingEntity le : UnitServerEvents.getAllUnits()) {
                        if (le instanceof Unit unit && le.getId() == intValue) {
                            unit.setScenarioRoleIndex(roleIndex);
                            UnitSyncClientboundPacket.sendSyncScenarioRoleIndexPacket(unit);
                        }
                    }
                }
                case SET_BUILDING_ROLE -> {
                    BuildingPlacement bpl = BuildingUtils.findBuilding(false, new BlockPos(x,y,z));
                    if (bpl != null) {
                        bpl.scenarioRoleIndex = roleIndex;
                        BuildingClientboundPacket.syncBuilding(bpl.originPos, bpl.getBlocksPlaced(), bpl.partialBlocksDestroyed, bpl.ownerName, bpl.scenarioRoleIndex);
                    }
                }
                case SET_SCENARIO_NAME -> {
                }
                case SAVE_SCENARIO -> ScenarioServerEvents.saveScenarioRoles();
            }
        });
    }
}
