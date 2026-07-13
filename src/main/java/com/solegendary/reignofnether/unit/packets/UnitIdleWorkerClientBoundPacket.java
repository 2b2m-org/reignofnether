package com.solegendary.reignofnether.unit.packets;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.ArrayUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedList;

// send a list of worker unit ids that are idle at this point in time
public class UnitIdleWorkerClientBoundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnitIdleWorkerClientBoundPacket> TYPE =
        CustomPacketPayload.createType("reignofnether:unit_idle_worker_client_bound");
    public static final StreamCodec<FriendlyByteBuf, UnitIdleWorkerClientBoundPacket> STREAM_CODEC =
        StreamCodec.ofMember(UnitIdleWorkerClientBoundPacket::encode, UnitIdleWorkerClientBoundPacket::new);

    @Override
    public CustomPacketPayload.Type<UnitIdleWorkerClientBoundPacket> type() {
        return TYPE;
    }

    private final int[] oldUnitIds; // units to be controlled

    public static void sendIdleWorkerPacket() {
        var units = new LinkedList<Integer>();
        for (LivingEntity livingEntity : UnitServerEvents.getAllUnits()) {
            if (livingEntity instanceof WorkerUnit wu && WorkerUnit.isIdle(wu)) units.add(livingEntity.getId());
        }
        PacketDistributor.sendToAllPlayers(new UnitIdleWorkerClientBoundPacket(ArrayUtil.intListToArray(units)));
    }

    // packet-handler functions
    public UnitIdleWorkerClientBoundPacket(
            int[] oldUnitIds
    ) {
        this.oldUnitIds = oldUnitIds;
    }

    public UnitIdleWorkerClientBoundPacket(FriendlyByteBuf buffer) {
        this.oldUnitIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarIntArray(this.oldUnitIds);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            UnitClientEvents.syncIdleWorkers(oldUnitIds);
        });
    }
}
