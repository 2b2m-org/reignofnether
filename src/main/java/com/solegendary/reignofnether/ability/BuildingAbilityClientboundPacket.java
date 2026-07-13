package com.solegendary.reignofnether.ability;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.monsters.Graveyard;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.Blacksmith;
import com.solegendary.reignofnether.building.buildings.villagers.Library;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;


public class BuildingAbilityClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuildingAbilityClientboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:building_ability_clientbound");
    public static final StreamCodec<FriendlyByteBuf, BuildingAbilityClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(BuildingAbilityClientboundPacket::encode, BuildingAbilityClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<BuildingAbilityClientboundPacket> type() {
        return TYPE;
    }

    UnitAction abilityAction;
    BlockPos buildingPos;

    public static void doAbility(UnitAction ability, BlockPos buildingPos) {
        PacketDistributor.sendToAllPlayers(new BuildingAbilityClientboundPacket(ability, buildingPos));
    }

    // packet-handler functions
    public BuildingAbilityClientboundPacket(UnitAction ability, BlockPos buildingPos) {
        this.abilityAction = ability;
        this.buildingPos = buildingPos;
    }

    public BuildingAbilityClientboundPacket(FriendlyByteBuf buffer) {
        this.abilityAction = buffer.readEnum(UnitAction.class);
        this.buildingPos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(abilityAction);
        buffer.writeBlockPos(buildingPos);
    }

    // client-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            BuildingPlacement building = BuildingUtils.findBuilding(true, buildingPos);
            if (building != null && building.getBuilding() instanceof Library) {
                Ability ability = null;
                for (Ability abl : building.getAbilities())
                    if (abl.action == abilityAction)
                        ability = abl;
                if (ability instanceof EnchantAbility enchantAbility) {
                    if (building.getDataStorage().getData(Library.AUTO_CAST_ENCHANT) == enchantAbility)
                        building.getDataStorage().setData(Library.AUTO_CAST_ENCHANT, null);
                    else
                        building.getDataStorage().setData(Library.AUTO_CAST_ENCHANT, enchantAbility);
                }
            }
            else if (building != null && building.getBuilding() instanceof Blacksmith) {
                Ability ability = null;
                for (Ability abl : building.getAbilities())
                    if (abl.action == abilityAction)
                        ability = abl;
                if (ability instanceof EquipAbility equipAbility) {
                    building.getDataStorage().setData(Blacksmith.AUTO_CAST_EQUIP, equipAbility);
                }
            }
            else if (building instanceof GraveyardPlacement gy) {
                if (abilityAction == UnitAction.SET_GRAVEYARD_RELEASE_ON)
                    gy.autoRelease = true;
                else if (abilityAction == UnitAction.SET_GRAVEYARD_RELEASE_OFF)
                    gy.autoRelease = false;
            }
        });
    }
}