package com.solegendary.reignofnether.unit.packets;

import static com.solegendary.reignofnether.ReignOfNether.payloadType;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.util.ArrayUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

// send a list of the old units ids that have been converted into new units (with new ids) so the client can retain
// these units' selections, goals and continue the same actions they were taking
public class UnitConvertClientboundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnitConvertClientboundPacket> TYPE =
        payloadType("unit_convert_clientbound");
    public static final StreamCodec<FriendlyByteBuf, UnitConvertClientboundPacket> STREAM_CODEC =
        StreamCodec.ofMember(UnitConvertClientboundPacket::encode, UnitConvertClientboundPacket::new);

    @Override
    public CustomPacketPayload.Type<UnitConvertClientboundPacket> type() {
        return TYPE;
    }

    private final String ownerName; // the player that owns these units
    private final int[] oldUnitIds; // units to be controlled
    private final int[] newUnitIds; // units to be controlled

    public static void syncConvertedUnits(String ownerName, List<Integer> oldUnitIds, List<Integer> newUnitIds) {
        PacketDistributor.sendToAllPlayers(new UnitConvertClientboundPacket(
                ownerName,
                ArrayUtil.intListToArray(oldUnitIds),
                ArrayUtil.intListToArray(newUnitIds)
            ));
    }

    // packet-handler functions
    public UnitConvertClientboundPacket(
            String ownerName,
            int[] oldUnitIds,
            int[] newUnitIds
    ) {
        this.ownerName = ownerName;
        this.oldUnitIds = oldUnitIds;
        this.newUnitIds = newUnitIds;
    }

    public UnitConvertClientboundPacket(FriendlyByteBuf buffer) {
        this.ownerName = buffer.readUtf();
        this.oldUnitIds = buffer.readVarIntArray();
        this.newUnitIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.ownerName);
        buffer.writeVarIntArray(this.oldUnitIds);
        buffer.writeVarIntArray(this.newUnitIds);
    }

    // server-side packet-consuming functions
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            UnitClientEvents.syncConvertedUnits(ownerName, oldUnitIds, newUnitIds);
        });
    }
}
