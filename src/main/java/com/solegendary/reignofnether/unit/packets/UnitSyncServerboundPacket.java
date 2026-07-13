package com.solegendary.reignofnether.unit.packets;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.UnitSyncAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;


public class UnitSyncServerboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnitSyncServerboundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:unit_sync_serverbound");
    public static final StreamCodec<FriendlyByteBuf, UnitSyncServerboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(UnitSyncServerboundPacket::encode, UnitSyncServerboundPacket::new);

    @Override
    public CustomPacketPayload.Type<UnitSyncServerboundPacket> type() {
        return TYPE;
    }

    private final UnitSyncAction syncAction;
    private final int entityId;

    public static void requestSyncAbilities(int unitId) {
        PacketDistributor.sendToServer(new UnitSyncServerboundPacket(UnitSyncAction.REQUEST_SYNC_ABILITIES, unitId));
    }

    // packet-handler functions
    public UnitSyncServerboundPacket(
        UnitSyncAction syncAction,
        int unitId
    ) {
        this.syncAction = syncAction;
        this.entityId = unitId;
    }

    public UnitSyncServerboundPacket(FriendlyByteBuf buffer) {
        this.syncAction = buffer.readEnum(UnitSyncAction.class);
        this.entityId = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.syncAction);
        buffer.writeInt(this.entityId);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (this.syncAction == UnitSyncAction.REQUEST_SYNC_ABILITIES) {
                for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                    if (entity.getId() == this.entityId) {
                        UnitSyncAbilityClientboundPacket.sendSyncAbilitiesPacket(entity);
                    }
                }
            }
        });
    }
}
